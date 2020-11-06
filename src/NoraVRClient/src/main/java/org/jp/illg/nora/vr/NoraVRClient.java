package org.jp.illg.nora.vr;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.nora.vr.NoraVRClientStatusInformation.NoraVRClientRouteDirection;
import org.jp.illg.nora.vr.NoraVRClientStatusInformation.NoraVRClientRouteReport;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.nora.vr.protocol.model.NoraVRCommandType;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.nora.vr.protocol.model.NoraVRVoicePacket;
import org.jp.illg.nora.vr.protocol.model.VTAMBE;
import org.jp.illg.nora.vr.protocol.model.VTOPUS;
import org.jp.illg.nora.vr.protocol.model.VTPCM;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.audio.vocoder.VoiceVocoder;
import org.jp.illg.util.audio.vocoder.opus.OpusVocoderFactory;
import org.jp.illg.util.audio.vocoder.pcm.PCMVocoder;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRClient {

	private static final int uplinkTransmitTimeoutSecondsDefault = 180; // sec

	private static final Random frameIDRandom = new Random(System.currentTimeMillis() ^ 0x19AD65D1);


	private class NoraVRAudioPacket<T extends Buffer> {
		public final boolean frameEnd;
		public final byte[] slowdata;
		public final T audio;
		public final NoraVRCodecType codec;

		public String repeater2Callsign;
		public String repeater1Callsign;
		public String myCallsignLong;
		public String myCallsignShort;
		public String yourCallsign;

		public int frameID;

		NoraVRAudioPacket(
			final NoraVRCodecType codec,
			final int frameID, final T audio, final byte[] slowdata
		) {
			this(codec, frameID, audio, slowdata, false);
		}

		NoraVRAudioPacket(
			final NoraVRCodecType codec,
			final int frameID,
			final T audio,
			final byte[] slowdata,
			final boolean frameEnd
		) {
			super();

			this.codec = codec;
			this.audio = audio;
			this.slowdata = slowdata;
			this.frameEnd = frameEnd;

			repeater2Callsign = DSTARDefines.EmptyLongCallsign;
			repeater1Callsign = DSTARDefines.EmptyLongCallsign;
			myCallsignLong = DSTARDefines.EmptyLongCallsign;
			myCallsignShort = DSTARDefines.EmptyShortCallsign;
			yourCallsign = DSTARDefines.EmptyLongCallsign;

			this.frameID = frameID;
		}
	}

	public class NoraVRDownlinkAudioPacket<T extends Buffer> extends NoraVRAudioPacket<T>{

		public byte[] flags = null;
		public boolean frameStart = false;
		public int shortSequence;

		public NoraVRDownlinkAudioPacket(
				final NoraVRCodecType codec,
				final int frameID,
				final T audio,
				final byte[] slowdata,
				final int shortSequence
		) {
			this(codec, frameID, audio, slowdata, (shortSequence & 0x80) != 0x0, shortSequence);
		}

		public NoraVRDownlinkAudioPacket(
			final NoraVRCodecType codec,
			final int frameID,
			final T audio,
			final byte[] slowdata,
			final boolean frameEnd,
			final int shortSequence
		) {
			super(codec, frameID, audio, slowdata, frameEnd);

			this.shortSequence = shortSequence;
		}
	}

	public class NoraVRDownlinkAudioPCMPacket extends NoraVRDownlinkAudioPacket<ShortBuffer>{

		public NoraVRDownlinkAudioPCMPacket(
				final NoraVRCodecType codec,
				final int frameID,
				ShortBuffer audio,
				byte[] slowdata,
				final int shortSequence
		) {
			this(codec, frameID, audio, slowdata, (shortSequence & 0x80) != 0x0, shortSequence);
		}

		public NoraVRDownlinkAudioPCMPacket(
			final NoraVRCodecType codec,
			final int frameID,
			final ShortBuffer audio,
			final byte[] slowdata,
			final boolean frameEnd,
			final int shortSequence
		) {
			super(codec, frameID, audio, slowdata, frameEnd, shortSequence);
		}
	}

	public class NoraVRDownlinkAudioAMBEPacket extends NoraVRDownlinkAudioPacket<ByteBuffer>{

		public NoraVRDownlinkAudioAMBEPacket(
			final NoraVRCodecType codec,
			final int frameID,
			ByteBuffer audio,
			byte[] slowdata,
			final int shortSequence
		) {
			this(codec, frameID, audio, slowdata, (shortSequence & 0x80) != 0x0, shortSequence);
		}

		public NoraVRDownlinkAudioAMBEPacket(
			final NoraVRCodecType codec,
			final int frameID,
			final ByteBuffer audio,
			final byte[] slowdata,
			final boolean frameEnd,
			final int shortSequence
		) {
			super(codec, frameID, audio, slowdata, frameEnd, shortSequence);
		}
	}

	private class NoraVRUplinkInputAudioPCM extends NoraVRAudioPacket<ShortBuffer>{

		public NoraVRUplinkInputAudioPCM(
			final NoraVRCodecType codec, ShortBuffer audio, byte[] slowdata
		) {
			super(codec, 0x0, audio, slowdata);
		}

		public NoraVRUplinkInputAudioPCM(
			final NoraVRCodecType codec,
			final ShortBuffer audio,
			final byte[] slowdata,
			final boolean frameEnd
		) {
			super(codec, 0x0, audio, slowdata, frameEnd);

			if(codec == NoraVRCodecType.AMBE)
				throw new IllegalArgumentException();
		}
	}

	private class NoraVRUplinkInputAudioAMBE extends NoraVRAudioPacket<ByteBuffer>{

		public NoraVRUplinkInputAudioAMBE(ByteBuffer audio, byte[] slowdata) {
			super(NoraVRCodecType.AMBE, 0x0, audio, slowdata);
		}

		public NoraVRUplinkInputAudioAMBE(
			final ByteBuffer audio,
			final byte[] slowdata,
			final boolean frameEnd
		) {
			super(NoraVRCodecType.AMBE, 0x0, audio, slowdata, frameEnd);
		}
	}


	private class UplinkInfo {
		final Timer timekeeper;
		final NewDataSegmentEncoder slowdataEncoder;
		int frameID;
		String myCallsignLong;
		String myCallsignShort;
		String yourCallsign;
		String repeater2Callsign;
		String repeater1Callsign;
		boolean isAMBE;
		boolean isSlowdataPresent;

		private UplinkInfo() {
			super();

			timekeeper = new Timer();
			slowdataEncoder = new NewDataSegmentEncoder();

			clear();
		}

		void clear(){
			timekeeper.updateTimestamp();
			slowdataEncoder.reset();
			frameID = 0x0;
			myCallsignLong = DSTARDefines.EmptyLongCallsign;
			myCallsignShort = DSTARDefines.EmptyShortCallsign;
			yourCallsign = DSTARDefines.EmptyLongCallsign;
			repeater2Callsign = DSTARDefines.EmptyLongCallsign;
			repeater1Callsign = DSTARDefines.EmptyLongCallsign;
			isAMBE = false;
			isSlowdataPresent = false;
		}
	}


	private class UplinkProcessInfo extends UplinkInfo {
		int longSequence;
		int shortSequence;
		long packetCount;
		boolean isTimeout;

		private UplinkProcessInfo(){
			super();

			clear();
		}

		@Override
		void clear(){
			super.clear();

			longSequence = 0x0;
			shortSequence = 0x0;

			packetCount = 0;
			isTimeout = false;
		}
	}

	private final Runnable mainProcessLoopTask = new Runnable() {

		@Override
		public void run() {
			if(
				workerThread == null ||
				workerThread.getId() != Thread.currentThread().getId()
			) {return;}

			currentCodec = codecType;

			if(!createVocoder(currentCodec)) {
				if(log.isErrorEnabled())
					log.error("Could not create vocoder instance = " + currentCodec + ".");

				return;
			}

			boolean workerStopRequest = false;
			while(!workerStopRequest) {
				try {
					if(!protocol.process(workerThreadAvailable)) {workerStopRequest = true;}

					if(changeRequestCodec != null) {
						final NoraVRConfiguration config = protocol.getClientConfig().clone();

						config.setSupportedCodecAMBE(changeRequestCodec == NoraVRCodecType.AMBE);
						config.setSupportedCodecPCM(changeRequestCodec == NoraVRCodecType.PCM);
						config.setSupportedCodecOpus64k(changeRequestCodec == NoraVRCodecType.Opus64k);
						config.setSupportedCodecOpus24k(changeRequestCodec == NoraVRCodecType.Opus24k);
						config.setSupportedCodecOpus8k(changeRequestCodec == NoraVRCodecType.Opus8k);

						if(!createVocoder(changeRequestCodec)) {
							if(log.isWarnEnabled())
								log.warn("Could not create vocoder instance = " + codecType + ".");

							return;
						}

						codecType = changeRequestCodec;
						currentCodec = changeRequestCodec;

						protocol.updateClientConfiguration(config);

						changeRequestCodec = null;
					}

					processUplinkPacket();

					processDownlinkPackets();

					processLoopLocker.lock();
					try {
						processLoop.await(5, TimeUnit.MILLISECONDS);
					}catch(InterruptedException ex) {

					}finally {
						processLoopLocker.unlock();
					}
				}catch(Exception ex) {
					if(log.isErrorEnabled()) {
						log.error(NoraVRClient.class.getSimpleName() + " worker thread has exception occurred.", ex);
					}

					break;
				}
			}

			if(vocoder != null) {vocoder.dispose();}
		}
	};

	@Getter
	private int uplinkTransmitTimeoutSeconds;

	@Getter
	private NoraVRClientStatusInformation statusInformation;

	private boolean isWorkerExecutorPresent;
	private ExecutorService workerExecutor;

	private final Lock processLoopLocker;
	private final Condition processLoop;

	private final NoraVRClientProtocolProcessor protocol;
	private Thread workerThread;
	private boolean workerThreadAvailable;

	private final NoraVREventListener eventListener;
	private final boolean isRFNode;

	private final UplinkInfo uplinkInputInfo;
	private final UplinkProcessInfo uplinkProcessInfo;

	private final Timer downlinkTimekeeper;
	private int currentDownlinkFrameID;
	private byte[] currentDownlinkFlags;
	private String currentDownlinkMyCallsignLong;
	private String currentDownlinkMyCallsignShort;
	private String currentDownlinkYourCallsign;
	private String currentDownlinkRepeater2Callsign;
	private String currentDownlinkRepeater1Callsign;
	private int downlinkShortSequence;
	private int downlinkLongSequence;
	private final Queue<NoraVRVoicePacket<?>> downlinkSilencePackets;

	private final Queue<NoraVRAudioPacket<Buffer>> writeVoicePackets;
	private final Queue<NoraVRDownlinkAudioPacket<Buffer>> readVoicePackets;
	private final Lock rwVoicePacketsLocker;

	private NoraVRCodecType codecType, currentCodec;
	private NoraVRCodecType changeRequestCodec;

	private VoiceVocoder<ShortBuffer> vocoder;

	public NoraVRClient(
		final NoraVREventListener eventListener,
		final boolean isRFNode,
		final String applicationName,
		final String applicationVersion
	) {
		super();

		this.eventListener = eventListener;
		this.isRFNode = isRFNode;

		processLoopLocker = new ReentrantLock();
		processLoop = processLoopLocker.newCondition();

		protocol = new NoraVRClientProtocolProcessor(
			processLoopLocker, processLoop,
			eventListener, applicationName, applicationVersion
		);

		writeVoicePackets = new LinkedList<NoraVRAudioPacket<Buffer>>();
		readVoicePackets = new LinkedList<NoraVRDownlinkAudioPacket<Buffer>>();
		rwVoicePacketsLocker = new ReentrantLock();

		uplinkInputInfo = new UplinkInfo();
		uplinkProcessInfo = new UplinkProcessInfo();

		downlinkTimekeeper = new Timer();
		downlinkSilencePackets = new LinkedList<NoraVRVoicePacket<?>>();

		uplinkTransmitTimeoutSeconds = uplinkTransmitTimeoutSecondsDefault;

		statusInformation = new NoraVRClientStatusInformation(null, null);

		codecType = null;
		currentCodec = null;
		changeRequestCodec = null;

		workerExecutor = null;
		isWorkerExecutorPresent = false;
	}

	public void setUplinkTransmitTimeoutSeconds(final int timeoutSeconds) {
		if(timeoutSeconds < 60 || timeoutSeconds > 180)
			throw new IllegalArgumentException("timeoutSeconds must have 60-180 seconds.");

		uplinkTransmitTimeoutSeconds = timeoutSeconds;
	}

	public boolean isRunning() {
		return
			workerThreadAvailable &&
			workerThread != null && workerThread.isAlive() &&
			protocol != null && protocol.isRunning();
	}

	public boolean connect(
		@NonNull final String loginUserCallsign,
		@NonNull final String loginPassword,
		@NonNull final String serverAddress,
		final int serverPort,
		@NonNull final NoraVRCodecType codec
	) {
		return connect(
			loginUserCallsign,
			loginPassword,
			serverAddress,
			serverPort,
			codec,
			null
		);
	}

	public boolean connect(
		@NonNull final String loginUserCallsign,
		@NonNull final String loginPassword,
		@NonNull final String serverAddress,
		final int serverPort,
		@NonNull final NoraVRCodecType codec,
		final ExecutorService workerExecutor
	) {
		disconnect();

		if(workerExecutor != null) {
			if(workerExecutor.isShutdown())
				throw new IllegalStateException("worker executor is terminated.");

			this.workerExecutor = workerExecutor;
			isWorkerExecutorPresent = true;
		}
		else {
			this.workerExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					final Thread thread = new Thread(r);
					thread.setName(NoraVRClient.class.getSimpleName() + "Worker_" + thread.getId());

					return thread;
				}
			});

			isWorkerExecutorPresent = false;
		}

		final NoraVRConfiguration config = new NoraVRConfiguration();
		switch(codec) {
		case AMBE:
			config.setSupportedCodecAMBE(true);
			break;
		case PCM:
			config.setSupportedCodecPCM(true);
			break;
		case Opus64k:
			config.setSupportedCodecOpus24k(true);
			break;
		case Opus24k:
			config.setSupportedCodecOpus24k(true);
			break;
		case Opus8k:
			config.setSupportedCodecOpus8k(true);
			break;
		default:
			return false;
		}
		config.setRfNode(isRFNode);

		if(
			!protocol.connect(
				this.workerExecutor,
				loginUserCallsign, loginPassword,
				serverAddress, serverPort,
				NoraVRClientProtocolProcessor.getSupportProtocolVersion(),
				config
			)
		) {
			disconnect();

			return false;
		}

		this.codecType = codec;

		if(!start()) {
			disconnect();

			return false;
		}

		return true;
	}

	public void disconnect() {
		stop();

		if(workerExecutor != null && !isWorkerExecutorPresent) {workerExecutor.shutdown();}
		workerExecutor = null;
		isWorkerExecutorPresent = false;

		protocol.disconnect();
	}

	public boolean isConnected() {
		return protocol.getConnectionState() == NoraVRClientConnectionState.ConnectionEstablished;
	}

	public boolean isTransmitting() {
		return uplinkProcessInfo.frameID != 0x0;
	}

	public boolean writeVoice(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ShortBuffer audio,
		final boolean isFrameEnd,
		final String shortMessage
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			false,	// isAMBE
			null,
			isFrameEnd,
			shortMessage,
			null
		);
	}

	public boolean writeVoiceAMBE(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ByteBuffer audio,
		final boolean isFrameEnd,
		final String shortMessage
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			true,	// isAMBE
			null,
			isFrameEnd,
			shortMessage,
			null
		);
	}

	public boolean writeVoice(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ShortBuffer audio,
		final boolean isFrameEnd,
		final String shortMessage,
		final String aprsMessage
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			false,	// isAMBE
			null,
			isFrameEnd,
			shortMessage,
			aprsMessage
		);
	}

	public boolean writeVoiceAMBE(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ByteBuffer audio,
		final boolean isFrameEnd,
		final String shortMessage,
		final String aprsMessage
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			true,	// isAMBE
			null,
			isFrameEnd,
			shortMessage,
			aprsMessage
		);
	}

	public boolean writeVoice(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ShortBuffer audio,
		@NonNull final byte[] slowdata,
		final boolean isFrameEnd
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			false,	// isAMBE
			slowdata,
			isFrameEnd,
			null,
			null
		);
	}

	public boolean writeVoiceAMBE(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final ByteBuffer audio,
		@NonNull final byte[] slowdata,
		final boolean isFrameEnd
	) {
		return writeVoiceInternal(
			isDestinationGateway,
			myCallsignLong, myCallsignShort, yourCallsign,
			audio,
			true,	// isAMBE
			slowdata,
			isFrameEnd,
			null,
			null
		);
	}

	public NoraVRDownlinkAudioPacket<?> readVoice() {
		rwVoicePacketsLocker.lock();
		try {
			return readVoicePackets.poll();
		}finally {rwVoicePacketsLocker.unlock();}
	}

	public boolean changeEcho(final boolean on) {
		if(!isConnected()) {return false;}

		final NoraVRConfiguration config = protocol.getClientConfig().clone();

		config.setEchoback(on);

		return protocol.updateClientConfiguration(config);
	}

	public boolean changeCodec(@NonNull final NoraVRCodecType codecType) {
		if(
			!isConnected() ||
			changeRequestCodec != null
		) {return false;}

		changeRequestCodec = codecType;

		return true;
	}

	public String getLoginUserCallsign() {
		return protocol.getLoginUserCallsign();
	}

	public String getLoginPassword() {
		return protocol.getLoginPassword();
	}

	public String getServerAddress() {
		return protocol.getServerAddress();
	}

	public int getServerPort() {
		return protocol.getServerPort();
	}

	public long getClientCode() {
		return protocol.getClientCode();
	}

	public int getProtocolVersion() {
		return protocol.getProtocolVersion();
	}

	public String getGatewayCallsign() {
		return protocol.getGatewayCallsign();
	}

	public String getRepeaterCallsign() {
		return protocol.getRepeaterCallsign();
	}

	public String getLinkedReflectorCallsign() {
		return protocol.getLinkedReflectorCallsign();
	}

	public String getRoutingServiceName() {
		return protocol.getRoutingServiceName();
	}

	public String getRepeaterName() {
		return protocol.getRepeaterName();
	}

	public String getRepeaterLocation() {
		return protocol.getRepeaterLocation();
	}

	public double getRepeaterFrequencyMHz() {
		return protocol.getRepeaterFrequencyMHz();
	}

	public double getRepeaterFrequencyOffsetMHz() {
		return protocol.getRepeaterFrequencyOffsetMHz();
	}

	public double getRepeaterServiceRange() {
		return protocol.getRepeaterServiceRange();
	}

	public double getRepeaterAgl() {
		return protocol.getRepeaterAgl();
	}

	public double getRepeaterUrl() {
		return protocol.getRepeaterAgl();
	}

	public String getRepeaterDescription1() {
		return protocol.getRepeaterDescription1();
	}

	public String getRepeaterDescription2() {
		return protocol.getRepeaterDescription2();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private boolean writeVoiceInternal(
		final boolean isDestinationGateway,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String yourCallsign,
		@NonNull final Buffer audio,
		final boolean isAudioAMBE,
		final byte[] slowdata,
		final boolean frameEnd,
		final String shortMessage,
		final String aprsMessage
	) {
		if(!isRunning() || !isConnected())
			return false;
		else if(isAudioAMBE && !(audio instanceof ByteBuffer))
			throw new IllegalArgumentException("'audio' arguments must be type ByteBuffer for audio type AMBE.");
		else if(!isAudioAMBE && !(audio instanceof ShortBuffer))
			throw new IllegalArgumentException("'audio' arguments must be type ShortBuffer for audio type PCM.");

		NoraVRAudioPacket<Buffer> uplinkPacket = null;

		if(uplinkInputInfo.timekeeper.isTimeout(500, TimeUnit.MILLISECONDS)) {
			uplinkInputInfo.clear();
		}

		if(uplinkInputInfo.frameID == 0x0) {
			final String myCallsignLongFormatted = formatCallsignFullLength(myCallsignLong);
			final String myCallsignShortFormatted = formatCallsignShortLength(myCallsignShort);
			final String yourCallsignFormatted = formatCallsignFullLength(yourCallsign);

			uplinkInputInfo.isSlowdataPresent = slowdata != null;
			final byte[] slowdataSegment =
				uplinkInputInfo.isSlowdataPresent ? slowdata : new byte[DSTARDefines.DataSegmentLength];

			if(!uplinkInputInfo.isSlowdataPresent) {
				uplinkInputInfo.slowdataEncoder.reset();

				final Header slowdataHeader =
					new Header(
						yourCallsignFormatted.toCharArray(),
						protocol.getRepeaterCallsign().toCharArray(),
						(
							isDestinationGateway ?
							protocol.getGatewayCallsign().toCharArray() : protocol.getRepeaterCallsign().toCharArray()
						),
						myCallsignLongFormatted.toCharArray(),
						myCallsignShortFormatted.toCharArray()
					);
				slowdataHeader.setFlags(new byte[] {0x40, 0x00, 0x00});
				uplinkInputInfo.slowdataEncoder.setHeader(slowdataHeader);
				uplinkInputInfo.slowdataEncoder.setEnableHeader(true);

				if(shortMessage != null) {
					uplinkInputInfo.slowdataEncoder.setShortMessage(shortMessage);
					uplinkInputInfo.slowdataEncoder.setEnableShortMessage(true);
				}
				if(aprsMessage != null) {
					uplinkInputInfo.slowdataEncoder.setAprsMessage(aprsMessage);
					uplinkInputInfo.slowdataEncoder.setEnableAprsMessage(true);
				}

				uplinkInputInfo.slowdataEncoder.setEnableEncode(true);

				uplinkInputInfo.slowdataEncoder.encode(slowdataSegment);
			}

			if(isAudioAMBE)
				uplinkPacket = (NoraVRAudioPacket)new NoraVRUplinkInputAudioAMBE((ByteBuffer)audio, slowdataSegment);
			else
				uplinkPacket = (NoraVRAudioPacket)new NoraVRUplinkInputAudioPCM(codecType, (ShortBuffer)audio, slowdataSegment);

			uplinkInputInfo.isAMBE = isAudioAMBE;
			uplinkInputInfo.frameID = generateFrameID();
			uplinkInputInfo.repeater2Callsign = isDestinationGateway ?
				protocol.getGatewayCallsign() : protocol.getRepeaterCallsign();
			uplinkInputInfo.repeater1Callsign = protocol.getRepeaterCallsign();
			uplinkInputInfo.yourCallsign = yourCallsignFormatted;
			uplinkInputInfo.myCallsignLong = myCallsignLongFormatted;
			uplinkInputInfo.myCallsignShort = myCallsignShortFormatted;

			uplinkPacket.frameID = uplinkInputInfo.frameID;
			uplinkPacket.repeater2Callsign = uplinkInputInfo.repeater2Callsign;
			uplinkPacket.repeater1Callsign = uplinkInputInfo.repeater1Callsign;
			uplinkPacket.yourCallsign = yourCallsignFormatted;
			uplinkPacket.myCallsignLong = myCallsignLongFormatted;
			uplinkPacket.myCallsignShort = myCallsignShortFormatted;

			uplinkInputInfo.timekeeper.updateTimestamp();
		}
		else {
			if(uplinkInputInfo.isSlowdataPresent && slowdata == null)
				throw new IllegalArgumentException("Must have slowdata, if slowdata is under user control.");

			final byte[] slowdataSegment =
				uplinkInputInfo.isSlowdataPresent ? slowdata : new byte[DSTARDefines.DataSegmentLength];
			if(!uplinkInputInfo.isSlowdataPresent)
				uplinkInputInfo.slowdataEncoder.encode(slowdataSegment);

			if(uplinkInputInfo.isAMBE) {
				uplinkPacket = (NoraVRAudioPacket)new NoraVRUplinkInputAudioAMBE(
					(ByteBuffer)audio, slowdataSegment, frameEnd
				);
			}
			else {
				uplinkPacket = (NoraVRAudioPacket)new NoraVRUplinkInputAudioPCM(
					codecType, (ShortBuffer)audio, slowdataSegment, frameEnd
				);
			}

			uplinkPacket.frameID = uplinkInputInfo.frameID;
			uplinkPacket.repeater2Callsign = uplinkInputInfo.repeater2Callsign;
			uplinkPacket.repeater1Callsign = uplinkInputInfo.repeater1Callsign;
			uplinkPacket.yourCallsign = uplinkInputInfo.yourCallsign;
			uplinkPacket.myCallsignLong = uplinkInputInfo.myCallsignLong;
			uplinkPacket.myCallsignShort = uplinkInputInfo.myCallsignShort;

			if(frameEnd) {
				uplinkInputInfo.clear();
			}
			else {
				uplinkInputInfo.timekeeper.updateTimestamp();
			}
		}

		rwVoicePacketsLocker.lock();
		try {
			if(!writeVoicePackets.add(uplinkPacket)) {return false;}
		}finally {rwVoicePacketsLocker.unlock();}

		processLoopLocker.lock();
		try {
			processLoop.signalAll();
		}finally {
			processLoopLocker.unlock();
		}

		return true;
	}

	private boolean start() {
		stop();

		workerThread = new Thread(mainProcessLoopTask);
		workerThread.setName(
			NoraVRClient.class.getSimpleName() + "_" + workerThread.getId()
		);
		workerThreadAvailable = true;
		workerThread.start();

		return true;
	}

	private void stop() {
		workerThreadAvailable = false;

		if(
			workerThread != null && workerThread.isAlive() &&
			workerThread.getId() != Thread.currentThread().getId()
		) {
			workerThread.interrupt();
			try {
				workerThread.join();
			}catch(InterruptedException ex) {}
		}
	}

	private int getUplinkPacketLimit(){
		return getUplinkTransmitTimeoutSeconds() * 50;
	}

	private void processUplinkPacket() {
		NoraVRAudioPacket<Buffer> inputUplinkPacket = null;
		do {
			rwVoicePacketsLocker.lock();
			try {
				inputUplinkPacket = writeVoicePackets.poll();
			}finally {rwVoicePacketsLocker.unlock();}

			if(inputUplinkPacket != null) {
				if(uplinkProcessInfo.frameID == 0x0 && inputUplinkPacket.frameID != 0x0) {
					// new frame
					uplinkProcessInfo.frameID = inputUplinkPacket.frameID;
					uplinkProcessInfo.repeater2Callsign = inputUplinkPacket.repeater2Callsign;
					uplinkProcessInfo.repeater1Callsign = inputUplinkPacket.repeater1Callsign;
					uplinkProcessInfo.yourCallsign = inputUplinkPacket.yourCallsign;
					uplinkProcessInfo.myCallsignLong = inputUplinkPacket.myCallsignLong;
					uplinkProcessInfo.myCallsignShort = inputUplinkPacket.myCallsignShort;

					uplinkProcessInfo.timekeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
					uplinkProcessInfo.timekeeper.updateTimestamp();

					createStatusInformation();
				}

				if(
					!uplinkProcessInfo.timekeeper.isTimeout() &&
					uplinkProcessInfo.frameID == inputUplinkPacket.frameID
				) {
					uplinkProcessInfo.packetCount++;

					final boolean isPacketLimitExceed =
							getUplinkPacketLimit() <= uplinkProcessInfo.packetCount;
					final boolean frameTerminate =
							isPacketLimitExceed || inputUplinkPacket.frameEnd;

					if(!uplinkProcessInfo.isTimeout){
						byte[] encodeData = null;
						if(vocoder != null && inputUplinkPacket.codec != NoraVRCodecType.AMBE) {
							vocoder.encodeInput((ShortBuffer)inputUplinkPacket.audio);
							encodeData = vocoder.encodeOutput();
						}

						NoraVRVoicePacket<?> uplinkPacket = null;
						switch(currentCodec) {
							case PCM:
								final VTPCM pcmPacket = new VTPCM();
								for(int i = 0; i < encodeData.length; i += 2) {
									final short pcmSample =
											(short)(((encodeData[i] << 8) & 0xFF00) | (encodeData[i + 1] & 0x00FF));
									pcmPacket.getAudio().add(pcmSample);
								}
								uplinkPacket = pcmPacket;
								break;
							case Opus64k:
							case Opus24k:
							case Opus8k:
							case Opus:
								final VTOPUS opusPacket = new VTOPUS();
								for(int i = 0; i < encodeData.length; i++) {
									opusPacket.getAudio().add(encodeData[i]);
								}
								uplinkPacket = opusPacket;
								break;

							case AMBE:
								final VTAMBE ambePacket = new VTAMBE();
								final ByteBuffer inputAMBE = (ByteBuffer)inputUplinkPacket.audio;
								while(inputAMBE.hasRemaining()) {ambePacket.getAudio().add(inputAMBE.get());}
								uplinkPacket = ambePacket;
								break;
							default:
								continue;
						}

						uplinkPacket.setFrameID(uplinkProcessInfo.frameID);
						uplinkPacket.setLongSequence(uplinkProcessInfo.longSequence);
						uplinkPacket.setShortSequence(uplinkProcessInfo.shortSequence);
						uplinkPacket.getFlags()[0] = (byte)0x40;
						uplinkPacket.getFlags()[1] = (byte)0x0;
						uplinkPacket.getFlags()[2] = (byte)0x0;
						uplinkPacket.setRepeater2Callsign(uplinkProcessInfo.repeater2Callsign);
						uplinkPacket.setRepeater1Callsign(uplinkProcessInfo.repeater1Callsign);
						uplinkPacket.setYourCallsign(uplinkProcessInfo.yourCallsign);
						uplinkPacket.setMyCallsignLong(uplinkProcessInfo.myCallsignLong);
						uplinkPacket.setMyCallsignShort(uplinkProcessInfo.myCallsignShort);
						if(frameTerminate) {uplinkPacket.setEndSequence(true);}
						if(inputUplinkPacket.slowdata != null && inputUplinkPacket.slowdata.length >= 3) {
							for(int i = 0; i < 3; i++)
								uplinkPacket.getSlowdata()[i] = inputUplinkPacket.slowdata[i];
						}

						protocol.writeVoicePacket(uplinkPacket);
					}

					uplinkProcessInfo.timekeeper.updateTimestamp();

					if(isPacketLimitExceed){
						if(!uplinkProcessInfo.isTimeout && eventListener != null) {
							if(log.isWarnEnabled()) {
								log.warn(
									"Uplink transmit timeout frameID = " + uplinkProcessInfo.frameID +
									"/MY = " + uplinkProcessInfo.myCallsignLong
								);
							}

							final int timeoutFrameID = uplinkInputInfo.frameID;
							workerExecutor.submit(new Runnable() {
								@Override
								public void run() {
									eventListener.transmitTimeout(timeoutFrameID);
								}
							});
						}

						uplinkProcessInfo.isTimeout = true;
					}
					else if(inputUplinkPacket.frameEnd) {
						uplinkProcessInfo.clear();

						createStatusInformation();
					}
					else {
						uplinkProcessInfo.longSequence = nextLongSequence(uplinkProcessInfo.longSequence);
						uplinkProcessInfo.shortSequence = nextShortSequence(uplinkProcessInfo.shortSequence);
					}
				}

			}
		}while(inputUplinkPacket != null);

		if(
			uplinkProcessInfo.frameID != 0x0 &&
			uplinkProcessInfo.timekeeper.isTimeout()
		) {
			for(int i = 0; i < 2; i++) {
				NoraVRVoicePacket<?> endPacket = null;

				switch(currentCodec) {
				case PCM:
					final VTPCM pcmPacket = new VTPCM();
					endPacket = pcmPacket;
					for(int c = 0; c < 160; c++) {pcmPacket.getAudio().add((short)0x0000);}
					break;

				case Opus64k:
				case Opus24k:
				case Opus8k:
				case Opus:
					final VTOPUS opusPacket = new VTOPUS(currentCodec);
					endPacket = opusPacket;
					break;

				case AMBE:
					final VTAMBE ambePacket = new VTAMBE();
					if(i == 0) {
						for(final byte d : DSTARUtils.getEndAMBE()) {ambePacket.getAudio().add(d);}
					}
					else {
						for(final byte d : DSTARUtils.getLastAMBE()) {ambePacket.getAudio().add(d);}
					}

					endPacket = ambePacket;
					break;

				default:
					break;
				}

				if(endPacket != null) {
					endPacket.setFrameID(uplinkProcessInfo.frameID);
					endPacket.setLongSequence(uplinkProcessInfo.longSequence);
					endPacket.setShortSequence(uplinkProcessInfo.shortSequence);
					endPacket.setEndSequence(true);
					endPacket.getFlags()[0] = (byte)0x40;
					endPacket.getFlags()[1] = (byte)0x0;
					endPacket.getFlags()[2] = (byte)0x0;
					endPacket.setRepeater2Callsign(uplinkProcessInfo.repeater2Callsign);
					endPacket.setRepeater1Callsign(uplinkProcessInfo.repeater1Callsign);
					endPacket.setYourCallsign(uplinkProcessInfo.yourCallsign);
					endPacket.setMyCallsignLong(uplinkProcessInfo.myCallsignLong);
					endPacket.setMyCallsignShort(uplinkProcessInfo.myCallsignShort);

					if(i == 0)
						ArrayUtil.copyOf(endPacket.getSlowdata(), DSTARUtils.getEndSlowdata());
					else
						ArrayUtil.copyOf(endPacket.getSlowdata(), DSTARUtils.getLastSlowdata());

					protocol.writeVoicePacket(endPacket);
				}

				uplinkProcessInfo.longSequence = nextLongSequence(uplinkProcessInfo.longSequence);
				uplinkProcessInfo.shortSequence = nextShortSequence(uplinkProcessInfo.shortSequence);
			}

			uplinkProcessInfo.clear();

			createStatusInformation();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void processDownlinkPackets() {
		boolean hasReadPacket = false;

		NoraVRVoicePacket<?> voicePacket = null;
		do {
			voicePacket = protocol.readVoicePacket();

			if(
				voicePacket != null &&
				(
					voicePacket.getCodecType() == currentCodec ||
					(
						voicePacket.getCommandType() == NoraVRCommandType.VTOPUS &&
						voicePacket.getCodecType() == NoraVRCodecType.Opus
					)
				)
			) {
				boolean frameStart = false;

				if(
					currentDownlinkFrameID == 0x0
				) {
					frameStart = true;

					currentDownlinkFrameID = voicePacket.getFrameID();

					currentDownlinkFlags = voicePacket.getFlags();
					currentDownlinkRepeater2Callsign = voicePacket.getRepeater2Callsign();
					currentDownlinkRepeater1Callsign = voicePacket.getRepeater1Callsign();
					currentDownlinkYourCallsign = voicePacket.getYourCallsign();
					currentDownlinkMyCallsignLong = voicePacket.getMyCallsignLong();
					currentDownlinkMyCallsignShort = voicePacket.getMyCallsignShort();

					downlinkLongSequence = 0x0;
					downlinkShortSequence = 0x0;

					downlinkTimekeeper.updateTimestamp();

					createStatusInformation();
				}

				if(currentDownlinkFrameID == voicePacket.getFrameID()) {
					final int receiveLongSequence = voicePacket.getLongSequence();

					final int loss = calcPacketLoss(downlinkLongSequence, receiveLongSequence, 0x10000);

					if(loss >= 1 && loss <= 10) {
						downlinkSilencePackets.clear();

						final int fwdSeq =
							insertDownlinkSilencePacket(voicePacket, downlinkSilencePackets, loss);

						downlinkLongSequence = nextLongSequence(downlinkLongSequence, fwdSeq);
						downlinkShortSequence = nextShortSequence(downlinkShortSequence, fwdSeq);
					}

					if(voicePacket.getCodecType() == NoraVRCodecType.AMBE) {
						downlinkSilencePackets.add(voicePacket);

						for(Iterator<NoraVRVoicePacket<?>> it = downlinkSilencePackets.iterator(); it.hasNext();) {
							final NoraVRVoicePacket<?> ambePacket = it.next();
							it.remove();

							final VTAMBE ambe = (VTAMBE)ambePacket;
							final ByteBuffer ambeVoice = ByteBuffer.allocate(ambe.getAudio().size());
							while(ambeVoice.hasRemaining() && !ambe.getAudio().isEmpty())
								ambeVoice.put(ambe.getAudio().poll());

							ambeVoice.flip();

							final NoraVRDownlinkAudioAMBEPacket audio =
								new NoraVRDownlinkAudioAMBEPacket(
										voicePacket.getCodecType(),
										voicePacket.getFrameID(),
										ambeVoice, voicePacket.getSlowdata(),
										voicePacket.isEndSequence(),
										downlinkShortSequence
								);

							audio.flags = voicePacket.getFlags();
							audio.repeater2Callsign = voicePacket.getRepeater2Callsign();
							audio.repeater1Callsign = voicePacket.getRepeater1Callsign();
							audio.yourCallsign = voicePacket.getYourCallsign();
							audio.myCallsignLong = voicePacket.getMyCallsignLong();
							audio.myCallsignShort = voicePacket.getMyCallsignShort();
							audio.frameStart = frameStart;

							rwVoicePacketsLocker.lock();
							try {
								readVoicePackets.add((NoraVRDownlinkAudioPacket)audio);
							}finally {rwVoicePacketsLocker.unlock();}

							downlinkTimekeeper.updateTimestamp();

							hasReadPacket = true;
						}
					}
					else {
						for(Iterator<NoraVRVoicePacket<?>> it = downlinkSilencePackets.iterator(); it.hasNext();) {
							final NoraVRVoicePacket<?> silencePacket = it.next();
							it.remove();

							voiceDecodeInput(voicePacket.getCodecType(), silencePacket, vocoder, true);
						}

						voiceDecodeInput(voicePacket.getCodecType(), voicePacket, vocoder, false);

						if(vocoder != null) {
							ShortBuffer pcm = null;
							while((pcm = vocoder.decodeOutput()) != null) {

								final NoraVRDownlinkAudioPCMPacket audio =
									new NoraVRDownlinkAudioPCMPacket(
											voicePacket.getCodecType(),
											voicePacket.getFrameID(),
											pcm,
											voicePacket.getSlowdata(),
											voicePacket.isEndSequence(),
											downlinkShortSequence
									);

								audio.flags = voicePacket.getFlags();
								audio.repeater2Callsign = voicePacket.getRepeater2Callsign();
								audio.repeater1Callsign = voicePacket.getRepeater1Callsign();
								audio.yourCallsign = voicePacket.getYourCallsign();
								audio.myCallsignLong = voicePacket.getMyCallsignLong();
								audio.myCallsignShort = voicePacket.getMyCallsignShort();
								audio.frameStart = frameStart;

								rwVoicePacketsLocker.lock();
								try {
									readVoicePackets.add((NoraVRDownlinkAudioPacket)audio);
								}finally {rwVoicePacketsLocker.unlock();}

								downlinkTimekeeper.updateTimestamp();

								hasReadPacket = true;
							}
						}
					}

					if(voicePacket.isEndSequence()) {
						clearDownlinkSequence();

						createStatusInformation();
					}else {
						downlinkLongSequence = nextLongSequence(downlinkLongSequence);
						downlinkShortSequence = nextShortSequence(downlinkShortSequence);
					}
				}
			}
		}while(voicePacket != null);

		if(currentDownlinkFrameID != 0x0 && downlinkTimekeeper.isTimeout(1, TimeUnit.SECONDS)) {
			for(int i = 0; i < 2; i++) {
				NoraVRDownlinkAudioPacket<Buffer> endPacket = null;

				if(currentCodec == NoraVRCodecType.AMBE) {
					endPacket =
						(NoraVRDownlinkAudioPacket)new NoraVRDownlinkAudioAMBEPacket(
							currentCodec,
							currentDownlinkFrameID,
							ByteBuffer.wrap(i == 0 ? DSTARUtils.getEndAMBE() : DSTARUtils.getLastAMBE()),
							i == 0 ? DSTARUtils.getEndSlowdata() : DSTARUtils.getLastSlowdata(),
							i != 0,
							downlinkShortSequence
						);
				}
				else {
					final short[] dummyPCMArray = new short[160];
					Arrays.fill(dummyPCMArray, (short)0x0);
					final ShortBuffer dummyPCM = ShortBuffer.wrap(dummyPCMArray);

					endPacket =
						(NoraVRDownlinkAudioPacket)new NoraVRDownlinkAudioPCMPacket(
							currentCodec,
							currentDownlinkFrameID,
							dummyPCM,
							i == 0 ? DSTARUtils.getEndSlowdata() : DSTARUtils.getLastSlowdata(),
							i != 0,
							downlinkShortSequence
						);
				}

				endPacket.flags = currentDownlinkFlags;
				endPacket.repeater2Callsign = currentDownlinkRepeater2Callsign;
				endPacket.repeater1Callsign = currentDownlinkRepeater1Callsign;
				endPacket.yourCallsign = currentDownlinkYourCallsign;
				endPacket.myCallsignLong = currentDownlinkMyCallsignLong;
				endPacket.myCallsignShort = currentDownlinkMyCallsignShort;
				endPacket.frameStart = false;

				rwVoicePacketsLocker.lock();
				try {
					readVoicePackets.add(endPacket);
				}finally {rwVoicePacketsLocker.unlock();}

				downlinkShortSequence = nextShortSequence(downlinkShortSequence);
			}

			clearDownlinkSequence();

			createStatusInformation();
		}

		if(hasReadPacket && eventListener != null)
			eventListener.receiveVoice();
	}

	private void clearDownlinkSequence() {
		currentDownlinkFlags = null;
		currentDownlinkMyCallsignLong = null;
		currentDownlinkMyCallsignShort = null;
		currentDownlinkYourCallsign = null;
		currentDownlinkRepeater2Callsign = null;
		currentDownlinkRepeater1Callsign = null;

		downlinkLongSequence = 0x0;
		downlinkShortSequence = 0x0;

		currentDownlinkFrameID = 0x0;
	}

	private int insertDownlinkSilencePacket(
		final NoraVRVoicePacket<?> receivePacket,
		final Queue<NoraVRVoicePacket<?>> silencePackets,
		final int loss
	) {
		silencePackets.clear();

		for(int i = 0; i < loss; i++) {
			NoraVRVoicePacket<?> copyPacket = null;

			switch(receivePacket.getCodecType()) {
			case PCM:
				final VTPCM pcmPacket = (VTPCM)receivePacket.clone();
				copyPacket = pcmPacket;
				pcmPacket.getAudio().clear();
				for(int c = 0; c < 160; c++) {pcmPacket.getAudio().add((short)0x0000);}
				break;
			case Opus64k:
			case Opus24k:
			case Opus8k:
			case Opus:
				final VTOPUS opusPacket = (VTOPUS)receivePacket.clone();
				copyPacket = opusPacket;
				opusPacket.getAudio().clear();
				break;
			case AMBE:
				final VTAMBE ambePacket = (VTAMBE)receivePacket.clone();
				ambePacket.getAudio().clear();
				for(final byte ambe : DSTARUtils.getNullAMBE()) {ambePacket.getAudio().add(ambe);}
				copyPacket = ambePacket;
				break;
			default:
				continue;
			}

			silencePackets.add(copyPacket);
		}

		return silencePackets.size();
	}

	private boolean voiceDecodeInput(
		final NoraVRCodecType codecType,
		final NoraVRVoicePacket<?> voicePacket,
		final VoiceVocoder<ShortBuffer> vocoder,
		final boolean isPacketLoss
	) {
		if(vocoder == null)
			return false;

		switch(codecType) {
		case PCM:
			final byte[] pcm = new byte[voicePacket.getAudio().size() * 2];
			for(int i = 0; i < pcm.length && !voicePacket.getAudio().isEmpty(); i += 2) {
				final short sample = ((VTPCM)voicePacket).getAudio().poll();
				pcm[i] = (byte)((sample >> 8) & 0xFF);
				pcm[i + 1] = (byte)(sample & 0xFF);
			}
			vocoder.decodeInput(pcm, isPacketLoss);
			break;
		case Opus64k:
		case Opus24k:
		case Opus8k:
		case Opus:
			final byte[] opus = new byte[voicePacket.getAudio().size()];
			for(int i = 0; i < opus.length && !voicePacket.getAudio().isEmpty(); i++) {
				opus[i] = ((VTOPUS)voicePacket).getAudio().poll();
			}
			vocoder.decodeInput(opus, isPacketLoss);
			break;
		default:
			return false;
		}

		return true;
	}

	private boolean createVocoder(final NoraVRCodecType codecType) {

		if(vocoder != null) {
			vocoder.dispose();
			vocoder = null;
		}

		switch(codecType) {
		case AMBE:
			vocoder = null;
			break;
		case Opus64k:
			vocoder = OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus64k.getTypeName(), true);
			if(!vocoder.init(8000, 1, 64000)) {return false;}
			break;
		case Opus24k:
			vocoder = OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus24k.getTypeName(), true);
			if(!vocoder.init(8000, 1, 24000)) {return false;}
			break;
		case Opus8k:
			vocoder = OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus8k.getTypeName(), true);
			if(!vocoder.init(8000, 1, 8000)) {return false;}
			break;
		case PCM:
			vocoder = new PCMVocoder(NoraVRCodecType.PCM.getTypeName(), false);
			break;

		default:
			return false;
		}

		return true;
	}

	private int nextLongSequence(final int currentSequence) {
		return nextLongSequence(currentSequence, 1);
	}

	private int nextLongSequence(final int currentSequence, final int fwdSequence) {
		return (currentSequence + fwdSequence) % 0x10000;
	}

	private int nextShortSequence(final int currentSequence) {
		return nextShortSequence(currentSequence, 1);
	}

	private int nextShortSequence(final int currentSequence, final int fwdSequence) {
		return (currentSequence + fwdSequence) % 0x15;
	}

	private int calcPacketLoss(final int currentSeq, final int receiveSeq, final int sequenceLength) {
		int loss = 0;
		if(receiveSeq >= currentSeq)
			loss = receiveSeq - currentSeq;
		else
			loss = (receiveSeq + sequenceLength) - currentSeq;

		return loss;
	}

	private static int generateFrameID() {
		synchronized(frameIDRandom) {
			return frameIDRandom.nextInt(0xFFFF) + 1;
		}
	}

	private String formatCallsignFullLength(final String callsign){
		return formatCallsignLength(callsign, 8);
	}

	private String formatCallsignShortLength(final String callsign){
		return formatCallsignLength(callsign, 4);
	}

	private String formatCallsignLength(final String callsign, final int length) {
		final String call = callsign != null ? callsign : "";

		return String.format("%-" + length + "S", call);
	}

	private void createStatusInformation() {
		NoraVRClientRouteReport uplinkReport = null;
		NoraVRClientRouteReport downlinkReport = null;

		if(uplinkProcessInfo.frameID != 0x0) {
			uplinkReport = new NoraVRClientRouteReport();
			uplinkReport.setDirection(NoraVRClientRouteDirection.Uplink);
			uplinkReport.setFrameID(uplinkProcessInfo.frameID);
			uplinkReport.setRepeater1Callsign(uplinkProcessInfo.repeater1Callsign);
			uplinkReport.setRepeater2Callsign(uplinkProcessInfo.repeater2Callsign);
			uplinkReport.setYourCallsign(uplinkProcessInfo.yourCallsign);
			uplinkReport.setMyCallsignLong(uplinkProcessInfo.myCallsignLong);
			uplinkReport.setMyCallsignShort(uplinkProcessInfo.myCallsignShort);
		}

		if(currentDownlinkFrameID != 0x0) {
			downlinkReport = new NoraVRClientRouteReport();
			downlinkReport.setDirection(NoraVRClientRouteDirection.Downlink);
			downlinkReport.setFrameID(currentDownlinkFrameID);
			downlinkReport.setRepeater1Callsign(currentDownlinkRepeater1Callsign);
			downlinkReport.setRepeater2Callsign(currentDownlinkRepeater2Callsign);
			downlinkReport.setYourCallsign(currentDownlinkYourCallsign);
			downlinkReport.setMyCallsignLong(currentDownlinkMyCallsignLong);
			downlinkReport.setMyCallsignShort(currentDownlinkMyCallsignShort);
		}

		statusInformation = new NoraVRClientStatusInformation(uplinkReport, downlinkReport);
	}
}
