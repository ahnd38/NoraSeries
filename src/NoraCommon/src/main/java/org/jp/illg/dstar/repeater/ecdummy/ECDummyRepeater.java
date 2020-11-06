package org.jp.illg.dstar.repeater.ecdummy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDummyRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.dstar.service.web.model.DummyRepeaterStatusData;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.FormatUtil;
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
public class ECDummyRepeater implements DSTARRepeater, WebRemoteControlDummyRepeaterHandler {

	private final int modemTransmitterCacheSize = 20;

	private static enum ProcessMode {
		ModemToGateway,
		GatewayToModem,
		;
	}

	private static class ProcessEntry {
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private UUID id;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTimestamp;

		@Getter
		private final Timer activityTimestamp;

		@Getter
		@Setter
		private int frameID;

		@Getter
		@Setter
		private byte sequence;

		@Getter
		@Setter
		private ProcessMode processMode;

		@Getter
		@Setter
		private DSTARPacket headerPacket;

		@Getter
		@Setter
		private boolean active;

		@Getter
		private final DataSegmentDecoder slowdataDecoder;

		@Getter
		@Setter
		private boolean validHeader;

		@Getter
		private final UUID loopBlockID;

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(final int indentLevel) {
			final int lvl = indentLevel < 0 ? 0 : indentLevel;
			final StringBuilder sb = new StringBuilder();
			for(int c = 0; c < lvl; c++) {sb.append(' ');}

			sb.append("[");
			sb.append(this.getClass().getSimpleName());
			sb.append("]:");

			sb.append("ID=");
			sb.append(getId().toString());

			sb.append("/");

			sb.append("FrameID=");
			sb.append(String.format("0x%04X", getFrameID()));

			sb.append("/");

			sb.append("Sequence=");
			sb.append(String.format("0x%04X", getSequence()));

			sb.append("/");

			sb.append("ProcessMode=");
			sb.append(getProcessMode().toString());

			sb.append("/");

			sb.append("Active=");
			sb.append(isActive());

			sb.append("/");

			sb.append("isValidHeader=");
			sb.append(isValidHeader());

			sb.append("/");

			sb.append("CreatedTime=");
			sb.append(FormatUtil.dateFormat(getCreatedTimestamp()));

			sb.append("/");

			sb.append("ActivityTime=");
			sb.append(FormatUtil.dateFormat(getActivityTimestamp().getTimestampMilis()));

			if(getHeaderPacket() != null) {
				sb.append("/\n");
				sb.append(getHeaderPacket().getRFHeader().toString(lvl + 4));
			}

			return sb.toString();
		}

		private ProcessEntry() {
			super();

			setId(UUID.randomUUID());
			setCreatedTimestamp(System.currentTimeMillis());
			activityTimestamp = new Timer();
			activityTimestamp.updateTimestamp();
			slowdataDecoder = new DataSegmentDecoder();

			setActive(false);

			setHeaderPacket(null);
			setSequence((byte)0x0);

			setValidHeader(false);

			loopBlockID = DSTARUtils.generateLoopBlockID();
		}

		public ProcessEntry(int frameID, ProcessMode processMode) {
			this();

			setFrameID(frameID);
			setProcessMode(processMode);
		}


		public void updateActivityTimestamp() {
			getActivityTimestamp().updateTimestamp();
		}

		public boolean isTimeoutActivity() {
			return getActivityTimestamp().isTimeout(500, TimeUnit.MILLISECONDS);
		}
	}

	private static class CacheHeaderEntry{

		@Getter
		private final DSTARPacket header;

		@Getter
		private final Timer timekeeper;

		public CacheHeaderEntry(@NonNull DSTARPacket header) {
			super();

			if(!header.getDVPacket().hasPacketType(PacketType.Header))
				throw new IllegalArgumentException("Header is not include header packet");

			this.header = header;
			timekeeper = new Timer();
		}
	}

	private static class ModemTransmitPacketEntry extends TransmitterPacketImpl{

		public ModemTransmitPacketEntry(
			final PacketType packetType,
			final DSTARPacket packet, final FrameSequenceType frameSequenceType
		) {
			super(packetType, packet, frameSequenceType);
		}
	}

	private final String logTag;

