package org.jp.illg.dstar.gateway;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.g123.G123CommunicationService;
import org.jp.illg.dstar.gateway.define.QueryRequestSource;
import org.jp.illg.dstar.gateway.model.RoutingQueryTask;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManagerImpl;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.GlobalIPInfo;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.HeardEntry;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationServiceManager;
import org.jp.illg.dstar.reflector.model.ReflectorCallbackListener;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.routing.RoutingServiceManager;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.routing.model.PositionUpdateInfo;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.RoutingCompletedTaskInfo;
import org.jp.illg.dstar.routing.model.RoutingInfo;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.service.reflectorname.ReflectorNameService;
import org.jp.illg.dstar.service.remotecontrol.RemoteControlService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.model.GatewayStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.io.FileSource;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.Callback;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARGatewayImpl extends ThreadBase
	implements DSTARGateway, ReflectorCallbackListener, WebRemoteControlGatewayHandler {

	private static final int heardEntriesLimit = 100;

	private static enum GatewayProcessStates {
		Initialize,
		Processing,
		;
	}

	private static final String logHeader = DSTARGatewayImpl.class.getSimpleName() + " : ";

	private String gatewayCallsign;

	@Getter
	private final ExecutorService workerExecutor;

	@Getter
	private final SocketIO socketIO;

	@Getter
	private final UUID systemID;

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

	@Setter(AccessLevel.PROTECTED)
	@Getter(AccessLevel.PROTECTED)
	private GlobalIPInfo gatewayGlobalAddress;

	@Setter
	@Getter
	private String lastHeardCallsign;

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
	private final ReflectorNameService reflectorNameService;

	@Getter
	private final RepeaterNameService repeaterNameService;

	private final Timer gatewayGlobalAddressProcessIntervalTimekeeper;

	private WebRemoteControlService webRemoteControlService;

	private final Queue<HeardEntry> heardEntries;

	private GatewayProcessStates processState;

	private final G123CommunicationService g2CommunicationService;

	@Getter
	private final RemoteControlService remoteControlService;
	private boolean remoteControlServiceEnable;

	@Getter
	private final ReflectorLinkManager reflectorLinkManager;

	@Setter
	@Getter
	private boolean disableWakeupAnnounce;
	private static final boolean disableWakeupAnnounceDefault = false;

	private final ApplicationInformation<?> applicationVersion;

	private final Queue<RoutingQueryTask> routingTasks;
	private final Queue<RoutingQueryTask> routingRequestTasks;
	private final Lock routingTasksLocker;
	private final Timer routingProcessIntervalTimer;

	private final TaskQueue<DSTARPacket, Boolean> reflectorWriteQueue;

	private final DSTARGatewayHelper helper;

	@Getter
	private final EventListener<ReflectorCommunicationServiceEvent> reflectorEventListener =
		new EventListener<ReflectorCommunicationServiceEvent>() {
			@Override
			public void event(ReflectorCommunicationServiceEvent event, Object attachment) {
				switch(event) {
				case ReceivePacket:
					processReflectors();
					break;

				case ReflectorEventAdded:
					reflectorLinkManager.processReflectorLinkManagement();
					break;
				}
			}
		};

	@Getter
	private final EventListener<DStarRepeaterEvent> onRepeaterEventListener =
		new EventListener<DStarRepeaterEvent>() {
			@Override
			public void event(DStarRepeaterEvent event, Object attachment) {
				helper.processInputPacketFromRepeaters();
			}
		};

	private final EventListener<RoutingServiceEvent> onRoutingServiceEventHandler =
		new EventListener<RoutingServiceEvent>() {
			@Override
			public void event(RoutingServiceEvent event, Object attachment) {
				processRoutingService();
			}
		};

	private final Callback<List<ReflectorHostInfo>> onReflectorHostChangeEventListener =
		new Callback<List<ReflectorHostInfo>>() {
			@Override
			public void call(List<ReflectorHostInfo> data) {
				notifyReflectorHostChanged(data);
			}
		};

	private DSTARGatewayImpl(
		final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> gatewayClass,
		final String gatewayCallsign,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		final ApplicationInformation<?> applicationVersion,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) {
		super(exceptionListener, gatewayClass.getSimpleName());

		this.systemID = systemID;
		this.applicationVersion = applicationVersion;

		if (!CallSignValidator.isValidJARLRepeaterCallsign(gatewayCallsign))
			setGatewayCallsign(gatewayCallsign);
		else
			throw new IllegalArgumentException("DO NOT USE JARL REPEATER CALLSIGN!!!");

		setProcessLoopIntervalTime(60, TimeUnit.MILLISECONDS);

		this.workerExecutor = workerExecutor;
		this.socketIO = socketio;

		reflectorNameService.setOnReflectorHostChangeEventListener(onReflectorHostChangeEventListener);
		this.reflectorNameService = reflectorNameService;
		this.repeaterNameService = repeaterNameService;

		setGatewayGlobalAddress(null);
		gatewayGlobalAddressProcessIntervalTimekeeper = new Timer();

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

		setWebRemoteControlService(null);

		setLastHeardCallsign(DSTARDefines.EmptyLongCallsign);

		heardEntries = new LinkedList<>();

		processState = GatewayProcessStates.Initialize;

		g2CommunicationService = new G123CommunicationService(
			this, workerExecutor, this, this.socketIO
		);
		remoteControlService = new RemoteControlService(
			getSystemID(), this, this, this.socketIO
		);
		reflectorLinkManager = new ReflectorLinkManagerImpl(
			getSystemID(), this, workerExecutor, reflectorNameService
		);

		routingTasks = new LinkedList<RoutingQueryTask>();
		routingRequestTasks = new LinkedList<RoutingQueryTask>();
		routingTasksLocker = new ReentrantLock();
		routingProcessIntervalTimer = new Timer();
		routingProcessIntervalTimer.updateTimestamp();

		reflectorWriteQueue = new TaskQueue<>(workerExecutor);

		helper = new DSTARGatewayHelper(this);

		setDisableWakeupAnnounce(disableWakeupAnnounceDefault);
	}

	protected DSTARGatewayImpl(
		final UUID systemID,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final String gatewayCallsign,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		@NonNull ApplicationInformation<?> applicationVersion,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) throws IllegalStateException {
		this(
			systemID,
			exceptionListener,
			DSTARGatewayImpl.class,
			gatewayCallsign,
			workerExecutor,
			socketio,
			applicationVersion,
			reflectorNameService,
			repeaterNameService
		);
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
	public boolean setProperties(GatewayProperties properties) {
		if (properties == null) {
			return false;
		}

		boolean isSuccess = true;

		// Create routing services
		for (RoutingServiceProperties routingServiceProperties : properties.getRoutingServices().values()) {
			if (!routingServiceProperties.isEnable()) {
				continue;
			}

			if (!createRoutingService(routingServiceProperties)) {
				isSuccess = false;
			}
		}

		// Create reflector communication service
		for (ReflectorProperties reflectorProperties : properties.getReflectors().values()) {
			if (!reflectorProperties.isEnable()) {
				continue;
			}

			if (!createReflectorProtocolProcessor(reflectorProperties)) {
				isSuccess = false;
			}
		}

		// Read reflector hosts file
		if (properties.getHostFileOutputPath() != null &&
			!"".equals(properties.getHostFileOutputPath())) {
			reflectorNameService.setOutputFilePath(properties.getHostFileOutputPath());
		}

		if (log.isInfoEnabled()) {
			log.info(logHeader + "Reading saved hosts file..." + properties.getHostFileOutputPath());
		}
		loadHostsFile(properties.getHostFileOutputPath(), FileSource.StandardFileSystem, false, true);

		if (log.isInfoEnabled()) {
			log.info(logHeader + "Reading user's base hosts file..." + properties.getHostsFile());
		}
		if (!loadHostsFile(
			properties.getHostsFile(),
			SystemUtil.IS_Android ? FileSource.AndroidAssets : FileSource.StandardFileSystem, true, false
		)) {
			isSuccess = false;
		}

		setUseProxy(
			RoutingServiceManager.isEnableService(getSystemID(), RoutingServiceTypes.JapanTrust) &&
				properties.isUseProxyGateway());
		setProxyServerAddress(properties.getProxyGatewayAddress());
		setProxyServerPort(properties.getProxyPort());

		g2CommunicationService.setPortNumber(properties.getPort());
		//XXX プロトコルバージョンは将来用で現状ではデフォルト値とする
		//		g2Protocol.setProtocolVersion(properties.getG2protocolVersion());
		g2CommunicationService.setUseProxyGateway(isUseProxy());
		g2CommunicationService.setProxyGatewayAddress(getProxyServerAddress());

		remoteControlServiceEnable = properties.getRemoteControlService().isEnable();
		remoteControlService.setPortNumber(properties.getRemoteControlService().getPort());
		remoteControlService.setConnectPassword(properties.getRemoteControlService().getPassword());

		reflectorLinkManager.setProperties(properties.getReflectorLinkManager());

		helper.setDisableHeardAtReflector(properties.isDisableHeardAtReflector());
		helper.setAutoReplaceCQFromReflectorLinkCommand(properties.isAutoReplaceCQFromReflectorLinkCommand());

		VoiceCharactors chara = VoiceCharactors.getTypeByCharactorName(properties.getAnnounceVoice());
		if (chara == null || chara == VoiceCharactors.Unknown) {
			chara = VoiceCharactors.KizunaAkari;
		}
		helper.setAnnounceCharactor(chara);

		setDisableWakeupAnnounce(properties.isDisableWakeupAnnounce());

		final AccessScope scope = AccessScope.getTypeByTypeNameIgnoreCase(properties.getScope());
		setScope(scope);
		setLatitude(properties.getLatitude());
		setLongitude(properties.getLongitude());
		setDescription1(properties.getDescription1());
		setDescription2(properties.getDescription2());
		setUrl(properties.getUrl());
		setName(properties.getName());
		setLocation(properties.getLocation());
		setDashboardUrl(properties.getDashboardUrl());

		return isSuccess;
	}

	@Override
	public GatewayProperties getProperties(GatewayProperties properties) {
		return properties;
	}

	@Override
	public boolean start() {
		if (getWebRemoteControlService() != null) {
			if (!getWebRemoteControlService().initializeGatewayHandler(this)) {
				if (log.isErrorEnabled())
					log.error(logHeader + "Failed to initialize web remote control service(gateway).");

				return false;
			}

			for (final RoutingService routingService : getRoutingServiceAll()) {
				if (!routingService.initializeWebRemoteControl(getWebRemoteControlService())) {
					if (log.isErrorEnabled())
						log.error(logHeader + "Failed to initialize web remote service("
							+ routingService.getServiceType() + ")");

					return false;
				}
			}

			for (final ReflectorCommunicationService reflectorService : getReflectorCommunicationServiceAll()) {
				if (!reflectorService.initializeWebRemoteControl(getWebRemoteControlService())) {
					if (log.isErrorEnabled())
						log.error(logHeader + "Failed to initialize web remote service("
							+ reflectorService.getProcessorType() + ")");

					return false;
				}
			}
		}

		if (!super.start()) {
			this.stop();

			return false;
		}

		return true;
	}

	@Override
	public void stop() {
		super.stop();
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	public ThreadProcessResult process() {
		ThreadProcessResult result = ThreadProcessResult.NoErrors;

//		reflectorNameService.process();

		gatewayGlobalAddressProcess();


		boolean reProcess;
		do {
			reProcess = false;

			switch (this.processState) {
			case Initialize:
				result = onStateInitialize();
				break;

			case Processing:
				result = onStateProcessing();
				break;
			}
		} while (reProcess && result == ThreadProcessResult.NoErrors);

		return result;
	}

	@Override
	protected void threadFinalize() {
		gatewayFinalize();

		removeReflectorCommunicationServiceAll();
		removeRoutingServiceAll();

		RoutingServiceManager.finalizeSystem(getSystemID());
		ReflectorCommunicationServiceManager.finalizeManager();
	}

	@Override
	public boolean linkReflector(
		DSTARRepeater repeater, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		if (repeater == null || reflectorHostInfo == null) {
			return false;
		}

		if (!repeater.isReflectorLinkSupport()) {
			if (log.isInfoEnabled()) {
				log.info(
					"Could not link to reflector from reflector link non supported repeater. REF=" +
						reflectorHostInfo.getReflectorCallsign() + "/RPT=" + repeater.getRepeaterCallsign());
			}

			return false;
		}

		return reflectorLinkManager.linkReflector(repeater, reflectorCallsign, reflectorHostInfo);
	}

	@Override
	public void unlinkReflector(DSTARRepeater repeater) {
		if (repeater == null || !repeater.isReflectorLinkSupport()) {
			return;
		}

		reflectorLinkManager.unlinkReflector(repeater);
	}

	@Override
	public boolean isReflectorLinked(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir) {
		return getReflectorLinkManager().isReflectorLinked(repeater, dir);
	}

	@Override
	public List<String> getLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir) {
		return getReflectorLinkManager().getLinkedReflectorCallsign(repeater, dir);
	}

	@Override
	public String getOutgoingLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater) {
		final List<String> callsigns = getLinkedReflectorCallsign(repeater, ConnectionDirectionType.OUTGOING);

		if (callsigns == null || callsigns.isEmpty()) {
			return DSTARDefines.EmptyLongCallsign;
		}

		return callsigns.get(0);
	}

	@Override
	public List<String> getIncommingLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater) {
		return getLinkedReflectorCallsign(repeater, ConnectionDirectionType.INCOMING);
	}

	@Override
	public boolean changeRoutingService(DSTARRepeater repeater, RoutingServiceTypes routingServiceType) {
		if (repeater == null || routingServiceType == null || routingServiceType == RoutingServiceTypes.Unknown)
			return false;

		RoutingService routingService = getRoutingService(routingServiceType);
		if (routingService == null) {
			if (log.isWarnEnabled())
				log.warn("RoutingService " + routingServiceType + " is not activated.");

			return false;
		}

		return RoutingServiceManager.changeRoutingService(repeater, routingService);
	}

	@Override
	public boolean writePacketToG123Route(DSTARPacket packet, InetAddress destinationAddress) {
		return this.g2CommunicationService.writePacket(packet, destinationAddress);
	}

	@Override
	public boolean writePacketToReflectorRoute(
		final DSTARRepeater repeater, final ConnectionDirectionType direction, final DSTARPacket packet
	) {
		final boolean isAllowRepeaterIncoming = getReflectorLinkManager()
			.isAllowReflectorIncomingConnectionWithLocalRepeater(repeater.getRepeaterCallsign());
		//Incoming向けのパケットで送信元のレピータが許可していない場合には、送信をブロックする
		if (direction == ConnectionDirectionType.INCOMING && !isAllowRepeaterIncoming) {
			return true;
		}

		//Incoming/Outgoing向けのパケットで送信元のレピータが許可していない場合には、Outgoingのみに切り替える
		final ConnectionDirectionType targetDirection = direction == ConnectionDirectionType.BIDIRECTIONAL
			&& !isAllowRepeaterIncoming ? ConnectionDirectionType.OUTGOING : direction;

		final List<ReflectorCommunicationService> reflectors = getReflectorCommunicationServiceAll();

		for (final ReflectorCommunicationService processor : reflectors) {
			reflectorWriteQueue.addEventQueue(new Consumer<DSTARPacket>() {
				@Override
				public void accept(DSTARPacket packet) {
					processor.writePacket(repeater, packet.clone(), targetDirection);
				}
			}, packet, getExceptionListener());
		}

		if (
			packet.getBackBoneHeader().getFrameType() == BackBoneHeaderFrameType.VoiceDataHeader ||
			(
				packet.getBackBoneHeader().getFrameType() == BackBoneHeaderFrameType.VoiceData &&
				packet.getBackBoneHeader().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
			)
		) {
			getReflectorLinkManager().notifyUseReflector(repeater, direction);
		}

		return true;
	}

	@Override
	public UUID findRepeater(
		@NonNull final DSTARRepeater repeater,
		@NonNull final String repeaterCall, final Header header) {
		final RoutingService routingService = repeater.getRoutingService();
		if (routingService == null) {
			if (log.isErrorEnabled())
				log.error("Routing service for repeater " + repeater.getRepeaterCallsign() + " is unavailable.");

			return null;
		}

		return findRepeater(
			routingService, QueryRequestSource.Repeater, repeaterCall, repeater, header, null
		);
	}

	@Override
	public UUID findRepeater(
		@NonNull final RoutingServiceTypes routingServiceType,
		@NonNull final Callback<RoutingInfo> callback,
		@NonNull final String queryRepeaterCallsign) {
		final RoutingService routingService = getRoutingService(routingServiceType);
		if (routingService == null) {
			if (log.isErrorEnabled())
				log.error("Routing service " + routingServiceType + " is unavailable.");

			return null;
		}

		return findRepeater(
			routingService, QueryRequestSource.Callback, queryRepeaterCallsign, null, null, callback
		);
	}

	@Override
	public UUID findUser(
		@NonNull final DSTARRepeater repeater,
		@NonNull final String userCall,
		final Header header) {
		final RoutingService routingService = repeater.getRoutingService();
		if (routingService == null) {
			if (log.isErrorEnabled())
				log.error("Routing service for repeater " + repeater.getRepeaterCallsign() + " is unavailable.");

			return null;
		}

		return findUser(
			routingService, QueryRequestSource.Repeater, userCall, repeater, header, null
		);
	}

	@Override
	public UUID findUser(
		@NonNull final RoutingServiceTypes routingServiceType,
		@NonNull final Callback<RoutingInfo> callback,
		@NonNull final String queryUserCallsign) {
		final RoutingService routingService = getRoutingService(routingServiceType);
		if (routingService == null) {
			if (log.isErrorEnabled())
				log.error("Routing service " + routingServiceType + " is unavailable.");

			return null;
		}

		return findUser(
			routingService, QueryRequestSource.Callback, queryUserCallsign, null, null, callback);
	}

	@Override
	public UUID positionUpdate(
		@NonNull final DSTARRepeater repeater,
		final int frameID,
		@NonNull final String myCall, @NonNull final String myCallExt,
		@NonNull final String yourCall,
		@NonNull final String repeater1, @NonNull final String repeater2,
		final byte flag1, final byte flag2, final byte flag3) {
		final RoutingService routingService = repeater.getRoutingService();
		if (routingService == null) {
			if (log.isErrorEnabled())
				log.error("Routing service for repeater " + repeater.getRepeaterCallsign() + " is unavailable.");

			return null;
		}

		final UUID queryId = routingService.positionUpdate(
			frameID,
			myCall, myCallExt, yourCall, repeater1, repeater2, flag1, flag2, flag3);
		if (queryId == null) {
			if (log.isErrorEnabled()) {
				log.error(
					"Routing service " +
						routingService.getServiceType() + " is unavailable, query id is not returned.");
			}

			return null;
		}

		final RoutingQueryTask task = new RoutingQueryTask(queryId, routingService, repeater);

		routingTasksLocker.lock();
		try {
			if (!routingRequestTasks.add(task)) {
				if (log.isErrorEnabled()) {
					log.error("Could not add routing service task.");
				}

				return null;
			}
		} finally {
			routingTasksLocker.unlock();
		}

		return queryId;
	}

	@Override
	public void kickWatchdogFromRepeater(final String repeaterCallsign, final String statusMessage) {
		final DSTARRepeater repeater = getRepeater(repeaterCallsign);
		if (repeater == null) {
			if (log.isErrorEnabled())
				log.error("Failed kick watchdog, Cound not found repeater = " + repeaterCallsign + ".");

			return;
		}

		final String message = applicationVersion.getRunningOperatingSystem().toLowerCase(Locale.ENGLISH) + "_" +
			(statusMessage != null ? (statusMessage.toLowerCase(Locale.ENGLISH) + "_") : "") +
			getApplicationName().toLowerCase(Locale.ENGLISH) + "-v" + getApplicationVersion();

		final RoutingService routingService = repeater.getRoutingService();

		if (routingService == null) {
			if (log.isTraceEnabled())
				log.trace("Failed kick watchdog, Routing service has not been set Repeater " + repeaterCallsign + ".");

			return;
		}

		routingService.kickWatchdog(repeaterCallsign, message);
	}

	@Override
	public void notifyLinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo) {
		if (repeaterCallsign == null || reflectorCallsign == null) {
			return;
		}

		helper.notifyLinkReflector(repeaterCallsign, reflectorCallsign);
	}

	@Override
	public void notifyUnlinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo) {
		if (repeaterCallsign == null || reflectorCallsign == null) {
			return;
		}

		helper.notifyUnlinkReflector(repeaterCallsign, reflectorCallsign);
	}

	@Override
	public void notifyLinkFailedReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo) {
		if (repeaterCallsign == null || reflectorCallsign == null) {
			return;
		}

		helper.notifyLinkFailedReflector(repeaterCallsign, reflectorCallsign);
	}

	@Override
	public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {
		if (super.getExceptionListener() != null)
			super.getExceptionListener().threadUncaughtExceptionEvent(ex, thread);
	}

	@Override
	public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
		if (super.getExceptionListener() != null)
			super.getExceptionListener().threadFatalApplicationErrorEvent(message, ex, thread);
	}

	@Override
	public List<String> getRouterStatus() {
		return helper.getRouterStatus();
	}

	@Override
	public GatewayStatusReport getGatewayStatusReport() {
		return helper.getGatewayStatusReport();
	}

	@Override
	public List<ReflectorStatusReport> getReflectorStatusReport() {
		return Stream.of(getReflectorCommunicationServiceAll())
			.map(new Function<ReflectorCommunicationService, ReflectorStatusReport>() {
				@Override
				public ReflectorStatusReport apply(ReflectorCommunicationService service) {
					return service.getStatusReport();
				}
			}).toList();
	}

	@Override
	public List<RoutingServiceStatusReport> getRoutingStatusReport() {
		return Stream.of(getRoutingServiceAll())
			.map(new Function<RoutingService, RoutingServiceStatusReport>() {
				@Override
				public RoutingServiceStatusReport apply(RoutingService service) {
					return service.getRoutingServiceStatusReport();
				}
			}).toList();
	}

	@Override
	public void wakeupGatewayWorker() {
		super.wakeupProcessThread();
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull final DSTARRepeater localRepeater, @NonNull final String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull final List<ReflectorRemoteUserEntry> users) {
		if (localRepeater.isRunning()) {
			getWorkerExecutor().submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					localRepeater.notifyReflectorLoginUsers(
						reflectorType,
						protocol,
						remoteCallsign, connectionDir, users);
				}
			});
		}
	}

	@Override
	public DSTARRepeater getRepeater(String repeaterCallsign) {
		if (repeaterCallsign == null || "".equals(repeaterCallsign)) {
			return null;
		}

		return DSTARRepeaterManager.getRepeater(getSystemID(), repeaterCallsign);
	}

	@Override
	public List<DSTARRepeater> getRepeaters() {
		return DSTARRepeaterManager.getRepeaters(getSystemID());
	}

	@Override
	public DSTARGateway getGateway() {
		return this;
	}

	@Override
	public String getGatewayCallsign() {
		return this.gatewayCallsign;
	}

	@Override
	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(DSTARProtocol reflectorProtocol) {
		return ReflectorCommunicationServiceManager.getService(getSystemID(), reflectorProtocol);
	}

	@Override
	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(String reflectorCallsign) {
		if (reflectorCallsign == null) {
			return Optional.empty();
		}

		final String reflectorCallsignFormated = DSTARUtils.formatFullLengthCallsign(reflectorCallsign);
		/*
			String reflectorCall =
				DStarUtils.formatFullLengthCallsign(
					reflectorCallsign.substring(0, DStarDefines.CallsignFullLength - 1)
				);
		*/
		Optional<ReflectorHostInfo> opHostInfo = reflectorNameService
			.findHostByReflectorCallsign(reflectorCallsignFormated);
		if (!opHostInfo.isPresent()) {
			return Optional.empty();
		}

		ReflectorHostInfo hostInfo = opHostInfo.get();

		return getReflectorCommunicationService(hostInfo.getReflectorProtocol());
	}

	@Override
	public List<ReflectorCommunicationService> getReflectorCommunicationServiceAll() {
		return ReflectorCommunicationServiceManager.getServices(getSystemID());
	}

	@Override
	public RoutingService getRoutingService(RoutingServiceTypes routingServiceType) {
		return RoutingServiceManager.getService(getSystemID(), routingServiceType);
	}

	@Override
	public List<RoutingService> getRoutingServiceAll() {
		return RoutingServiceManager.getServices(getSystemID());
	}

	@Override
	public void setWebRemoteControlService(final WebRemoteControlService webRemoteControlService) {
		this.webRemoteControlService = webRemoteControlService;
	}

	@Override
	public WebRemoteControlService getWebRemoteControlService() {
		return webRemoteControlService;
	}

	@Override
	public Optional<InetAddress> findReflectorAddressByCallsign(String reflectorCallsign) {
		if (reflectorCallsign == null ||
			(!CallSignValidator.isValidReflectorCallsign(reflectorCallsign))) {
			return Optional.empty();
		}

		Optional<ReflectorHostInfo> hostInfo = findReflectorByCallsign(reflectorCallsign);

		InetAddress reflectorAddress = null;

		if (hostInfo.isPresent()) {
			try {
				reflectorAddress = InetAddress.getByName(hostInfo.get().getReflectorAddress());
			} catch (UnknownHostException ex) {
				if (log.isWarnEnabled()) {
					log.warn(
						"Could not resolve reflector address. " +
							hostInfo.get().getReflectorCallsign() + "," +
							hostInfo.get().getReflectorAddress() + ".");
				}
			}
		} else {
			if (log.isWarnEnabled()) {
				log.warn(
					"Could not find reflector " +
						reflectorCallsign + ".");
			}
		}

		return Optional.ofNullable(reflectorAddress);
	}

	@Override
	public Optional<ReflectorHostInfo> findReflectorByCallsign(String reflectorCallsign) {
		if (!CallSignValidator.isValidReflectorCallsign(reflectorCallsign)) {
			return Optional.empty();
		}

		final Optional<ReflectorHostInfo> hostInfo = reflectorNameService
			.findHostByReflectorCallsign(reflectorCallsign);

		return hostInfo;
	}

	@Override
	public List<ReflectorHostInfo> findReflectorByFullText(final String queryText, final int resultSizeLimit) {
		return reflectorNameService.findHostByFullText(queryText, resultSizeLimit);
	}

	@Override
	public boolean loadReflectorHosts(String filePath, boolean rewriteDataSource) {
		if (filePath == null || "".equals(filePath)) {
			return false;
		}

		return reflectorNameService.loadHosts(filePath, rewriteDataSource, false);
	}

	@Override
	public boolean loadReflectorHosts(URL url, boolean rewriteDataSource) {
		if (url == null) {
			return false;
		}

		return reflectorNameService.loadHosts(url, rewriteDataSource, false);
	}

	@Override
	public boolean loadReflectorHosts(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts,
		final String dataSource,
		final boolean deleteSameDataSource) {
		if (readHosts == null) {
			return false;
		}

		return reflectorNameService.loadHosts(readHosts, dataSource, deleteSameDataSource, false);
	}

	@Override
	public boolean saveReflectorHosts(String filePath) {
		if (filePath == null || "".equals(filePath)) {
			return false;
		}

		return reflectorNameService.saveHosts(filePath);
	}

	@Override
	public Optional<InetAddress> getGatewayGlobalIP() {
		if (getGatewayGlobalAddress() != null)
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
		final HeardEntry entry = new HeardEntry(
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

		synchronized (heardEntries) {
			for (Iterator<HeardEntry> it = heardEntries.iterator(); it.hasNext();) {
				final HeardEntry e = it.next();
				if (e.getMyCallsignLong().equals(myCallsignLong)) {
					it.remove();
					break;
				}
			}

			while (heardEntries.size() > heardEntriesLimit) {
				heardEntries.poll();
			}

			result = heardEntries.add(entry);
		}

		if (getWebRemoteControlService() != null)
			getWebRemoteControlService().notifyGatewayUpdateHeard(entry);

		return result;
	}

	@Override
	public List<HeardEntry> getHeardEntries() {
		synchronized (heardEntries) {
			return new ArrayList<>(heardEntries);
		}
	}

	@Override
	/**
	 *
	 */
	public void notifyIncomingPacketFromG123Route(
		@NonNull String myCallsign,
		@NonNull InetAddress gatewayAddress) {
		for (RoutingService service : getRoutingServiceAll()) {
			service.updateCache(myCallsign, gatewayAddress);
		}
	}

	@Override
	public void notifyReflectorHostChanged(
		List<ReflectorHostInfo> hosts) {
		if (webRemoteControlService != null)
			webRemoteControlService.notifyGatewayReflectorHostsUpdated(hosts);
	}

	@Override
	public List<HeardEntry> requestHeardLog() {
		synchronized (heardEntries) {
			return new LinkedList<HeardEntry>(heardEntries);
		}
	}

	@Override
	public List<ReflectorHostInfo> getReflectorHosts() {
		return reflectorNameService.getHosts();
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGatewayCallsign());
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
	public Class<? extends GatewayStatusData> getStatusDataType() {
		return GatewayStatusData.class;
	}

	/**
	 * データを送受信しているかを取得する
	 *
	 * @return データ送受信中であればtrue
	 */
	@Override
	public boolean isDataTransferring() {
		return helper.isDataTransferring();
	}

	protected boolean removeRoutingServiceAll() {
		return removeRoutingServiceAll(true);
	}

	protected boolean removeRoutingServiceAll(boolean stopRoutingService) {
		return RoutingServiceManager.removeServices(getSystemID(), stopRoutingService);
	}

	protected boolean removeReflectorCommunicationServiceAll() {
		return removeReflectorCommunicationServiceAll(true);
	}

	protected boolean removeReflectorCommunicationServiceAll(boolean stopReflectorProtocolProcessor) {
		return ReflectorCommunicationServiceManager.removeServices(systemID, stopReflectorProtocolProcessor);
	}

	protected Optional<RoutingService> getRepeaterRoutingService(String repeaterCallsign) {
		assert repeaterCallsign != null;

		final DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(getSystemID(), repeaterCallsign);
		if (repeater != null) {
			if (repeater.getRoutingService() != null) {
				return Optional.of(repeater.getRoutingService());
			}
			else {
				final List<RoutingService> routingServices = getRoutingServiceAll();
				if (!routingServices.isEmpty()) {
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

	private ThreadProcessResult onStateInitialize() {
		routingTasksLocker.lock();
		try {
			routingTasks.clear();
			routingRequestTasks.clear();
		}finally {
			routingTasksLocker.unlock();
		}

		boolean startSuccess = true;
		if (!g2CommunicationService.start()) {
			startSuccess = false;
			if (log.isWarnEnabled())
				log.warn("Could not start G2 service.");
		}

		for (RoutingService routingService : getRoutingServiceAll()) {
			threadSleep(500);

			if (!routingService.start()) {
				startSuccess = false;

				if (log.isWarnEnabled())
					log.warn("Could not start routing service, name="
						+ routingService.getServiceType().getTypeName() + ".");
			}
		}

		for (ReflectorCommunicationService reflector : getReflectorCommunicationServiceAll()) {
			threadSleep(500);

			if (!reflector.start()) {
				startSuccess = false;

				if (log.isWarnEnabled())
					log.warn("Could not start reflector service, name="
						+ reflector.getProcessorType().getTypeName() + ".");
			}
		}

		if (remoteControlServiceEnable && !remoteControlService.start()) {
			startSuccess = false;

			if (log.isWarnEnabled())
				log.warn("Could not start remote control service");
		}

		if (startSuccess) {
			this.processState = GatewayProcessStates.Processing;

			if (!isDisableWakeupAnnounce()) {helper.announceWakeup();}
		} else {
			return super.threadFatalError("Gateway startup process failed.", null);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateProcessing() {
		if (routingProcessIntervalTimer.isTimeout(100, TimeUnit.MILLISECONDS)) {
			routingProcessIntervalTimer.updateTimestamp();

			processRoutingService();
		}

		processG2();

		helper.processInputPacketFromRepeaters();

		processReflectors();

		processAnnounce();

		reflectorLinkManager.processReflectorLinkManagement();

		helper.processHelper();

		return ThreadProcessResult.NoErrors;
	}

	private boolean createRoutingService(RoutingServiceProperties routingServiceProperties) {
		return RoutingServiceManager.createService(
			getSystemID(),
			this, getWorkerExecutor(), routingServiceProperties,
			applicationVersion,
			onRoutingServiceEventHandler,
			getSocketIO()
		) != null;
	}

	private boolean createReflectorProtocolProcessor(
		ReflectorProperties reflectorProperties
	) {
		return ReflectorCommunicationServiceManager.createService(
			getSystemID(),
			applicationVersion,
			this, reflectorProperties.getType(), reflectorProperties,
			getWorkerExecutor(),
			getSocketIO(),
			getReflectorLinkManager(),
			reflectorEventListener
		) != null;
	}

	private boolean gatewayGlobalAddressProcess() {
		if (gatewayGlobalAddressProcessIntervalTimekeeper.isTimeout()) {
			gatewayGlobalAddressProcessIntervalTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			gatewayGlobalAddressProcessIntervalTimekeeper.updateTimestamp();

			for (final RoutingService service : getRoutingServiceAll()) {
				service.getGlobalIPAddress()
					.ifPresent(new Consumer<GlobalIPInfo>() {
						@Override
						public void accept(GlobalIPInfo ip) {
							if (getGatewayGlobalAddress() == null ||
								(!getGatewayGlobalAddress().getGlobalIP().equals(ip.getGlobalIP()) &&
									getGatewayGlobalAddress().getCreateTime() < ip.getCreateTime() &&
									!ip.getGlobalIP().isSiteLocalAddress())) {
								if (log.isInfoEnabled()) {
									log.info(
										logHeader +
											"Gateway ip address changed from " + service.getServiceType() +
											" " +
											(getGatewayGlobalAddress() != null ? getGatewayGlobalAddress().getGlobalIP()
												: "NOTHING")
											+ "->" + ip.getGlobalIP() + ".");
								}

								setGatewayGlobalAddress(ip);
							}
						}
					});
			}
		}

		return true;
	}

	private void setGatewayCallsign(String gatewayCallsign) {
		assert DSTARUtils.isValidCallsignFullLength(gatewayCallsign);
		if (!DSTARUtils.isValidCallsignFullLength(gatewayCallsign))
			throw new IllegalArgumentException();

		this.gatewayCallsign = gatewayCallsign;
	}

	private void processRoutingService() {
		routingTasksLocker.lock();
		try {
			for (final Iterator<RoutingQueryTask> it = routingTasks.iterator(); it.hasNext();) {
				final RoutingQueryTask task = it.next();

				if (task.isTimeout(15, TimeUnit.SECONDS)) {
					if (log.isWarnEnabled())
						log.warn("Query timeout = " + task.getRepeater() + "@" + task.getRoutingService());

					it.remove();

					continue;
				}

				final RoutingService routingService = task.getRoutingService();

				final RoutingCompletedTaskInfo compTask = routingService.getServiceTaskCompleted(task.getTaskID());
				if (compTask == null) {
					continue;
				}

				switch (compTask.getServiceTask()) {
				case PositionUpdate:
					final PositionUpdateInfo positionUpdateInfo = routingService
						.getPositionUpdateCompleted(task.getTaskID());

					if (positionUpdateInfo != null) {
						if (task.getRequestSource() == QueryRequestSource.Repeater) {
							helper.completeHeard(task.getTaskID(), positionUpdateInfo.getRoutingResult());
						} else if (task.getRequestSource() == QueryRequestSource.Callback) {
							task.getCallback().setAttachData(null);

							getWorkerExecutor().submit(task.getCallback());
						}

						it.remove();
					}
					break;

				case FindRepeater:
					final RepeaterRoutingInfo repeaterRoutingInfo = routingService.getRepeaterInfo(task.getTaskID());

					if (repeaterRoutingInfo != null) {
						if (task.getRequestSource() == QueryRequestSource.Repeater) {
							helper.completeResolveQueryRepeater(task.getTaskID(), repeaterRoutingInfo);
						} else if (task.getRequestSource() == QueryRequestSource.Callback) {
							task.getCallback().setAttachData(repeaterRoutingInfo);

							getWorkerExecutor().submit(task.getCallback());
						}

						it.remove();
					}
					break;

				case FindUser:
					final UserRoutingInfo userRoutingInfo = routingService.getUserInfo(task.getTaskID());

					if (userRoutingInfo != null) {
						if (task.getRequestSource() == QueryRequestSource.Repeater) {
							helper.completeResolveQueryUser(task.getTaskID(), userRoutingInfo);
						} else if (task.getRequestSource() == QueryRequestSource.Callback) {
							task.getCallback().setAttachData(userRoutingInfo);

							getWorkerExecutor().submit(task.getCallback());
						}

						it.remove();
					}
					break;

				default:
					it.remove();
					break;
				}
			}

			for (final Iterator<RoutingQueryTask> it = routingRequestTasks.iterator(); it.hasNext();) {
				routingTasks.add(it.next());

				it.remove();
			}
		} finally {
			routingTasksLocker.unlock();
		}
	}

	private void processG2() {
		DSTARPacket packet = null;
		while ((packet = this.g2CommunicationService.readPacket()) != null) {
			helper.processInputPacketFromG123(packet);
		}
	}

	private void processReflectors() {
		final List<ReflectorCommunicationService> services =
			ReflectorCommunicationServiceManager.getServices(getSystemID());

		if(services != null && services.size() > 0) {
			for (ReflectorCommunicationService processor : services) {

				List<DSTARRepeater> repeaters = DSTARRepeaterManager.getRepeaters(getSystemID());
				for (DSTARRepeater repeater : repeaters) {
					DSTARPacket packet = null;
					while ((packet = processor.readPacket(repeater)) != null) {
						if(packet.getRFHeader() != null) {
							packet.getRFHeader().setRepeater1Callsign(this.getGatewayCallsign().toCharArray());
							packet.getRFHeader().setRepeater2Callsign(repeater.getRepeaterCallsign().toCharArray());
						}

						helper.processInputPacketFromReflector(packet);
					}
				}
			}
		}
	}

	private void processAnnounce() {
		helper.processAnnounce();
	}

	private UUID findUser(
		@NonNull final RoutingService routingService,
		@NonNull final QueryRequestSource requestSource,
		@NonNull final String queryUserCallsign,
		final DSTARRepeater repeater,
		final Header header,
		final Callback<RoutingInfo> callback) {
		if (!CallSignValidator.isValidUserCallsign(queryUserCallsign)) {
			if (log.isErrorEnabled())
				log.error("Invalid query target callsign " + queryUserCallsign);

			return null;
		} else if (requestSource == QueryRequestSource.Repeater &&
			(repeater == null || header == null)) {
			throw new NullPointerException();
		} else if (requestSource == QueryRequestSource.Callback && callback == null) {
			throw new NullPointerException();
		}

		final UUID queryId = routingService.findUser(
			queryUserCallsign,
			requestSource == QueryRequestSource.Repeater ? header : null);
		if (queryId == null) {
			if (log.isErrorEnabled()) {
				log.error(
					"Routing service " +
						routingService.getServiceType() + " is unavailable, query id is not returned.");
			}

			return null;
		}

		final RoutingQueryTask task = requestSource == QueryRequestSource.Repeater
			? new RoutingQueryTask(queryId, routingService, repeater)
			: new RoutingQueryTask(queryId, routingService, callback);

		routingTasksLocker.lock();
		try {
			if (!routingRequestTasks.add(task)) {
				if (log.isErrorEnabled()) {
					log.error("Could not add routing service task.");
				}

				return null;
			}
		} finally {
			routingTasksLocker.unlock();
		}

		return queryId;
	}

	private UUID findRepeater(
		@NonNull final RoutingService routingService,
		@NonNull final QueryRequestSource requestSource,
		@NonNull final String queryRepeaterCallsign,
		final DSTARRepeater repeater,
		final Header header,
		final Callback<RoutingInfo> callback) {
		if (!CallSignValidator.isValidUserCallsign(queryRepeaterCallsign)) {
			if (log.isErrorEnabled())
				log.error("Unvalid query target callsign " + queryRepeaterCallsign);

			return null;
		} else if (requestSource == QueryRequestSource.Repeater &&
			(repeater == null || header == null)) {
			throw new NullPointerException();
		} else if (requestSource == QueryRequestSource.Callback && callback == null) {
			throw new NullPointerException();
		}

		final UUID queryId = routingService.findRepeater(
			queryRepeaterCallsign,
			requestSource == QueryRequestSource.Repeater ? header : null);
		if (queryId == null) {
			if (log.isErrorEnabled()) {
				log.error(
					"Routing service " +
						routingService.getServiceType() + " is unavailable, query id is not returned.");
			}

			return null;
		}

		final RoutingQueryTask task = requestSource == QueryRequestSource.Repeater
			? new RoutingQueryTask(queryId, routingService, repeater)
			: new RoutingQueryTask(queryId, routingService, callback);

		routingTasksLocker.lock();
		try {
			if (!routingRequestTasks.add(task)) {
				if (log.isErrorEnabled()) {
					log.error("Could not add routing service task.");
				}

				return null;
			}
		} finally {
			routingTasksLocker.unlock();
		}

		return queryId;
	}

	@SuppressWarnings("unused")
	private boolean loadHostsFile(boolean logSuppress) {
		// Read reflector hosts file
		return reflectorNameService.loadHosts(logSuppress);
	}

	private boolean loadHostsFile(
		String filePath, @NonNull FileSource src,
		boolean rewriteDataSource, boolean logSuppress) {
		boolean filePathAvailable = true;
		if (filePath == null || "".equals(filePath))
			filePathAvailable = false;

		// Read reflector hosts file
		if (filePathAvailable) {
			if (SystemUtil.IS_Android && src == FileSource.AndroidAssets)
				return reflectorNameService.loadHostsFromAndroidAssets(filePath, rewriteDataSource, logSuppress);
			else
				return reflectorNameService.loadHosts(filePath, rewriteDataSource, logSuppress);
		} else {
			return reflectorNameService.loadHosts(logSuppress);
		}
	}

	private void gatewayFinalize() {
		routingTasksLocker.lock();
		try {
			this.routingRequestTasks.clear();
			this.routingTasks.clear();
		} finally {
			routingTasksLocker.unlock();
		}

		g2CommunicationService.stop();

		remoteControlService.stop();

		RoutingServiceManager.finalizeSystem(getSystemID());

		ReflectorCommunicationServiceManager.finalizeSystem(getSystemID());
	}
}
