package org.jp.illg.dstar.jarl.xchange.addon.extconn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.model.HeardState;
import org.jp.illg.dstar.gateway.tool.announce.AnnounceTool;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManagerImpl;
import org.jp.illg.dstar.jarl.xchange.addon.extconn.model.ExternalConnectorProperties;
import org.jp.illg.dstar.jarl.xchange.addon.extconn.model.ExternalConnectorRepeaterProperties;
import org.jp.illg.dstar.jarl.xchange.addon.extconn.model.ProcessEntry;
import org.jp.illg.dstar.jarl.xchange.addon.extconn.model.ProcessMode;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacket;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketDirection;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketHeader;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketKeepalive;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketResponse;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketType;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketVoice;
import org.jp.illg.dstar.jarl.xchange.model.XChangeRouteFlag;
import org.jp.illg.dstar.jarl.xchange.model.XChangeRouteFlagData;
import org.jp.illg.dstar.jarl.xchange.util.XChangePacketLogger;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.GlobalIPInfo;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.HeardEntry;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.AuthType;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationServiceManager;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.repeater.ecdummy.ECDummyRepeater;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.routing.RoutingServiceManager;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.model.RoutingInfo;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.service.reflectorhosts.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.model.GatewayStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.DvVoiceTool;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.dstar.util.aprs.APRSMessageDecoder;
import org.jp.illg.dstar.util.aprs.APRSMessageDecoder.APRSMessageDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.io.FileSource;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.Callback;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.slf4j.event.Level;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalConnector
extends SocketIOHandlerWithThread<BufferEntry>
implements DSTARGateway, WebRemoteControlGatewayHandler
{

	private static final String logHeader = ExternalConnector.class.getSimpleName() + " : ";

	private static final int heardEntriesLimit = 100;

	private static final int authResultLogLimit = 100;

	private static final Pattern controlCommandPattern =
		Pattern.compile("^((([ ]|[_]){4}[G][2][R][A-Z])|(([ ]|[_]){7}([D]|[I]))|(([ ]|[_]){2}[R][L][M][A][C]([E]|[D])))$");

	private static final Pattern reflectorLinkPattern =
		Pattern.compile(
			"^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-FH-RT-Z][L])|"
			+ "((([X][R][F])|([X][L][X])|([D][C][S])|([R][E][F]))[0-9]{3}[A-Z][L]))$"
		);

	private static final Pattern reflectorCommandPattern =
		Pattern.compile("^([ ]{7}([E]|[U]|[I]))$");

	private class AuthEntry{

		@Getter
		@Setter
		private int frameID;

		@Getter
		private final Timer frameSequenceTimekeeper;

		@Getter
		@Setter
		private DSTARPacket header;

		@Getter
		@Setter
		private String myCallsign;

		@Getter
		@Setter
		private UUID queryID;

		@Getter
		@Setter
		private RoutingService routingService;

		@Getter
		private final Timer queryTimekeeper;

		@Getter
		@Setter
		private AuthResult authState;

		@Getter
		@Setter
		private DSTARRepeater repeater;

		@Getter
		@Setter
		private ConnectionDirectionType connectionDirection;

		@Getter
		private AuthType authMode;


		public AuthEntry(
			@NonNull final DSTARRepeater repeater,
			@NonNull final AuthType authMode
		) {
			super();

			this.repeater = repeater;
			this.authMode = authMode;

			this.queryTimekeeper = new Timer();
			this.frameSequenceTimekeeper = new Timer();
		}
	}

	private static enum AuthResult{
		None,
		Wait,
		Valid,
		Invalid,
		;
	}

	@ToString
	private class AuthResultLog{
		@Getter
		private final String callsign;

		@Getter
		private final RoutingService routingService;

		@Getter
		private final long authTime;

		@Getter
		private final AuthResult authResult;

		public AuthResultLog(
			final String callsign,
			final RoutingService routingService,
			final AuthResult authResult
		) {
			super();

			this.authTime = System.currentTimeMillis();

			this.callsign = callsign;
			this.routingService = routingService;
			this.authResult = authResult;
		}
	}

	private class ReflectorAnnounceTransmitterPacket extends TransmitterPacketImpl {

		@Getter
		private DSTARRepeater targetRepeater;

		@Getter
		private String myCallsign;

		@Getter
		private ConnectionDirectionType direction;

		public ReflectorAnnounceTransmitterPacket(
			@NonNull DSTARRepeater targetRepeater,
			@NonNull String myCallsign,
			@NonNull ConnectionDirectionType direction,
			@NonNull PacketType packetType,
			@NonNull DSTARPacket packet,
			@NonNull FrameSequenceType frameSequenceType
		) {
			super(packetType, packet, frameSequenceType);

			this.targetRepeater = targetRepeater;
			this.myCallsign = myCallsign;
			this.direction = direction;
		}

	}

	@Getter
	@Setter
	private String gatewayCallsign;

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
	private String dashboardUrl;

	@Getter
	@Setter
	private boolean useProxy;

	@Getter
	@Setter
	private String proxyServerAddress;

	@Getter
	@Setter
	private int proxyServerPort;

	@Getter
	@Setter
	private String lastHeardCallsign;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private String xchangeServerAddress;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int xchangeServerPort;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private AuthType authMode;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int localPort;

	@Setter(AccessLevel.PROTECTED)
	@Getter(AccessLevel.PROTECTED)
	private GlobalIPInfo gatewayGlobalAddress;

	@Getter
	private final UUID systemID;

	private final ApplicationInformation<?> applicationVersion;

	private final SocketIO socketio;
	private final ExecutorService workerExecutor;
	private final WebRemoteControlService webRemoteControlService;

	private final Queue<HeardEntry> heardEntries;

	@Getter
	private final ReflectorNameService reflectorNameService;

	@Getter
	private final RepeaterNameService repeaterNameService;

	private final ReflectorLinkManager reflectorLinkManager;

	private final AnnounceTool announceTool;

	private SocketIOEntryUDP channel;

	private final XChangePacket keepalivePacket = new XChangePacketKeepalive();
	private final XChangePacket headerPacket = new XChangePacketHeader();
	private final XChangePacket voicePacket = new XChangePacketVoice();

	private final Queue<XChangePacket> receivePackets;
	private final Lock receivePacketsLocker;

	private final Map<Integer, ProcessEntry> processEntries;
	private final Lock processEntriesLocker;
	private final Timer processCleanupTimekeeper;

	private int xchangePacketNo;
	private final Lock xchangePacketNoLocker;

	private VoiceCharactors announceVoiceCharactor;

	private final Map<DSTARRepeater, AuthEntry> currentAuths;

	private final Map<String, AuthResultLog> authResultLogs;
	private final Timer authResultLogsCleanupTimekeeper;

	private final Map<
		DSTARRepeater,
		RateAdjuster<ReflectorAnnounceTransmitterPacket>
	> reflectorAnnounceTransmitters;

	private final Timer xchangeServerKeepaliveTimerkeeper;

	private ExternalConnectorProperties properties;

	private final EventListener<ReflectorCommunicationServiceEvent> reflectorEventListener =
		new EventListener<ReflectorCommunicationServiceEvent>() {
			@Override
			public void event(ReflectorCommunicationServiceEvent event, Object attachment) {
				//TODO
				wakeupGatewayWorker();
			}
		};

	private final EventListener<DStarRepeaterEvent> repeaterEventListener =
		new EventListener<DStarRepeaterEvent>() {
			@Override
			public void event(DStarRepeaterEvent event, Object attachment) {
				//TODO
				wakeupGatewayWorker();
			}
		};

	private final EventListener<RoutingServiceEvent> routingServiceEventListener =
		new EventListener<RoutingServiceEvent>() {
			@Override
			public void event(RoutingServiceEvent event, Object attachment) {
				//TODO
				wakeupGatewayWorker();
			}
		};

	private final Callback<List<ReflectorHostInfo>> onReflectorHostChangeEventListener =
		new Callback<List<ReflectorHostInfo>>() {
			@Override
			public void call(List<ReflectorHostInfo> data) {
				notifyReflectorHostChanged(data);
			}
		};

	public ExternalConnector(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final SocketIO socketio,
		final WebRemoteControlService webRemoteControlService,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) {
		super(
			exceptionListener, ExternalConnector.class, BufferEntry.class,
			HostIdentType.RemoteLocalAddressPort
		);

		setProcessLoopIntervalTimeMillis(5L);

		this.systemID = systemID;

		this.applicationVersion = applicationVersion;
		this.workerExecutor = workerExecutor;
		this.socketio = socketio;
		this.webRemoteControlService = webRemoteControlService;

		this.reflectorNameService = reflectorNameService;
		this.reflectorNameService.setOnReflectorHostChangeEventListener(onReflectorHostChangeEventListener);
		this.repeaterNameService = repeaterNameService;

		heardEntries = new LinkedList<>();
		reflectorLinkManager = new ReflectorLinkManagerImpl(
			getSystemID(), this, workerExecutor, reflectorNameService
		);
		announceTool = new AnnounceTool(workerExecutor, this);

		receivePackets = new LinkedList<>();
		receivePacketsLocker = new ReentrantLock();

		processEntries = new ConcurrentHashMap<>();
		processEntriesLocker = new ReentrantLock();
		processCleanupTimekeeper = new Timer(10 ,TimeUnit.SECONDS);

		xchangePacketNo = 0x0;
		xchangePacketNoLocker = new ReentrantLock();

		announceVoiceCharactor = VoiceCharactors.KizunaAkari;

		currentAuths = new ConcurrentHashMap<>();
		authResultLogs = new ConcurrentHashMap<>();
		authResultLogsCleanupTimekeeper = new Timer(2, TimeUnit.MINUTES);

		reflectorAnnounceTransmitters = new HashMap<>(2);

		xchangeServerKeepaliveTimerkeeper = new Timer();

		properties = null;

		setScope(AccessScope.Unknown);
		setLatitude(0.0d);
		setLongitude(0.0d);
		setDescription1("");
		setDescription2("");
		setUrl("");
		setName("");
		setLocation("");
		setDashboardUrl("");

		setLastHeardCallsign(DSTARDefines.EmptyLongCallsign);

		setUseProxy(false);
		setProxyServerAddress("");
		setProxyServerPort(0);
		setLocalPort(0);
		setAuthMode(AuthType.INCOMING);
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
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull DSTARRepeater targetRepeater,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {

	}

	@Override
	public void wakeupGatewayWorker() {
		wakeupProcessThread();
	}

	@Override
	public String getApplicationVersion() {
		return applicationVersion.getApplicationVersion();
	}

	@Override
	public String getApplicationName() {
		return applicationVersion.getApplicationName();
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(getWebRemoteControlService() != null) {
			if(!getWebRemoteControlService().initializeGatewayHandler(this)) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Failed web remote control service(gateway).");

				return false;
			}

			for(final ReflectorCommunicationService reflectorService : getReflectorCommunicationServiceAll()) {
				if(!reflectorService.initializeWebRemoteControl(getWebRemoteControlService())) {
					if(log.isErrorEnabled())
						log.error(logHeader + "Failed to initialize web remote service(" + reflectorService.getProcessorType() + ")");

					return false;
				}
			}

			for(final DSTARRepeater repeater : DSTARRepeaterManager.getRepeaters(systemID)) {
				repeater.setWebRemoteControlService(getWebRemoteControlService());

				if(repeater.getRepeaterType() != RepeaterTypes.ExternalConnectorDummy) {continue;}

				final ECDummyRepeater dummyRepeater = (ECDummyRepeater)repeater;

				if(!dummyRepeater.initializeWebRemote(getWebRemoteControlService()))
					return false;
			}
		}

		boolean startSuccess = true;

		for(ReflectorCommunicationService reflector : getReflectorCommunicationServiceAll()) {
			if(!reflector.start()) {
				startSuccess = false;
				if(log.isErrorEnabled())
					log.error("Could not start reflector service, name=" + reflector.getProcessorType().getTypeName() + ".");
			}
		}
		for(final RoutingService routingService : getRoutingServiceAll()) {
			if(!routingService.start()) {
				startSuccess = false;

				if(log.isErrorEnabled()) {
					log.error(logHeader + "Could not start routing service = " + routingService.getServiceType() + ".");
				}
			}
		}
		if(!startSuccess) {
			ReflectorCommunicationServiceManager.finalizeManager();
			RoutingServiceManager.finalizeManager();

			return false;
		}



		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						channel =
							getSocketIO().registUDP(
								new InetSocketAddress(getLocalPort()), getHandler(),
								ExternalConnector.this.getClass().getSimpleName() + "@" + getLocalPort()
							);
					}
				}
			)
		) {
			this.stop();

			closeChannel();

			return false;
		}

		return true;
	}

	@Override
	public void stop() {
		closeChannel();

		stopRefectorCommunicationServices();
		stopRepeaters();

		super.stop();
	}

	@Override
	public boolean isRunning() {
		return super.isRunning();
	}

	@Override
	public boolean writePacketToG123Route(DSTARPacket packet, InetAddress destinationAddress) {
		return true;
	}

	@Override
	public boolean writePacketToReflectorRoute(
		DSTARRepeater repeater, ConnectionDirectionType direction, DSTARPacket packet
	) {
		final List<ReflectorCommunicationService> reflectors = getReflectorCommunicationServiceAll();

		for(ReflectorCommunicationService processor : reflectors)
			processor.writePacket(repeater, packet, direction);

		return true;
	}

	@Override
	public UUID positionUpdate(
		DSTARRepeater repeater, int frameID, String myCall, String myCallExt, String yourCall,
		String repeater1, String repeater2, byte flag1, byte flag2, byte flag3
	) {
		return null;
	}

	@Override
	public UUID findRepeater(DSTARRepeater repeater, String repeaterCall, Header header) {
		return null;
	}

	@Override
	public UUID findUser(DSTARRepeater repeater, String userCall, Header header) {
		return null;
	}

	@Override
	public boolean linkReflector(
		DSTARRepeater repeater, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		return reflectorLinkManager.linkReflector(repeater, reflectorCallsign, reflectorHostInfo);
	}

	@Override
	public void unlinkReflector(DSTARRepeater repeater) {
		reflectorLinkManager.unlinkReflector(repeater);
	}

	@Override
	public void notifyLinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		final DSTARRepeater targetRepeater = getRepeater(repeaterCallsign);
		if(targetRepeater == null) {return;}

		if(log.isInfoEnabled())
			log.info("[Reflector Link Established] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		targetRepeater.setLinkedReflectorCallsign(reflectorCallsign);

		if(targetRepeater != null)
			announceTool.announceReflectorConnected(targetRepeater, announceVoiceCharactor, reflectorCallsign);
	}

	@Override
	public void notifyUnlinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		final DSTARRepeater targetRepeater = getRepeater(repeaterCallsign);
		if(targetRepeater == null) {return;}

		if(log.isInfoEnabled())
			log.info("[Reflector Unlinked] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		targetRepeater.setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		if(targetRepeater != null)
			announceTool.announceReflectorDisconnected(targetRepeater, announceVoiceCharactor, reflectorCallsign);
	}

	@Override
	public void notifyLinkFailedReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		final DSTARRepeater targetRepeater = getRepeater(repeaterCallsign);
		if(targetRepeater == null) {return;}

		if(log.isWarnEnabled())
			log.warn("[Reflector Link Failed] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		targetRepeater.setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		if(targetRepeater != null)
			announceTool.announceReflectorConnectionError(targetRepeater, announceVoiceCharactor, reflectorCallsign);
	}

	@Override
	public void kickWatchdogFromRepeater(String repeaterCallsign, String statusMessage) {
	}

	@Override
	public ReflectorLinkManager getReflectorLinkManager() {
		return reflectorLinkManager;
	}

	@Override
	public UUID findUser(
		RoutingServiceTypes routingServiceType, Callback<RoutingInfo> callback,
		String queryUserCallsign
	) {
		return null;
	}

	@Override
	public UUID findRepeater(
		RoutingServiceTypes routingServiceType, Callback<RoutingInfo> callback,
		String queryRepeaterCallsign
	) {
		return null;
	}

	@Override
	public boolean isReflectorLinked(DSTARRepeater repeater, ConnectionDirectionType dir) {
		return reflectorLinkManager.isReflectorLinked(repeater, dir);
	}

	@Override
	public List<String> getLinkedReflectorCallsign(DSTARRepeater repeater, ConnectionDirectionType dir) {
		return reflectorLinkManager.getLinkedReflectorCallsign(repeater, dir);
	}

	@Override
	public String getOutgoingLinkedReflectorCallsign(DSTARRepeater repeater) {
		final List<String> callsigns = getLinkedReflectorCallsign(repeater, ConnectionDirectionType.OUTGOING);

		if(callsigns == null || callsigns.isEmpty()) {return DSTARDefines.EmptyLongCallsign;}

		return callsigns.get(0);
	}

	@Override
	public List<String> getIncommingLinkedReflectorCallsign(DSTARRepeater repeater) {
		return getLinkedReflectorCallsign(repeater, ConnectionDirectionType.INCOMING);
	}

	@Override
	public boolean changeRoutingService(DSTARRepeater repeater, RoutingServiceTypes routingServiceType) {
		return false;
	}

	@Override
	public List<String> getRouterStatus() {
		return new ArrayList<>();
	}

	@Override
	public GatewayStatusReport getGatewayStatusReport() {
		final GatewayStatusReport report = new GatewayStatusReport();

		report.setGatewayCallsign(getGateway().getGatewayCallsign());
		report.setLastHeardCallsign(getGateway().getLastHeardCallsign());
		report.setScope(getGateway().getScope());
		report.setLatitude(getGateway().getLatitude());
		report.setLongitude(getGateway().getLongitude());
		report.setDescription1(getGateway().getDescription1());
		report.setDescription2(getGateway().getDescription2());
		report.setUrl(getGateway().getUrl());
		report.setName(getGateway().getName());
		report.setLocation(getGateway().getLocation());
		report.setDashboardUrl(getGateway().getDashboardUrl());

		return report;
	}


	@Override
	public DSTARRepeater getRepeater(String repeaterCallsign) {
		if(repeaterCallsign == null || "".equals(repeaterCallsign)) {return null;}

		return DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
	}

	@Override
	public List<DSTARRepeater> getRepeaters(){
		return DSTARRepeaterManager.getRepeaters(systemID);
	}

	protected boolean removeRoutingServiceAll(){
		return removeRoutingServiceAll(true);
	}

	protected boolean removeRoutingServiceAll(boolean stopRoutingService){
		return RoutingServiceManager.removeServices(systemID, stopRoutingService);
	}

	protected boolean removeReflectorCommunicationServiceAll(){
		return removeReflectorCommunicationServiceAll(true);
	}

	protected boolean removeReflectorCommunicationServiceAll(boolean stopReflectorProtocolProcessor){
		return ReflectorCommunicationServiceManager.removeServices(getSystemID(), stopReflectorProtocolProcessor);
	}

	@Override
	public DSTARGateway getGateway() {
		return this;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		final StringBuilder sb = new StringBuilder("== Configuration Information ==\n");
		for(
			final Iterator<DSTARRepeater> it =
				Stream.of(getRepeaters()).sorted(ComparatorCompat.comparing(new Function<DSTARRepeater, String>(){
					@Override
					public String apply(DSTARRepeater t) {
						return t.getRepeaterCallsign();
					}
				})).toList().iterator();
				it.hasNext();
		) {
			final DSTARRepeater repeater = it.next();

			final ExternalConnectorRepeaterProperties rp =
				properties.getRepeaters().get(repeater.getRepeaterCallsign());

			sb.append("    ");
			sb.append('[');
			sb.append(repeater.getRepeaterCallsign());
			sb.append(']');
			sb.append('\n');
			sb.append("-> AuthMode : ");
			sb.append(rp != null ? rp.getAuthMode() : AuthType.UNKNOWN);

			sb.append('\n');
			sb.append("-> Use XChange : ");
			sb.append(rp != null && rp.isUseXChange() ? "ON" : "OFF");

			if(it.hasNext()) {sb.append('\n');}
		}

		sb.append("\n\n");
		sb.append("    Default auth mode : ");
		sb.append(getAuthMode());

		if(log.isInfoEnabled()) {
			log.info(logHeader + sb.toString());
		}
		pushLogToWebRemoteControlService(Level.INFO, sb.toString());

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize(){
		super.threadFinalize();

		removeReflectorCommunicationServiceAll();
		removeRoutingServiceAll();

		RoutingServiceManager.finalizeSystem(getSystemID());
		ReflectorCommunicationServiceManager.finalizeManager();
	}

	public boolean setProperties(ExternalConnectorProperties properties) {
		if(properties == null) {return false;}

		this.properties = properties;

		boolean success = true;

		if(!CallSignValidator.isValidGatewayCallsign(properties.getGatewayCallsign())) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Illegal gateway callsign = " + properties.getGatewayCallsign());
			}
			return false;
		}
		setGatewayCallsign(properties.getGatewayCallsign());


		setScope(scope);

		setLocalPort(properties.getLocalPort());

		if(properties.isEnableAuthOutgoingLink()) {
			if(properties.getAuthMode() == AuthType.INCOMING)
				setAuthMode(AuthType.BIDIRECTIONAL);
			else if(properties.getAuthMode() == AuthType.OFF)
				setAuthMode(AuthType.OUTGOING);
			else
				setAuthMode(properties.getAuthMode());
		}
		else{setAuthMode(properties.getAuthMode());}


		final VoiceCharactors vc =
			VoiceCharactors.getTypeByCharactorName(properties.getAnnounceVoice());
		if(vc != null && vc != VoiceCharactors.Unknown)
			announceVoiceCharactor = vc;
		else
			announceVoiceCharactor = VoiceCharactors.KizunaAkari;

		for(RepeaterProperties repeaterProperties : properties.getRepeaters().values()) {
			if(!repeaterProperties.isEnable()) {continue;}

			if(
				DSTARRepeaterManager.createRepeater(
					systemID,
					socketio,
					workerExecutor,
					this, repeaterEventListener,
					repeaterProperties.getType(), repeaterProperties.getCallsign(), repeaterProperties,
					webRemoteControlService
				) == null
			){success = false;}
		}

		// Create reflector communication service
		for(ReflectorProperties reflectorProperties : properties.getReflectors().values()) {
			if(!reflectorProperties.isEnable()) {continue;}

			if(!createReflectorProtocolProcessor(reflectorProperties)){success = false;}
		}

		// Create routing service
		final RoutingServiceProperties g1RoutingServiceProperty =
			properties.getRoutingServices().get(RoutingServiceTypes.JapanTrust);
		final RoutingServiceProperties g2RoutingServiceProperty =
			properties.getRoutingServices().get(RoutingServiceTypes.GlobalTrust);
		if(
			g1RoutingServiceProperty == null &&
			g2RoutingServiceProperty == null &&
			getAuthMode() != AuthType.OFF
		) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not start routing service, Can't disable both JapanTrust and GlobalTrust");
			}
			return false;
		}
		if(
			g1RoutingServiceProperty != null &&
			g1RoutingServiceProperty.isEnable() &&
			RoutingServiceManager.createService(
				systemID,
				this, workerExecutor, g1RoutingServiceProperty,
				applicationVersion, routingServiceEventListener, this.socketio
			) == null
		) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not create JapanTrust routing service.");
			}
			return false;
		}
		if(
			g2RoutingServiceProperty != null &&
			g2RoutingServiceProperty.isEnable() &&
			RoutingServiceManager.createService(
				systemID,
				this, workerExecutor, g2RoutingServiceProperty,
				applicationVersion, routingServiceEventListener, this.socketio
			) == null
		) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not create GlobalTrust routing service.");
			}
			return false;
		}

		// Read reflector hosts file
		if(
			properties.getHostFileOutputPath() != null &&
			!"".equals(properties.getHostFileOutputPath())
		) {reflectorNameService.setOutputFilePath(properties.getHostFileOutputPath());}

		if(log.isInfoEnabled()) {log.info(logHeader + "Reading saved hosts file..." + properties.getHostFileOutputPath());}
		loadHostsFile(properties.getHostFileOutputPath(), FileSource.StandardFileSystem, false, true);

		if(log.isInfoEnabled()) {log.info(logHeader + "Reading user's base hosts file..." + properties.getHostsFile());}
		if(
			!loadHostsFile(
				properties.getHostsFile(),
				SystemUtil.IS_Android ? FileSource.AndroidAssets : FileSource.StandardFileSystem,
				true, false
			)
		) {success = false;}

		if(!reflectorLinkManager.setProperties(properties.getReflectorLinkManager())) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Failed to set properties to ReflectorLinkManager.");
			}

			return false;
		}

		final AccessScope scope =
			AccessScope.getTypeByTypeNameIgnoreCase(properties.getScope());
		setScope(scope);
		setLatitude(properties.getLatitude());
		setLongitude(properties.getLongitude());
		setDescription1(properties.getDescription1());
		setDescription2(properties.getDescription2());
		setUrl(properties.getUrl());
		setName(properties.getName());
		setLocation(properties.getLocation());
		setDashboardUrl(properties.getDashboardUrl());

		return success;
	}

	@Override
	public boolean setProperties(GatewayProperties properties) {
		return false;
	}

	@Override
	public GatewayProperties getProperties(GatewayProperties properties) {
		return properties;
	}



	public ThreadProcessResult processThread() {

		//RoutingService Process
		processAuthRoutingService();

		//XChange -> Repeater
		processXChangePacketToRepeater();

		//XChange <- Repeater
		processRepeaterPacketToXChange();

		//Reflector <- Repeater
		processRepeaterPacketToReflector();

		//Reflector -> Repeater
		processReflectorPacketToRepeater();

		//Repeater internal process
		repeaterProcess();

		//Authenticator process
		processAuth();

		//Reflector announce process
		processReflectorAnnounceTransmitters();

		announceTool.process();

		reflectorLinkManager.processReflectorLinkManagement();

		reflectorNameService.processService();


		cleanupProcessEntry();

		// XChangeとの接続がタイムアウトしたらサーバーアドレスを消去
		if(xchangeServerKeepaliveTimerkeeper.isTimeout(3, TimeUnit.MINUTES)) {
			if(getXchangeServerAddress() != null) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Keepalive timeout for XChange server connection = " +
						getXchangeServerAddress() + ":" + getXchangeServerPort()
					);
				}
			}

			setXchangeServerAddress(null);
			setXchangeServerPort(0);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(DSTARProtocol reflectorProtocol) {
		return ReflectorCommunicationServiceManager.getService(getSystemID(), reflectorProtocol);
	}

	@Override
	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(String reflectorCallsign) {
		if(
			reflectorCallsign == null
		) {return Optional.empty();}

		reflectorCallsign = DSTARUtils.formatFullLengthCallsign(reflectorCallsign);

		String reflectorCall =
			DSTARUtils.formatFullLengthCallsign(
				reflectorCallsign.substring(0, DSTARDefines.CallsignFullLength - 1)
			);

		Optional<ReflectorHostInfo> opHostInfo =
			reflectorNameService.findHostByReflectorCallsign(reflectorCall);
		if(!opHostInfo.isPresent()) {return Optional.empty();}

		ReflectorHostInfo hostInfo = opHostInfo.get();

		return getReflectorCommunicationService(hostInfo.getReflectorProtocol());
	}

	@Override
	public List<ReflectorCommunicationService> getReflectorCommunicationServiceAll(){
		return ReflectorCommunicationServiceManager.getServices(getSystemID());
	}

	@Override
	public RoutingService getRoutingService(RoutingServiceTypes routingServiceType) {
		return RoutingServiceManager.getService(systemID, routingServiceType);
	}

	@Override
	public List<RoutingService> getRoutingServiceAll() {
		return RoutingServiceManager.getServices(systemID);
	}

	@Override
	public void setWebRemoteControlService(final WebRemoteControlService webRemoteControlService) {

	}

	@Override
	public WebRemoteControlService getWebRemoteControlService() {
		return webRemoteControlService;
	}

	public boolean loadHostsFile(boolean logSuppress) {
		// Read reflector hosts file
		return reflectorNameService.loadHosts(logSuppress);
	}

	public boolean loadHostsFile(
		String filePath, @NonNull FileSource src,
		boolean rewriteDataSource, boolean logSuppress
	) {
		boolean filePathAvailable = true;
		if(filePath == null || "".equals(filePath))
			filePathAvailable = false;

		// Read reflector hosts file
		if(filePathAvailable) {
			if(SystemUtil.IS_Android && src == FileSource.AndroidAssets)
				return reflectorNameService.loadHostsFromAndroidAssets(filePath, rewriteDataSource, logSuppress);
			else
				return reflectorNameService.loadHosts(filePath, rewriteDataSource, logSuppress);
		}
		else {return reflectorNameService.loadHosts(logSuppress);}
	}

	@Override
	public Optional<InetAddress> findReflectorAddressByCallsign(String reflectorCallsign) {
		if(
			reflectorCallsign == null ||
			(
				!CallSignValidator.isValidReflectorCallsign(reflectorCallsign)
			)
		) {return Optional.empty();}

		Optional<ReflectorHostInfo> hostInfo = findReflectorByCallsign(reflectorCallsign);

		InetAddress reflectorAddress = null;

		if(hostInfo.isPresent()) {
			try {
				reflectorAddress = InetAddress.getByName(hostInfo.get().getReflectorAddress());
			}catch(UnknownHostException ex) {
				log.warn(
					"Could not resolve reflector address. " +
					hostInfo.get().getReflectorCallsign() + "," +
					hostInfo.get().getReflectorAddress() + "."
				);
			}
		}
		else {
			log.warn(
				"Could not find reflector " +
				reflectorCallsign.substring(0, DSTARDefines.CallsignFullLength - 2) + "."
			);
		}

		return Optional.ofNullable(reflectorAddress);
	}

	@Override
	public Optional<ReflectorHostInfo> findReflectorByCallsign(String reflectorCallsign) {
		if(
			!CallSignValidator.isValidReflectorCallsign(reflectorCallsign)
		) {return Optional.empty();}

		Optional<ReflectorHostInfo> hostInfo =
				reflectorNameService.findHostByReflectorCallsign(reflectorCallsign);

		return hostInfo;
	}

	@Override
	public List<ReflectorHostInfo> findReflectorByFullText(final String queryText, final int resultSizeLimit) {
		return reflectorNameService.findHostByFullText(queryText, resultSizeLimit);
	}

	@Override
	public boolean loadReflectorHosts(String filePath, boolean rewriteDataSource){
		if(filePath == null || "".equals(filePath)){return false;}

		return reflectorNameService.loadHosts(filePath, rewriteDataSource, false);
	}

	@Override
	public boolean loadReflectorHosts(URL url, boolean rewriteDataSource) {
		if(url == null) {return false;}

		return reflectorNameService.loadHosts(url, rewriteDataSource, false);
	}

	@Override
	public boolean loadReflectorHosts(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts,
		final String dataSource,
		final boolean deleteSameDataSource
	) {
		if(readHosts == null) {return false;}

		return reflectorNameService.loadHosts(readHosts, dataSource, deleteSameDataSource, false);
	}

	@Override
	public boolean saveReflectorHosts(String filePath){
		if(filePath == null || "".equals(filePath)){return false;}

		return reflectorNameService.saveHosts(filePath);
	}

	@Override
	public Optional<InetAddress> getGatewayGlobalIP(){
		if(getGatewayGlobalAddress() != null)
			return Optional.of(getGatewayGlobalAddress().getGlobalIP());
		else
			return Optional.empty();
	}

	@Override
	public boolean addHeardEntry(
		@NonNull final HeardEntryState state,
		@NonNull final DSTARProtocol protocol,
		@NonNull final ConnectionDirectionType direction,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String destination,
		@NonNull final String from,
		@NonNull final String shortMessage,
		final boolean locationAvailable,
		final double latitude,
		final double longitude,
		final int packetCount,
		final double packetDropRate,
		final double bitErrorRate
	) {
		final HeardEntry entry =
			new HeardEntry(
				state, protocol, direction,
				yourCallsign,
				repeater1Callsign,
				repeater2Callsign,
				myCallsignLong,
				myCallsignShort,
				destination,
				from,
				shortMessage,
				locationAvailable,
				latitude, longitude,
				packetCount,
				packetDropRate,
				bitErrorRate
			);

		boolean result = false;

		synchronized(heardEntries) {
			for(Iterator<HeardEntry> it = heardEntries.iterator(); it.hasNext();) {
				final HeardEntry e = it.next();
				if(e.getMyCallsignLong().equals(myCallsignLong)
				) {
					it.remove();
					break;
				}
			}

			while(heardEntries.size() > heardEntriesLimit) {heardEntries.poll();}

			result = heardEntries.add(entry);
		}

		if(getWebRemoteControlService() != null)
			getWebRemoteControlService().notifyGatewayUpdateHeard(entry);

		return result;
	}

	@Override
	public List<HeardEntry> getHeardEntries(){
		synchronized(heardEntries) {
			return new ArrayList<>(heardEntries);
		}
	}

	@Override
	public void notifyIncomingPacketFromG123Route(
		@NonNull String myCallsign,
		@NonNull InetAddress gatewayAddress
	) {
		for(RoutingService service : getRoutingServiceAll()) {
			service.updateCache(myCallsign, gatewayAddress);
		}
	}

	@Override
	public List<ReflectorStatusReport> getReflectorStatusReport() {
		return Stream.of(getReflectorCommunicationServiceAll())
			.map(e -> e.getStatusReport())
			.toList();
	}

	protected Optional<RoutingService> getRepeaterRoutingService(String repeaterCallsign) {
		assert repeaterCallsign != null;

		DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
		if(repeater != null) {
			if(repeater.getRoutingService() != null) {
				return Optional.of(repeater.getRoutingService());
			}
			else {
				List<RoutingService> routingServices = getRoutingServiceAll();
				if(!routingServices.isEmpty()) {
					RoutingService newRoutingService = routingServices.get(0);
					repeater.setRoutingService(newRoutingService);
					return Optional.of(newRoutingService);
				}
				else
					return Optional.empty();
			}
		}
		else
			return Optional.empty();
	}

	private boolean createReflectorProtocolProcessor(ReflectorProperties reflectorProperties){
		return ReflectorCommunicationServiceManager.createService(
			getSystemID(),
			this,
			reflectorProperties.getType(),
			reflectorProperties,
			workerExecutor,
			getSocketIO(),
			reflectorLinkManager,
			reflectorEventListener,
			getApplicationName() + "@" + applicationVersion.getRunningOperatingSystem(),
			getApplicationVersion()
		) != null;
	}

	public List<HeardEntry> requestHeardLog() {
		synchronized(heardEntries) {
			return new LinkedList<HeardEntry>(heardEntries);
		}
	}

	@Override
	public void notifyReflectorHostChanged(List<ReflectorHostInfo> hosts) {
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		onReceivePacketFromXChange();
	}

	@Override
	public List<RoutingServiceStatusReport> getRoutingStatusReport() {
		return new ArrayList<>(0);
	}

	private void closeChannel(){
		if(channel != null && channel.getChannel().isOpen()) {
			try {
				channel.getChannel().close();
				channel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}

	@Override
	public List<ReflectorHostInfo> getReflectorHosts() {
		return reflectorNameService.getHosts();
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
				getGatewayCallsign()
			);
	}

	@Override
	public GatewayStatusData createStatusData() {
		final GatewayStatusData status = new GatewayStatusData();

		status.setWebSocketRoomId(getWebSocketRoomId());
		status.setGatewayCallsign(getGatewayCallsign());
		status.setScope(getScope());
		status.setLatitude(getLatitude());
		status.setLongitude(getLongitude());
		status.setDescription1(getDescription1());
		status.setDescription2(getDescription2());
		status.setUrl(getUrl());
		status.setName(getName());
		status.setLocation(getLocation());
		status.setDashboardUrl(getDashboardUrl());
		getGatewayGlobalIP().ifPresent(new Consumer<InetAddress>() {
			@Override
			public void accept(InetAddress t) {
				status.setGatewayGlobalIpAddress(t.getHostAddress());
			}
		});
		status.setLastheardCallsign(getLastHeardCallsign());
		status.setUseProxy(isUseProxy());
		status.setProxyServerAddress(getProxyServerAddress());
		status.setProxyServerPort(getProxyServerPort());

		return status;
	}

	@Override
	public Class<? extends GatewayStatusData> getStatusDataType(){
		return GatewayStatusData.class;
	}

	@Override
	public EventListener<DStarRepeaterEvent> getOnRepeaterEventListener() {
		return repeaterEventListener;
	}

	@Override
	public boolean isDataTransferring() {
		return false;
	}

	private void stopRepeaters() {
		DSTARRepeaterManager.finalizeManager();
	}

	private void stopRefectorCommunicationServices() {
		ReflectorCommunicationServiceManager.finalizeManager();
	}

	private void repeaterProcess() {
		for(final DSTARRepeater repeater : getRepeaters()) {
			if(repeater.getRepeaterType() == RepeaterTypes.ExternalConnectorDummy) {
				final ECDummyRepeater dummyRepeater = (ECDummyRepeater)repeater;

				dummyRepeater.process();
			}
		}
	}

	private void processRepeaterPacketToXChange() {
		for(final DSTARRepeater repeater : getRepeaters()) {
			if(repeater.getRepeaterType() == RepeaterTypes.ExternalConnectorDummy) {
				final ECDummyRepeater dummyRepeater = (ECDummyRepeater)repeater;
				dummyRepeater.process();

				DSTARPacket packet = null;
				while(
					(
						packet = packetAuth(
							dummyRepeater,
							passPacketToXChange(dummyRepeater, dummyRepeater.readModemPacket())
						)
					) != null
				) {
					if(packet.getPacketType() != DSTARPacketType.DV) {continue;}

					packet.getBackBone().setId((byte)0x20);
					packet.getBackBone().setDestinationRepeaterID((byte)0x00);
					packet.getBackBone().setSendRepeaterID((byte)0x01);
					packet.getBackBone().setSendTerminalID((byte)0x02);

					if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
						packet.getRfHeader().setRepeater1Callsign(dummyRepeater.getRepeaterCallsign().toCharArray());
						packet.getRfHeader().setRepeater2Callsign(dummyRepeater.getRepeaterCallsign().toCharArray());

						final XChangePacket xchangePacket =
							new XChangePacketHeader(
								packet.getDVPacket(), XChangePacketDirection.FromGateway,
								new XChangeRouteFlagData(XChangeRouteFlag.ZoneRepeater, XChangeRouteFlag.Forward)
							);

						XChangePacketLogger.log(xchangePacket, logHeader + " [OUT]");

						sendToXChange(xchangePacket);
					}

					if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {
						final XChangePacket xchangePacket =
							new XChangePacketVoice(
								packet.getDVPacket(), XChangePacketDirection.FromGateway,
								new XChangeRouteFlagData(XChangeRouteFlag.ZoneRepeater, XChangeRouteFlag.Forward)
							);

						XChangePacketLogger.log(xchangePacket, logHeader + " [OUT]");

						sendToXChange(xchangePacket);
					}
				}
			}
		}
	}

	private void processReflectorPacketToRepeater() {
		for(final ReflectorCommunicationService reflectorService : ReflectorCommunicationServiceManager.getServices(getSystemID())) {
			for(final DSTARRepeater repeater : getRepeaters()) {
				DSTARPacket packet = null;
				while((packet = reflectorService.readPacket(repeater)) != null) {
					if(packet.getPacketType() != DSTARPacketType.DV) {continue;}

					final int frameID = packet.getBackBone().getFrameIDNumber();

					processEntriesLocker.lock();
					try {
						ProcessEntry entry = processEntries.get(frameID);

						if(
							entry == null &&
							packet.getDVPacket().hasPacketType(PacketType.Header)
						) {
							if (!packet.getRfHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL)) {
								if(log.isInfoEnabled())
									log.info("Reject unsupported reflector packet.\n" + packet.toString(4));

								continue;
							}
							else if (
								!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) ||
								!(
									CallSignValidator.isValidUserCallsign(packet.getRfHeader().getYourCallsign()) ||
									CallSignValidator.isValidCQCQCQ(packet.getRfHeader().getYourCallsign())
								)
							) {
								if(log.isInfoEnabled())
									log.info("Reject unknown reflector packet.\n" + packet.toString(4));

								continue;
							}

							//ヘッダを書き換え
							packet.getRfHeader().setRepeater1Callsign(repeater.getRepeaterCallsign().toCharArray());
							packet.getRfHeader().setRepeater2Callsign(repeater.getRepeaterCallsign().toCharArray());

							if(log.isInfoEnabled()) {
								log.info(
									"[Reflector IN(" + ProcessMode.ReflectorToRepeater + ")] " +
									"MY:" + String.valueOf(packet.getRfHeader().getMyCallsign()) + " " +
										String.valueOf(packet.getRfHeader().getMyCallsignAdd()) +
									"/UR:" + String.valueOf(packet.getRfHeader().getYourCallsign()) +
									"/RPT2:" + String.valueOf(packet.getRfHeader().getRepeater2Callsign()) +
									"/RPT1:" + String.valueOf(packet.getRfHeader().getRepeater1Callsign()) +
									"/From:" + DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
										String.valueOf(packet.getRfHeader().getSourceRepeater2Callsign())
									)
								);
							}

							entry = new ProcessEntry(frameID, ProcessMode.ReflectorToRepeater, repeater);
							entry.setHeaderPacket(packet);

							final Header heardHeader = packet.getRfHeader().clone();
							heardHeader.setRepeater1Callsign(getGateway().getGatewayCallsign().toCharArray());
							heardHeader.setRepeater2Callsign(repeater.getRepeaterCallsign().toCharArray());
							entry.getHeardInfo().setHeardHeader(heardHeader);


							if(log.isDebugEnabled()) {
								log.debug(logHeader + "Create new process entry from reflector.\n" + entry.toString(4));
							}
							processEntries.put(frameID, entry);

							repeater.writePacket(packet);

							writeToReflectorPacket(this, entry, repeater, packet);

							processStatusHeardEntry(frameID, PacketType.Header, packet, packet.getProtocol(), entry, repeater);

							entry.getActivityTimekeeper().setTimeoutTime(2, TimeUnit.SECONDS);
							entry.getActivityTimekeeper().updateTimestamp();
						}

						if(
							entry != null &&
							packet.getDVPacket().hasPacketType(PacketType.Voice)
						) {
							repeater.writePacket(packet);
							if(
								!packet.isEndVoicePacket() &&
								packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
							) {
								repeater.writePacket(entry.getHeaderPacket());
							}

							writeToReflectorPacket(this, entry, repeater, packet);
							if(
								!packet.isEndVoicePacket() &&
								packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
							) {
								writeToReflectorPacket(this, entry, repeater, entry.getHeaderPacket());
							}

							entry.getErrorDetector().update(packet.getDVPacket());
							processStatusHeardEntry(frameID, PacketType.Voice, packet, packet.getProtocol(), entry, repeater);

							if(packet.isEndVoicePacket()) {
								if(log.isDebugEnabled()) {
									log.debug(logHeader + "Remove process entry from reflector.\n" + entry.toString(4));
								}

								processEntries.remove(frameID);
							}
							else {
								entry.getActivityTimekeeper().updateTimestamp();
							}
						}
					}finally {
						processEntriesLocker.unlock();
					}
				}
			}
		}
	}

	private void processRepeaterPacketToReflector() {
		for(final DSTARRepeater repeater : getRepeaters()) {
			if(repeater.getRepeaterType() == RepeaterTypes.ExternalConnectorDummy) {
				final ECDummyRepeater dummyRepeater = (ECDummyRepeater)repeater;

				final DSTARPacket packet = dummyRepeater.readPacket();
				if(packet != null && packet.getPacketType() == DSTARPacketType.DV)
					processRepeaterPacketToReflector(dummyRepeater, packet);
			}
		}
	}

	private void processRepeaterPacketToReflector(final ECDummyRepeater repeater, final DSTARPacket packet) {
		final int frameID = packet.getBackBone().getFrameIDNumber();

		processEntriesLocker.lock();
		try {
			final ProcessEntry entry = processEntries.get(frameID);

			if(entry == null && packet.getDVPacket().hasPacketType(PacketType.Header)) {
				processRepeaterPacketToReflectorHeader(
					frameID, repeater, packet
				);
			}

			if(entry != null && packet.getDVPacket().hasPacketType(PacketType.Voice)) {
				processRepeaterPacketToReflectorVoice(
					entry, frameID, repeater, packet
				);
			}
		}finally {
			processEntriesLocker.unlock();
		}
	}

	private void processRepeaterPacketToReflectorHeader(
		final int frameID,
		final ECDummyRepeater repeater, final DSTARPacket packet
	) {
		packet.getRfHeader().replaceCallsignsIllegalCharToSpace();

		if(!checkHeader(packet.getDVPacket(), DSTARRepeaterManager.getRepeaterCallsigns(systemID))) {return;}

		ProcessEntry entry = null;

		//ルート判断
		if(
			(entry = processControlCommand(packet.getDVPacket(), repeater)) == null &&
			(entry = processReflectorRoute(packet.getDVPacket(), repeater)) == null &&
			(entry = processG2Route(packet.getDVPacket(), repeater)) == null &&
			(entry = processLocalCQRoute(packet.getDVPacket(), repeater)) == null &&
			(entry = processUnknownRoute(packet.getDVPacket(), repeater)) == null
		) {
			if(log.isWarnEnabled())
				log.warn("Could not routing unknown repeater packet...\n" + packet.toString(4));

			return;
		}

		entry.setHeaderPacket(packet);

		final Header heardHeader = packet.getRfHeader().clone();
		entry.getHeardInfo().setHeardHeader(heardHeader);

		if(
			entry.getProcessMode() == ProcessMode.RepeaterToG2 ||
			entry.getProcessMode() == ProcessMode.RepeaterToReflector
		) {setLastHeardCallsign(String.valueOf(packet.getRfHeader().getMyCallsign()));}

		if(log.isDebugEnabled()) {
			log.debug(logHeader + "Create new process entry from repeater.\n" + entry.toString(4));
		}

		if(log.isInfoEnabled()) {
			log.info(
				logHeader +
				"[XChange IN(" + entry.getProcessMode() + ":" + repeater.getRepeaterCallsign() + ")] " +
				"MY:" + String.valueOf(packet.getRfHeader().getMyCallsign()) + " " +
					String.valueOf(packet.getRfHeader().getMyCallsignAdd()) + "/" +
				"UR:" + String.valueOf(packet.getRfHeader().getYourCallsign()) + "/" +
				"RPT1:" + String.valueOf(packet.getRfHeader().getRepeater1Callsign()) + "/" +
				"RPT2:" + String.valueOf(packet.getRfHeader().getRepeater2Callsign())
			);
		}

		processEntries.put(frameID, entry);


		writeToReflectorPacket(this, entry, repeater, packet);

		processStatusHeardEntry(frameID, PacketType.Header, packet, packet.getProtocol(), entry, repeater);

		entry.getActivityTimekeeper().setTimeoutTime(2, TimeUnit.SECONDS);
		entry.getActivityTimekeeper().updateTimestamp();
	}

	private void processRepeaterPacketToReflectorVoice(
		final ProcessEntry entry, final int frameID,
		final ECDummyRepeater repeater, final DSTARPacket packet
	) {
		if(entry.getRepeater() != repeater ) {return;}

		writeToReflectorPacket(this, entry, repeater, packet);

		if(
			!packet.isEndVoicePacket() &&
			packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
		) {
			writeToReflectorPacket(this, entry, repeater, entry.getHeaderPacket());
		}

		entry.getErrorDetector().update(packet.getDVPacket());
		processStatusHeardEntry(frameID, PacketType.Voice, packet, packet.getProtocol(), entry, repeater);

		if(packet.isEndVoicePacket()) {
			if(log.isDebugEnabled()) {
				log.debug(logHeader + "Remove process entry from repeater.\n" + entry.toString(4));
			}

			processEntries.remove(frameID);
		}
		else {
			entry.getActivityTimekeeper().updateTimestamp();
		}
	}

	private ThreadProcessResult processXChangePacketToRepeater() {
		XChangePacket packet = null;
		do {
			receivePacketsLocker.lock();
			try {
				packet = receivePackets.poll();
			}finally {
				receivePacketsLocker.unlock();
			}

			if(packet != null) {
				if(
					!packet.getRemoteAddress().getAddress().getHostAddress().equals(getXchangeServerAddress()) ||
					packet.getRemoteAddress().getPort() != getXchangeServerPort()
				) {
					setXchangeServerAddress(packet.getRemoteAddress().getAddress().getHostAddress());
					setXchangeServerPort(packet.getRemoteAddress().getPort());

					if(log.isInfoEnabled()) {
						log.info(
							logHeader +
							"XChange server address set to " + getXchangeServerAddress() + ":" + getXchangeServerPort()
						);
					}
				}

				replyToXChange(packet);

				xchangeServerKeepaliveTimerkeeper.updateTimestamp();

				XChangePacketLogger.log(packet, logHeader + " [IN]");

				if(packet.getType() == XChangePacketType.Voice) {
					if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
						final Header header = packet.getRfHeader();

						//RPT2に識別符号が入っていない場合には補完する
						if(
							header.getRepeater1Callsign()[header.getRepeater1Callsign().length - 1] != ' ' &&
							header.getRepeater2Callsign()[header.getRepeater2Callsign().length - 1] == ' '
						) {
							header.getRepeater2Callsign()[header.getRepeater2Callsign().length - 1] =
								header.getRepeater1Callsign()[header.getRepeater1Callsign().length - 1];
						}
					}

					for(final DSTARRepeater repeater : getRepeaters()) {
						if(repeater.getRepeaterType() == RepeaterTypes.ExternalConnectorDummy) {
							final ECDummyRepeater dummyRepeater = (ECDummyRepeater)repeater;

							final ExternalConnectorRepeaterProperties rp =
								properties.getRepeaters().get(repeater.getRepeaterCallsign());

							if(rp != null && rp.isUseXChange())
								dummyRepeater.writeModemPacket(packet.clone());
						}
					}
				}
			}
		}while(packet != null);

		return ThreadProcessResult.NoErrors;
	}

	private void replyToXChange(final XChangePacket receivePacket) {
		final XChangePacketResponse response = new XChangePacketResponse(receivePacket);

		sendToXChange(response);
	}

	private void onReceivePacketFromXChange() {
		parsePacket(receivePackets, receivePacketsLocker);

		wakeupGatewayWorker();
	}

	private boolean sendToXChange(final XChangePacket packet) {
		if(
			channel == null ||
			getXchangeServerAddress() == null
		) {return false;}

		xchangePacketNoLocker.lock();
		try {
			packet.setPacketNo(xchangePacketNo);

			xchangePacketNo = (xchangePacketNo + 1) % 0x10000;
		}finally {
			xchangePacketNoLocker.unlock();
		}

		final ByteBuffer buffer = packet.assemblePacket();
		if(buffer == null) {return false;}

		final InetSocketAddress dst =
			new InetSocketAddress(getXchangeServerAddress(), getXchangeServerPort());
		packet.setRemoteAddress(dst);
		if(dst.isUnresolved()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not resolve xchange server address = " + dst + ".");

			return false;
		}

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"Send packet to xchange(" + dst + ").\n" +
				packet.toString(4) + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
			buffer.rewind();
		}

		return writeUDPPacket(
				channel.getKey(),
				new InetSocketAddress(getXchangeServerAddress(), getXchangeServerPort()),
				buffer
			);
	}

	private boolean parsePacket(
		final Queue<XChangePacket> receivePackets,
		final Lock receivePacketsLocker
	) {
		final ProcessResult<Boolean> update = new ProcessResult<>(false);

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			opEntry.ifPresent(new Consumer<BufferEntry>() {
				@Override
				public void accept(BufferEntry buffer) {

					buffer.getLocker().lock();
					try {
						if(!buffer.isUpdate()) {return;}

						buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

						for (
							Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator();
							itBufferBytes.hasNext();
						) {
							final PacketInfo packetInfo = itBufferBytes.next();
							final int bufferLength = packetInfo.getPacketBytes();
							itBufferBytes.remove();

							if (bufferLength <= 0) {
								continue;
							}

							final ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
							for (int i = 0; i < bufferLength; i++) {
								receivePacket.put(buffer.getBuffer().get());
							}
							BufferState.toREAD(receivePacket, BufferState.WRITE);

							if(log.isTraceEnabled()) {
								final StringBuilder sb = new StringBuilder(logHeader);
								sb.append(bufferLength);
								sb.append(" bytes received.\n");
								sb.append("    ");
								sb.append("[RemoteHost]:");
								sb.append(buffer.getRemoteAddress());
								sb.append("/");
								sb.append("[LocalHost]:");
								sb.append(buffer.getLocalAddress());
								sb.append("\n");
								sb.append(FormatUtil.byteBufferToHexDump(receivePacket, 4));
								log.trace(sb.toString());

								receivePacket.rewind();
							}

							boolean match = false;

							receivePacketsLocker.lock();
							try {
								match = parsePacket(buffer, receivePacket, receivePackets);
							}finally {receivePacketsLocker.unlock();}

							if(match) {update.setResult(true);}
						}

						buffer.setUpdate(false);

					}finally{
						buffer.getLocker().unlock();
					}
				}
			});
		}

		return update.getResult();
	}

	private boolean parsePacket(
		final BufferEntry bufferEntry, final ByteBuffer buffer,
		final Queue<XChangePacket> receivePackets
	) {
		boolean match = false;
		XChangePacket parsedPacket = null;
		do {
			if (
				(parsedPacket = keepalivePacket.parsePacket(buffer)) != null ||
				(parsedPacket = headerPacket.parsePacket(buffer)) != null ||
				(parsedPacket = voicePacket.parsePacket(buffer)) != null
			) {
				parsedPacket.setRemoteAddress(bufferEntry.getRemoteAddress());

				while(receivePackets.size() >= 500) {receivePackets.poll();}
				receivePackets.add(parsedPacket.clone());

				if(log.isTraceEnabled())
					log.trace(logHeader + "Receive xchange packet.\n    " + parsedPacket.toString());

				match = true;
			} else {
				match = false;
			}
		} while (match);

		return match;
	}

	private boolean checkHeader(
		@NonNull final DVPacket header, @NonNull final List<String> repeaters
	) {
		if(!header.hasPacketType(PacketType.Header)) {return false;}

		if(!header.getRfHeader().isSetRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL)) {
			if(log.isWarnEnabled())
				log.warn("Non terminal packet received, ignore header packet.\n" + header.toString(4));

			return false;
		}

		if (
			RepeaterControlFlag.getTypeByValue(header.getRfHeader().getFlags()[0]) != RepeaterControlFlag.NOTHING_NULL &&
			RepeaterControlFlag.getTypeByValue(header.getRfHeader().getFlags()[0]) != RepeaterControlFlag.CANT_REPEAT &&
			RepeaterControlFlag.getTypeByValue(header.getRfHeader().getFlags()[0]) != RepeaterControlFlag.AUTO_REPLY
		) {
			if(log.isWarnEnabled())
				log.warn("Illegal control flag received, ignore.\n" + header.toString(4));

			return false;
		}

		final String repeater1Callsign = String.valueOf(header.getRfHeader().getRepeater1Callsign());
		final String repeater2Callsign = String.valueOf(header.getRfHeader().getRepeater2Callsign());

		//RPT1,RPT2チェック
		final boolean repeater1CallsignValid =
			getGateway().getGatewayCallsign().equals(repeater1Callsign) ||
			Stream.of(repeaters)
			.anyMatch(new Predicate<String>() {
				@Override
				public boolean test(String repeaterCallsign) {
					return repeaterCallsign.equals(repeater1Callsign);
				}
			});

		final boolean repeater2CallsignValid =
			getGateway().getGatewayCallsign().equals(repeater2Callsign) ||
			Stream.of(repeaters)
			.anyMatch(new Predicate<String>() {
				@Override
				public boolean test(String repeaterCallsign) {
					return repeaterCallsign.equals(repeater2Callsign);
				}
			});

		if(!repeater1CallsignValid || !repeater2CallsignValid) {
			if(log.isWarnEnabled())
				log.warn("Unknown route packet received.\n" + header.toString(4));

			return false;
		}

		else if(
			!CallSignValidator.isValidUserCallsign(header.getRfHeader().getMyCallsign())
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					"G/W:" + getGateway().getGatewayCallsign() +
					" received invalid my callsign header.\n" + header.toString(4)
				);
			}

			return false;
		}
		else if(
			DSTARDefines.EmptyLongCallsign.equals(String.valueOf(header.getRfHeader().getYourCallsign()))
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					"G/W:" + getGateway().getGatewayCallsign() +
					" received invalid empty your callsign header from modem...\n" + header.toString(4)
				);
			}

			return false;
		}

		return true;
	}

	private ProcessEntry processControlCommand(@NonNull final DVPacket packet, final DSTARRepeater repeater) {
		boolean isControlCommand = false;
		boolean isReflectorLinkCommand = false;
		boolean isReflectorCommand = false;
		if(
			!packet.hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) ||
			(
				!(isControlCommand = controlCommandPattern.matcher(String.valueOf(packet.getRfHeader().getYourCallsign())).matches()) &&
				!(isReflectorLinkCommand = reflectorLinkPattern.matcher(String.valueOf(packet.getRfHeader().getYourCallsign())).matches()) &&
				!(isReflectorCommand = reflectorCommandPattern.matcher(String.valueOf(packet.getRfHeader().getYourCallsign())).matches())
			) ||
			!getGateway().getGatewayCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign()))
		) {
			return null;
		}

		ProcessEntry entry = null;
		final int frameID = packet.getBackBone().getFrameIDNumber();

		boolean success = false;