	@Getter
	private final UUID systemID;

	@Getter
	private final String repeaterCallsign;

	@Getter
	@Setter
	private String linkedReflectorCallsign;

	@Getter
	@Setter
	private String lastHeardCallsign;

	@Getter
	@Setter
	private AccessScope scope;

	@Getter
	@Setter
	private double latitude;

	@Getter
	@Setter
	private double longitude;

	@Getter
	@Setter
	private double agl;

	@Getter
	@Setter
	private String description1;

	@Getter
	@Setter
	private String description2;

	@Getter
	@Setter
	private String url;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String location;

	@Getter
	@Setter
	private double range;

	@Getter
	@Setter
	private double frequency;

	@Getter
	@Setter
	private double frequencyOffset;

	@Getter
	@Setter
	private int autoDisconnectFromReflectorOutgoingUnusedMinutes;

	@Getter
	@Setter
	private boolean allowIncomingConnection;

	@Getter
	@Setter
	private boolean allowOutgoingConnection;

	@Getter
	private final DSTARGateway gateway;

	@Getter
	private final ExecutorService workerExecutor;

	private final Map<Integer, ProcessEntry> entries;
	private final Lock entriesLocker;

	private final Map<Integer, CacheHeaderEntry> cacheHeaders;

	private boolean isRunning;

	private WebRemoteControlService webRemoteControlService;

	private final Queue<DSTARPacket> gatewayPackets;
	private final Lock gatewayPacketsLocker;

	private final CacheTransmitter<ModemTransmitPacketEntry> modemPackets;
//	private final Queue<DvPacket> modemPackets;
	private final Lock modemPacketsLocker;

	private final Timer processCleanTimekeeper;

	public ECDummyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public ECDummyRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		super();

		logTag = ECDummyRepeater.class.getSimpleName() + "(" + repeaterCallsign + ") : ";

		this.systemID = systemID;

		this.gateway = gateway;
		this.repeaterCallsign = repeaterCallsign;
		this.workerExecutor = workerExecutor;

		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;

		entries = new ConcurrentHashMap<>();
		entriesLocker = new ReentrantLock();
		cacheHeaders = new ConcurrentHashMap<>();

		gatewayPacketsLocker = new ReentrantLock();
		gatewayPackets = new LinkedList<>();

		modemPacketsLocker = new ReentrantLock();
//		modemPackets = new LinkedList<>();
		modemPackets = new CacheTransmitter<>(modemTransmitterCacheSize);

		processCleanTimekeeper = new Timer(10, TimeUnit.SECONDS);

		setScope(AccessScope.Unknown);
		setLatitude(0.0d);
		setLongitude(0.0d);
		setAgl(0.0d);
		setDescription1("");
		setDescription2("");
		setUrl("");
		setName("");
		setLocation("");
		setRange(0.0d);
		setFrequency(0.0d);
		setFrequencyOffset(0.0d);
		setAllowIncomingConnection(true);
		setAllowOutgoingConnection(true);

		setAutoDisconnectFromReflectorOutgoingUnusedMinutes(0);

