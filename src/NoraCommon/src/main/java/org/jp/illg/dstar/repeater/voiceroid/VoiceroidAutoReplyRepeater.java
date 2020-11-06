package org.jp.illg.dstar.repeater.voiceroid;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.repeater.DSTARRepeaterBase;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlVoiceAutoReplyHandler;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.service.web.model.VoiceroidAutoReplyRepeaterStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.DvVoiceTool;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VoiceroidAutoReplyRepeater extends DSTARRepeaterBase
implements WebRemoteControlVoiceAutoReplyHandler
{
	private static final long processEntryTimeoutMillis = TimeUnit.SECONDS.toMillis(10);


	@Getter
	@Setter
	private String autoReplyOperatorCallsign;
	private static final String autoReplyOperatorCallsignDefault = DSTARDefines.EmptyLongCallsign;
	public static final String autoReplyOperatorCallsignPropertyName = "AutoReplyOperatorCallsign";

	@Getter
	@Setter
	private String autoReplyShortMessage;
	private static final String autoReplyShortMessageDefault = "";
	public static final String autoReplyShortMessagePropertyName = "AutoReplyShortMessage";

	@Getter
	@Setter
	private VoiceCharactors autoReplyVoiceCharactor;
	private static final VoiceCharactors autoReplyVoiceCharactorDefault =
			VoiceCharactors.KizunaAkari;
	public static final String autoReplyVoiceCharactorNamePropertyName = "AutoReplyVoiceCharactorName";

	@Getter
	@Setter
	private int autoReplyHeardIntervalHours;
	private static final int autoReplyHeardIntervalHoursDefault = 12;
	private static final String autoReplyHeardIntervalHoursPropertyName = "AutoReplyHeardIntervalHours";


	private static enum ProcessModes{
		InetToInetValid,
		InetToInetInvalid,
		InetToNull,
		InetToInetBusy,
		;
	}

	private static enum ProcessStates{
		WaitEndSequence,
		WaitTransmit,
		ReplyVoice,
		;
	}

	private class ProcessEntry{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private UUID id;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long activityTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private int frameID;

		@Getter
		@Setter
		private ProcessModes processMode;

		@Getter
		@Setter
		private ProcessStates processState;

		@Getter
		@Setter
		private DSTARPacket header;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private RateAdjuster<TransmitterPacketImpl> rateMatcher;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private DataSegmentDecoder dataSegmentDecoder;

		@Getter
		@Setter
		private String shortMessage;

		@Getter
		private final UUID loopBlockID;

		private ProcessEntry() {
			super();
			setId(UUID.randomUUID());
			setCreatedTimestamp(System.currentTimeMillis());
			updateActivityTimestamp();
//			setAutoReplyPackets(new LinkedList<DvPacket>());
			final RateAdjuster<TransmitterPacketImpl> rateAdjustor = new RateAdjuster<>(1, false);
			setRateMatcher(rateAdjustor);
			setDataSegmentDecoder(new DataSegmentDecoder());
			loopBlockID = DSTARUtils.generateLoopBlockID();
		}

		public ProcessEntry(int frameID, ProcessModes processMode) {
			this();

			setFrameID(frameID);
			setProcessMode(processMode);
			setProcessState(ProcessStates.WaitEndSequence);
		}

		public void updateActivityTimestamp() {
			setActivityTimestamp(System.currentTimeMillis());
		}

		public boolean isTimeoutActivity() {
			return System.currentTimeMillis() > (this.activityTimestamp + processEntryTimeoutMillis);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			sb.append(this.getClass().getSimpleName());
			sb.append("]:");

			sb.append("ID=");
			sb.append(getId().toString());

			sb.append("/");

			sb.append("FrameID=");
			sb.append(String.format("0x%04X", getFrameID()));

			sb.append("/");

			sb.append("ProcessMode=");
			sb.append(getProcessMode().toString());

			sb.append("/");

			sb.append("CreatedTime=");
			sb.append(FormatUtil.dateFormat(getCreatedTimestamp()));

			sb.append("/");

			sb.append("ActivityTime=");
			sb.append(FormatUtil.dateFormat(getActivityTimestamp()));

			if(getHeader() != null) {
				sb.append("/");
				sb.append(getHeader().toString());
			}

			return sb.toString();
		}
	}

	private Map<Integer, ProcessEntry> processEntries;

	private Queue<DSTARPacket> fromGatewayPackets;

	private Queue<DSTARPacket> heardPackets;

	private final Timer heardIntervalTimekeeper;
	private int heardCount;
	private final int initialHeardDelaySeconds;

	public VoiceroidAutoReplyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public VoiceroidAutoReplyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		final SocketIO socketIO
	) {
		super(
			VoiceroidAutoReplyRepeater.class,
			systemID,
			gateway,
			repeaterCallsign,
			workerExecutor,
			eventListener,
			socketIO
		);

		setProcessLoopIntervalTimeMillis(TimeUnit.MILLISECONDS.toMillis(20));

		processEntries = new HashMap<Integer, VoiceroidAutoReplyRepeater.ProcessEntry>();

		fromGatewayPackets = new LinkedList<>();
		heardPackets = new LinkedList<>();
		heardIntervalTimekeeper = new Timer();
		heardIntervalTimekeeper.updateTimestamp();
		heardCount = 0;
		initialHeardDelaySeconds = new Random().nextInt(300) + 300;

		setAutoReplyOperatorCallsign(autoReplyOperatorCallsignDefault);
		setAutoReplyVoiceCharactor(autoReplyVoiceCharactorDefault);
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {
		if(!super.setProperties(properties)) {return false;}

		String autoReplyOperatorCallsign =
			properties.getConfigurationProperties().getProperty(autoReplyOperatorCallsignPropertyName);
		if(autoReplyOperatorCallsign != null)
			autoReplyOperatorCallsign = DSTARUtils.formatFullLengthCallsign(autoReplyOperatorCallsign);
		else
			autoReplyOperatorCallsign = "";

		if(!CallSignValidator.isValidUserCallsign(autoReplyOperatorCallsign)) {
			if(log.isErrorEnabled()) {
				log.error(
					"Could not set Repeater=" + getRepeaterCallsign() + "/PropertyName:" + autoReplyOperatorCallsignPropertyName +
					", not valid user callsign " + autoReplyOperatorCallsign + "."
				);
			}

			return false;
		}
		setAutoReplyOperatorCallsign(autoReplyOperatorCallsign);

		String autoReplyHeardIntervalHoursString =
			properties.getConfigurationProperties().getProperty(autoReplyHeardIntervalHoursPropertyName);
		int autoReplyHeardIntervalHours = autoReplyHeardIntervalHoursDefault;
		if(autoReplyHeardIntervalHoursString != null) {
			try {
				autoReplyHeardIntervalHours =
					Integer.valueOf(autoReplyHeardIntervalHoursString);
			}catch(NumberFormatException ex) {
				if(log.isWarnEnabled()) {
					log.warn(
						"Could not set Repeater = " + getRepeaterCallsign() + "/PropertyName:" + autoReplyHeardIntervalHoursPropertyName +
						", not valid number format " + autoReplyHeardIntervalHoursString + "."
					);
				}
			}
		}
		setAutoReplyHeardIntervalHours(autoReplyHeardIntervalHours);

		String autoReplyShortMessage =
			properties.getConfigurationProperties().getProperty(autoReplyShortMessagePropertyName);
		if(autoReplyShortMessage == null) {autoReplyShortMessage = autoReplyShortMessageDefault;}
		setAutoReplyShortMessage(autoReplyShortMessage);

		String autoReplyCharactorName =
				properties.getConfigurationProperties().getProperty(autoReplyVoiceCharactorNamePropertyName);
		if(autoReplyCharactorName == null) {autoReplyCharactorName = "null";}
		VoiceCharactors autoReplyCharactor =
				VoiceCharactors.getTypeByCharactorName(autoReplyCharactorName);
		if(autoReplyCharactor == null || autoReplyCharactor == VoiceCharactors.Unknown) {
			if(log.isWarnEnabled()) {
				log.warn(
					"Could not set Repeater=" + getRepeaterCallsign() + "/PropertyName:" + autoReplyVoiceCharactorNamePropertyName +
					", default charactor " + autoReplyVoiceCharactorDefault.getCharactorName()  + " is set."
				);
			}
			autoReplyCharactor = autoReplyVoiceCharactorDefault;
		}
		setAutoReplyVoiceCharactor(autoReplyCharactor);

		return true;
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		if(properties == null) {return null;}

		if(getAutoReplyOperatorCallsign() != null)
			properties.getConfigurationProperties().put(autoReplyOperatorCallsignPropertyName, getAutoReplyOperatorCallsign());
		else
			properties.getConfigurationProperties().put(autoReplyOperatorCallsignPropertyName, "");

		if(getAutoReplyVoiceCharactor() != null)
			properties.getConfigurationProperties().put(autoReplyVoiceCharactorNamePropertyName, getAutoReplyVoiceCharactor().getCharactorName());
		else
			properties.getConfigurationProperties().put(autoReplyVoiceCharactorNamePropertyName, "");

		return super.getProperties(properties);
	}

	@Override
	public DSTARPacket readPacket() {
		DSTARPacket packet = null;
		synchronized(processEntries) {
			if(!heardPackets.isEmpty()) {packet = heardPackets.poll();}

			if(packet == null) {
				for(final Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
					final ProcessEntry entry = it.next();
					if(
						entry.getProcessState() == ProcessStates.ReplyVoice &&
						entry.getRateMatcher().hasReadableDvPacket()
					) {
						Optional<TransmitterPacketImpl> p = entry.getRateMatcher().readDvPacket();
						if(p.isPresent()) {
							packet = p.get().getPacket();

							packet.setLoopblockID(entry.getLoopBlockID());

							entry.updateActivityTimestamp();
							if(packet.isEndVoicePacket()){
								// Complete task
								entry.getRateMatcher().reset();
								it.remove();
							}
							break;
						}
					}
				}
			}
		}

		if(packet != null) {
			if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
				setLastHeardCallsign(
					DSTARUtils.formatFullLengthCallsign(String.valueOf(packet.getRfHeader().getMyCallsign()))
				);
			}
		}

		return packet;
	}

	@Override
	public boolean hasReadPacket() {
		boolean found = false;;
		synchronized(processEntries) {
			if(!(found = !heardPackets.isEmpty())) {
				for(ProcessEntry entry : processEntries.values()) {
					if(
						entry.processState == ProcessStates.ReplyVoice &&
						entry.getRateMatcher().hasReadableDvPacket()
					) {found = true; break;}
				}
			}
		}
		return found;
	}

	@Override
	public boolean writePacket(DSTARPacket packet) {
		if(packet == null) {return false;}

		boolean success = false;
		synchronized(fromGatewayPackets) {
			success = fromGatewayPackets.add(packet);
		}

		wakeupProcessThread();

		return success;
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	protected boolean isAutoWatchdog() {
		return true;
	};

	@Override
	public boolean isReflectorLinkSupport() {
		return false;
	}

	@Override
	public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {
		if(super.getExceptionListener() != null)
			super.getExceptionListener().threadUncaughtExceptionEvent(ex, thread);
	}

	@Override
	public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
		if(super.getExceptionListener() != null)
			super.getExceptionListener().threadFatalApplicationErrorEvent(message, ex, thread);
	}

	@Override
	protected void threadFinalize() {

		return;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		return super.threadInitialize();
	}

	@Override
	protected ThreadProcessResult processRepeater() {

		Queue<DSTARPacket> receivePacket = null;
		synchronized(fromGatewayPackets) {
			receivePacket = new LinkedList<>(fromGatewayPackets);
			fromGatewayPackets.clear();
		}

		for(final Iterator<DSTARPacket> it = receivePacket.iterator(); it.hasNext(); ) {
			final DSTARPacket packet = it.next();
			it.remove();

			if(	// only null flag header
				(
					!packet.getDVPacket().hasPacketType(PacketType.Voice) &&
					!packet.getDVPacket().hasPacketType(PacketType.Header)
				) ||
				(
					packet.getDVPacket().hasPacketType(PacketType.Header) &&
					packet.getRFHeader().getRepeaterControlFlag() != RepeaterControlFlag.NOTHING_NULL
				) ||
				packet.getBackBoneHeader().getFrameIDNumber() == 0x0
			) {continue;}

			ProcessEntry entry = null;
			synchronized(processEntries) {
				entry = processEntries.get(packet.getBackBone().getFrameIDNumber());
			}

			if(
				entry == null &&
				packet.getDVPacket().hasPacketType(PacketType.Header)
			) {
				if(
					CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) &&
					CallSignValidator.isValidRepeaterCallsign(packet.getRfHeader().getRepeater2Callsign()) &&
					getRepeaterCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign()))
				){
					entry = new ProcessEntry(packet.getBackBone().getFrameIDNumber(), ProcessModes.InetToInetValid);
				}
				else {
					if(log.isInfoEnabled())
						log.info("Unknown route header, ignore.\n" + packet.toString());

					entry = new ProcessEntry(packet.getBackBone().getFrameIDNumber(), ProcessModes.InetToNull);
				}

				entry.setHeader(packet);
				entry.setFrameID(packet.getBackBone().getFrameIDNumber());

				synchronized(processEntries) {
					processEntries.put(entry.getFrameID(), entry);
				}
			}
			else if(
				entry != null &&
				packet.getDVPacket().hasPacketType(PacketType.Voice)
			) {
				entry.updateActivityTimestamp();

				if(	// decode short message
					entry.getDataSegmentDecoder().decode(packet.getVoiceData().getDataSegment()) ==
						DataSegmentDecoderResult.ShortMessage
				) {entry.setShortMessage(String.valueOf(entry.getDataSegmentDecoder().getShortMessage()));}


				if(packet.isLastFrame()) {
					final Queue<DSTARPacket> autoReplyVoice =
							generateAutoReplyVoice(entry, entry.getHeader().getRFHeader(), entry.getShortMessage());

					if(autoReplyVoice != null) {
						synchronized(processEntries) {
							final Queue<TransmitterPacketImpl> dpra = new LinkedList<>();
							dpra.add(
								new TransmitterPacketImpl(PacketType.Header, entry.getHeader(), FrameSequenceType.Start)
							);

							for(final DSTARPacket p : autoReplyVoice) {
								dpra.add(new TransmitterPacketImpl(
									PacketType.Voice, p, p.isLastFrame() ? FrameSequenceType.End : FrameSequenceType.None
								));
							}
							entry.getRateMatcher().writePacket(dpra);
							dpra.clear();
							autoReplyVoice.clear();
						}
						entry.setProcessState(ProcessStates.WaitTransmit);
					}else {
						synchronized(processEntries) {processEntries.remove(entry.getFrameID());}
					}
				}
			}
		}

		synchronized(processEntries) {
			for(Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
				final ProcessEntry entry = it.next();

				if(
					entry.getProcessState() == ProcessStates.WaitTransmit &&
					(System.currentTimeMillis() - entry.getActivityTimestamp()) > TimeUnit.MILLISECONDS.toMillis(2000)
				) {
					entry.updateActivityTimestamp();
					entry.getRateMatcher().start();
					entry.setProcessState(ProcessStates.ReplyVoice);
				}
				else if((System.currentTimeMillis() - entry.getActivityTimestamp()) > TimeUnit.MILLISECONDS.toMillis(60000)) {
					it.remove();
				}
			}
		}

		if(
			(heardCount <= 0 && heardIntervalTimekeeper.isTimeout(initialHeardDelaySeconds, TimeUnit.SECONDS)) ||
			heardIntervalTimekeeper.isTimeout(getAutoReplyHeardIntervalHours(), TimeUnit.HOURS)
		){
			heardIntervalTimekeeper.updateTimestamp();
			if(heardCount < Integer.MAX_VALUE) {heardCount++;}

			final Queue<DSTARPacket> generatedHeardPackets = createHeardPackets();
			if(generatedHeardPackets != null && !generatedHeardPackets.isEmpty()) {
				synchronized(processEntries) {
					heardPackets.addAll(generatedHeardPackets);
				}
				generatedHeardPackets.clear();
			}
		}


		cleanProcessEntries();

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected boolean isCanSleep() {
		synchronized(processEntries) {
			return processEntries.isEmpty();
		}
	};

	@Override
	public boolean isUseRoutingService() {
		return true;
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {

	}

	@Override
	public boolean isDataTransferring() {
		synchronized(processEntries) {
			return !processEntries.isEmpty();
		}
	}

	private Queue<DSTARPacket> generateAutoReplyVoice(
		final ProcessEntry entry,
		final Header header, final String shortMessage
	) {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(32768);

		final char[] yourCall = header.getMyCallsign();
		final char[] myCall = getAutoReplyOperatorCallsign().toCharArray();
		final char[] repeaterCall = getRepeaterCallsign().toCharArray();

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final Header replyHeader = new Header();
		replyHeader.setYourCallsign(yourCall);
		replyHeader.setRepeater2Callsign(getGateway().getGatewayCallsign().toCharArray());
		replyHeader.setRepeater1Callsign(repeaterCall);
		replyHeader.setMyCallsign(myCall);
		replyHeader.setMyCallsignAdd("ECHO".toCharArray());
		replyHeader.setRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY);
		replyHeader.setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);

		final BackBoneHeader headerBackbone =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);
		headerBackbone.setDestinationRepeaterID((byte)0x00);
		headerBackbone.setSendRepeaterID((byte)0x01);
		headerBackbone.setSendTerminalID((byte)0xff);
		headerBackbone.setFrameIDNumber(frameID);

		final DVPacket headerDvPacket = new DVPacket(headerBackbone, replyHeader);

		final DSTARPacket headerPacket =
			new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, headerDvPacket);

		entry.setHeader(headerPacket);

		if(log.isTraceEnabled())
			log.trace("Generating auto reply header.\n" + headerPacket);

		// yourCall
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceCallsign(getAutoReplyVoiceCharactor(), yourCall, buffer, false);

		//局、こんにちわ
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), "AR0", buffer);

		//こちらは
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), "AR1", buffer);

		// myCall
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceCallsign(getAutoReplyVoiceCharactor(), myCall, buffer, false);

		for(int count = 0; count < 3; count++)
			if(buffer.hasRemaining()) {DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), " ", buffer);}

		// repeaterCall
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceCallsign(getAutoReplyVoiceCharactor(), repeaterCall, buffer, true);

		// より自動応答にて送信中です
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), "AR2", buffer);

		// おみくじ的な何か
		if(
			buffer.hasRemaining() &&
			shortMessage != null && shortMessage.contains("PLSJOKE")
		) {
			final List<String> jokeVoices = getJokeAmbeList();
			if(jokeVoices != null && !jokeVoices.isEmpty()) {

				final int jokeIndex = jokeVoices.size() >= 2 ? new Random().nextInt(jokeVoices.size()) : 0;
				String jokeFile = jokeVoices.size() > jokeIndex ? jokeVoices.get(jokeIndex) : jokeVoices.get(0);

				// __xxx.ambe -> _xxx
				if(jokeFile.startsWith("__")) {jokeFile = jokeFile.substring(1, jokeFile.length());}
				if(jokeFile.endsWith(".ambe")) {jokeFile = jokeFile.substring(0, jokeFile.length() - 5);}

				if(jokeFile != null && !"".equals(jokeFile))
					DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor() , "_" + jokeFile, buffer);
			}
		}

		// 良い一日を
		if(buffer.hasRemaining())
			DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), "AR3", buffer);

		//無音
		for(int count = 0; count < 2; count++)
			if(buffer.hasRemaining()) {DvVoiceTool.generateVoiceByFilename(getAutoReplyVoiceCharactor(), " ", buffer);}

		buffer.flip();

		final NewDataSegmentEncoder dataSegmentEncoder = new NewDataSegmentEncoder();
		dataSegmentEncoder.setHeader(replyHeader);
		dataSegmentEncoder.setEnableHeader(true);
		dataSegmentEncoder.setShortMessage(
			!"".equals(getAutoReplyShortMessage()) ? getAutoReplyShortMessage() : "NoraGatewayAutoReply"
		);
		dataSegmentEncoder.setEnableShortMessage(true);

		final Queue<DVPacket> voicePackets = DvVoiceTool.generateVoicePacketFromBuffer(
			buffer, frameID, dataSegmentEncoder
		);

		final Queue<DSTARPacket> result = new LinkedList<>();
		for(final DVPacket voicePacket : voicePackets)
			result.add(new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, voicePacket));

		if(log.isTraceEnabled())
			log.trace("Auto reply " + result.size() + " packets generated.");

		return result;
	}

	private Queue<DSTARPacket> createHeardPackets(){
		final Queue<DSTARPacket> generatePackets = new LinkedList<>();

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final Header header = new Header(
			DSTARDefines.CQCQCQ.toCharArray(),
			getRepeaterCallsign().toCharArray(),
			getRepeaterCallsign().toCharArray(),
			getAutoReplyOperatorCallsign().toCharArray(),
			"AUTO".toCharArray()
		);
		header.setRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL);
		header.setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);

		final BackBoneHeader backbone =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);

		backbone.setDestinationRepeaterID((byte)0x00);
		backbone.setSendRepeaterID((byte)0x01);
		backbone.setSendTerminalID((byte)0xff);
		backbone.setSequenceNumber((byte)0x0);

		final DSTARPacket headerPacket =
			new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, new DVPacket(backbone, header));

		generatePackets.add(headerPacket);

		final NewDataSegmentEncoder slowdataEncoder = new NewDataSegmentEncoder();
		slowdataEncoder.setHeader(header);
		slowdataEncoder.setEnableHeader(true);
		slowdataEncoder.setEnableEncode(true);

		for(byte index = 0; index <= DSTARDefines.MaxSequenceNumber; index++) {
			final BackBoneHeader voiceBackbone = backbone.clone();
			voiceBackbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
			voiceBackbone.setSequenceNumber(index);
			final VoiceAMBE voice = new VoiceAMBE();
			final DVPacket voicePacket = new DVPacket(voiceBackbone, header.clone(), voice);

			if(index >= DSTARDefines.LastSequenceNumber) {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getEndAMBE());
				voicePacket.getBackBone().setEndSequence();
				ArrayUtil.copyOf(voicePacket.getVoiceData().getDataSegment(), DSTARUtils.getLastSlowdata());
			}
			else if(index == DSTARDefines.PreLastSequenceNumber) {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
				ArrayUtil.copyOf(voicePacket.getVoiceData().getDataSegment(), DSTARUtils.getEndSlowdata());
			}
			else {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
				slowdataEncoder.encode(voicePacket.getVoiceData().getDataSegment());
			}

			generatePackets.add(
				new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, voicePacket)
			);
		}

		return generatePackets;
	}

	private void cleanProcessEntries() {
		synchronized(processEntries){
			for(Iterator<ProcessEntry> it = processEntries.values().iterator();it.hasNext();) {
				ProcessEntry entry = it.next();
				if(entry.isTimeoutActivity()) {it.remove();}
			}
		}
	}

	private List<String> getJokeAmbeList(){
		final List<String> ambeList = new ArrayList<>();

		if(SystemUtil.IS_Android){
			try {
				final Class<?> androidHelperClass = Class.forName("org.jp.illg.util.android.AndroidHelper");
				final Method getApplicationContext = androidHelperClass.getMethod("getApplicationContext");

				final Object context = getApplicationContext.invoke(null, new Object[]{});
				final Method getAssets = context.getClass().getMethod("getAssets");
				final Object assetManager = getAssets.invoke(context, new Object[]{});

				final Method list = assetManager.getClass().getMethod("list", new Class[]{String.class});

				final String[] files =
					(String[])list.invoke(
						assetManager,
						new Object[]{getAutoReplyVoiceCharactor().getVoiceDataAndroidAssetPath()}
					);
				if(files != null){
					for(String fileName : files){
						if (fileName.startsWith("__") && fileName.endsWith(".ambe"))
							ambeList.add(fileName);
					}
				}
			} catch (
				ClassNotFoundException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException ex
			) {
				if(log.isWarnEnabled())
					log.warn("Could not load asset " + getAutoReplyVoiceCharactor().getVoiceDataAndroidAssetPath() + ".");
			}

		}
		else{
			final File sourceDir = new File(getAutoReplyVoiceCharactor().getVoiceDataDirectoryPath());
			if(sourceDir != null && sourceDir.canRead()) {
				for (File f : sourceDir.listFiles()) {
					if (f.isFile() && f.getName().startsWith("__") && f.getName().endsWith(".ambe")) {
						ambeList.add(f.getName());
					}
				}
			}
		}

		return ambeList;
	}

	@Override
	public List<String> getRouterStatus() {
		List<String> routerStatus = new LinkedList<>();

		synchronized(this.processEntries) {
			for(ProcessEntry entry : this.processEntries.values()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Mode:");
				sb.append(entry.getProcessMode().toString());
				sb.append(" / ");
				sb.append("ID:");
				sb.append(String.format("%04X", entry.getFrameID()));
				sb.append(" / ");
				sb.append("Time:");
				sb.append(
					String.format(
						"%3d",
						(int)(System.currentTimeMillis() - entry.getCreatedTimestamp()) / (int)1000
					)
				);
				sb.append("s");
				sb.append(" / ");
				if(entry.getHeader() != null) {
					final Header header = entry.getHeader().getRFHeader();
					sb.append("UR:");
					sb.append(header.getYourCallsign());
					sb.append(" / ");
					sb.append("RPT1:");
					sb.append(header.getRepeater1Callsign());
					sb.append(" / ");
					sb.append("RPT2:");
					sb.append(header.getRepeater2Callsign());
					sb.append(" / ");
					sb.append("MY:");
					sb.append(header.getMyCallsign());
					sb.append(" ");
					sb.append(header.getMyCallsignAdd());
				}else {sb.append("Header:nothing");}

				routerStatus.add(sb.toString());
			}
		}

		return routerStatus;
	}

	@Override
	public RepeaterStatusReport getRepeaterStatusReportInternal(
		RepeaterStatusReport report
	){

		report.setRepeaterCallsign(String.valueOf(getRepeaterCallsign()));
		report.setLinkedReflectorCallsign(getLinkedReflectorCallsign() != null ? getLinkedReflectorCallsign() : "");
		report.setRoutingService(
				getRoutingService() != null ? getRoutingService().getServiceType() : RoutingServiceTypes.Unknown
		);
		report.setRepeaterType(getRepeaterType());

		report.getRepeaterProperties().put("AutoReplyOperatorCallsign", getAutoReplyOperatorCallsign());
		report.getRepeaterProperties().put("AutoReplyShortMessage", getAutoReplyShortMessage());

		synchronized(this.processEntries) {
			for (ProcessEntry entry : this.processEntries.values()) {
				RepeaterRouteStatusReport routeReport = new RepeaterRouteStatusReport();

				routeReport.setRouteMode(entry.getProcessMode().toString());
				routeReport.setFrameID(entry.getFrameID());
				routeReport.setFrameSequenceStartTime(entry.getCreatedTimestamp());
				if(entry.getHeader() != null){
					routeReport.setYourCallsign(String.valueOf(entry.getHeader().getRFHeader().getYourCallsign()));
					routeReport.setRepeater1Callsign(String.valueOf(entry.getHeader().getRFHeader().getRepeater1Callsign()));
					routeReport.setRepeater2Callsign(String.valueOf(entry.getHeader().getRFHeader().getRepeater2Callsign()));
					routeReport.setMyCallsign(String.valueOf(entry.getHeader().getRFHeader().getMyCallsign()));
					routeReport.setMyCallsignAdd(String.valueOf(entry.getHeader().getRFHeader().getMyCallsignAdd()));
				}

				report.getRouteReports().add(routeReport);
			}
		}

		return report;
	}

	@Override
	public boolean initializeWebRemote(WebRemoteControlService service) {
		return service.initializeRepeaterVoiceroidAutoReply(this);
	}

	@Override
	protected RepeaterStatusData createStatusDataInternal() {
		final VoiceroidAutoReplyRepeaterStatusData status =
			new VoiceroidAutoReplyRepeaterStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends RepeaterStatusData> getStatusDataTypeInternal() {
		return VoiceroidAutoReplyRepeaterStatusData.class;
	}

	@Override
	public boolean isAllowOutgoingConnection() {return false;}

	@Override
	public void setAllowOutgoingConnection(boolean allowOutgoingConndction) {};

	@Override
	public boolean isAllowIncomingConnection() {return false;}

	@Override
	public void setAllowIncomingConnection(boolean allowIncomingConndction) {}
}
