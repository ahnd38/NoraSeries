package org.jp.illg.nora.vr;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.VoiceCodecType;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemBase;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemEvent;
import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRConfig;
import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRLoginClient;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNoraVRHandler;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.service.web.model.NoraVRStatusData;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.nora.vr.model.NoraVRClientEntry;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.nora.vr.model.NoraVRLoginUserEntry;
import org.jp.illg.nora.vr.protocol.NoraVRProtocolEventListener;
import org.jp.illg.nora.vr.protocol.NoraVRProtocolProcessor;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.nora.vr.protocol.model.NoraVRPacket;
import org.jp.illg.nora.vr.protocol.model.NoraVRVoicePacket;
import org.jp.illg.nora.vr.protocol.model.VTAMBE;
import org.jp.illg.nora.vr.protocol.model.VTOPUS;
import org.jp.illg.nora.vr.protocol.model.VTPCM;
import org.jp.illg.nora.vr.protocol.model.VoiceTransferBase;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.ambe.dv3k.DV3KController;
import org.jp.illg.util.ambe.dv3k.DV3KEvent;
import org.jp.illg.util.audio.vocoder.VoiceVocoder;
import org.jp.illg.util.audio.vocoder.opus.OpusVocoderFactory;
import org.jp.illg.util.audio.vocoder.pcm.PCMVocoder;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVR extends DStarRepeaterModemBase
implements NoraVRProtocolEventListener, WebRemoteControlNoraVRHandler{

	private final int uplinkPacketLimit = 50 * 60 * 5;	// 5 min
//	private final int uplinkPacketLimit = 50 * 10;
	private final int uplinkTransmitterCacheSize = 50;


	private class UplinkReadPacket extends TransmitterPacketImpl {

		public UplinkReadPacket(
			@NonNull final PacketType packetType,
			@NonNull final DSTARPacket packet,
			@NonNull final FrameSequenceType frameSequenceType
		) {
			super(packetType, packet, frameSequenceType);

		}

		@Override
		public UplinkReadPacket clone() {
			final UplinkReadPacket copy = (UplinkReadPacket)super.clone();

			return copy;
		}
	}

	private enum UplinkState {
		WaitFrameStart,
		FrameProcessing,
		FrameTerminate,
		FrameTimeout,
	}

	private class DownlinkInfo {

		int packetCount;
		int longSequence;
		int shortSequence;
		final Queue<Byte[]> slowdata;
		boolean isEnd;

		public DownlinkInfo() {
			slowdata = new LinkedList<Byte[]>();

			clear();
		}

		public void clear() {
			packetCount = 0;
			longSequence = 0;
			shortSequence = 0x0;
			isEnd = false;

			slowdata.clear();
		}
	}

	private class HeaderCache {
		@Getter
		private final int frameID;

		@Getter
		private final long createdTimestamp;

		@Getter
		private final Timer activityTimekeeper;

		@Getter
		private Header voiceHeader;

		HeaderCache(
			final int frameID, final Header voiceHeader, final int timeoutSeconds
		) {
			super();

			this.frameID = frameID;
			this.createdTimestamp = System.currentTimeMillis();
			this.activityTimekeeper = new Timer(timeoutSeconds, TimeUnit.SECONDS);
			this.voiceHeader = voiceHeader;
		}
	}

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String dv3kServerAddress;
	public static final String dv3kServerAddressPropertyName = "DV3KServerAddress";
	private static final String dv3kServerAddressDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int dv3kServerPort;
	public static final String dv3kServerPortPropertyName = "DV3KServerPort";
	private static final int dv3kServerPortDefault = 2460;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int noravrPort;
	public static final String noravrPortPropertyName = "NoraVRPort";
	private static final int noravrPortDefault = 52161;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String noravrLoginPassword;
	public static final String noravrLoginPasswordPropertyName = "NoraVRLoginPassword";
	private static final String noravrLoginPasswordDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int NoraVRClientConnectionLimit;
	public static final String noravrClientConnectionLimitPropertyName = "NoraVRClientConnectionLimit";
	public static final int noravrClientConnectionLimitDefault = 10;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrAllowRFNode;
	public static final String noravrAllowRFNodePropertyName = "NoraVRAllowRFNode";
	private static final boolean noravrAllowRFNodeDefault = false;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrUseCodecAMBE;
	public static final String noravrUseCodecAMBEPropertyName = "NoraVRUseCodecAMBE";
	private static final boolean noravrUseCodecAMBEDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrUseCodecPCM;
	public static final String noravrUseCodecPCMPropertyName = "NoraVRUseCodecPCM";
	private static final boolean noravrUseCodecPCMDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrUseCodecOpus64k;
	public static final String noravrUseCodecOpus64kPropertyName = "NoraVRUseCodecOpus64k";
	private static final boolean noravrUseCodecOpus64kDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrUseCodecOpus24k;
	public static final String noravrUseCodecOpus24kPropertyName = "NoraVRUseCodecOpus24k";
	private static final boolean noravrUseCodecOpus24kDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean noravrUseCodecOpus8k;
	public static final String noravrUseCodecOpus8kPropertyName = "NoraVRUseCodecOpus8k";
	private static final boolean noravrUseCodecOpus8kDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String noraVRPublicServerAddress;
	public static final String noraVRPublicServerAddressPropertyName = "NoraVRPublicServerAddress";
	private static final String noraVRPublicServerAddressDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int noraVRPublicServerPort;
	public static final String noraVRPublicServerPortPropertyName = "NoraVRPublicServerPort";
	private static final int noraVRPublicServerPortDefault = -1;

	private final String logHeader;

	private DV3KController dv3k;

	private NoraVRProtocolProcessor noravr;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final Map<NoraVRCodecType, VoiceVocoder<ShortBuffer>> vocoders;

	private UplinkState uplinkState;
	private int currentUplinkFrameID;
	private Header currentUplinkHeader;
	private final Timer uplinkTimekeeper;
	private int currentUplinkLongSequence;
	private int currentUplinkOutputShortSequence;
	private NoraVRCodecType currentUplinkCodec;
	private long uplinkInputPacketCount;
	private long uplinkOutputPacketCount;
	private final Queue<NoraVRVoicePacket<?>> uplinkSilencePackets;
	private final Queue<Byte[]> uplinkSlowdata;
	private final DataSegmentDecoder uplinkSlowdataDecoder;
	private final CacheTransmitter<UplinkReadPacket> uplinkTransmitter;


	private UplinkState downlinkState;
	private int currentDownlinkFrameID;
	private Header currentDownlinkHeader;
	private final Timer downlinkTimekeeper;
	private final DownlinkInfo downlinkPCMInfo;
	private final DownlinkInfo downlinkOpus64kInfo;
	private final DownlinkInfo downlinkOpus24kInfo;
	private final DownlinkInfo downlinkOpus8kInfo;
	private int currentDownlinkAMBELongSequence;
	private int currentDownlinkInputPacketCount;

	private UUID loopBlockID;

	private final Queue<DSTARPacket> writePackets;
	private final Lock rwPacketsLocker;

	private final NoraVRConfiguration serverConfig;

	private final Map<Integer, HeaderCache> downlinkHeaderCache;
	private final Timer downlinkHeaderCacheCleanupTimekeeper;

	private final EventListener<DV3KEvent> dv3kEventListener =
		new EventListener<DV3KEvent>() {
			@Override
			public void event(DV3KEvent event, Object attachment) {
				if(event == DV3KEvent.ReceivePacket) {wakeupProcessThread();}
			}
		};

	public NoraVR(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway,
		@NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		this(exceptionListener, workerExecutor, gateway, repeater, eventListener, null);
	}

	public NoraVR(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway,
		@NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			NoraVR.class.getSimpleName(),
			workerExecutor,
			ModemTypes.NoraVR,
			gateway,
			repeater,
			eventListener,
			socketIO
		);

		logHeader = NoraVR.class.getSimpleName() + " : ";

		this.exceptionListener = exceptionListener;

		vocoders = new HashMap<NoraVRCodecType, VoiceVocoder<ShortBuffer>>();

//		readPackets = new LinkedList<DvPacket>();
		writePackets = new LinkedList<>();
		rwPacketsLocker = new ReentrantLock();

		loopBlockID = null;

		uplinkTimekeeper = new Timer();
		uplinkSilencePackets = new LinkedList<NoraVRVoicePacket<?>>();
		uplinkSlowdata = new LinkedList<Byte[]>();
		uplinkSlowdataDecoder = new DataSegmentDecoder();
		uplinkTransmitter = new CacheTransmitter<>(uplinkTransmitterCacheSize);
		clearUplink();

		downlinkTimekeeper = new Timer();
		downlinkPCMInfo = new DownlinkInfo();
		downlinkOpus64kInfo = new DownlinkInfo();
		downlinkOpus24kInfo = new DownlinkInfo();
		downlinkOpus8kInfo = new DownlinkInfo();
		clearDownlink();

		downlinkHeaderCache = new HashMap<Integer, NoraVR.HeaderCache>();
		downlinkHeaderCacheCleanupTimekeeper = new Timer();

		serverConfig = new NoraVRConfiguration();

		setDv3kServerAddress(dv3kServerAddressDefault);
		setDv3kServerPort(dv3kServerPortDefault);
		setNoravrPort(noravrPortDefault);
		setNoravrLoginPassword(noravrLoginPasswordDefault);
		setNoraVRClientConnectionLimit(noravrClientConnectionLimitDefault);
		setNoravrAllowRFNode(noravrAllowRFNodeDefault);
		setNoravrUseCodecAMBE(noravrUseCodecAMBEDefault);
		setNoravrUseCodecPCM(noravrUseCodecPCMDefault);
		setNoravrUseCodecOpus64k(noravrUseCodecOpus64kDefault);
		setNoravrUseCodecOpus24k(noravrUseCodecOpus24kDefault);
		setNoravrUseCodecOpus8k(noravrUseCodecOpus8kDefault);
	}

	@Override
	public ModemTransceiverMode getDefaultTransceiverMode() {
		return ModemTransceiverMode.FullDuplex;
	}

	@Override
	public ModemTransceiverMode[] getSupportedTransceiverModes() {
		return new ModemTransceiverMode[] {
			ModemTransceiverMode.HalfDuplex,
			ModemTransceiverMode.FullDuplex,
			ModemTransceiverMode.RxOnly,
			ModemTransceiverMode.TxOnly
		};
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {
		if(noravr != null && noravr.isRunning())
			noravr.updateRemoteUsers(reflectorType, protocol, remoteCallsign, connectionDir, users);

		if(log.isDebugEnabled()) {
			log.debug(
				logHeader +
				"Remote users received " + users.size() + " users from " + remoteCallsign + "@" + reflectorType
			);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean setPropertiesInternal(ModemProperties properties) {

		setDv3kServerAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				dv3kServerAddressPropertyName,
				dv3kServerAddressDefault
			)
		);

		setDv3kServerPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				dv3kServerPortPropertyName,
				dv3kServerPortDefault
			)
		);
		if(getDv3kServerPort() <= 1023 || getDv3kServerPort() > 65535) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Illegal DV3K Server Port = " + getDv3kServerPort() + ".");

			return false;
		}

		setNoravrPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				noravrPortPropertyName,
				noravrPortDefault
			)
		);
		if(getNoravrPort() <= 1023 || getNoravrPort() > 65535) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Illegal NoraVR Port = " + getNoravrPort() + ".");

			return false;
		}

		setNoravrLoginPassword(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				noravrLoginPasswordPropertyName,
				noravrLoginPasswordDefault
			)
		);

		setNoravrAllowRFNode(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrAllowRFNodePropertyName,
				noravrAllowRFNodeDefault
			)
		);

		setNoravrUseCodecAMBE(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrUseCodecAMBEPropertyName,
				noravrUseCodecAMBEDefault
			)
		);

		setNoravrUseCodecPCM(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrUseCodecPCMPropertyName,
				noravrUseCodecPCMDefault
			)
		);

		setNoravrUseCodecOpus64k(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrUseCodecOpus64kPropertyName,
				noravrUseCodecOpus64kDefault
			)
		);

		setNoravrUseCodecOpus24k(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrUseCodecOpus24kPropertyName,
				noravrUseCodecOpus24kDefault
			)
		);

		setNoravrUseCodecOpus8k(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				noravrUseCodecOpus8kPropertyName,
				noravrUseCodecOpus8kDefault
			)
		);

		setNoraVRClientConnectionLimit(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				noravrClientConnectionLimitPropertyName,
				noravrClientConnectionLimitDefault
			)
		);

		setNoraVRPublicServerAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				noraVRPublicServerAddressPropertyName,
				noraVRPublicServerAddressDefault
			)
		);

		setNoraVRPublicServerPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				noraVRPublicServerPortPropertyName,
				noraVRPublicServerPortDefault
			)
		);

		if(
			properties.getTransceiverMode() == null ||
			properties.getTransceiverMode() == ModemTransceiverMode.Unknown
		) {
			setTransceiverMode(ModemTransceiverMode.FullDuplex);
		}

		//サーバ設定を作成
		serverConfig.setRfNode(isNoravrAllowRFNode());
		serverConfig.setSupportedCodecPCM(isNoravrUseCodecPCM());
		serverConfig.setSupportedCodecAMBE(isNoravrUseCodecAMBE());
		serverConfig.setSupportedCodecOpus64k(isNoravrUseCodecOpus64k());
		serverConfig.setSupportedCodecOpus24k(isNoravrUseCodecOpus24k());
		serverConfig.setSupportedCodecOpus8k(isNoravrUseCodecOpus8k());

		vocoderDispose();

		if(serverConfig.isSupportedCodecPCM()) {
			vocoders.put(NoraVRCodecType.PCM, new PCMVocoder(NoraVRCodecType.PCM.getTypeName(), false));
		}
		else {
			if(log.isInfoEnabled()) {log.info(logHeader + "PCM codec disabled.");}
		}

		if(serverConfig.isSupportedCodecOpus64k()) {
			final VoiceVocoder<ShortBuffer> opus64k =
				OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus64k.getTypeName(),true);
			opus64k.init(8000, 1, 64000);
			vocoders.put(NoraVRCodecType.Opus64k, opus64k);
		}
		else {
			if(log.isInfoEnabled()) {log.info(logHeader + "Opus(64k) codec disabled.");}
		}

		if(serverConfig.isSupportedCodecOpus24k()) {
			final VoiceVocoder<ShortBuffer> opus24k =
				OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus24k.getTypeName(), true);
			opus24k.init(8000, 1, 24000);
			vocoders.put(NoraVRCodecType.Opus24k, opus24k);
		}
		else {
			if(log.isInfoEnabled()) {log.info(logHeader + "Opus(24k) codec disabled.");}
		}

		if(serverConfig.isSupportedCodecOpus8k()) {
			final VoiceVocoder<ShortBuffer> opus8k =
				OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus8k.getTypeName(), true);
			opus8k.init(8000, 1, 8000);
			vocoders.put(NoraVRCodecType.Opus8k, opus8k);
		}
		else {
			if(log.isInfoEnabled()) {log.info(logHeader + "Opus(8k) codec disabled.");}
		}

		if(
			serverConfig.isSupportedCodecOpus64k() ||
			serverConfig.isSupportedCodecOpus24k() ||
			serverConfig.isSupportedCodecOpus8k()
		) {
			final VoiceVocoder<ShortBuffer> opus =
				OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus64k.getTypeName(),true);
			opus.init(8000, 1, 6000);
			vocoders.put(NoraVRCodecType.Opus, opus);
		}
		else {
			if(log.isInfoEnabled()) {log.info(logHeader + "All Opus codec disabled.");}
		}

		if(!isNeedDV3K(serverConfig)) {
			if(log.isInfoEnabled()) {
				log.info(logHeader + "DV3K has been disabled by configuration, pcm and opus codec are disabled.");
			}
		}

		List<NoraVRLoginUserEntry> loginUsers = null;
		if(properties.getConfigurationProperties().containsKey("LoginUserList")) {
			try {
				loginUsers =
					 (List<NoraVRLoginUserEntry>)properties.getConfigurationProperties().get("LoginUserList");
			} catch(ClassCastException ex) {
				loginUsers = Collections.emptyList();
			}
		}
		else {
			loginUsers = Collections.emptyList();
		}


		if(dv3k != null) {dv3k.stop();}
		if(isNeedDV3K(serverConfig)) {
			dv3k = new DV3KController(getWorkerExecutor(), exceptionListener, dv3kEventListener);

			if(!dv3k.setProperties(properties.getConfigurationProperties())) {
				vocoderDispose();

				return false;
			}
		}
		else {
			dv3k = null;
		}

		if(noravr != null && noravr.isRunning()) {noravr.stop();}
		noravr = new NoraVRProtocolProcessor(
			exceptionListener,
			this,
			getWorkerExecutor(),
			getNoravrPort(),
			getGatewayCallsign(),
			getRepeaterCallsign(),
			getNoravrLoginPassword(), getNoraVRClientConnectionLimit(),
			serverConfig,
			loginUsers,
			getGateway(),
			getRepeater(),
			getSocketIO()
		);

		return true;
	}


	private void vocoderDispose() {
		for(Iterator<VoiceVocoder<ShortBuffer>> it = vocoders.values().iterator(); it.hasNext();) {
			final VoiceVocoder<ShortBuffer> vocoder = it.next();
			it.remove();

			vocoder.dispose();
		}
	}

	@Override
	public ModemProperties getProperties(ModemProperties properties) {
		return properties;
	}

	@Override
	public DSTARPacket readPacket() {
		rwPacketsLocker.lock();
		try {
			final Optional<UplinkReadPacket> packet =
				uplinkTransmitter.outputRead();
			if(packet.isPresent()) {
				final DSTARPacket dvPacket = packet.get().getPacket();

				if(log.isTraceEnabled()) {
					log.trace(logHeader + "Transmit to repeater.\n" + dvPacket.toString(4));
				}

				return packet.get().getPacket();
			}
			else
				return null;
		}finally {rwPacketsLocker.unlock();}
	}

	@Override
	public boolean hasReadPacket() {
		rwPacketsLocker.lock();
		try {
			return uplinkTransmitter.hasOutputRead();
		}finally {rwPacketsLocker.unlock();}
	}

	@Override
	public boolean writePacketInternal(@NonNull DSTARPacket packet) {
		rwPacketsLocker.lock();
		try {
			return writePackets.add(packet);
		}finally {rwPacketsLocker.unlock();}
	}

	@Override
	public boolean hasWriteSpace() {
		rwPacketsLocker.lock();
		try {
			return writePackets.size() < 100;
		}finally {rwPacketsLocker.unlock();}
	}

	@Override
	public VoiceCodecType getCodecType() {
		return VoiceCodecType.AMBE;
	}

	@Override
	public boolean initializeWebRemoteControlInt(
		final WebRemoteControlService webRemoteControlService
	) {
		if(!webRemoteControlService.initializeModemNoraVR(this)) {
			noravr.setWebRemoteControlService(webRemoteControlService);
		}

		return true;
	}

	@Override
	protected void threadFinalize() {
		if(dv3k != null) {dv3k.stop();}
		if(noravr != null) {noravr.stop();}

		vocoderDispose();
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		if(dv3k != null && !dv3k.start()) {
			return threadFatalError("Failed start DV3K controller.", null);
		}

		if(noravr == null || !noravr.start()) {
			return threadFatalError("Failed start NoraVR protocol handler.", null);
		}

		downlinkHeaderCacheCleanupTimekeeper.setTimeoutTime(5, TimeUnit.SECONDS);

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processModem() {

		if(dv3k != null) {dv3k.process();}

		processUplinkPacket();

		processDownlinkPacket();

		// タイムアウトしたヘッダキャッシュを掃除
		if(downlinkHeaderCacheCleanupTimekeeper.isTimeout()) {
			downlinkHeaderCacheCleanupTimekeeper.updateTimestamp();

			for(Iterator<HeaderCache> it = downlinkHeaderCache.values().iterator(); it.hasNext();) {
				final HeaderCache cache = it.next();

				if(cache.getActivityTimekeeper().isTimeout()) {it.remove();}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ProcessIntervalMode getProcessLoopIntervalMode() {
		return ProcessIntervalMode.VoiceTransfer;
	}

	protected List<NoraVRClientEntry> getLoginClientsInt(){
		//TODO
		return null;
	}

	private ThreadProcessResult processDownlinkPacket() {

		DSTARPacket ambePacket = null;
		while(true) {
			rwPacketsLocker.lock();
			try {
				ambePacket = writePackets.poll();
			}finally {rwPacketsLocker.unlock();}

			if(ambePacket == null) {break;}
			else if(ambePacket.getPacketType() != DSTARPacketType.DV) {continue;}

			if(log.isTraceEnabled()) {
				log.trace(logHeader + "Receive downlink packet.\n" + ambePacket.toString(4));
			}

			final int receiveFrameID = ambePacket.getBackBone().getFrameIDNumber();

			//ヘッダキャッシュを検索して復帰できるようなら復帰させる
			final HeaderCache cacheHeader =
				downlinkHeaderCache.get(receiveFrameID);
			if(cacheHeader == null && ambePacket.getDVPacket().hasPacketType(PacketType.Header)) {
				downlinkHeaderCache.put(
					ambePacket.getBackBone().getFrameIDNumber(),
					new HeaderCache(receiveFrameID, ambePacket.getRfHeader(), 30)
				);
			}
			else if(cacheHeader != null){
				cacheHeader.getActivityTimekeeper().updateTimestamp();
			}

			final boolean newFrame =
				currentDownlinkFrameID == 0x00 &&
				receiveFrameID != 0x0 &&
				(
					ambePacket.getDVPacket().hasPacketType(PacketType.Header) ||
					cacheHeader != null
				);

			boolean frameIDMatched =
				currentDownlinkFrameID == receiveFrameID;

			if(newFrame && downlinkState == UplinkState.WaitFrameStart) {

				clearDownlink();

				downlinkTimekeeper.updateTimestamp();

				downlinkState = UplinkState.FrameProcessing;
				currentDownlinkFrameID = receiveFrameID;
				currentDownlinkHeader =
					cacheHeader != null ? cacheHeader.getVoiceHeader() : ambePacket.getRfHeader();

				if(cacheHeader != null) {frameIDMatched = true;}

				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Start downlink frameID = " + String.format("0x%04X", currentDownlinkFrameID) + "."
					);
				}
			}

			if(
				frameIDMatched &&
				downlinkState == UplinkState.FrameProcessing &&
				ambePacket.getDVPacket().hasPacketType(PacketType.Voice)
			) {
				downlinkTimekeeper.updateTimestamp();

				if(serverConfig.isSupportedCodecAMBE()) {
					final VTAMBE noraAMBEPacket = new VTAMBE(currentDownlinkHeader);
					noraAMBEPacket.setFrameID(currentDownlinkFrameID);
					noraAMBEPacket.setLongSequence(currentDownlinkAMBELongSequence);
					noraAMBEPacket.setShortSequence(ambePacket.getBackBone().getSequenceNumber());
					noraAMBEPacket.setEndSequence(ambePacket.isEndVoicePacket());
					for(int i = 0; i < ambePacket.getVoiceData().getVoiceSegment().length; i++) {
						noraAMBEPacket.getAudio().add(ambePacket.getVoiceData().getVoiceSegment()[i]);
					}
					ArrayUtil.copyOf(noraAMBEPacket.getSlowdata(), ambePacket.getVoiceData().getDataSegment());

					noravr.writePacket(noraAMBEPacket);
					currentDownlinkAMBELongSequence = nextLongSequence(currentDownlinkAMBELongSequence);
				}

				final Byte[] slowdata = new Byte[ambePacket.getVoiceData().getDataSegment().length];
				for(int i = 0; i < slowdata.length; i++) {
					slowdata[i] = ambePacket.getVoiceData().getDataSegment()[i];
				}
				if(serverConfig.isSupportedCodecPCM()) { downlinkPCMInfo.slowdata.add(slowdata); }
				if(serverConfig.isSupportedCodecOpus64k()) { downlinkOpus64kInfo.slowdata.add(slowdata); }
				if(serverConfig.isSupportedCodecOpus24k()) { downlinkOpus24kInfo.slowdata.add(slowdata); }
				if(serverConfig.isSupportedCodecOpus8k()) { downlinkOpus8kInfo.slowdata.add(slowdata); }

				final ByteBuffer voicedata =
					ByteBuffer.wrap(ambePacket.getVoiceData().getVoiceSegment());

				if(dv3k != null) {dv3k.decodeInput(voicedata);}

				currentDownlinkInputPacketCount++;

				if(ambePacket.isEndVoicePacket()) {
					//ヘッダキャッシュ削除
					downlinkHeaderCache.remove(receiveFrameID);

					if(isNeedDV3K(serverConfig))
						downlinkState = UplinkState.FrameTerminate;
					else {
						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"End downlink frameID = " + String.format("0x%04X", currentDownlinkFrameID) + "."
							);
						}

						clearDownlink();
					}
				}
			}
		}

		ShortBuffer dv3kdecoded = null;
		while(dv3k != null && (dv3kdecoded = dv3k.decodeOutput()) != null) {
			for(final VoiceVocoder<ShortBuffer> vocoder : vocoders.values()) {
				dv3kdecoded.rewind();

				vocoder.encodeInput(dv3kdecoded);
			}
		}

		for(final Map.Entry<NoraVRCodecType, VoiceVocoder<ShortBuffer>> vocoderEntry : vocoders.entrySet()) {
			final NoraVRCodecType vocoderCodec = vocoderEntry.getKey();
			final VoiceVocoder<ShortBuffer> vocoder = vocoderEntry.getValue();

			byte[] vocoderEncoded = null;
			while((vocoderEncoded = vocoder.encodeOutput()) != null) {
				NoraVRVoicePacket<?> noraPacket = null;

				switch(vocoderCodec) {
				case PCM:
					if(!serverConfig.isSupportedCodecPCM() || downlinkPCMInfo.isEnd) {continue;}

					final VTPCM pcmPacket = new VTPCM(currentDownlinkHeader);
					noraPacket = pcmPacket;

					pcmPacket.setLongSequence(downlinkPCMInfo.longSequence);
					pcmPacket.setShortSequence(downlinkPCMInfo.shortSequence);
					final Byte[] pcmSlowdata = downlinkPCMInfo.slowdata.poll();
					if(pcmSlowdata != null) {
						for(
							int i = 0;
							i < DSTARDefines.DataSegmentLength &&
							i < pcmPacket.getSlowdata().length &&
							i < pcmSlowdata.length;
							i++
						) {pcmPacket.getSlowdata()[i] = pcmSlowdata[i];}
					}
					else {
						ArrayUtil.copyOf(pcmPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);
					}

					for(int i = 0; i < vocoderEncoded.length; i += 2) {
						final short sample =
							(short)(((vocoderEncoded[i] << 8) & 0xFF00) | (vocoderEncoded[i + 1] & 0x00FF));

						pcmPacket.getAudio().add(sample);
					}

					downlinkPCMInfo.longSequence = nextLongSequence(downlinkPCMInfo.longSequence);
					downlinkPCMInfo.shortSequence = nextShortSequence(downlinkPCMInfo.shortSequence);
					downlinkPCMInfo.packetCount++;

					if(
						downlinkState == UplinkState.FrameTerminate &&
						downlinkPCMInfo.packetCount >= currentDownlinkInputPacketCount
					) {
						downlinkPCMInfo.isEnd = true;
						pcmPacket.setEndSequence(true);
					}
					break;

				case Opus64k:
					if(!serverConfig.isSupportedCodecOpus64k() || downlinkOpus64kInfo.isEnd) {continue;}

					final VTOPUS opus64kPacket = new VTOPUS(vocoderCodec, currentDownlinkHeader);
					noraPacket = opus64kPacket;

					opus64kPacket.setLongSequence(downlinkOpus64kInfo.longSequence);
					opus64kPacket.setShortSequence(downlinkOpus64kInfo.shortSequence);
					final Byte[] opus64kSlowdata = downlinkOpus64kInfo.slowdata.poll();
					if(opus64kSlowdata != null) {
						for(
							int i = 0;
							i < DSTARDefines.DataSegmentLength &&
							i < opus64kPacket.getSlowdata().length &&
							i < opus64kSlowdata.length;
							i++
						) {opus64kPacket.getSlowdata()[i] = opus64kSlowdata[i];}
					}
					else {
						ArrayUtil.copyOf(opus64kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);
					}

					for(int i = 0; i < vocoderEncoded.length; i++) {
						opus64kPacket.getAudio().add(vocoderEncoded[i]);
					}

					downlinkOpus64kInfo.longSequence = nextLongSequence(downlinkOpus64kInfo.longSequence);
					downlinkOpus64kInfo.shortSequence = nextShortSequence(downlinkOpus64kInfo.shortSequence);
					downlinkOpus64kInfo.packetCount++;

					if(
						downlinkState == UplinkState.FrameTerminate &&
						downlinkOpus64kInfo.packetCount >= currentDownlinkInputPacketCount
					) {
						downlinkOpus64kInfo.isEnd = true;
						opus64kPacket.setEndSequence(true);
					}
					break;

				case Opus24k:
					if(!serverConfig.isSupportedCodecOpus24k() || downlinkOpus24kInfo.isEnd) {continue;}

					final VTOPUS opus24kPacket = new VTOPUS(vocoderCodec, currentDownlinkHeader);
					noraPacket = opus24kPacket;

					opus24kPacket.setLongSequence(downlinkOpus24kInfo.longSequence);
					opus24kPacket.setShortSequence(downlinkOpus24kInfo.shortSequence);
					final Byte[] opus24kSlowdata = downlinkOpus24kInfo.slowdata.poll();
					if(opus24kSlowdata != null) {
						for(
							int i = 0;
							i < DSTARDefines.DataSegmentLength &&
							i < opus24kPacket.getSlowdata().length &&
							i < opus24kSlowdata.length;
							i++
						) {opus24kPacket.getSlowdata()[i] = opus24kSlowdata[i];}
					}
					else {
						ArrayUtil.copyOf(opus24kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);
					}

					for(int i = 0; i < vocoderEncoded.length; i++) {
						opus24kPacket.getAudio().add(vocoderEncoded[i]);
					}

					downlinkOpus24kInfo.longSequence = nextLongSequence(downlinkOpus24kInfo.longSequence);
					downlinkOpus24kInfo.shortSequence = nextShortSequence(downlinkOpus24kInfo.shortSequence);
					downlinkOpus24kInfo.packetCount++;

					if(
						downlinkState == UplinkState.FrameTerminate &&
						downlinkOpus24kInfo.packetCount >= currentDownlinkInputPacketCount
					) {
						downlinkOpus24kInfo.isEnd = true;
						opus24kPacket.setEndSequence(true);
					}
					break;

				case Opus8k:
					if(!serverConfig.isSupportedCodecOpus8k() || downlinkOpus8kInfo.isEnd) {continue;}

					final VTOPUS opus8kPacket = new VTOPUS(vocoderCodec, currentDownlinkHeader);
					noraPacket = opus8kPacket;

					opus8kPacket.setLongSequence(downlinkOpus8kInfo.longSequence);
					opus8kPacket.setShortSequence(downlinkOpus8kInfo.shortSequence);
					final Byte[] opus8kSlowdata = downlinkOpus8kInfo.slowdata.poll();
					if(opus8kSlowdata != null) {
						for(
							int i = 0;
							i < DSTARDefines.DataSegmentLength &&
							i < opus8kPacket.getSlowdata().length &&
							i < opus8kSlowdata.length;
							i++
						) {opus8kPacket.getSlowdata()[i] = opus8kSlowdata[i];}
					}
					else {
						ArrayUtil.copyOf(opus8kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);
					}

					for(int i = 0; i < vocoderEncoded.length; i++) {
						opus8kPacket.getAudio().add(vocoderEncoded[i]);
					}

					downlinkOpus8kInfo.longSequence = nextLongSequence(downlinkOpus8kInfo.longSequence);
					downlinkOpus8kInfo.shortSequence = nextShortSequence(downlinkOpus8kInfo.shortSequence);
					downlinkOpus8kInfo.packetCount++;

					if(
						downlinkState == UplinkState.FrameTerminate &&
						downlinkOpus8kInfo.packetCount >= currentDownlinkInputPacketCount
					) {
						downlinkOpus8kInfo.isEnd = true;
						opus8kPacket.setEndSequence(true);
					}
					break;

				case Opus:
					continue;

				default:
					new RuntimeException("Illegal vocoder codec " + vocoderCodec.getTypeName() + ".");
				}

				noraPacket.setFrameID(currentDownlinkFrameID);

				noravr.writePacket(noraPacket);

				if(
					downlinkState == UplinkState.FrameTerminate &&
					(!serverConfig.isSupportedCodecPCM() || downlinkPCMInfo.isEnd) &&
					(!serverConfig.isSupportedCodecOpus64k() || downlinkOpus64kInfo.isEnd) &&
					(!serverConfig.isSupportedCodecOpus24k() || downlinkOpus24kInfo.isEnd) &&
					(!serverConfig.isSupportedCodecOpus8k() || downlinkOpus8kInfo.isEnd)
				) {
					//ヘッダキャッシュ削除
					downlinkHeaderCache.remove(currentDownlinkFrameID);

					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"End downlink frameID = " + String.format("0x%04X", currentDownlinkFrameID) + "."
						);
					}

					clearDownlink();
				}
			}
		}

		if(
			downlinkState != UplinkState.WaitFrameStart &&
			downlinkTimekeeper.isTimeout(1, TimeUnit.SECONDS)
		) {
			if(serverConfig.isSupportedCodecAMBE() && currentDownlinkInputPacketCount >= 1) {
				final VTAMBE ambeEndPacket = new VTAMBE(currentDownlinkHeader);
				ambeEndPacket.setFrameID(currentDownlinkFrameID);
				ambeEndPacket.setLongSequence(downlinkPCMInfo.longSequence);
				ambeEndPacket.setShortSequence(downlinkPCMInfo.shortSequence);
				ambeEndPacket.setEndSequence(true);
				ambeEndPacket.getAudio().clear();
				for(int i = 0; i < DSTARDefines.VoiceSegmentNullBytes.length; i++) {
					ambeEndPacket.getAudio().add(DSTARDefines.VoiceSegmentNullBytes[i]);
				}
				ArrayUtil.copyOf(ambeEndPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);

				noravr.writePacket(ambeEndPacket);
			}

			if(serverConfig.isSupportedCodecPCM() && downlinkPCMInfo.packetCount >= 1) {
				final VTPCM pcmPacket = new VTPCM(currentDownlinkHeader);
				pcmPacket.setFrameID(currentDownlinkFrameID);
				pcmPacket.setLongSequence(downlinkPCMInfo.longSequence);
				pcmPacket.setShortSequence(downlinkPCMInfo.shortSequence);
				pcmPacket.setEndSequence(true);
				ArrayUtil.copyOf(pcmPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);

				noravr.writePacket(pcmPacket);
			}

			if(serverConfig.isSupportedCodecOpus64k() && downlinkOpus64kInfo.packetCount >= 1) {
				final VTOPUS opus64kPacket = new VTOPUS(NoraVRCodecType.Opus64k, currentDownlinkHeader);

				opus64kPacket.setFrameID(currentDownlinkFrameID);
				opus64kPacket.setLongSequence(downlinkOpus64kInfo.longSequence);
				opus64kPacket.setShortSequence(downlinkOpus64kInfo.shortSequence);
				opus64kPacket.setEndSequence(true);
				ArrayUtil.copyOf(opus64kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);

				noravr.writePacket(opus64kPacket);
			}

			if(serverConfig.isSupportedCodecOpus24k() && downlinkOpus24kInfo.packetCount >= 1) {
				final VTOPUS opus24kPacket = new VTOPUS(NoraVRCodecType.Opus24k, currentDownlinkHeader);

				opus24kPacket.setFrameID(currentDownlinkFrameID);
				opus24kPacket.setLongSequence(downlinkOpus24kInfo.longSequence);
				opus24kPacket.setShortSequence(downlinkOpus24kInfo.shortSequence);
				opus24kPacket.setEndSequence(true);
				ArrayUtil.copyOf(opus24kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);

				noravr.writePacket(opus24kPacket);
			}

			if(serverConfig.isSupportedCodecOpus8k() && downlinkOpus8kInfo.packetCount >= 1) {
				final VTOPUS opus8kPacket = new VTOPUS(NoraVRCodecType.Opus8k, currentDownlinkHeader);

				opus8kPacket.setFrameID(currentDownlinkFrameID);
				opus8kPacket.setLongSequence(downlinkOpus24kInfo.longSequence);
				opus8kPacket.setShortSequence(downlinkOpus24kInfo.shortSequence);
				opus8kPacket.setEndSequence(true);
				ArrayUtil.copyOf(opus8kPacket.getSlowdata(), DSTARDefines.SlowdataNullBytes);

				noravr.writePacket(opus8kPacket);
			}

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Timeout downlink frameID = " + String.format("0x%04X", currentDownlinkFrameID) + "."
				);
			}

			clearDownlink();
		}


		return ThreadProcessResult.NoErrors;
	}

	private void clearDownlink() {
		downlinkState = UplinkState.WaitFrameStart;
		currentDownlinkFrameID = 0x0;
		currentDownlinkHeader = null;
		downlinkPCMInfo.clear();
		downlinkOpus64kInfo.clear();
		downlinkOpus24kInfo.clear();
		downlinkOpus8kInfo.clear();
		currentDownlinkAMBELongSequence = 0;
		currentDownlinkInputPacketCount = 0;
	}

	private ThreadProcessResult processUplinkPacket() {
		NoraVRPacket nrvrPacket = null;
		while((nrvrPacket = noravr.readPacket()) != null) {
			if(!(nrvrPacket instanceof VoiceTransferBase)) {continue;}

			final NoraVRVoicePacket<?> voice = (NoraVRVoicePacket<?>)nrvrPacket;

			if(log.isTraceEnabled()) {
				log.trace(logHeader + "Receive uplink packet.\n" + voice.toString(4));
			}

			final boolean newFrame =
				!voice.isEndSequence() &&
				currentUplinkFrameID == 0x0 && voice.getFrameID() != 0x0;

			if(newFrame && uplinkState == UplinkState.WaitFrameStart) {
				//New Frame
				clearUplink();

				currentUplinkFrameID = voice.getFrameID();
				uplinkTimekeeper.setTimeoutTime(1000, TimeUnit.MILLISECONDS);
				uplinkTimekeeper.updateTimestamp();
				loopBlockID = UUID.randomUUID();

				final Header voiceHeader = new Header();
				ArrayUtil.copyOf(voiceHeader.getFlags(), voice.getFlags());
				voiceHeader.setRepeater2Callsign(voice.getRepeater2Callsign().toCharArray());
				voiceHeader.setRepeater1Callsign(voice.getRepeater1Callsign().toCharArray());
				voiceHeader.setYourCallsign(voice.getYourCallsign().toCharArray());
				voiceHeader.setMyCallsign(voice.getMyCallsignLong().toCharArray());
				voiceHeader.setMyCallsignAdd(voice.getMyCallsignShort().toCharArray());

				currentUplinkCodec = voice.getCodecType();
				uplinkState = UplinkState.FrameProcessing;

				currentUplinkHeader = voiceHeader;
				currentUplinkLongSequence = voice.getLongSequence();
				currentUplinkOutputShortSequence = voice.getShortSequence();

//				uplinkTransmitter.reset();
			}

			final boolean frameIDMatched =
				currentUplinkFrameID == voice.getFrameID() &&
				currentUplinkFrameID != 0x0 &&
				voice.getFrameID() != 0x0;

			if(frameIDMatched) {
				if(uplinkState == UplinkState.FrameProcessing) {

					uplinkTimekeeper.updateTimestamp();

					final DataSegmentDecoderResult slowdataResult =
						uplinkSlowdataDecoder.decode(voice.getSlowdata());

					if(newFrame) {
						addReadPacket(
							PacketType.Header,
							new InternalPacket(
								loopBlockID,
								new DVPacket(
									new BackBoneHeader(
										BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, currentUplinkFrameID
									),
									currentUplinkHeader.clone()
								)
							),
							FrameSequenceType.Start
						);
					}

					if(
						currentUplinkCodec == NoraVRCodecType.AMBE &&
						voice instanceof VTAMBE
					) {
						uplinkSilencePackets.clear();

						final DVPacket voicePacket = convertVTAMBE2DvPacket(currentUplinkFrameID, (VTAMBE)voice);

						if(slowdataResult == DataSegmentDecoderResult.SYNC) {
							if(currentUplinkOutputShortSequence != (voice.getShortSequence() & 0x1F) && log.isDebugEnabled())
								if(log.isDebugEnabled()) {log.debug(logHeader + "Uplink short sequence not matched.");}

							currentUplinkOutputShortSequence = 0x0;
						}
						voicePacket.setRfHeader(currentUplinkHeader.clone());
						voicePacket.setPacketType(PacketType.Header, PacketType.Voice);
						voicePacket.getBackBone().setSequenceNumber((byte)currentUplinkOutputShortSequence);
						if(voice.isEndSequence()) { voicePacket.getBackBone().setEndSequence(); }

						if(uplinkOutputPacketCount < uplinkPacketLimit) {
							addReadPacket(
								PacketType.Voice,
								new InternalPacket(loopBlockID, voicePacket),
								voice.isEndSequence() ? FrameSequenceType.End : FrameSequenceType.None
							);

							if(
								voice.getShortSequence() == DSTARDefines.MaxSequenceNumber &&
								!voice.isEndSequence()
							) {
								addReadPacket(
									PacketType.Voice,
									new InternalPacket(
										loopBlockID,
										new DVPacket(
											new BackBoneHeader(
												BackBoneHeaderType.DV,
												BackBoneHeaderFrameType.VoiceDataHeader,
												currentUplinkFrameID
											),
											currentUplinkHeader.clone()
										)
									),
									FrameSequenceType.None
								);
							}

							if(voice.isEndSequence()) {
								if(log.isDebugEnabled()) {
									log.debug(
										logHeader +
										"End uplink frameID = " + String.format("0x%04X", currentUplinkFrameID) + "."
									);
								}

								clearUplink();
							}
						}
						else {
							if(log.isWarnEnabled()) {
								log.warn(
									logHeader +
									"Uplink transmission time limit exceeded, frameID = " + String.format("0x%04X", currentUplinkFrameID) + ".\n" +
									currentUplinkHeader.toString(4)
								);
							}

							uplinkState = UplinkState.FrameTimeout;
							voicePacket.getBackBone().setEndSequence();

							addReadPacket(
								PacketType.Voice,
								new InternalPacket(loopBlockID, voicePacket),
								FrameSequenceType.End
							);
						}

						if(!voice.isEndSequence()) {
							currentUplinkLongSequence = nextLongSequence(currentUplinkLongSequence);
							currentUplinkOutputShortSequence = nextShortSequence(currentUplinkOutputShortSequence);
							uplinkOutputPacketCount++;
						}
					}
					else {
						uplinkSilencePackets.clear();

						final int loss =
							insertUplinkSilencePacket(uplinkSilencePackets, voice, currentUplinkCodec, currentUplinkLongSequence);

						if(loss >= 1) {
							if(log.isDebugEnabled())
								log.debug(logHeader + loss + " packet loss on frame id = " + String.format("0x%04X", currentUplinkFrameID));

							final int silencePacketsSize = uplinkSilencePackets.size();
							writeToVocoderDecode(uplinkSilencePackets, true);
							final int insertSilencePackets = silencePacketsSize - uplinkSilencePackets.size();

							for(int i = 0; i < insertSilencePackets; i++) {
								final Byte[] copySlowdataNullBytes = new Byte[DSTARDefines.DataSegmentLength];
								for(int c = 0; c < DSTARDefines.DataSegmentLength; c++) {
									copySlowdataNullBytes[c] = DSTARDefines.SlowdataNullBytes[c];
								}
								uplinkSlowdata.add(copySlowdataNullBytes);
							}

							currentUplinkLongSequence =
								nextLongSequence(currentUplinkLongSequence, insertSilencePackets);
							uplinkInputPacketCount += insertSilencePackets;
						}
						uplinkSilencePackets.clear();


						writeToVocoderDecode(voice, false);
						currentUplinkLongSequence = nextLongSequence(currentUplinkLongSequence);
						uplinkInputPacketCount++;

						final Byte[] copySlowdata = new Byte[DSTARDefines.DataSegmentLength];
						for(int i = 0; i < voice.getSlowdata().length; i++) {
							copySlowdata[i] = voice.getSlowdata()[i];
						}
						uplinkSlowdata.add(copySlowdata);

						if(voice.isEndSequence()) {
							uplinkState = UplinkState.FrameTerminate;
						}
					}
				}
				else if(uplinkState == UplinkState.FrameTimeout) {
					uplinkTimekeeper.updateTimestamp();

					if(voice.isEndSequence()) {clearUplink();}
				}
			}
		}


		for(final VoiceVocoder<ShortBuffer> vocoder : vocoders.values()) {
			ShortBuffer decodedPCM = null;
			while((decodedPCM = vocoder.decodeOutput()) != null) {
				if(currentUplinkCodec == NoraVRCodecType.AMBE) {continue;}

				dv3k.encodeInput(decodedPCM);
			}
		}

		ByteBuffer ambe = null;
		while(dv3k != null && (ambe = dv3k.encodeOutput()) != null) {
			if(
				currentUplinkCodec == NoraVRCodecType.AMBE ||
				uplinkState == UplinkState.FrameTimeout ||
				currentUplinkFrameID == 0x0 ||
				currentUplinkHeader == null
			) {continue;}

			final boolean frameEnd =
				uplinkState == UplinkState.FrameTerminate &&
				(uplinkOutputPacketCount + 1) >= uplinkInputPacketCount;

			if(uplinkOutputPacketCount == 0 || uplinkOutputPacketCount % 0x15 == 0) {
				addReadPacket(
					PacketType.Header,
					new InternalPacket(
						loopBlockID,
						new DVPacket(
							new BackBoneHeader(
								BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, currentUplinkFrameID
							),
							currentUplinkHeader.clone()
						)
					),
					FrameSequenceType.None
				);
			}

			final VoiceAMBE ambeVoice = new VoiceAMBE();
			for(int i = 0; i < DSTARDefines.VoiceSegmentLength && ambe.hasRemaining(); i++) {
				ambeVoice.getVoiceSegment()[i] = ambe.get();
			}
			final Byte[] ambeSlowdata = uplinkSlowdata.poll();
			if(frameEnd) {
				ambeVoice.setDataSegment(DSTARDefines.SlowdataEndBytes);
			}
			else if(ambeSlowdata != null) {
				final byte[] copySlowdata = new byte[ambeSlowdata.length];
				for(int i = 0; i < copySlowdata.length; i++) {
					copySlowdata[i] = ambeSlowdata[i];
				}
				ambeVoice.setDataSegment(copySlowdata);
			}
			else {
				ambeVoice.setDataSegment(DSTARDefines.SlowdataNullBytes);
			}

			addReadPacket(
				PacketType.Voice,
				new InternalPacket(
					loopBlockID,
					new DVPacket(
						new BackBoneHeader(
							BackBoneHeaderType.DV,
							BackBoneHeaderFrameType.VoiceData,
							currentUplinkFrameID,
							(byte)currentUplinkOutputShortSequence
						),
						currentUplinkHeader.clone(),
						ambeVoice
					)
				),
				FrameSequenceType.None
			);

			if(uplinkOutputPacketCount < uplinkPacketLimit) {
				if(frameEnd) {
					addReadPacket(
						PacketType.Voice,
						new InternalPacket(
							loopBlockID,
							DSTARUtils.createLastVoicePacket(
								currentUplinkFrameID, (byte)currentUplinkOutputShortSequence,
								currentUplinkHeader.clone()
							)
						),
						FrameSequenceType.End
					);
				}

				uplinkOutputPacketCount++;

				currentUplinkOutputShortSequence = nextShortSequence(currentUplinkOutputShortSequence);

				if(frameEnd) {
					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"End uplink frameID = " + String.format("0x%04X", currentUplinkFrameID) + "."
						);
					}

					clearUplink();
				}
			}
			else {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Uplink transmission time limit exceeded, frameID = " + String.format("0x%04X", currentUplinkFrameID) + ".\n" +
						currentUplinkHeader.toString(4)
					);
				}

				addReadPacket(
					PacketType.Voice,
					new InternalPacket(
						loopBlockID,
						DSTARUtils.createLastVoicePacket(
							currentUplinkFrameID, (byte)currentUplinkOutputShortSequence,
							currentUplinkHeader.clone()
						)
					),
					FrameSequenceType.End
				);

				uplinkState = UplinkState.FrameTimeout;
			}
		}

		if(
			uplinkState != UplinkState.WaitFrameStart &&
			uplinkTimekeeper.isTimeout() &&
			currentUplinkFrameID != 0x0 &&
			currentUplinkHeader != null
		) {
			addReadPacket(
				PacketType.Voice,
				new InternalPacket(
					loopBlockID,
					DSTARUtils.createPreLastVoicePacket(
						currentUplinkFrameID,
						(byte)currentUplinkOutputShortSequence,
						currentUplinkHeader.clone()
					)
				),
				FrameSequenceType.None
			);

			addReadPacket(
				PacketType.Voice,
				new InternalPacket(
					loopBlockID,
					DSTARUtils.createLastVoicePacket(
						currentUplinkFrameID,
						DSTARUtils.getNextShortSequence((byte)currentUplinkOutputShortSequence),
						currentUplinkHeader.clone()
					)
				),
				FrameSequenceType.End
			);

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Timeout uplink frameID = " + String.format("0x%04X", currentUplinkFrameID) + "."
				);
			}

			clearUplink();
		}

		return ThreadProcessResult.NoErrors;
	}

	private void clearUplink() {
		uplinkState = UplinkState.WaitFrameStart;
		currentUplinkFrameID = 0x0;
		currentUplinkHeader = null;
		currentUplinkLongSequence = 0;
		currentUplinkOutputShortSequence = 0x0;
		currentUplinkCodec = null;
		uplinkInputPacketCount = 0;
		uplinkOutputPacketCount = 0;
		uplinkSilencePackets.clear();
		uplinkSlowdata.clear();
		uplinkSlowdataDecoder.reset();
	}

	private int insertUplinkSilencePacket(
		@NonNull final Queue<NoraVRVoicePacket<?>> silencePackets,
		@NonNull final NoraVRVoicePacket<?> receivePacket,
		@NonNull final NoraVRCodecType codecType,
		int currentSequence
	) {
		final int receiveSequence = receivePacket.getLongSequence();

		int loss = calcPacketLoss(currentSequence, receiveSequence, 0x10000);

		if(loss >= 1 && loss <= 10) {
			for(int i = 0; i < loss; i++) {
				final NoraVRVoicePacket<?> copy = receivePacket.clone();
				copy.setLongSequence(currentSequence);

				switch(codecType) {
				case PCM:
					final VTPCM pcmPacket = (VTPCM)copy;
					pcmPacket.getAudio().clear();
					for(int c = 0; c < 160; c++) {pcmPacket.getAudio().add((short)0x0000);}
					break;

				case Opus64k:
				case Opus24k:
				case Opus8k:
				case Opus:
					copy.getAudio().clear();
					break;

				case AMBE:
					final VTAMBE ambePacket = (VTAMBE)copy;
					ambePacket.getAudio().clear();
					for(int c = 0; c < DSTARDefines.VoiceSegmentNullBytes.length; c++)
						ambePacket.getAudio().add(DSTARDefines.VoiceSegmentNullBytes[i]);

					break;

				default:
					throw new RuntimeException();
				}

				silencePackets.add(copy);

				currentSequence = nextLongSequence(currentSequence);
			}
		}
		else if(loss > 10) {
			loss = -1;
		}

		return loss;
	}

	private int nextLongSequence(final int currentSequence) {
		return nextLongSequence(currentSequence, 1);
	}

	/**
	 *
	 * @param currentSequence 現在のシーケンス値
	 * @param fwdSequence 進めるシーケンス値
	 * @return 進めた新しいシーケンス値
	 */
	private int nextLongSequence(final int currentSequence, final int fwdSequence) {
		return (currentSequence + fwdSequence) % 0x10000;
	}

	private int nextShortSequence(final int currentSequence) {
		return nextShortSequence(currentSequence, 1);
	}

	private int nextShortSequence(final int currentSequence, final int fwdSequence) {
		return (currentSequence + fwdSequence) % 0x15;
	}

	private int calcPacketLoss(final int currentSeq, final int receiveSeq, final int maxSequence) {
		int loss = 0;
		if(receiveSeq >= currentSeq)
			loss = receiveSeq - currentSeq;
		else
			loss = (receiveSeq + maxSequence) - currentSeq;

		return loss;
	}

	private boolean writeToVocoderDecode(final Queue<NoraVRVoicePacket<?>> voices, final boolean loss) {
		boolean result = true;
		for(Iterator<NoraVRVoicePacket<?>> it = voices.iterator(); it.hasNext();) {
			final NoraVRVoicePacket<?> voice = it.next();

			if(writeToVocoderDecode(voice, loss))
				it.remove();
			else
				result = false;
		}

		return result;
	}

	private boolean writeToVocoderDecode(final NoraVRVoicePacket<?> voice, boolean loss) {

		final VoiceVocoder<ShortBuffer> vocoder =
			findVocoder(voice.getCodecType());

		if(vocoder == null) {return false;}

		if(voice instanceof VTOPUS) {
			final VTOPUS opusVoice = (VTOPUS)voice;
			final byte[] opusdata = new byte[opusVoice.getAudio().size()];
			for(int i = 0; i < opusdata.length && !opusVoice.getAudio().isEmpty(); i++) {
				opusdata[i] = opusVoice.getAudio().poll();
			}

			return vocoder.decodeInput(opusdata, loss);
		}
		else if(voice instanceof VTPCM) {
			final VTPCM pcmVoice = (VTPCM)voice;
			final int pcmdataLength = pcmVoice.getAudio().size() << 1;
			final byte[] pcmdata = new byte[pcmdataLength];
			for(int i = 0; i < pcmdata.length && !pcmVoice.getAudio().isEmpty(); i += 2) {
				final int pcmSample = pcmVoice.getAudio().poll();
				pcmdata[i] = (byte)((pcmSample >> 8) & 0xFF);
				pcmdata[i + 1] = (byte)(pcmSample & 0xFF);
			}

			return vocoder.decodeInput(pcmdata, loss);
		}
		else {
			return false;
		}
	}

	private VoiceVocoder<ShortBuffer> findVocoder(final NoraVRCodecType vocoderType){
		return vocoders.get(vocoderType);
	}

	private @NonNull DVPacket convertVTAMBE2DvPacket(
		final int frameID, @NonNull final VTAMBE vtambe
	) {
		final VoiceAMBE ambeVoice = new VoiceAMBE();

		for(int i = 0; i < ambeVoice.getVoiceSegment().length && !vtambe.getAudio().isEmpty(); i++) {
			ambeVoice.getVoiceSegment()[i] = (byte)vtambe.getAudio().poll();
		}
		ambeVoice.setDataSegment(vtambe.getSlowdata());
		final DVPacket voicePacket = new DVPacket(
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID),
			ambeVoice
		);
		voicePacket.getBackBone().setSequenceNumber((byte)vtambe.getShortSequence());
		if(vtambe.isEndSequence()) {voicePacket.getBackBone().setEndSequence();}

		return voicePacket;
	}

	private boolean addReadPacket(
		final PacketType packetType,
		final DSTARPacket packet,
		FrameSequenceType frameSequenceType
	) {
		boolean result = false;

		rwPacketsLocker.lock();
		try {
			result =
				uplinkTransmitter.inputWrite(new UplinkReadPacket(packetType, packet, frameSequenceType));
		}finally {rwPacketsLocker.unlock();}

		if(result)
			getRepeater().wakeupRepeaterWorker();

		return result;
	}

	private boolean isNeedDV3K(final NoraVRConfiguration config) {
		return
			config.isSupportedCodecPCM() |
			config.isSupportedCodecOpus64k() |
			config.isSupportedCodecOpus24k() |
			config.isSupportedCodecOpus8k();
	}

	@Override
	protected ModemStatusReport getStatusReportInternal(ModemStatusReport report) {
		if(getScope() == AccessScope.Public) {
			report.getModemProperties().put(
				noraVRPublicServerAddressPropertyName, getNoraVRPublicServerAddress()
			);
			report.getModemProperties().put(
				noraVRPublicServerPortPropertyName,
				String.valueOf(
					getNoraVRPublicServerPort() > 0 ? getNoraVRPublicServerPort() : getNoravrPort()
				)
			);

			report.getModemProperties().put(
				noravrPortPropertyName,
				String.valueOf(getNoravrPort())
			);

			report.getModemProperties().put(
				noravrLoginPasswordPropertyName, getNoravrLoginPassword()
			);

			report.getModemProperties().put(
				noravrUseCodecPCMPropertyName, String.valueOf(isNoravrUseCodecPCM())
			);
			report.getModemProperties().put(
				noravrUseCodecOpus64kPropertyName, String.valueOf(isNoravrUseCodecOpus64k())
			);
			report.getModemProperties().put(
				noravrUseCodecOpus24kPropertyName, String.valueOf(isNoravrUseCodecOpus24k())
			);
			report.getModemProperties().put(
				noravrUseCodecOpus8kPropertyName, String.valueOf(isNoravrUseCodecOpus8k())
			);
			report.getModemProperties().put(
				noravrUseCodecAMBEPropertyName, String.valueOf(isNoravrUseCodecAMBE())
			);

			report.getModemProperties().put(
				noravrAllowRFNodePropertyName, String.valueOf(isNoravrAllowRFNode())
			);

			report.getModemProperties().put(
				"NoraVRProtocolVersion",
				String.valueOf(NoraVRProtocolProcessor.getSupportedProtocolVersion())
			);

			final List<NoraVRLoginClient> loginClients = getLoginClients();
			report.getModemProperties().put(
				"NoraVRLoginClientCount",
				String.valueOf(loginClients.size())
			);

			final Gson gson = new GsonBuilder().create();
			report.getModemProperties().put(
				"NoraVRLoginClientInformation",
				gson.toJson(loginClients)
			);
		}

		return report;
	}

	@Override
	public NoraVRConfig getConfig() {
		final NoraVRConfiguration config = noravr.getServerConfiguration();
		return new NoraVRConfig(
			config != null ? config.isSupportedCodecPCM() : false,
			config != null ? config.isSupportedCodecOpus64k() : false,
			config != null ? config.isSupportedCodecOpus24k() : false,
			config != null ? config.isSupportedCodecOpus8k() : false,
			config != null ? config.isSupportedCodecAMBE() : false,
			config != null ? config.isEchoback() : false,
			config != null ? config.isRfNode() : false
		);
	}

	@Override
	public List<NoraVRLoginClient> getLoginClients() {
		return Stream.of(noravr.getLoginUsers())
			.map(new Function<NoraVRClientEntry, NoraVRLoginClient>(){
				@Override
				public NoraVRLoginClient apply(NoraVRClientEntry client) {
					return new NoraVRLoginClient(
						client.getClientID(),
						client.getLoginCallsign(),
						client.getCreateTime(),
						client.getDownlinkCodec() != null ? client.getDownlinkCodec().toString() : ""
					);
				}
			}).toList();
	}

	@Override
	public void onClientLoginEvent(NoraVRClientEntry client) {

		if(log.isInfoEnabled()) {
			log.info(
				logHeader +
				"Client " + client.getLoginCallsign() + String.format("(ID:0x%08X)", client.getClientID()) +
				" is logged in, " + client.getApplicationName() + " v" + client.getApplicationVersion() +
				"/ProtocolVersion=" + client.getProtocolVersion() +
				"/RemoteHost:" + client.getRemoteHostAddress()
			);
		}

		if(isEnableWebRemoteControlService()) {
			getWebRemoteControlService().notifyModemNoraVRClientLogin(
				this,
				new NoraVRLoginClient(
					client.getClientID(),
					client.getLoginCallsign(),
					client.getCreateTime(),
					client.getDownlinkCodec() != null ? client.getDownlinkCodec().toString() : ""
				)
			);
		}
	}

	@Override
	public void onClientLogoutEvent(NoraVRClientEntry client) {

		if(log.isInfoEnabled()) {
			log.info(
				logHeader +
				"Client " + client.getLoginCallsign() + String.format("(ID:0x%08X)", client.getClientID()) +
				" is logout, " + client.getApplicationName() + " v" + client.getApplicationVersion() +
				"/ProtocolVersion=" + client.getProtocolVersion() +
				"/RemoteHost:" + client.getRemoteHostAddress()
			);
		}

		if(isEnableWebRemoteControlService()) {
			getWebRemoteControlService().notifyModemNoraVRClientLogout(
				this,
				new NoraVRLoginClient(
					client.getClientID(),
					client.getLoginCallsign(),
					client.getCreateTime(),
					client.getDownlinkCodec() != null ? client.getDownlinkCodec().toString() : ""
				)
			);
		}
	}

	@Override
	protected ModemStatusData createStatusDataInternal() {
		final NoraVRStatusData status = new NoraVRStatusData(getWebSocketRoomId());
		status.setLoginClients(getLoginClients());
		status.setServerConfig(getConfig());

		return status;
	}

	@Override
	protected Class<? extends ModemStatusData> getStatusDataTypeInternal() {
		return NoraVRStatusData.class;
	}
}