		setLastHeardCallsign(DSTARDefines.EmptyLongCallsign);
	}

	@Override
	public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {}

	@Override
	public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {}

	@Override
	public void wakeupRepeaterWorker() {}

	@Override
	public boolean start() {
		isRunning = true;
		return true;
	}

	@Override
	public void stop() {
		isRunning = false;
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public RepeaterTypes getRepeaterType() {
		return RepeaterTypes.ExternalConnectorDummy;
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {
		final AccessScope scope =
			AccessScope.getTypeByTypeNameIgnoreCase(properties.getScope());
		setScope(scope);
		setLatitude(properties.getLatitude());
		setLongitude(properties.getLongitude());
		setAgl(properties.getAgl());
		setDescription1(properties.getDescription1());
		setDescription2(properties.getDescription2());
		setUrl(properties.getUrl());
		setName(properties.getName());
		setLocation(properties.getLocation());
		setRange(properties.getRange());
		setFrequency(properties.getFrequency());
		setFrequencyOffset(properties.getFrequencyOffset());

		setAutoDisconnectFromReflectorOutgoingUnusedMinutes(
			properties.getAutoDisconnectFromReflectorOutgoingUnusedMinutes()
		);
		setAllowIncomingConnection(properties.isAllowIncomingConnection());
		setAllowOutgoingConnection(properties.isAllowOutgoingConnection());

		return true;
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		return properties;
	}

	@Override
	public DSTARPacket readPacket() {
		DSTARPacket packet = null;
		gatewayPacketsLocker.lock();
		try {
			packet = gatewayPackets.poll();
		}finally {
			gatewayPacketsLocker.unlock();
		}

		return packet;
	}

	@Override
	public boolean hasReadPacket() {
		gatewayPacketsLocker.lock();
		try {
			return !gatewayPackets.isEmpty();
		}finally {
			gatewayPacketsLocker.unlock();
		}
	}

	@Override
	public boolean writePacket(@NonNull DSTARPacket packet) {
		return writePacket(packet, true);
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public boolean isReflectorLinkSupport() {
		return true;
	}

	@Override
	public RoutingService getRoutingService() {
		return null;
	}

	@Override
	public void setRoutingService(RoutingService routingService) {}

	@Override
	public boolean isRoutingServiceFixed() {
		return false;
	}

	@Override
	public void setRoutingServiceFixed(boolean routingServiceFixed) {}

	@Override
	public boolean isTransparentMode() {
		return false;
	}

	@Override
	public void setTransparentMode(boolean transparentMode) {}

	@Override
	public boolean isUseRoutingService() {
		return false;
	}

	@Override
	public boolean isAutoDisconnectFromReflectorOnTxToG2Route() {
		return true;
	}

	@Override
	public List<String> getRouterStatus() {
		return new ArrayList<>();
	}

	@Override
	public RepeaterStatusReport getRepeaterStatusReport() {
		final RepeaterStatusReport report = new RepeaterStatusReport();

		report.setRepeaterCallsign(String.valueOf(getRepeaterCallsign()));
		report.setLinkedReflectorCallsign(
			getLinkedReflectorCallsign() != null ? getLinkedReflectorCallsign() : ""
		);
		report.setRoutingService(
			getRoutingService() != null ? getRoutingService().getServiceType() : RoutingServiceTypes.Unknown
		);
		report.setRepeaterType(getRepeaterType());

		report.setLastHeardCallsign(getLastHeardCallsign());

		report.setFrequency(getFrequency());
		report.setFrequencyOffset(getFrequencyOffset());
		report.setRange(getRange());
		report.setLatitude(getLatitude());
		report.setLongitude(getLongitude());
		report.setAgl(getAgl());
		report.setDescription1(getDescription1());
		report.setDescription2(getDescription2());
		report.setUrl(getUrl());
		report.setName(getName());
		report.setLocation(getLocation());
		report.setScope(getScope());

		return report;
	}

	@Override
	public List<RepeaterModem> getModems() {
		return new ArrayList<>();
	}

	@Override
	public void setWebRemoteControlService(WebRemoteControlService webRemoteControlService) {
		this.webRemoteControlService = webRemoteControlService;
	}

	@Override
	public WebRemoteControlService getWebRemoteControlService() {
		return webRemoteControlService;
	}

	@Override
	public WebRemoteControlRepeaterHandler getWebRemoteControlHandler() {
		return this;
	}

	@Override
	public RepeaterStatusData createStatusData() {
		final DummyRepeaterStatusData status =
			new DummyRepeaterStatusData(getWebSocketRoomId());

		status.setRepeaterType(getRepeaterType());
		status.setRepeaterCallsign(getRepeaterCallsign());
		status.setWebSocketRoomId(getWebSocketRoomId());
		status.setGatewayCallsign(getGateway().getGatewayCallsign());
		status.setLastheardCallsign(getLastHeardCallsign());
		status.setLinkedReflectorCallsign(getLinkedReflectorCallsign());
		status.setRoutingService(RoutingServiceTypes.Unknown);
		status.setRoutingServiceFixed(isRoutingServiceFixed());
		status.setUseRoutingService(isUseRoutingService());
		status.setAllowDIRECT(false);
		status.setTransparentMode(isTransparentMode());
		status.setAutoDisconnectFromReflectorOnTxToG2Route(isAutoDisconnectFromReflectorOnTxToG2Route());
		status.setScope(getScope());
		status.setLatitude(getLatitude());
		status.setLongitude(getLongitude());
		status.setAgl(getAgl());
		status.setDescriotion1(getDescription1());
		status.setDescription2(getDescription2());
		status.setUrl(getUrl());
		status.setName(getName());
		status.setLocation(getLocation());
		status.setRange(getRange());
		status.setFrequency(getFrequency());
		status.setFrequencyOffset(getFrequencyOffset());

		return status;
	}

	@Override
	public Class<? extends RepeaterStatusData> getStatusDataType() {
		return DummyRepeaterStatusData.class;
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGateway().getGatewayCallsign(),
			getRepeaterCallsign()
		);
	}

	@Override
	public boolean isDataTransferring() {
		return false;
	}

	public boolean initializeWebRemote(@NonNull final WebRemoteControlService service) {
		return service.initializeRepeaterDummy(this);
	}

	public boolean writeModemPacket(@NonNull final DSTARPacket packet) {
		return writePacket(packet, false);
	}

	public DSTARPacket readModemPacket() {
		modemPacketsLocker.lock();
		try {
//			return modemPackets.poll();
			final Optional<ModemTransmitPacketEntry> packet = modemPackets.outputRead();

			if(packet.isPresent())
				return packet.get().getPacket();
			else
				return null;
		}finally {
			modemPacketsLocker.unlock();
		}
	}

	public boolean hasReadModemPacket() {
		modemPacketsLocker.lock();
		try {
			return modemPackets.hasOutputRead();
		}finally {
			modemPacketsLocker.unlock();
		}
	}

	public ThreadProcessResult process() {

		if(processCleanTimekeeper.isTimeout()) {
			entriesLocker.lock();
			try {
				for(final Iterator<ProcessEntry> it = entries.values().iterator(); it.hasNext();) {
					final ProcessEntry entry = it.next();

					if(entry.isTimeoutActivity()) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Remove timeout frame.\n" + entry.toString(4));

						it.remove();

						if(
							entry.isActive() && entry.getHeaderPacket() != null &&
							entry.isValidHeader() &&
							entry.getProcessMode() == ProcessMode.ModemToGateway
						) {
							byte seq = entry.getSequence();

							final DSTARPacket endPacket = new InternalPacket(
								entry.getHeaderPacket().getLoopBlockID(),
								ConnectionDirectionType.Unknown,
								DSTARUtils.createPreLastVoicePacket(
									entry.getFrameID(), seq
								)
							);
							addPacket(PacketType.Voice, entry, endPacket, FrameSequenceType.None);

							final DSTARPacket lastPacket = new InternalPacket(
								entry.getHeaderPacket().getLoopBlockID(),
								ConnectionDirectionType.Unknown,
								DSTARUtils.createLastVoicePacket(
									entry.getFrameID(),
									DSTARUtils.getNextShortSequence(seq)
								)
							);
							addPacket(PacketType.Voice, entry, lastPacket, FrameSequenceType.End);
						}
					}
				}

				for(final Iterator<CacheHeaderEntry> it = cacheHeaders.values().iterator(); it.hasNext();) {
					final CacheHeaderEntry entry = it.next();

					if(entry.getTimekeeper().isTimeout(30, TimeUnit.SECONDS)) {
						if(log.isTraceEnabled())
							log.trace(logTag + "Remove timeout cache header.\n" + entry.getHeader().toString(4));

						it.remove();
					}
				}
			}finally {
				entriesLocker.unlock();
			}

			processCleanTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
			processCleanTimekeeper.updateTimestamp();
		}

		return ThreadProcessResult.NoErrors;
	}

	public boolean writePacket(final DSTARPacket packet, final boolean isFromGateway) {
		if(packet.getPacketType() != DSTARPacketType.DV) {return false;}

		boolean success = true;

		if(packet.getDVPacket().hasPacketType(PacketType.Header))
			success &= writeHeader(packet, isFromGateway);

		if(packet.getDVPacket().hasPacketType(PacketType.Voice))
			success &= writeVoice(packet, isFromGateway);

		return success;
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

	private boolean writeHeader(final DSTARPacket packet, final boolean isFromGateway) {
		if(!packet.getDVPacket().hasPacketType(PacketType.Header)) {return false;}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		entriesLocker.lock();
		try {
			ProcessEntry entry = entries.get(frameID);

			final boolean isCanActive =
				!hasActiveEntry(isFromGateway ? ProcessMode.GatewayToModem : ProcessMode.ModemToGateway);

			if(entry != null) {
				if(entry.getHeaderPacket() == null) {
					entry.setValidHeader(checkHeader(packet));

					entry.setHeaderPacket(packet);
				}

				if(!entry.isActive() && isCanActive && entry.isValidHeader()) {
					entry.setActive(true);

					if(!isFromGateway)
						packet.setLoopBlockID(entry.getLoopBlockID());

					addPacket(PacketType.Header, entry, packet, FrameSequenceType.Start);

					if(log.isDebugEnabled()) {
						log.debug(logTag + "Start non active frame from header.\n" + entry.toString(4));
					}
				}

				entry.updateActivityTimestamp();

				final CacheHeaderEntry cacheHeader = cacheHeaders.get(frameID);
				if(cacheHeader != null) {cacheHeader.getTimekeeper().updateTimestamp();}

				return true;
			}
			else {
				entry = new ProcessEntry(
					frameID, isFromGateway ? ProcessMode.GatewayToModem : ProcessMode.ModemToGateway
				);

				entry.setHeaderPacket(packet);
				entry.setValidHeader(checkHeader(packet));
				entry.setSequence((byte)0x00);
				entry.updateActivityTimestamp();
				entry.setActive(isCanActive && entry.isValidHeader());

				if(entry.isActive()) {
					if(!isFromGateway)
						packet.setLoopBlockID(entry.getLoopBlockID());

					addPacket(PacketType.Header, entry, packet, FrameSequenceType.Start);
				}

				if(
					entry.getProcessMode() == ProcessMode.ModemToGateway &&
					getRepeaterCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater1Callsign()))
				) {setLastHeardCallsign(String.valueOf(packet.getRFHeader().getMyCallsign()));}

				if(log.isDebugEnabled()) {
					log.debug(logTag + "Start frame.\n" + entry.toString(4));
				}

				final CacheHeaderEntry cacheHeader = new CacheHeaderEntry(packet);
				cacheHeader.getTimekeeper().updateTimestamp();
				cacheHeaders.put(frameID, cacheHeader);

				return entries.put(frameID, entry) == null;
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private boolean writeVoice(final DSTARPacket packet, final boolean isFromGateway) {
		if(!packet.getDVPacket().hasPacketType(PacketType.Voice)) {return false;}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		entriesLocker.lock();
		try {
			ProcessEntry entry = entries.get(frameID);
			if(entry == null) {
				entry = new ProcessEntry(
					frameID, isFromGateway ? ProcessMode.GatewayToModem : ProcessMode.ModemToGateway
				);
				entries.put(frameID, entry);

				final CacheHeaderEntry cacheHeader = cacheHeaders.get(frameID);
				if(cacheHeader != null) {
					entry.setHeaderPacket(cacheHeader.getHeader());
					entry.setValidHeader(checkHeader(cacheHeader.getHeader()));

					cacheHeader.getTimekeeper().updateTimestamp();

					if(log.isDebugEnabled())
						log.debug(logTag + "Resync header by cache header.\n" + cacheHeader.getHeader().toString(4));
				}
			}

			entry.updateActivityTimestamp();

			entry.setSequence(packet.getBackBoneHeader().getSequenceNumber());

			//ヘッダが不明であれば低速データから復帰を試みる
			if(entry.getHeaderPacket() == null) {
				final DataSegmentDecoderResult decoderResult =
					entry.getSlowdataDecoder().decode(packet.getDVData().getDataSegment());
				if(decoderResult == DataSegmentDecoderResult.Header) {
					final Header slowdataHeader = entry.getSlowdataDecoder().getHeader();
					slowdataHeader.setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);
					slowdataHeader.setRepeater1Callsign(getRepeaterCallsign().toCharArray());
					slowdataHeader.setRepeater2Callsign(getRepeaterCallsign().toCharArray());

					final BackBoneHeader backbone =
						new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);
					final DSTARPacket repairHeader = new InternalPacket(
						entry.getLoopBlockID(),
						ConnectionDirectionType.Unknown,
						new DVPacket(backbone, slowdataHeader
					));

					entry.setHeaderPacket(repairHeader);

					entry.setValidHeader(checkHeader(repairHeader));

					if(log.isDebugEnabled())
						log.debug(logTag + "Resync header by slowdata.\n" + repairHeader.toString(4));
				}
			}

			//アクティブではないフレームで他にアクティブなフレームが無ければアクティブにする
			if(
				entry.getHeaderPacket() != null &&
				entry.isValidHeader() &&
				!entry.isActive() &&
				!hasActiveEntry(isFromGateway ? ProcessMode.GatewayToModem : ProcessMode.ModemToGateway)
			) {
				entry.setActive(true);

				if(!isFromGateway) {
					entry.getHeaderPacket().setLoopBlockID(entry.getLoopBlockID());
				}

				addPacket(PacketType.Header, entry, entry.getHeaderPacket(), FrameSequenceType.Start);

				if(log.isDebugEnabled()) {
					log.debug(logTag + "Start non active frame from voice.\n" + entry.toString(4));
				}
			}

			if(entry.isActive()) {
				if(!isFromGateway) {
					packet.setLoopBlockID(entry.getLoopBlockID());
				}

				if(
					entry.getHeaderPacket() != null &&
					!packet.isLastFrame() &&
					packet.getBackBoneHeader().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
				) {
					addPacket(
						PacketType.Header,
						entry, entry.getHeaderPacket(),
						FrameSequenceType.None
					);
				}

				addPacket(
					PacketType.Voice,
					entry, packet,
					packet.isLastFrame() ? FrameSequenceType.End : FrameSequenceType.None
				);
			}

			if(packet.isLastFrame()) {
				if(log.isDebugEnabled())
					log.debug(logTag + "End frame.\n" + entry.toString(4));

				entries.remove(frameID);
			}
			else {
				final CacheHeaderEntry cacheHeader = cacheHeaders.get(frameID);
				if(cacheHeader != null) {cacheHeader.getTimekeeper().updateTimestamp();}
			}

			return true;

		}finally {
			entriesLocker.unlock();
		}
	}

	private boolean addPacket(
		final PacketType packetType,
		final ProcessEntry entry,
		final DSTARPacket packet,
		final FrameSequenceType sequenceType
	) {
		if(entry.getProcessMode() == ProcessMode.GatewayToModem)
			return addModemPacket(packetType, packet, sequenceType);
		else if(entry.getProcessMode() == ProcessMode.ModemToGateway)
			return addGatewayPacket(packet);
		else
			return false;
	}

	private boolean addGatewayPacket(final DSTARPacket packet) {
		gatewayPacketsLocker.lock();
		try {
			return gatewayPackets.add(packet);
		}finally {
			gatewayPacketsLocker.unlock();
		}
	}

	private boolean addModemPacket(
		final PacketType packetType,
		final DSTARPacket packet,
		final FrameSequenceType sequenceType
	) {
		modemPacketsLocker.lock();
		try {
			return modemPackets.inputWrite(new ModemTransmitPacketEntry(packetType, packet, sequenceType));
		}finally {
			modemPacketsLocker.unlock();
		}
	}

	@SuppressWarnings("unused")
	private boolean hasActiveEntry() {
		return hasActiveEntry(null);
	}

	private boolean hasActiveEntry(final ProcessMode processMode) {
		entriesLocker.lock();
		try {
			for(final ProcessEntry entry : entries.values()) {
				if((processMode == null || entry.getProcessMode() == processMode) && entry.isActive())
					return true;
			}
		}finally {
			entriesLocker.unlock();
		}

		return false;
	}

	private boolean checkHeader(final DSTARPacket headerPacket) {
		return headerPacket != null &&
			headerPacket.getDVPacket().hasPacketType(PacketType.Header) &&
			(
				getRepeaterCallsign().equals(String.valueOf(headerPacket.getRFHeader().getRepeater1Callsign())) ||
				getRepeaterCallsign().equals(String.valueOf(headerPacket.getRFHeader().getRepeater2Callsign()))
			);
	}
}