//		String yr = String.valueOf(packet.getRfHeader().getYourCallsign());
		final String yourCallsign = String.valueOf(packet.getRfHeader().getYourCallsign());

		if(isReflectorCommand) {
			final char command = packet.getRfHeader().getYourCallsign()[DSTARDefines.CallsignFullLength - 1];
			switch(command) {
			case 'U':
				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Unlink Request] " +
						"MY:" + String.valueOf(packet.getRfHeader().getMyCallsign()) +
							String.valueOf(packet.getRfHeader().getMyCallsignAdd()) +
						"/UR:" + String.valueOf(packet.getRfHeader().getYourCallsign()) +
						"/RPT2:" + String.valueOf(packet.getRfHeader().getRepeater2Callsign()) +
						"/RPT1:" + String.valueOf(packet.getRfHeader().getRepeater1Callsign())
					);
				}
				getGateway().unlinkReflector(repeater);
				success = true;
				break;

			case 'E':
				break;

			case 'I':
				break;

			default:
				break;
			}
		}
		else if(isReflectorLinkCommand) {
			// Link to Reflector
			final String reflectorCallsign =
					yourCallsign.substring(0, DSTARDefines.CallsignFullLength - 2) + ' ' +
					yourCallsign.charAt(DSTARDefines.CallsignFullLength - 2);

			final Optional<ReflectorHostInfo> opReflectorHostInfo =
				getGateway().findReflectorByCallsign(reflectorCallsign);

			if(opReflectorHostInfo.isPresent()) {
				final ReflectorHostInfo host = opReflectorHostInfo.get();

				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Link Request(" + host.getReflectorProtocol() + ")] " +
						"DEST:" + reflectorCallsign +
							(!reflectorCallsign.equals(host.getReflectorCallsign()) ? "*" + host.getReflectorCallsign() : "") +
							(!"".equals(host.getName()) ? "(" + host.getName() + ")" : "") +
						"/MY:" + String.valueOf(packet.getRfHeader().getMyCallsign()) + " " +
							String.valueOf(packet.getRfHeader().getMyCallsignAdd()) +
						"/RPT:" + String.valueOf(packet.getRfHeader().getRepeater1Callsign()) +
						"/ADDR:" + host.getReflectorAddress() + ":" + host.getReflectorPort() +
						"/DS:" + host.getDataSource()
					);
				}

				success =
					getGateway().linkReflector(repeater, reflectorCallsign, host);
			}
			else {
				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Link Request(Failed)] " +
						"Reflector host " + reflectorCallsign +
						" information not found, ignore request.\n" + packet.getRfHeader().toString(4)
					);
				}
			}
		}
		else if(isControlCommand) {

			if(	// Reflector Link Manager Configulation
				yourCallsign.length() >= DSTARDefines.CallsignFullLength &&
				yourCallsign.substring(2, 7).startsWith("RLMAC"))
			{
				final char controlChar = yourCallsign.charAt(DSTARDefines.CallsignFullLength - 1);

				switch(controlChar) {
				case 'E':
					success =
						getGateway().getReflectorLinkManager().setAutoControlEnable(repeater, true);
					break;

				case 'D':
					success =
						getGateway().getReflectorLinkManager().setAutoControlEnable(repeater, false);
					break;

				default:
					if(log.isInfoEnabled())
						log.info("Unknown ReflectorLinkManager configulation request received.\n" + packet.toString());
					break;
				}
			}
			else {
				final char command =
					packet.getRfHeader().getYourCallsign()[DSTARDefines.CallsignFullLength - 1];

				switch(command) {
				case 'D':	//debug
					success = true;
					break;

				case 'I':	// Repeater Information
					announceTool.announceInformation(
						repeater, announceVoiceCharactor, repeater.getLinkedReflectorCallsign()
					);
					success = true;
					break;

				default:
					if(log.isInfoEnabled())
						log.info("Unknown gateway control command received.\n" + packet.toString());
					break;
				}
			}
		}

		entry = new ProcessEntry(frameID, ProcessMode.Control, repeater);

		if(!success) {
			sendFlagToRepeaterUser(
				repeater, String.valueOf(packet.getRfHeader().getMyCallsign()),RepeaterControlFlag.CANT_REPEAT
			);
		}

		return entry;
	}

	private ProcessEntry processReflectorRoute(
		@NonNull final DVPacket packet, final DSTARRepeater repeater
	) {
		if(
			!packet.hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) ||
			!CallSignValidator.isValidCQCQCQ(packet.getRfHeader().getYourCallsign()) ||
			!getGateway().getGatewayCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign()))
		) {
			return null;
		}

		final int frameID = packet.getBackBone().getFrameIDNumber();

		final ProcessEntry entry = new ProcessEntry(frameID, ProcessMode.RepeaterToReflector, repeater);

		if(log.isInfoEnabled()) {
			log.info(
				"[Reflector OUT] " +
				"MY:" + String.valueOf(packet.getRfHeader().getMyCallsign()) + String.valueOf(packet.getRfHeader().getMyCallsignAdd()) +
				"/UR:" + String.valueOf(packet.getRfHeader().getYourCallsign()) +
				"/RPT2:" + String.valueOf(packet.getRfHeader().getRepeater2Callsign()) +
				"/RPT1:" + String.valueOf(packet.getRfHeader().getRepeater1Callsign())
			);
		}

		return entry;
	}

	private ProcessEntry processG2Route(
		@NonNull final DVPacket packet, final DSTARRepeater repeater
	) {
		if(
			!packet.hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) ||
			(
				!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getYourCallsign()) &&
				!CallSignValidator.isValidAreaRepeaterCallsign(packet.getRfHeader().getYourCallsign()) &&
				!CallSignValidator.isValidCQCQCQ(packet.getRfHeader().getYourCallsign())
			) ||
			(
				!getGateway().getGatewayCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign())) &&
				!getGateway().getGatewayCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater1Callsign()))
			)
		) {
			return null;
		}

		final int frameID = packet.getBackBone().getFrameIDNumber();

		final boolean isOutgoing =
			getGateway().getGatewayCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign()));

		final ProcessEntry entry =
			new ProcessEntry(frameID, isOutgoing ? ProcessMode.RepeaterToG2 : ProcessMode.G2ToRepeater, repeater);

		if(isOutgoing) {
			autoDisconnectFromReflectorOnTxG2Route(
				RepeaterControlFlag.getTypeByValue(packet.getRfHeader().getFlags()[0]),
				String.valueOf(packet.getRfHeader().getRepeater1Callsign())
			);
		}

		return entry;
	}

	private ProcessEntry processLocalCQRoute(
		@NonNull final DVPacket packet, final DSTARRepeater repeater
	) {
		if(
			!packet.hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign()) ||
			!CallSignValidator.isValidRepeaterCallsign(packet.getRfHeader().getRepeater1Callsign()) ||
			!CallSignValidator.isValidRepeaterCallsign(packet.getRfHeader().getRepeater2Callsign()) ||
			!Arrays.equals(packet.getRfHeader().getRepeater1Callsign(), packet.getRfHeader().getRepeater2Callsign()) ||
			DSTARRepeaterManager.getRepeater(systemID, String.valueOf(packet.getRfHeader().getRepeater1Callsign())) == null
		) {return null;}

		final int frameID = packet.getBackBone().getFrameIDNumber();

		final ProcessEntry entry = new ProcessEntry(frameID, ProcessMode.LocalCQ, repeater);

		return entry;
	}

	private ProcessEntry processUnknownRoute(
		@NonNull final DVPacket packet, final DSTARRepeater repeater
	) {
		if(
			!packet.hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRfHeader().getMyCallsign())
		) {
			return null;
		}

		final int frameID = packet.getBackBone().getFrameIDNumber();

		final ProcessEntry entry = new ProcessEntry(frameID, ProcessMode.RepeaterToNull, repeater);

		return entry;
	}

	private boolean autoDisconnectFromReflectorOnTxG2Route(
		@NonNull final RepeaterControlFlag repeaterFlag,
		@NonNull final String repeaterCallsign
	) {
		if(repeaterFlag == RepeaterControlFlag.NOTHING_NULL) {
			final DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
			if(repeater == null) {return false;}

			if(!repeater.isAutoDisconnectFromReflectorOnTxToG2Route()) {return true;}

			final String linkedReflector =
				getGateway().getOutgoingLinkedReflectorCallsign(repeater);

			if(
				linkedReflector != null &&
				!DSTARDefines.EmptyLongCallsign.equals(linkedReflector) &&
				!"".equals(linkedReflector)
			) {
				getGateway().unlinkReflector(repeater);
			}
		}

		return true;
	}

	private boolean sendFlagToRepeaterUser(
		final DSTARRepeater repeater, final String userCallsign, final RepeaterControlFlag flag
	) {
		final Header header = new Header();
		header.setRepeaterControlFlag(flag);

		header.setMyCallsign(getGateway().getGatewayCallsign().toCharArray());
		header.setYourCallsign(userCallsign.toCharArray());
		header.setRepeater2Callsign(repeater.getRepeaterCallsign().toCharArray());
		header.setRepeater1Callsign(getGateway().getGatewayCallsign().toCharArray());

		return repeater.writePacket(new InternalPacket(
			DSTARUtils.generateLoopBlockID(),
			new DVPacket(
				new BackBoneHeader(
					BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, DSTARUtils.generateFrameID()
				),
				header
			)
		));
	}

	private boolean writeToReflectorPacket(
		final DSTARGateway gateway,
		final ProcessEntry entry,
		final DSTARRepeater targetRepeater,
		final DSTARPacket packet
	) {
		switch(entry.getProcessMode()) {
		case RepeaterToReflector:
			return gateway.writePacketToReflectorRoute(
				targetRepeater, ConnectionDirectionType.BIDIRECTIONAL, packet
			);

		case LocalCQ:
		case RepeaterToG2:
		case G2ToRepeater:
		case ReflectorToRepeater:
			return gateway.writePacketToReflectorRoute(
				targetRepeater, ConnectionDirectionType.INCOMING,
				updateToReflectorHeader(packet, targetRepeater)
			);

		default:
			return false;
		}
	}

	private static DSTARPacket updateToReflectorHeader(final DSTARPacket packet, final DSTARRepeater repeater) {
		if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
			final DSTARPacket copy = packet.clone();

			copy.getRfHeader().setRepeater1Callsign(repeater.getRepeaterCallsign().toCharArray());
			copy.getRfHeader().setRepeater2Callsign(repeater.getLinkedReflectorCallsign().toCharArray());

			return copy;
		}
		else {
			return packet;
		}
	}

	private void cleanupProcessEntry() {
		if(processCleanupTimekeeper.isTimeout()) {
			for(Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
				final ProcessEntry entry = it.next();

				if(entry.getActivityTimekeeper().isTimeout()) {
					if(log.isDebugEnabled()) {
						log.debug(logHeader + "Remove timeout process entry.\n" + entry.toString(4));
					}

					processStatusHeardEntryTimeout(entry.getFrameID(), entry);

					it.remove();
				}
			}

			processCleanupTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
			processCleanupTimekeeper.updateTimestamp();
		}
	}

	private void processStatusHeardEntry(
		final int frameID,
		final PacketType packetType,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater srcRepeater
	) {
		processStatusHeardEntry(getGateway(), frameID, packetType, packet, protocol, entry, srcRepeater, false);
	}

	private void processStatusHeardEntryTimeout(
		final int frameID,
		final ProcessEntry entry
	) {
		processStatusHeardEntry(getGateway(), frameID, null, null, entry.getHeardInfo().getProtocol(), entry, null, true);
	}

	private static void processStatusHeardEntry(
		final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater,
		final boolean timeout
	) {
		entry.getLocker().lock();
		try {
			if(timeout && entry.getHeardInfo().getState() == HeardState.Update) {
				if(entry.getHeardInfo().isHeardTransmit()) {
					gateway.addHeardEntry(
						HeardEntryState.End,
						entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().isLocationAvailable(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getErrorDetector().getPacketCount(),
						entry.getErrorDetector().getPacketDropRate(),
						entry.getErrorDetector().getBitErrorRate()
					);
				}

				entry.getHeardInfo().setState(HeardState.End);
			}
			else if(packetType == PacketType.Header) {
				processStatusHeardEntryHeader(gateway, frameID, packet, protocol, entry, repeater);
			}
			else if(packetType == PacketType.Voice) {
				processStatusHeardEntryVoice(gateway, frameID, packet, protocol, entry, repeater);
			}
		}finally {
			entry.getLocker().unlock();
		}
	}

	private static void processStatusHeardEntryHeader(
		final DSTARGateway gateway,
		final int frameID,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater
	) {
		if(packet.getDVPacket().hasPacketType(PacketType.Header)) {return;}

		if(entry.getHeardInfo().getState() == HeardState.Start) {
			if(entry.getSlowdataDecoder() == null)
				entry.setSlowdataDecoder(new DataSegmentDecoder());
			else
				entry.getSlowdataDecoder().reset();

			switch(entry.getProcessMode()) {
			case RepeaterToReflector:
				if(repeater != null && repeater.getLinkedReflectorCallsign() != null)
					entry.getHeardInfo().setDestination(repeater.getLinkedReflectorCallsign());
				else
					entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);

				entry.getHeardInfo().setFrom(
					repeater != null ?
						DSTARUtils.convertRepeaterCallToAreaRepeaterCall(repeater.getRepeaterCallsign()) :
						DSTARDefines.EmptyLongCallsign
				);

				if(repeater != null && repeater.getLinkedReflectorCallsign() != null) {
					gateway.findReflectorByCallsign(repeater.getLinkedReflectorCallsign())
					.ifPresentOrElse(new Consumer<ReflectorHostInfo>() {
						@Override
						public void accept(ReflectorHostInfo t) {
							entry.getHeardInfo().setProtocol(t.getReflectorProtocol());
						}
					}, new Runnable() {
						@Override
						public void run() {
							entry.getHeardInfo().setProtocol(protocol);
						}
					});
				}
				else
					entry.getHeardInfo().setProtocol(protocol);

				break;

			case RepeaterToG2:
				entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);

				entry.getHeardInfo().setFrom(
					repeater != null ?
						DSTARUtils.convertRepeaterCallToAreaRepeaterCall(repeater.getRepeaterCallsign()) :
						DSTARDefines.EmptyLongCallsign
				);
				entry.getHeardInfo().setProtocol(DSTARProtocol.G123);
				break;

			case ReflectorToRepeater:
				entry.getHeardInfo().setDestination(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(entry.getHeaderPacket().getRfHeader().getRepeater2Callsign())
					)
				);
/*
				entry.getHeardInfo().setFrom(
					repeater != null ?
						repeater.getLinkedReflectorCallsign():
						DStarDefines.EmptyLongCallsign
				);
*/
				entry.getHeardInfo().setFrom(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(packet.getRfHeader().getSourceRepeater2Callsign())
					)
				);

				entry.getHeardInfo().setProtocol(protocol);
				break;

			case G2ToRepeater:
				entry.getHeardInfo().setDestination(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(entry.getHeaderPacket().getRfHeader().getRepeater2Callsign())
					)
				);
				entry.getHeardInfo().setFrom(
					String.valueOf(entry.getHeaderPacket().getRfHeader().getMyCallsign())
				);

				entry.getHeardInfo().setProtocol(DSTARProtocol.G123);
				break;

			default:
				entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);
				entry.getHeardInfo().setFrom(DSTARDefines.EmptyLongCallsign);
				entry.getHeardInfo().setProtocol(protocol);
				break;
			}

			if(
				entry.getProcessMode() == ProcessMode.RepeaterToReflector
			) {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.OUTGOING);
			}
			else if(
				entry.getProcessMode() == ProcessMode.Control ||
				entry.getProcessMode() == ProcessMode.RepeaterToG2 ||
				entry.getProcessMode() == ProcessMode.ReflectorToRepeater ||
				entry.getProcessMode() == ProcessMode.G2ToRepeater ||
				entry.getProcessMode() == ProcessMode.LocalCQ
			) {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.INCOMING);
			}
			else {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.Unknown);
			}

			entry.getHeardInfo().setHeardTransmit(
				entry.getHeaderPacket().getRfHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL) ||
				entry.getHeaderPacket().getRfHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
			);

			if(entry.getHeardInfo().isHeardTransmit()) {
				gateway.addHeardEntry(
					HeardEntryState.Start,
					entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
					DSTARDefines.EmptyDvShortMessage,
					false, 0, 0,
					entry.getErrorDetector().getPacketCount(),
					entry.getErrorDetector().getPacketDropRate(),
					entry.getErrorDetector().getBitErrorRate()
				);
			}

			entry.getHeardInfo().setState(HeardState.Update);
		}
		else if(entry.getHeardInfo().getState() == HeardState.Update) {

			if(entry.getProcessMode() == ProcessMode.RepeaterToG2) {
				entry.getHeardInfo().setDestination(
					String.valueOf(entry.getHeaderPacket().getRfHeader().getRepeater2Callsign())
				);
			}

			if(entry.getHeardInfo().isHeardTransmit()) {
				gateway.addHeardEntry(
					HeardEntryState.Update,
					entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
					entry.getHeardInfo().getShortMessage(),
					entry.getHeardInfo().isLocationAvailable(),
					entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
					entry.getErrorDetector().getPacketCount(),
					entry.getErrorDetector().getPacketDropRate(),
					entry.getErrorDetector().getBitErrorRate()
				);
			}
		}

	}

	private static void processStatusHeardEntryVoice(
		final DSTARGateway gateway,
		final int frameID,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater
	) {
		if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {return;}

		if(entry.getHeardInfo().getState() == HeardState.Update) {
			entry.getHeardInfo().setPacketCount(entry.getHeardInfo().getPacketCount() + 1);

			if(entry.getSlowdataDecoder() != null) {
				final DataSegmentDecoderResult decoderResult =
					entry.getSlowdataDecoder().decode(packet.getVoiceData().getDataSegment());
				switch(decoderResult) {
				case ShortMessage:
					final String decodedShortMessage = entry.getSlowdataDecoder().getShortMessageString();
					entry.getHeardInfo().setShortMessage(decodedShortMessage);
					break;

				case APRS:
					final APRSMessageDecoderResult dprsResult =
						APRSMessageDecoder.decodeDPRS(entry.getSlowdataDecoder().getAprsMessage());
					if(dprsResult != null) {
						entry.getHeardInfo().setLatitude(dprsResult.getLatitude());
						entry.getHeardInfo().setLongitude(dprsResult.getLongitude());
						entry.getHeardInfo().setLocationAvailable(true);
					}
					break;

				default:
					break;
				}
			}

			if(
				entry.getHeardInfo().isHeardTransmit() &&
				entry.getHeardInfo().getHeardIntervalTimer().isTimeout(100, TimeUnit.MILLISECONDS)
			) {
				entry.getHeardInfo().getHeardIntervalTimer().updateTimestamp();

				gateway.addHeardEntry(
					HeardEntryState.Update,
					entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
					entry.getHeardInfo().getShortMessage(),
					entry.getHeardInfo().isLocationAvailable(),
					entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
					entry.getErrorDetector().getPacketCount(),
					entry.getErrorDetector().getPacketDropRate(),
					entry.getErrorDetector().getBitErrorRate()
				);
			}

			if (packet.isEndVoicePacket()) {

				if(entry.getHeardInfo().isHeardTransmit()) {
					gateway.addHeardEntry(
						HeardEntryState.End,
						entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().isLocationAvailable(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getErrorDetector().getPacketCount(),
						entry.getErrorDetector().getPacketDropRate(),
						entry.getErrorDetector().getBitErrorRate()
					);
				}

				entry.getHeardInfo().setState(HeardState.End);
			}
		}
	}

	private DSTARPacket passPacketToXChange(final DSTARRepeater repeater, final DSTARPacket packet) {
		if(
			repeater == null || packet == null ||
			// XChangeとの接続がされていなければXChangeに対して送信しない
			getXchangeServerAddress() == null
		) {return null;}

		final ExternalConnectorRepeaterProperties rp =
			properties.getRepeaters().get(repeater.getRepeaterCallsign());

		return (rp != null && rp.isUseXChange()) ? packet : null;
	}

	private DSTARPacket packetAuth(final DSTARRepeater repeater, final DSTARPacket packet) {
		if(repeater == null || packet == null) {return null;}

		// XChangeとの接続がされていなければ認証スキップ
		if(getXchangeServerAddress() == null) {return packet;}

		AuthEntry currentAuth = currentAuths.get(repeater);
		if(currentAuth == null) {
			final ExternalConnectorRepeaterProperties p =
				properties.getRepeaters().get(repeater.getRepeaterCallsign());
			AuthType authMode = p != null ? p.getAuthMode() : AuthType.UNKNOWN;
			if(authMode == null || authMode == AuthType.UNKNOWN) {authMode = getAuthMode();}
			if(authMode == AuthType.UNKNOWN) {authMode = AuthType.INCOMING;}

			currentAuth = new AuthEntry(repeater, authMode);

			currentAuths.put(repeater, currentAuth);

			if(log.isDebugEnabled()) {
				log.debug(logHeader + "Create auth entry repeater = " + repeater.getRepeaterCallsign() + ".");
			}
		}

		DSTARPacket result = null;

		if(packet.getDVPacket().hasPacketType(PacketType.Header) && currentAuth.getFrameID() == 0x0000) {

			final String myCallsign = String.valueOf(packet.getRfHeader().getMyCallsign());

			currentAuth.setFrameID(packet.getBackBone().getFrameIDNumber());
			currentAuth.getFrameSequenceTimekeeper().updateTimestamp();
			currentAuth.setHeader(packet);

			currentAuth.setConnectionDirection(packet.getConnectionDirection());

			currentAuth.setMyCallsign(myCallsign);

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Try auth user = " +
					String.format("0x%04X", currentAuth.getFrameID()) +
					"/MY:" + myCallsign
				);
			}

			//ゲートウェイコールサイン、もしくはレピータコールサインの場合には素通しする
			if(
				getGatewayCallsign().equals(myCallsign) ||
				Stream.of(getRepeaters())
				.anyMatch(new Predicate<DSTARRepeater>() {
					@Override
					public boolean test(DSTARRepeater repeater) {
						return repeater.getRepeaterCallsign().equals(myCallsign);
					}
				})
			) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Start authenticated(by special callsign) user's packet to xchange = " +
						String.format("0x%04X", currentAuth.getFrameID()) +
						"/MY:" + myCallsign
					);
				}

				currentAuth.setAuthState(AuthResult.Valid);

				result = packet;

				currentAuth.setHeader(null);
			}
			else if(
				currentAuth.getAuthMode() == AuthType.OFF ||
				(
					currentAuth.getAuthMode() == AuthType.INCOMING &&
					currentAuth.getConnectionDirection() == ConnectionDirectionType.OUTGOING
				) ||
				(
					currentAuth.getAuthMode() == AuthType.OUTGOING &&
					currentAuth.getConnectionDirection() == ConnectionDirectionType.INCOMING
				)
			) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Start authenticated(by auth bypass) user's packet to xchange = " +
						String.format("0x%04X", currentAuth.getFrameID()) +
						"/MY:" + myCallsign
					);
				}

				currentAuth.setAuthState(AuthResult.Valid);

				result = packet;

				currentAuth.setHeader(null);
			}
			else {
				final AuthResultLog authLog = authResultLogs.get(myCallsign);
				if(authLog != null) {
					if(authLog.getAuthResult() == AuthResult.Valid) {
						currentAuth.setAuthState(AuthResult.Valid);

						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"Start authenticated(by auth log) user's packet to xchange = " +
								String.format("0x%04X", currentAuth.getFrameID()) +
								"/MY:" + myCallsign
							);
						}

						currentAuth.setHeader(null);

						result = packet;
					}
					else {
						currentAuth.setAuthState(AuthResult.Invalid);

						final String logMessage =
							"User " + myCallsign +
							" is unregistered, Blocking transmission to xchange.";

						if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

						pushLogToWebRemoteControlService(Level.WARN, logMessage);
					}
				}
				else {
					// 認証記録がない場合には、ルーティングサービスに問い合わせる
					RoutingService routingService = null;
					if(
						(
							CallSignValidator.isValidJapanUserCallsign(myCallsign) &&
							(routingService = getRoutingService(RoutingServiceTypes.JapanTrust)) != null
						) ||
						(
							CallSignValidator.isValidUserCallsign(myCallsign) &&
							getRoutingService(RoutingServiceTypes.GlobalTrust) == null &&
							(routingService = getRoutingService(RoutingServiceTypes.JapanTrust)) != null
						)
					){
						currentAuth.setRoutingService(routingService);
						currentAuth.setQueryID(
							routingService.findUser(
								myCallsign, packet.getRfHeader().clone()
							)
						);
						if(currentAuth.getQueryID() != null) {
							currentAuth.setAuthState(AuthResult.Wait);

							currentAuth.getQueryTimekeeper().updateTimestamp();

							if(log.isInfoEnabled()) {
								log.info(logHeader + "User " + myCallsign + " is not found auth log, executing query.");
							}
						}
						else {
							currentAuth.setAuthState(AuthResult.Invalid);

							final String logMessage =
								"Could not query user " + String.valueOf(packet.getRfHeader().getMyCallsign()) +
								", " + routingService.getServiceType() + " returned invalid query id.";

							if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

							pushLogToWebRemoteControlService(Level.WARN, logMessage);
						}
					}
					else if(
						CallSignValidator.isValidUserCallsign(myCallsign) &&
						(routingService = getRoutingService(RoutingServiceTypes.GlobalTrust)) != null
					) {
						currentAuth.setRoutingService(routingService);
						currentAuth.setQueryID(
							routingService.findUser(
								myCallsign, packet.getRfHeader().clone()
							)
						);
						if(currentAuth.getQueryID() != null) {
							currentAuth.setAuthState(AuthResult.Wait);

							currentAuth.getQueryTimekeeper().updateTimestamp();

							if(log.isInfoEnabled()) {
								log.info(logHeader + "User " + myCallsign + " is not found auth log, executing query.");
							}
						}
						else {
							currentAuth.setAuthState(AuthResult.Invalid);

							final String logMessage =
								"Could not query user " + myCallsign +
								", " + routingService.getServiceType() + " returned invalid query id.";

							if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

							pushLogToWebRemoteControlService(Level.WARN, logMessage);
						}
					}
					else {
						currentAuth.setAuthState(AuthResult.Invalid);

						final String logMessage =
							"Faild to auth query user = " + myCallsign +
							", Invalid callsign or routing service is not alive.";

						if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

						pushLogToWebRemoteControlService(Level.WARN, logMessage);
					}
				}
			}
		}
		else if(
			packet.getDVPacket().hasPacketType(PacketType.Voice) &&
			packet.getBackBone().getFrameIDNumber() == currentAuth.getFrameID()
		) {
			currentAuth.getFrameSequenceTimekeeper().updateTimestamp();

			final boolean valid = currentAuth.getAuthState() == AuthResult.Valid;

			if(valid) {
				if(currentAuth.getHeader() != null) {
					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"Start authenticated(by query executed) user's packet to xchange = " +
							String.format("0x%04X", currentAuth.getFrameID()) +
							"/MY:" + currentAuth.getMyCallsign()
						);
					}

					result = currentAuth.getHeader();
					currentAuth.setHeader(null);
				}
				else {
					result = packet;
				}
			}

			if(packet.isEndVoicePacket()) {
				//認証が通らなかった場合、認証失敗を通知する
				if(!valid && currentAuth.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
					announceAuthFailedToReflectorIncoming(
						currentAuth.getRepeater(), currentAuth.getMyCallsign()
					);
				}

				if(valid && log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"End authenticated user's packet to xchange = " + String.format("0x%04X", currentAuth.getFrameID())
					);
				}

				clearCurrentAuth(currentAuth);
			}
		}

		return result;
	}

	private void processAuth() {

		for(final AuthEntry currentAuth : currentAuths.values()) {
			if(
				currentAuth.getFrameID() != 0x0000 &&
				currentAuth.getFrameSequenceTimekeeper().isTimeout(2, TimeUnit.SECONDS)
			) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader + "Clear timeout frame(auth) = " +
						String.format("0x%04X", currentAuth.getFrameID()) + "."
					);
				}

				//認証が通らなかった場合、認証失敗を通知する
				if(
					currentAuth.getAuthState() == AuthResult.Invalid &&
					currentAuth.getConnectionDirection() == ConnectionDirectionType.INCOMING
				) {
					announceAuthFailedToReflectorIncoming(
						currentAuth.getRepeater(), currentAuth.getMyCallsign()
					);
				}

				clearCurrentAuth(currentAuth);
			}
		}

		cleanupAuthResultLogs();
	}

	private void processAuthRoutingService() {

		for(final AuthEntry currentAuth : currentAuths.values()) {
			if(currentAuth.getAuthState() != AuthResult.Wait) {continue;}

			final UUID queryID = currentAuth.getQueryID();
			if(queryID != null) {
				for(final RoutingService routingService : getRoutingServiceAll()) {
					final UserRoutingInfo userInfo = routingService.getUserInfo(queryID);
					if(userInfo != null) {
						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"Receive query result = " + userInfo.getYourCallsign() + "/" + userInfo.getRoutingResult()
							);
						}

						if(userInfo.getRoutingResult() == RoutingServiceResult.Success) {
							currentAuth.setAuthState(AuthResult.Valid);

							final String logMessage =
								"Auth success user = " + userInfo.getYourCallsign() + ".";

							if(log.isInfoEnabled()) {log.info(logHeader + logMessage);}

							pushLogToWebRemoteControlService(Level.INFO, logMessage);
						}
						else {
							currentAuth.setAuthState(AuthResult.Invalid);

							final String logMessage =
								"Auth failed user = " + userInfo.getYourCallsign() + ".";

							if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

							pushLogToWebRemoteControlService(Level.WARN, logMessage);
						}

						addAuthResultLog(
							userInfo.getYourCallsign(),
							new AuthResultLog(
								userInfo.getYourCallsign(),
								routingService,
								userInfo.getRoutingResult() == RoutingServiceResult.Success ?
									AuthResult.Valid : AuthResult.Invalid
							)
						);
					}
				}
			}

			if(currentAuth.getQueryTimekeeper().isTimeout(3, TimeUnit.SECONDS)) {
				currentAuth.setAuthState(AuthResult.Invalid);

				final String logMessage =
					"Auth query timeout = " +
						String.valueOf(currentAuth.getHeader().getRfHeader().getMyCallsign()) + ".";

				if(log.isWarnEnabled()) {log.warn(logHeader + logMessage);}

				pushLogToWebRemoteControlService(Level.WARN, logMessage);
			}
		}
	}

	private boolean addAuthResultLog(final String callsign, final AuthResultLog authLog) {

		while(authResultLogs.size() > authResultLogLimit) {
			final Optional<AuthResultLog> removeEntry =
				Stream.of(authResultLogs.values())
				.min(ComparatorCompat.comparingLong(new ToLongFunction<AuthResultLog>() {
					@Override
					public long applyAsLong(AuthResultLog t) {
						return t.getAuthTime();
					}
				}));

			if(removeEntry.isPresent())
				authResultLogs.remove(removeEntry.get().getCallsign());
			else
				break;
		}

		final AuthResultLog oldLog = authResultLogs.put(callsign, authLog);
		if(oldLog != null) {
			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Replace auth result log user = " + authLog.getCallsign() +"\n" +
					"    OLD:" + oldLog + "\n    NEW:" + authLog
				);
			}
		}

		return true;
	}

	private void cleanupAuthResultLogs() {
		if(authResultLogsCleanupTimekeeper.isTimeout(2, TimeUnit.MINUTES)) {
			authResultLogsCleanupTimekeeper.updateTimestamp();

			for(final Iterator<AuthResultLog> it = authResultLogs.values().iterator(); it.hasNext();) {
				final AuthResultLog result = it.next();

				if(
					(
						result.getAuthResult() == AuthResult.Valid &&
						(System.currentTimeMillis() - result.getAuthTime()) > TimeUnit.DAYS.toMillis(7)
					) ||
					(
						result.getAuthResult() != AuthResult.Valid &&
						(System.currentTimeMillis() - result.getAuthTime()) > TimeUnit.MINUTES.toMillis(1)
					)
				) {
					it.remove();

					if(log.isDebugEnabled()) {
						log.debug(logHeader + "Cleanup auth result log..." + result);
					}
				}
			}
		}
	}

	private void clearCurrentAuth(final AuthEntry currentAuth) {
		currentAuth.setFrameID(0x0000);
		currentAuth.setAuthState(AuthResult.None);
		currentAuth.setHeader(null);
		currentAuth.setMyCallsign(DSTARDefines.EmptyLongCallsign);
		currentAuth.setRoutingService(null);
		currentAuth.setQueryID(null);
	}

	private void pushLogToWebRemoteControlService(final Level level, final String message) {
		final WebRemoteControlService service = getWebRemoteControlService();
		if(service == null) {return;}

		service.pushLog(level, message);
	}

	private void processReflectorAnnounceTransmitters() {
		for(
			final Map.Entry<DSTARRepeater, RateAdjuster<ReflectorAnnounceTransmitterPacket>> e :
			reflectorAnnounceTransmitters.entrySet()
		) {
			final RateAdjuster<ReflectorAnnounceTransmitterPacket> transmitter = e.getValue();

			Optional<ReflectorAnnounceTransmitterPacket> opPacket = null;
			while((opPacket = transmitter.readDvPacket()).isPresent()) {
				final ReflectorAnnounceTransmitterPacket packet = opPacket.get();

				writePacketToReflectorRoute(packet.getTargetRepeater(), packet.getDirection(), packet.getPacket());
			}
		}

	}

	private void announceAuthFailedToReflectorIncoming(
		final DSTARRepeater repeater,
		final String targetCallsign
	) {

		RateAdjuster<ReflectorAnnounceTransmitterPacket> transmitter =
			reflectorAnnounceTransmitters.get(repeater);
		if(transmitter == null) {
			transmitter = new RateAdjuster<>();

			reflectorAnnounceTransmitters.put(repeater, transmitter);
		}

		final VoiceCharactors charactor =
			announceVoiceCharactor != null ? announceVoiceCharactor : VoiceCharactors.Unknown;

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopBlockID = DSTARUtils.generateLoopBlockID();

		final Header header = new Header(
			DSTARDefines.CQCQCQ.toCharArray(),
			repeater.getRepeaterCallsign().toCharArray(),
			repeater.getRepeaterCallsign().toCharArray(),
			repeater.getRepeaterCallsign().toCharArray(),
			"AUTH".toCharArray()
		);

		final DSTARPacket headerPacket = new InternalPacket(
			loopBlockID, ConnectionDirectionType.Unknown,
			new DVPacket(
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID),
				header
			)
		);

		transmitter.writePacket(
			new ReflectorAnnounceTransmitterPacket(
				repeater, targetCallsign, ConnectionDirectionType.INCOMING,
				PacketType.Header, headerPacket, FrameSequenceType.Start
			)
		);

		final ByteBuffer buffer =	// 10秒分の領域を確保する
			ByteBuffer.allocate(DSTARDefines.DvFrameLength * (10000 / DSTARDefines.DvFrameIntervalTimeMillis));

		//音声作成
		//開始無音部
		for(int count = 0; count < 3; count++)
			if(buffer.hasRemaining()) {DvVoiceTool.generateVoiceByFilename(charactor, " ", buffer);}

		//ピー、認証エラー
		DvVoiceTool.generateVoiceByFilename(charactor, "AuthError", buffer);

		//音声パケット作成
		BufferState.toREAD(buffer, BufferState.WRITE);

		final NewDataSegmentEncoder encoder = new NewDataSegmentEncoder();
		encoder.setShortMessage(targetCallsign + " AUTH FAIL !");
		encoder.setEnableShortMessage(true);
		encoder.setEnableEncode(true);

		final Queue<DVPacket> voicePackets =
			DvVoiceTool.generateVoicePacketFromBuffer(
				buffer, frameID, encoder
			);

		for(final Iterator<DVPacket> it = voicePackets.iterator(); it.hasNext();) {
			final DVPacket voicePacket = it.next();

			voicePacket.getBackBone().setFrameIDNumber(frameID);

			transmitter.writePacket(
				new ReflectorAnnounceTransmitterPacket(
					repeater, targetCallsign, ConnectionDirectionType.INCOMING,
					PacketType.Voice,
					new InternalPacket(
						loopBlockID, ConnectionDirectionType.Unknown, voicePacket
					),
					it.hasNext() ? FrameSequenceType.None : FrameSequenceType.End
				)
			);
		}
	}
}


