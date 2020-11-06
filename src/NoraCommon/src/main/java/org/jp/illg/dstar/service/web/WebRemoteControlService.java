package org.jp.illg.dstar.service.web;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.protocol.dcs.DCSCommunicationService;
import org.jp.illg.dstar.reflector.protocol.dextra.DExtraCommunicationService;
import org.jp.illg.dstar.reflector.protocol.dplus.DPlusCommunicationService;
import org.jp.illg.dstar.reflector.protocol.jarllink.JARLLinkCommunicationService;
import org.jp.illg.dstar.repeater.echo.EchoAutoReplyRepeater;
import org.jp.illg.dstar.repeater.homeblew.HomeblewRepeater;
import org.jp.illg.dstar.repeater.icom.ExternalICOMRepeater;
import org.jp.illg.dstar.repeater.internal.InternalRepeater;
import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRLoginClient;
import org.jp.illg.dstar.repeater.reflectorecho.ReflectorEchoAutoReplyRepeater;
import org.jp.illg.dstar.repeater.voiceroid.VoiceroidAutoReplyRepeater;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.routing.service.gltrust.GlobalTrustClientService;
import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBRoutingService;
import org.jp.illg.dstar.routing.service.jptrust.JpTrustClientService;
import org.jp.illg.dstar.service.web.func.AccessPointFunctions;
import org.jp.illg.dstar.service.web.func.AnalogModemPiGPIOFunctions;
import org.jp.illg.dstar.service.web.func.ConfigFunctions;
import org.jp.illg.dstar.service.web.func.DCSFunctions;
import org.jp.illg.dstar.service.web.func.DExtraFunctions;
import org.jp.illg.dstar.service.web.func.DPlusFunctions;
import org.jp.illg.dstar.service.web.func.DummyFunctions;
import org.jp.illg.dstar.service.web.func.EchoAutoReplyFunctions;
import org.jp.illg.dstar.service.web.func.ExternalICOMRepeaterFunctions;
import org.jp.illg.dstar.service.web.func.GatewayFunctions;
import org.jp.illg.dstar.service.web.func.GlobalTrustClientServiceFunctions;
import org.jp.illg.dstar.service.web.func.HomeFunctions;
import org.jp.illg.dstar.service.web.func.HomeblewFunctions;
import org.jp.illg.dstar.service.web.func.InternalFunctions;
import org.jp.illg.dstar.service.web.func.IrcDDBRoutingServiceFunctions;
import org.jp.illg.dstar.service.web.func.JARLLinkFunctions;
import org.jp.illg.dstar.service.web.func.JpTrustClientServiceFunctions;
import org.jp.illg.dstar.service.web.func.MMDVMFunctions;
import org.jp.illg.dstar.service.web.func.NewAccessPointFunctions;
import org.jp.illg.dstar.service.web.func.NoraVRFunctions;
import org.jp.illg.dstar.service.web.func.ReflectorEchoAutoReplyFunctions;
import org.jp.illg.dstar.service.web.func.VoiceroidAutoReplyFunctions;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlAccessPointHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlAnalogModemPiGPIOHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDCSHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDExtraHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDPlusHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDummyRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlEchoAutoReplyHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlExternalICOMRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGlobalTrustClientHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlHomeblewHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlInternalRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlIrcDDBRoutingHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlJARLLinkHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlJpTrustClientHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlMMDVMHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlModemHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNewAccessPointHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNoraVRHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorEchoAutoReplyHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRoutingServiceHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlVoiceAutoReplyHandler;
import org.jp.illg.dstar.service.web.model.DashboardInfo;
import org.jp.illg.dstar.service.web.model.EnableDashboard;
import org.jp.illg.dstar.service.web.model.GatewayDashboardInfo;
import org.jp.illg.dstar.service.web.model.LogInfo;
import org.jp.illg.dstar.service.web.model.ModemDashboardInfo;
import org.jp.illg.dstar.service.web.model.ReflectorDashboardInfo;
import org.jp.illg.dstar.service.web.model.RepeaterDashboardInfo;
import org.jp.illg.dstar.service.web.model.RoutingServiceDashboardInfo;
import org.jp.illg.dstar.service.web.model.WebRemoteControlServiceEvent;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.io.websocket.WebSocketServerManager;
import org.jp.illg.util.logback.appender.NotifyAppender;
import org.jp.illg.util.logback.appender.NotifyAppenderListener;
import org.jp.illg.util.logback.appender.NotifyLogEvent;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.slf4j.event.Level;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebRemoteControlService {

	/**
	 * 要求するダッシュボードの最低バージョン
	 */
	private final String requiredDashboardVersion = "0.3.0a";

	/**
	 * アプリケーションログを保持する件数
	 */
	private final int logEntryLimit = 100;

	@Getter
	@Setter
	private BasicStatusInformation statusInformation;

	private final ApplicationInformation<?> applicationVersion;

	private final String logTag;

	private final Lock locker;

	private SocketIOServer server;
	private UUID serverID;

	private final WebSocketServerManager webSocketServerManager;

	private boolean initialized;
	private boolean serverStarted;
	private Throwable serverException;


	private final ExecutorService workerExecutor;
	private final String userListFilePath;

	private final int helperPort;

	private WebRemoteClientManager clientManeger;

	private final Deque<NotifyLogEvent> logs;

	private final Timer statusTransmitIntevalTimekeeper;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private WebRemoteControlGatewayHandler gatewayHandler;
	private final Map<ReflectorProtocolProcessorTypes, WebRemoteControlReflectorHandler> reflectorHandlers;
	private final Map<String, WebRemoteControlRepeaterHandler> repeaterHandlers;
	private final Map<Integer, WebRemoteControlModemHandler> modemHandlers;
	private final Map<RoutingServiceTypes, WebRemoteControlRoutingServiceHandler> routingServiceHandlers;

	private final EventListener<WebRemoteControlServiceEvent> eventListener;

	private final String configurationFilePath;

	private final NotifyAppenderListener logListener =
		new NotifyAppenderListener() {
			@Override
			public void notifyLog(String msg) {
				if(serverID == null) {return;}

				final SocketIOServer server = webSocketServerManager.getServer(serverID);
				if(server != null)
					server.getBroadcastOperations().sendEvent("notify_log", new LogInfo(msg));
			}

			@Override
			public void notifyLogEvent(NotifyLogEvent event) {
				if(serverID == null) {return;}

				pushLog(event);
			}
		};

	private final EventListener<SocketIOClient> onLoginEventListener =
		new EventListener<SocketIOClient>() {
			@Override
			public void event(SocketIOClient client, Object attachment) {
				onLoginEvent(client);
			}
		};

	private final EventListener<SocketIOClient> onLogoutEventListener =
		new EventListener<SocketIOClient>() {
			@Override
			public void event(SocketIOClient client, Object attachment) {
				onLogoutEvent(client);
			}
		};

	private final DashboardEventListener<EnableDashboard> onEnableDashboardListener =
		new DashboardEventListener<EnableDashboard>() {
			@Override
			public void onEvent(
				SocketIOClient client, EnableDashboard data, AckRequest ackSender
			) {
				onEnableDashboardEvent(client, data, ackSender);
			}
		};

	public WebRemoteControlService(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final EventListener<WebRemoteControlServiceEvent> eventListener,
		@NonNull final String userListFilePath,
		@NonNull final String configFilePath,
		final int helperPort
	) {
		super();

		this.exceptionListener = exceptionListener;
		this.applicationVersion = applicationVersion;
		this.workerExecutor = workerExecutor;
		this.eventListener = eventListener;
		this.userListFilePath = userListFilePath;
		this.configurationFilePath = configFilePath;
		this.helperPort = helperPort;

		logTag = WebRemoteControlService.class.getSimpleName() + " : ";

		locker = new ReentrantLock();

		webSocketServerManager = WebSocketServerManager.getInstance();

		initialized = false;
		serverStarted = false;
		serverException = null;
		server = null;
		serverID = null;

		logs = new LinkedList<>();

		statusInformation = null;

		statusTransmitIntevalTimekeeper = new Timer(10, TimeUnit.SECONDS);

		gatewayHandler = null;
		reflectorHandlers = new ConcurrentHashMap<>(8);
		repeaterHandlers = new ConcurrentHashMap<>(32);
		modemHandlers = new ConcurrentHashMap<>(64);
		routingServiceHandlers = new ConcurrentHashMap<>(4);
	}

	public boolean initialize(
		final int port,
		@NonNull final String context
	) {
		locker.lock();
		try {
			if(!initialized) {

				serverID = createWebSocketServer(port, context);
				if(serverID == null) {
					if(log.isErrorEnabled())
						log.error(logTag + "Failed initialize web remote service, Could not create server.");

					return false;
				}

				server = webSocketServerManager.getServer(serverID);
				if(server == null) {
					if(log.isErrorEnabled())
						log.error(logTag + "Failed initialize web remote service, Could not get server.");

					webSocketServerManager.removeServer(serverID);
					serverID = null;

					return false;
				}

				clientManeger = new WebRemoteClientManager(
					exceptionListener,
					applicationVersion, workerExecutor, server, userListFilePath,
					onLoginEventListener, onLogoutEventListener
				);

				server.addEventListener(
					"request_dashboardinfo",
					Object.class,
					new DashboardEventListenerBuilder<>(
						WebRemoteControlService.class, "request_dashboardinfo",
						new DashboardEventListener<Object>() {
							@Override
							public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
								client.sendEvent("response_dashboardinfo", createDashboardInfo());
							}
						}
					)
					.setExceptionListener(exceptionListener)
					.createDataListener()
				);
				server.addEventListener(
					"enable_dashboard",
					EnableDashboard.class,
					new DashboardEventListenerBuilder<>(
						WebRemoteControlService.class, "enable_dashboard", onEnableDashboardListener
					)
					.setExceptionListener(exceptionListener)
					.createDataListener()
				);

				initializeConfig(server);

				if(!NotifyAppender.isListenerRegisterd(logListener))
					NotifyAppender.addListener(logListener);
			}

			initialized = true;
		}finally {
			locker.unlock();
		}

		return true;
	}

	public boolean start() {
		if(!initialized || serverStarted)
			return false;

		final ReentrantLock locker = new ReentrantLock();
		final Condition comp = locker.newCondition();

		server.startAsync().addListener(new FutureListener<Void>() {
			@Override
			public void operationComplete(Future<Void> future) throws Exception {
				serverStarted = future.isSuccess();
				serverException = future.cause();

				locker.lock();
				try {
					comp.signalAll();
				}finally {
					locker.unlock();
				}
			}
		});

		boolean timeout = false;
		locker.lock();
		try {
			if(!serverStarted)
				timeout = !comp.await(20, TimeUnit.SECONDS);
		}catch(InterruptedException ex) {
			timeout = true;
		}finally {
			locker.unlock();
		}

		if(!serverStarted || timeout) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed start web remote service.", serverException);

			webSocketServerManager.removeServer(serverID);
			serverID = null;
			server = null;

			return false;
		}

		return true;
	}

	public void stop() {
		if(initialized && serverStarted && server != null)
			webSocketServerManager.removeServer(serverID, true);

		initialized = false;
		serverStarted = false;
		serverException = null;
		serverID = null;
		server = null;
	}

	public void processService() {

		final BasicStatusInformation status = getStatusInformation();
		if(statusTransmitIntevalTimekeeper.isTimeout() && status != null) {
			statusTransmitIntevalTimekeeper.setTimeoutTime(3, TimeUnit.SECONDS);
			statusTransmitIntevalTimekeeper.updateTimestamp();

			sendUpdateBasicStatusInformationBroadcast(status);
		}
	}

	public SocketIOServer getServer() {
		if(initialized)
			return server;
		else
			return null;
	}

	/*
	 * ------------------------------------
	 * Event Handlers
	 * ------------------------------------
	 */
	private void onLoginEvent(SocketIOClient client) {
		client.joinRoom(HomeFunctions.functionRoomName);

		final BasicStatusInformation status = getStatusInformation();
		if(status != null)
			sendUpdateBasicStatusInformationBroadcast(status);

		if(clientManeger.isAuthenticated(client)) {
			synchronized (logs) {
				for(final Iterator<NotifyLogEvent> it = logs.iterator(); it.hasNext();) {
					sendNotifyLogEvent(client, new LogInfo(it.next()));
				}
			}
		}
	}

	private void onLogoutEvent(SocketIOClient client) {

	}

	private void onEnableDashboardEvent(
		SocketIOClient client, EnableDashboard data, AckRequest ackSender
	) {
		final SocketIOServer server = getServer();

		if(
			initialized &&
			clientManeger.isAuthenticated(client) &&
			server != null &&
			data != null && data instanceof EnableDashboard &&
			data.getRoomId() != null && !"".equals(data.getRoomId())
		) {
			if(data.isEnable()) {
				client.joinRoom(data.getRoomId());

				onJoinRoom(server, client, data.getRoomId());
			}
			else {
				client.leaveRoom(data.getRoomId());

				onLeaveRoom(server, client, data.getRoomId());
			}
		}
		else {
			if(log.isDebugEnabled())
				log.debug(logTag + "Illegal message " + data);
		}
	}

	private void onJoinRoom(
		final SocketIOServer server,
		final SocketIOClient client,
		final String roomName
	) {
		if(log.isDebugEnabled())
			log.debug(logTag + "client "+ client.getSessionId() + " join to " + roomName);

		if(HomeFunctions.functionRoomName.equalsIgnoreCase(roomName)) {
			HomeFunctions.onJoinRoom(
				clientManeger, server, client, roomName, getStatusInformation()
			);
		}
	}

	private void onLeaveRoom(
		final SocketIOServer server,
		final SocketIOClient client, final String roomName
	) {
		if(log.isDebugEnabled())
			log.debug(logTag + "client "+ client.getSessionId() + " leave from " + roomName);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public boolean initializeGatewayHandler(
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler
	) {
		if(this.gatewayHandler != null) {return false;}

		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize web remote control gateway handler, server is null.");

			return false;
		}

		this.gatewayHandler = gatewayHandler;

		return GatewayFunctions.setup(exceptionListener, server, gatewayHandler);
	}

	public void notifyGatewayStatusChanged(
		@NonNull final WebRemoteControlGatewayHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server != null)
			GatewayFunctions.notifyStatusChanged(server, handler);
	}

	public boolean notifyGatewayUpdateHeard(
		@NonNull final org.jp.illg.dstar.model.HeardEntry e
	) {
		final SocketIOServer server = getServer();
		if(server == null) {return false;}

		final WebRemoteControlGatewayHandler handler = this.gatewayHandler;
		if(handler == null) {return false;}

		return GatewayFunctions.notifyUpdateHeard(server, this.gatewayHandler, e);
	}

	public boolean notifyGatewayReflectorHostsUpdated(
		@NonNull List<ReflectorHostInfo> updateHosts
	) {
		final SocketIOServer server = getServer();
		if(server == null) {return false;}

		final WebRemoteControlGatewayHandler handler = this.gatewayHandler;
		if(handler == null) {return false;}

		return GatewayFunctions.notifyReflectorHostsUpdated(server, handler, updateHosts);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public void notifyReflectorStatusChanged(
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server != null) {
			if(handler instanceof DCSCommunicationService)
				DCSFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof DExtraCommunicationService)
				DExtraFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof DPlusCommunicationService)
				DPlusFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof JARLLinkCommunicationService)
				JARLLinkFunctions.notifyStatusChanged(server, handler);
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Handler function is not implemented.");
			}
		}
	}

	public boolean initializeReflectorDExtra(
		@NonNull final WebRemoteControlDExtraHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registReflectorHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration reflector handler.");

			return false;
		}

		return DExtraFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeReflectorDPlus(
		@NonNull final WebRemoteControlDPlusHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registReflectorHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration reflector handler.");

			return false;
		}

		return DPlusFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeReflectorDCS(
		@NonNull final WebRemoteControlDCSHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registReflectorHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration reflector handler.");

			return false;
		}

		return DCSFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeReflectorJARLLink(
		@NonNull final WebRemoteControlJARLLinkHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registReflectorHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration reflector handler.");

			return false;
		}

		return JARLLinkFunctions.setup(exceptionListener, server, handler);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public void notifyRepeaterStatusChanged(
		@NonNull final WebRemoteControlRepeaterHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server != null) {
			if(handler instanceof InternalRepeater)
				InternalFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof HomeblewRepeater)
				HomeblewFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof ReflectorEchoAutoReplyRepeater)
				ReflectorEchoAutoReplyFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof EchoAutoReplyRepeater)
				EchoAutoReplyFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof VoiceroidAutoReplyRepeater)
				VoiceroidAutoReplyFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof ExternalICOMRepeater) {
				ExternalICOMRepeaterFunctions.notifyStatusChanged(server, handler);
			}
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Handler function is not implemented.");
			}
		}
	}

	public boolean initializeRepeaterInternal(
		@NonNull final WebRemoteControlInternalRepeaterHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return InternalFunctions.setup(exceptionListener, server, this.gatewayHandler, handler);
	}

	public boolean initializeRepeaterEchoAutoReply(
		@NonNull final WebRemoteControlEchoAutoReplyHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return EchoAutoReplyFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeRepeaterHomeblew(
		@NonNull final WebRemoteControlHomeblewHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return HomeblewFunctions.setup(exceptionListener, server, this.gatewayHandler, handler);
	}

	public boolean initializeRepeaterReflectorEchoAutoReply(
		@NonNull final WebRemoteControlReflectorEchoAutoReplyHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return ReflectorEchoAutoReplyFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeRepeaterVoiceroidAutoReply(
		@NonNull final WebRemoteControlVoiceAutoReplyHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return VoiceroidAutoReplyFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeRepeaterDummy(
		@NonNull final WebRemoteControlDummyRepeaterHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return DummyFunctions.setup(exceptionListener, server, this.gatewayHandler, handler);
	}

	public boolean initializeRepeaterExternalICOM(
		@NonNull final WebRemoteControlExternalICOMRepeaterHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Server must not null.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize repeater handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRepeaterHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration repeater handler.");

			return false;
		}

		return ExternalICOMRepeaterFunctions.setup(exceptionListener, server, this.gatewayHandler, handler);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public void notifyModemStatusChanged(
		@NonNull final WebRemoteControlModemHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server != null) {
			if(handler.getModemType() == ModemTypes.AccessPoint)
				AccessPointFunctions.notifyStatusChanged(server, handler);
			else if(handler.getModemType() == ModemTypes.NewAccessPoint)
				NewAccessPointFunctions.notifyStatusChanged(server, handler);
			else if(handler.getModemType() == ModemTypes.MMDVM)
				MMDVMFunctions.notifyStatusChanged(server, handler);
			else if(handler.getModemType() == ModemTypes.NoraVR)
				NoraVRFunctions.notifyStatusChanged(server, handler);
			else if(handler.getModemType() == ModemTypes.AnalogModemPiGPIO)
				AnalogModemPiGPIOFunctions.notifyStatusChanged(server, handler);
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Handler function is not implemented.");
			}
		}
	}

	public boolean initializeModemAccessPoint(
		@NonNull final WebRemoteControlAccessPointHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registModemHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler.");

			return false;
		}

		return AccessPointFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeModemNewAccessPoint(
		@NonNull final WebRemoteControlNewAccessPointHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registModemHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler.");

			return false;
		}

		return NewAccessPointFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeModemMMDVM(
		@NonNull final WebRemoteControlMMDVMHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registModemHandler(handler)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler.");

			return false;
		}

		return MMDVMFunctions.setup(exceptionListener, server, handler);
	}

	public boolean initializeModemNoraVR(
		@NonNull final WebRemoteControlNoraVRHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registModemHandler(handler)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to registration modem handler.");

			return false;
		}

		return NoraVRFunctions.setup(exceptionListener, server, handler);
	}

	public void notifyModemNoraVRClientLogin(
		@NonNull final WebRemoteControlNoraVRHandler handler,
		@NonNull final NoraVRLoginClient client
	) {
		final SocketIOServer server = getServer();
		if(server != null)
			NoraVRFunctions.notifyNoraVRClientLogin(server, handler, client);
	}

	public void notifyModemNoraVRClientLogout(
		@NonNull final WebRemoteControlNoraVRHandler handler,
		@NonNull final NoraVRLoginClient client
	) {
		final SocketIOServer server = getServer();
		if(server != null)
			NoraVRFunctions.notifyNoraVRClientLogout(server, handler, client);
	}

	public boolean initializeModemAnalogModemPiGPIO(
		@NonNull final WebRemoteControlAnalogModemPiGPIOHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration modem handler, Server must not null.");

			return false;
		}
		else if(!registModemHandler(handler)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to registration modem handler.");

			return false;
		}

		return AnalogModemPiGPIOFunctions.setup(exceptionListener, server, handler);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public void notifyRoutingServiceStatusChanged(
		@NonNull final WebRemoteControlRoutingServiceHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server != null) {
			if(handler instanceof GlobalTrustClientService)
				GlobalTrustClientServiceFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof IrcDDBRoutingService)
				IrcDDBRoutingServiceFunctions.notifyStatusChanged(server, handler);
			else if(handler instanceof JpTrustClientService)
				JpTrustClientServiceFunctions.notifyStatusChanged(server, handler);
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Handler function is not implemented.");
			}
		}
	}

	public boolean initializeGlobalTrustClientService(
		@NonNull final WebRemoteControlGlobalTrustClientHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration routing service handler, Socket io server is not initialized.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize routing handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRoutingServiceHandler(handler)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to registration routing service handler.");

			return false;
		}

		return GlobalTrustClientServiceFunctions.setup(exceptionListener, server, gatewayHandler, handler);
	}

	public boolean initializeIrcDDBClientService(
		@NonNull final WebRemoteControlIrcDDBRoutingHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration routing service handler, Socket io server is not initialized.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize routing handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRoutingServiceHandler(handler)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to registration routing service handler.");

			return false;
		}

		return IrcDDBRoutingServiceFunctions.setup(exceptionListener, server, gatewayHandler, handler);
	}

	public boolean initializeJpTrustClientService(
		@NonNull final WebRemoteControlJpTrustClientHandler handler
	) {
		final SocketIOServer server = getServer();
		if(server == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to registration routing service handler, Socket io server is not initialized.");

			return false;
		}
		else if(this.gatewayHandler == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to initialize routing handler, Gateway handler must not null.");

			return false;
		}
		else if(!registRoutingServiceHandler(handler)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to registration routing service handler.");

			return false;
		}

		return JpTrustClientServiceFunctions.setup(exceptionListener, server, gatewayHandler, handler);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	public void pushLog(NotifyLogEvent log) {
		if(serverID == null) {return;}

		synchronized(logs) {
			while(logs.size() >= logEntryLimit) {logs.poll();}

			logs.add(log);
		}

		sendNotifyLogEvent(new LogInfo(log));
	}

	public void pushLog(final Level level, final String message) {
		if(serverID == null) {return;}

		final NotifyLogEvent event =
			new NotifyLogEvent(
				System.currentTimeMillis(), Thread.currentThread().getName(), level, message, "", "", null
			);

		pushLog(event);
	}

	/*
	 * ------------------------------------
	 *
	 * ------------------------------------
	 */
	private boolean initializeConfig(final SocketIOServer server) {
		return ConfigFunctions.setup(
			exceptionListener,
			server, workerExecutor, clientManeger, eventListener,
			configurationFilePath, userListFilePath, helperPort
		);
	}

	private DashboardInfo createDashboardInfo() {
		final WebRemoteControlGatewayHandler handler = this.gatewayHandler;
		if(handler == null) {return null;}

		final DashboardInfo info = new DashboardInfo();
		info.setApplicationName(applicationVersion.getApplicationName());
		info.setApplicationVersion(applicationVersion.getApplicationVersion());
		info.setApplicationRunningOS(applicationVersion.getRunningOperatingSystem());
		info.setRequiredDashboardVersion(requiredDashboardVersion);

		info.setGatewayInfo(
			new GatewayDashboardInfo(
				handler.getGatewayCallsign(), WebSocketTool.formatRoomId(handler.getGatewayCallsign()))
		);

		for(final RoutingService routing : handler.getRoutingServiceAll()) {
			final RoutingServiceDashboardInfo routingInfo =
				new RoutingServiceDashboardInfo(
					routing.getServiceType(),
					routing.getWebRemoteControlHandler().getWebSocketRoomId()
				);

			info.getRoutingInfos().add(routingInfo);
		}

		for(
			final ReflectorCommunicationService reflector : handler.getReflectorCommunicationServiceAll()
		) {
			final ReflectorDashboardInfo reflectorInfo =
				new ReflectorDashboardInfo(
					reflector.getProcessorType(),
					reflector.getWebRemoteControlHandler().getWebSocketRoomId()
				);

			info.getReflectorInfos().add(reflectorInfo);
		}

		for(final DSTARRepeater repeater : handler.getRepeaters()) {
			final RepeaterDashboardInfo repeaterInfo =
				new RepeaterDashboardInfo(
					repeater.getRepeaterCallsign(),
					repeater.getWebRemoteControlHandler().getWebSocketRoomId()
				);
			info.getRepeaterInfos().add(repeaterInfo);

			for(final RepeaterModem modem : repeater.getModems()) {
				final ModemDashboardInfo modemInfo =
					new ModemDashboardInfo(
						modem.getModemId(),
						modem.getModemType(),
						modem.getWebRemoteControlHandler().getWebSocketRoomId()
					);

				repeaterInfo.getModemInfos().add(modemInfo);
			}
		}

		return info;
	}

	private UUID createWebSocketServer(
		final int port,
		final String context
	) {
		final SocketConfig socketConfig = new SocketConfig();
		socketConfig.setReuseAddress(true);
		socketConfig.setTcpKeepAlive(true);

		final Configuration config = new Configuration();
		config.setPort(port);
		if(context != null && !"".equals(context)) {config.setContext(context);}
		config.setSocketConfig(socketConfig);
		config.setBossThreads(1);
		config.setWorkerThreads(2);
		config.setMaxHttpContentLength(1024 * 1024);
		config.setMaxFramePayloadLength(1024 * 1024);

		return webSocketServerManager.createServer(config);
	}

	private boolean registModemHandler(final WebRemoteControlModemHandler handler) {
		locker.lock();
		try {
			return
				getModemHandler(handler.getModemId()) == null &&
				modemHandlers.put(handler.getModemId(), handler) == null;
		}finally {
			locker.unlock();
		}
	}

	private WebRemoteControlModemHandler getModemHandler(final int modemId) {
		locker.lock();
		try {
			return modemHandlers.get(modemId);
		}finally {
			locker.unlock();
		}
	}

	private boolean registRepeaterHandler(final WebRemoteControlRepeaterHandler handler) {
		locker.lock();
		try {
			return
				getRepeaterHandler(handler.getRepeaterCallsign()) == null &&
				repeaterHandlers.put(handler.getRepeaterCallsign(), handler) == null;
		}finally {
			locker.unlock();
		}
	}

	private WebRemoteControlRepeaterHandler getRepeaterHandler(final String repeaterCallsign) {
		locker.lock();
		try {
			return repeaterHandlers.get(repeaterCallsign);
		}finally {
			locker.unlock();
		}
	}

	private boolean registReflectorHandler(final WebRemoteControlReflectorHandler handler) {
		locker.lock();
		try {
			return
				getReflectorHandler(handler.getReflectorType()) == null &&
				reflectorHandlers.put(handler.getReflectorType(), handler) == null;
		}finally {
			locker.unlock();
		}
	}

	private WebRemoteControlReflectorHandler getReflectorHandler(
		final ReflectorProtocolProcessorTypes reflectorType
	) {
		locker.lock();
		try {
			return reflectorHandlers.get(reflectorType);
		}finally {
			locker.unlock();
		}
	}

	private boolean registRoutingServiceHandler(final WebRemoteControlRoutingServiceHandler handler) {
		locker.lock();
		try {
			return
				getRoutingServiceHandler(handler.getServiceType()) == null &&
				routingServiceHandlers.put(handler.getServiceType(), handler) == null;
		}finally {
			locker.unlock();
		}
	}

	private WebRemoteControlRoutingServiceHandler getRoutingServiceHandler(
		final RoutingServiceTypes reflectorType
	) {
		locker.lock();
		try {
			return routingServiceHandlers.get(reflectorType);
		}finally {
			locker.unlock();
		}
	}

	private void sendNotifyLogEvent(final LogInfo log) {
		final SocketIOServer server = webSocketServerManager.getServer(serverID);

		if(server != null) {sendNotifyLogEvent(server, log);}
	}

	private void sendNotifyLogEvent(final SocketIOServer server, final LogInfo log) {
		server.getBroadcastOperations().sendEvent("notify_logevent", log);
	}

	private void sendNotifyLogEvent(final SocketIOClient client, final LogInfo log) {
		client.sendEvent("notify_logevent", log);
	}

	private boolean sendUpdateBasicStatusInformationBroadcast(
		final BasicStatusInformation status
	) {
		final SocketIOServer server = getServer();
		if(server == null) {return false;}

		return HomeFunctions.sendUpdateBasicStatusInformationBroadcast(clientManeger, server, status);
	}
}