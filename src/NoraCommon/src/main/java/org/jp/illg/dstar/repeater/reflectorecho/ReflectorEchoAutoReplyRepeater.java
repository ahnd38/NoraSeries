package org.jp.illg.dstar.repeater.reflectorecho;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.repeater.DSTARRepeaterBase;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorEchoAutoReplyHandler;
import org.jp.illg.dstar.service.web.model.ReflectorEchoAutoReplyRepeaterStatusData;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
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
public class ReflectorEchoAutoReplyRepeater extends DSTARRepeaterBase
implements WebRemoteControlReflectorEchoAutoReplyHandler
{
	private static final long processEntryTimeoutMillis = TimeUnit.SECONDS.toMillis(10);

	private static final int autoReplyLengthLimit = 20 * 60;	// limit 1 minite

	private static final String echoOnMessage = "__NORA_ECHO_ON__";
	private static final String echoOffMessage = "__NORA_ECHO_OFF__";

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
		private Queue<DSTARPacket> cachePackets;

		@Getter
		private final DataSegmentDecoder slowdataDecoder;

		@Getter
		private final UUID loopBlockID;

		private ProcessEntry() {
			super();
			setId(UUID.randomUUID());
			setCreatedTimestamp(System.currentTimeMillis());
			updateActivityTimestamp();
			final RateAdjuster<TransmitterPacketImpl> rateAdjustor =
				new RateAdjuster<>(20, false);
			setRateMatcher(rateAdjustor);
			setCachePackets(new LinkedList<>());
			slowdataDecoder = new DataSegmentDecoder();
			loopBlockID = UUID.randomUUID();
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

	private final String logHeader;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean echoEnable;

	@Getter
	@Setter
	private boolean echoControlBySlowdata;
	private static final String echoControlBySlowdataPropertyName = "EchoControlBySlowdata";
	private static final boolean echoControlBySlowdataDefault = true;

	@Getter
	@Setter
	private String autoReplyOperatorCallsign;
	public static final String autoReplyOperatorCallsignPropertyName = "AutoReplyOperatorCallsign";



	private Map<Integer, ProcessEntry> processEntries;

	private Queue<DSTARPacket> fromGatewayPackets;

	public ReflectorEchoAutoReplyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public ReflectorEchoAutoReplyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		final SocketIO socketIO
	) {
		super(
			ReflectorEchoAutoReplyRepeater.class,
			systemID,
			gateway,
			repeaterCallsign,
			workerExecutor,
			eventListener,
			socketIO
		);

		setProcessLoopIntervalTimeMillis(TimeUnit.MILLISECONDS.toMillis(20));

		logHeader = this.getClass().getSimpleName() + "[" + repeaterCallsign + "]" + " : ";

		processEntries = new HashMap<Integer, ProcessEntry>();

		fromGatewayPackets = new LinkedList<>();

		setEchoEnable(true);
		setEchoControlBySlowdata(echoControlBySlowdataDefault);
	}

	@Override
	public void wakeupRepeaterWorker() {
		super.wakeupRepeaterWorker();
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {
		if(!super.setProperties(properties)) {return false;}

		String autoReplyOperatorCallsign =
			properties.getConfigurationProperties().getProperty(autoReplyOperatorCallsignPropertyName);
		if(autoReplyOperatorCallsign != null)
			autoReplyOperatorCallsign = DSTARUtils.formatFullLengthCallsign(autoReplyOperatorCallsign);
		else
			autoReplyOperatorCallsign = getRepeaterCallsign();

		if(!CallSignValidator.isValidUserCallsign(autoReplyOperatorCallsign)) {
			if(log.isErrorEnabled()) {
				log.error(
					"Could not set Repeater = " + getRepeaterCallsign() + "/PropertyName:" + autoReplyOperatorCallsignPropertyName +
					", not valid user callsign " + autoReplyOperatorCallsign + "."
				);
			}

			autoReplyOperatorCallsign = getRepeaterCallsign();
		}
		setAutoReplyOperatorCallsign(autoReplyOperatorCallsign);

		setEchoControlBySlowdata(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				echoControlBySlowdataPropertyName,
				echoControlBySlowdataDefault
			)
		);

		return true;
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		if(properties == null) {return null;}



		return super.getProperties(properties);
	}

	@Override
	public DSTARPacket readPacket() {
		DSTARPacket packet = null;
		synchronized(processEntries) {
			if(packet == null) {
				for(Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
						ProcessEntry entry = it.next();
					if(
						entry.getProcessState() == ProcessStates.ReplyVoice &&
						entry.getRateMatcher().hasReadableDvPacket()
					) {
						final Optional<TransmitterPacketImpl> p = entry.getRateMatcher().readDvPacket();
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
			for(ProcessEntry entry : processEntries.values()) {
				if(
					entry.processState == ProcessStates.ReplyVoice &&
					entry.getRateMatcher().hasReadableDvPacket()
				) {found = true; break;}
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
		return true;
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
	public boolean isUseRoutingService() {
		return false;
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

		for(Iterator<DSTARPacket> it = receivePacket.iterator(); it.hasNext(); ) {
			final DSTARPacket packet = it.next();
			it.remove();

			if(	// only null flag header
				packet.getPacketType() != DSTARPacketType.DV ||
				(
					!packet.getDVPacket().hasPacketType(PacketType.Header) &&
					!packet.getDVPacket().hasPacketType(PacketType.Voice)
				) ||
				(
					packet.getDVPacket().hasPacketType(PacketType.Header) &&
					packet.getRFHeader().getRepeaterControlFlag() != RepeaterControlFlag.NOTHING_NULL
				) ||
				packet.getBackBone().getFrameIDNumber() == 0x0
			) {continue;}

			ProcessEntry entry = null;
			synchronized(processEntries) {
				entry = this.processEntries.get(packet.getBackBone().getFrameIDNumber());
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

				// Store voice packets
				while(entry.getCachePackets().size() >= autoReplyLengthLimit) {entry.getCachePackets().poll();}
				entry.getCachePackets().add(packet);

				if(
					entry.getSlowdataDecoder().decode(packet.getDVData().getDataSegment()) ==
						DataSegmentDecoderResult.ShortMessage
				) {
					final String message = String.valueOf(entry.getSlowdataDecoder().getShortMessage()).trim();

					if(isEchoControlBySlowdata()) {
						if(!isEchoEnable() && message.contains(echoOnMessage)) {
							setEchoEnable(true);
							if(log.isInfoEnabled()) {
								log.info(
									logHeader +
									"Echo enabled by user " + String.valueOf(entry.getHeader().getDVPacket().getRfHeader().getMyCallsign()) + "."
								);
							}
						}
						else if(isEchoEnable() && message.contains(echoOffMessage)) {
							setEchoEnable(false);
							if(log.isInfoEnabled()) {
								log.info(
									logHeader +
									"Echo disabled by user " + String.valueOf(entry.getHeader().getDVPacket().getRfHeader().getMyCallsign()) + "."
								);
							}
						}
					}
				}

				if(packet.isEndVoicePacket()) {
					//受信したパケットが2秒以上である時のみ自動応答する
					if(entry.getCachePackets().size() >= (DSTARDefines.DvFramePerSeconds * 2)) {
						final Queue<DSTARPacket> autoReplyVoice =
								generateAutoReplyVoice(entry, entry.getHeader().getRFHeader(), entry.getCachePackets());

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
//								entry.getAutoReplyPackets().addAll(autoReplyVoice);
								entry.getRateMatcher().writePacket(dpra);
								dpra.clear();
								autoReplyVoice.clear();
							}
							entry.setProcessState(ProcessStates.WaitTransmit);
						}else {
							synchronized(processEntries) {processEntries.remove(entry.getFrameID());}
						}
					}else {
						synchronized(processEntries) {processEntries.remove(entry.getFrameID());}
					}
				}
			}
		}

		synchronized(processEntries) {
			for(Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
				ProcessEntry entry = it.next();

				if(
					entry.getProcessState() == ProcessStates.WaitTransmit &&
					(System.currentTimeMillis() - entry.getActivityTimestamp()) > TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS)
				) {
					if(isEchoEnable()) {
						entry.updateActivityTimestamp();
						entry.getRateMatcher().start();
						entry.setProcessState(ProcessStates.ReplyVoice);
					}
					else {	//エコーが無効であれば返送しない
						it.remove();
					}
				}
				else if((System.currentTimeMillis() - entry.getActivityTimestamp()) > TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS)) {
					it.remove();
				}
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
	public boolean isDataTransferring() {
		synchronized(processEntries) {
			return !processEntries.isEmpty();
		}
	}

	private Queue<DSTARPacket> generateAutoReplyVoice(
		final ProcessEntry entry,
		final Header header, final Queue<DSTARPacket> voicePackets
	) {
		final Queue<DSTARPacket> result = new LinkedList<>();

		final char[] myCall = getAutoReplyOperatorCallsign().toCharArray();
		final char[] repeaterCall = getRepeaterCallsign().toCharArray();

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final Header replyHeader = new Header();
		replyHeader.setYourCallsign(DSTARDefines.CQCQCQ);
		replyHeader.setRepeater2Callsign(getGateway().getGatewayCallsign().toCharArray());
		replyHeader.setRepeater1Callsign(repeaterCall);
		replyHeader.setMyCallsign(myCall);
		replyHeader.setMyCallsignAdd("ECHO".toCharArray());
		replyHeader.setRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL);
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
			log.trace("Generating auto reply header.\n" + headerPacket.toString());

		for(final Iterator<DSTARPacket> it = voicePackets.iterator(); it.hasNext();) {
			final DSTARPacket voicePacket = it.next();
			it.remove();

			final BackBoneHeader voiceBackbone = headerBackbone.clone();
			voiceBackbone.setType(BackBoneHeaderType.DV);
			voiceBackbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
			voiceBackbone.setFrameIDNumber(frameID);
			voicePacket.getDVPacket().setBackBone(voiceBackbone);

			voicePacket.setLoopBlockID(loopblockID);

			if(voicePacket.isLastFrame()) {voicePacket.getBackBoneHeader().setEndSequence();}

			voicePacket.getDVPacket().setRfHeader(replyHeader.clone());
			voicePacket.getDVPacket().setPacketType(PacketType.Header, PacketType.Voice);

			result.add(voicePacket);
		}

		if(log.isTraceEnabled())
			log.trace("Auto reply " + result.size() + " packets generated.");

		return result;
	}

	private void cleanProcessEntries() {
		synchronized(processEntries){
			for(Iterator<ProcessEntry> it = processEntries.values().iterator();it.hasNext();) {
				ProcessEntry entry = it.next();
				if(entry.isTimeoutActivity()) {it.remove();}
			}
		}
	}

	@Override
	public List<String> getRouterStatus() {
		List<String> routerStatus = new LinkedList<>();

		synchronized(this.processEntries) {
			for(ProcessEntry entry : this.processEntries.values()) {
				final StringBuilder sb = new StringBuilder();
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
		return service.initializeRepeaterReflectorEchoAutoReply(this);
	}

	@Override
	protected RepeaterStatusData createStatusDataInternal() {
		final ReflectorEchoAutoReplyRepeaterStatusData status =
			new ReflectorEchoAutoReplyRepeaterStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends RepeaterStatusData> getStatusDataTypeInternal() {
		return ReflectorEchoAutoReplyRepeaterStatusData.class;
	}

	@Override
	public boolean isAllowOutgoingConnection() {return true;}

	@Override
	public void setAllowOutgoingConnection(boolean allowOutgoingConnection) {};

	@Override
	public boolean isAllowIncomingConnection() {return false;}

	@Override
	public void setAllowIncomingConnection(boolean allowIncomingConnection) {}
}
