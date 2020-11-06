package org.jp.illg.dstar.routing.service.jptrust;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.GlobalIPInfo;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.define.RoutingServiceStatus;
import org.jp.illg.dstar.routing.define.RoutingServiceTasks;
import org.jp.illg.dstar.routing.model.PositionUpdateInfo;
import org.jp.illg.dstar.routing.model.QueryCallback;
import org.jp.illg.dstar.routing.model.QueryRepeaterResult;
import org.jp.illg.dstar.routing.model.QueryUserResult;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.RoutingCompletedTaskInfo;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.routing.service.RoutingServiceBase;
import org.jp.illg.dstar.routing.service.jptrust.model.AreaPositionCacheEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.AreaPositionQuery;
import org.jp.illg.dstar.routing.service.jptrust.model.AreaPositionQueryRequest;
import org.jp.illg.dstar.routing.service.jptrust.model.AreaPositionQueryResponse;
import org.jp.illg.dstar.routing.service.jptrust.model.GatewayIPUpdateRequest;
import org.jp.illg.dstar.routing.service.jptrust.model.GatewayIPUpdateResponse;
import org.jp.illg.dstar.routing.service.jptrust.model.JpTrustCommand;
import org.jp.illg.dstar.routing.service.jptrust.model.JpTrustCommandBase.CommandType;
import org.jp.illg.dstar.routing.service.jptrust.model.JpTrustResult;
import org.jp.illg.dstar.routing.service.jptrust.model.PositionCacheEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.PositionQueryRequest;
import org.jp.illg.dstar.routing.service.jptrust.model.PositionQueryResponse;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusBase;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusKeepAlive;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusLogin;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusLogoff;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusPTTOff;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusPTTOn;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusRepeaterEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.StatusUpdate;
import org.jp.illg.dstar.routing.service.jptrust.model.TableUpdateCacheEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.TableUpdateRequest;
import org.jp.illg.dstar.routing.service.jptrust.model.TableUpdateResponse;
import org.jp.illg.dstar.routing.service.jptrust.model.TaskEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.TaskStatus;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlJpTrustClientHandler;
import org.jp.illg.dstar.service.web.model.JpTrustClientServiceStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.dns.DNSRoundrobinUtil;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;
import com.google.common.util.concurrent.RateLimiter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JpTrustClientService
extends RoutingServiceBase<BufferEntry>
implements RoutingService, WebRemoteControlJpTrustClientHandler {

	/**
	 * 管理サーバ応答タイムアウト(ms)
	 */
	private static final long trustServerTimeoutMillisForSingle = 1000;

	/**
	 * プロキシサーバ応答タイムアウト(ms)
	 */
	private static final long trustServerTimeoutMillisForProxy = 2000;

	/**
	 * サーバリトライ回数
	 */
	private static final int trustServerRetryLimit = 1;

	/**
	 * キャッシュ生存秒数
	 */
	private static final int baseCacheTimeLimitSeconds = 30;

	/**
	 * クエリキャッシュ件数制限(件)
	 */
	private static final int cacheEntryLimit = 20;

	/**
	 * サーバクエリ送信リミットレート(/s)
	 */
	private static final double trustRateLimitPerSeconds = 10.0;


	public interface JpTrustProtocolProcessorReceiveEventHandler{
		public void handleJpTrustProtocolProcessorReceiveEvent(List<JpTrustCommand> receiveCommands);
	}

	private static enum ProcessStates{
		Initialize,
		GatewayIPUpdateRequest,
		TaskEntryWait,
		SendCommand,
		TimeWait,
		;
	}

	private static class StatusFrameEntry {

		private final int frameID;

		private final Timer activityTimekeeper;

		public StatusFrameEntry(final int frameID) {
			this.frameID = frameID;
			activityTimekeeper = new Timer();
			activityTimekeeper.updateTimestamp();
		}
	}

	private final String logHeader;

	private final Lock locker;

	private SocketIOEntryUDP trustChannel;

	private ProcessStates callbackState;
	private ProcessStates currentState;
	private ProcessStates nextState;
	private boolean isStateChanged;

	private final Timer processStateTimekeeper;
	private int processStateRetryCount;

	private final Map<UUID, TaskEntry> processTasks;
	private TaskEntry processingTask;

	private final List<JpTrustCommand> recvCommands;

	private final List<TableUpdateCacheEntry> tableUpdateCache;
	private final List<PositionCacheEntry> userRoutingCache;
	private final List<AreaPositionCacheEntry> repeaterRoutingCache;

	private final Timer gatewayipUpdateIntervalTimeKeeper;

	private JpTrustProtocolProcessorReceiveEventHandler receiveEventHandler;

	private final JpTrustLogTransporter logTransporter;

	private final RateLimiter trustRateLimiter;

	private final GatewayIPUpdateResponse gatewayIPUpdateResponse = new GatewayIPUpdateResponse();
	private final PositionQueryResponse positionQueryResponse = new PositionQueryResponse();
	private final AreaPositionQueryResponse areaPositionQueryResponse = new AreaPositionQueryResponse();
	private final TableUpdateResponse tableUpdateResponse = new TableUpdateResponse();


	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String trustAddress;
	public static final String trustAddressPropertyName = "ServerAddress";
	private static final String defaultTrustAddress = DSTARDefines.JpTrustServerAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int trustPort;
	public static final String trustPortPropertyName = "ServerPort";
	private static final int defaultTrustPort = 30001;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int keepAliveSeconds;
	public static final String keepAliveSecondsPropertyName = "KeepAliveSeconds";
	private static final int defaultKeepAliveSeconds = 60;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean disableLogTransport;
	public static final String disableLogTransportPropertyName = "DisableLogTransport";
	private static final boolean defaultDisableLogTransport = false;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean useProxyGateway;
	public static final String useProxyGatewayPropertyName = "UseProxyGateway";
	private static final boolean useProxyGatewayDefault = false;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String proxyGatewayAddress;
	public static final String proxyGatewayAddressPropertyName = "ProxyGatewayAddress";
	private static final String proxyGatewayAddressDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int proxyPort;
	public static final String proxyPortPropertyName = "ProxyPort";
	private static final int proxyPortDefault = 30001;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int queryID;
	public static final String queryIDPropertyName = "QueryID";
	private static final int queryIDDefault = 0x0;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean statusTransmit;
	public static final String statusTransmitPropertyName = "StatusTransmit";
	private static final boolean statusTransmitDefault = true;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String statusServerAddress;
	public static final String statusServerAddressPropertyName = "StatusServerAddress";
	private static final String statusServerAddressDefault = "status.d-star.info";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int statusServerPort;
	public static final String statusServerPortPropertyName = "statusServerPort";
	private static final int statusServerPortDefault = 21050;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean debugQueryFailTest;
	public static final String debugQueryFailTestPropertyName = "DebugQueryFailTest";
	private static final boolean debugQueryFailTestDefault = false;


	@Getter
	@Setter
	private String gatewayCallsign;

	private final DNSRoundrobinUtil trustAddressResolver;

	private boolean gwRegistRequestReceived;

	private int commandID;

	private final int id;

	private final Timer statusProcessIntervalTimekeeper;
	private final Map<String, StatusRepeaterEntry> statusRepeaters;
	private final Map<Integer, StatusFrameEntry> statusFrameEntries;
	private final Lock statusLocker;

	private int queryTxCount;

	private boolean isStatusLoggedin;


	public JpTrustClientService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener
	) {
		this(
			systemID,
			exceptionListener,
			gateway,
			workerExecutor,
			applicationVersion,
			eventListener,
			null
		);
	}

	public JpTrustClientService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		final SocketIO socketIO
	) {
		super(
			systemID,
			exceptionListener,
			gateway,
			workerExecutor,
			applicationVersion,
			eventListener,
			JpTrustClientService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteAddressOnly
		);

		setManualControlThreadTerminateMode(true);

		logHeader = this.getClass().getSimpleName() + " : ";

		locker = new ReentrantLock();

		logTransporter =
			new JpTrustLogTransporter(exceptionListener, socketIO != null ? socketIO : super.getSocketIO());

		tableUpdateCache = new ArrayList<>(cacheEntryLimit);
		userRoutingCache = new ArrayList<>(cacheEntryLimit);
		repeaterRoutingCache = new ArrayList<>(cacheEntryLimit);

		currentState = ProcessStates.Initialize;
		nextState = ProcessStates.Initialize;
		callbackState = ProcessStates.Initialize;

		processStateTimekeeper = new Timer();

		queryTxCount = 0;

		trustChannel = null;
		setTrustAddress(defaultTrustAddress);
		setTrustPort(defaultTrustPort);

		setDisableLogTransport(defaultDisableLogTransport);

		setKeepAliveSeconds(defaultKeepAliveSeconds);

		setUseProxyGateway(useProxyGatewayDefault);
		setProxyGatewayAddress(proxyGatewayAddressDefault);
		setProxyPort(proxyPortDefault);
		setQueryID(queryIDDefault);

		setStatusTransmit(statusTransmitDefault);
		setStatusServerAddress(statusServerAddressDefault);
		setStatusServerPort(statusServerPortDefault);

		setDebugQueryFailTest(debugQueryFailTestDefault);

		recvCommands = new LinkedList<JpTrustCommand>();

		processTasks = new LinkedHashMap<UUID, TaskEntry>();
		processingTask = null;

		gatewayipUpdateIntervalTimeKeeper = new Timer();

		trustAddressResolver = new DNSRoundrobinUtil();

		gwRegistRequestReceived = false;

		commandID = 0x0;

		trustRateLimiter = RateLimiter.create(trustRateLimitPerSeconds);

		statusProcessIntervalTimekeeper = new Timer();
		statusRepeaters = new HashMap<>();
		statusFrameEntries = new HashMap<>();
		statusLocker = new ReentrantLock();

		id = new Random(System.currentTimeMillis() ^ 0x45aa).nextInt(0xfffe) + 1;

		isStatusLoggedin = false;
	}

	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						trustChannel =
							getSocketIO().registUDP(
								new InetSocketAddress(0), JpTrustClientService.this.getHandler(),
								JpTrustClientService.this.getClass().getSimpleName() + "@" +
								getTrustAddress() + ":" + getTrustPort()
							);
					}
				}
			) ||
			trustChannel == null
		){
			closeTrustChannel();

			this.stop();

			return false;
		}

		if(isDisableLogTransport()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Log transport is disabled.");
		}

		return true;
	}

	public void stop() {
		super.stop();

		closeTrustChannel();
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		if(isUseProxyGateway()) {
			if(log.isInfoEnabled())
				log.info(logHeader + "Use DStarProxyGateway " + getProxyGatewayAddress() + ":" + getProxyPort() + ".");
		}

		if(logTransporter.start())
			return ThreadProcessResult.NoErrors;
		else
			return threadFatalError("Could not start jpTrustLogTransporter.", null);
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		logTransporter.stop();

		closeTrustChannel();

		currentState = ProcessStates.Initialize;

		this.recvCommands.clear();

		this.processTasks.clear();
		this.processingTask = null;
	}

	@Override
	protected boolean isCanSleep() {
		locker.lock();
		try {
			for(final TaskEntry entry : processTasks.values()) {
				if(entry.getTaskStatus() != TaskStatus.Complete)
					return false;
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {

	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress,
		Exception ex
	) {
		final StringBuffer sb = new StringBuffer(this.getClass().getSimpleName() + " socket error.");
		if(localAddress != null) {sb.append("\nLocal=" + localAddress.toString());}
		if(remoteAddress != null) {sb.append("\nRemote=" + remoteAddress.toString());}

		if(log.isDebugEnabled())
			log.debug(logHeader + sb.toString(), ex);
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
	public ThreadProcessResult processRoutingService() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		//バッファを解析してコマンドを抽出
		if(this.analyzePacket(recvCommands)) {
			//イベントハンドラをコール
			if(getReceiveEventHandler() != null) {
				//受信コマンドリストのコピーを作成
				final List<JpTrustCommand> receiveCommands =
						new LinkedList<JpTrustCommand>(recvCommands);

				getWorkerExecutor().submit(new RunnableTask() {
					@Override
					public void task() {
						getReceiveEventHandler().handleJpTrustProtocolProcessorReceiveEvent(receiveCommands);
					}
				});
			}
		}

		locker.lock();
		try {
			boolean reProcess;
			do {
				reProcess = false;

				isStateChanged = currentState != nextState;
				currentState = nextState;

				switch(currentState) {
				case Initialize:
					processResult = onStateInitialize();
					break;

				case GatewayIPUpdateRequest:{
					processResult = onStateGatewayIPUpdateRequest();
					break;
				}

				case TaskEntryWait:{
					processResult = onStateTaskEntryWait();
					break;
				}

				case SendCommand:
					processResult = onStateSendCommand();
					break;

				case TimeWait:
					processResult = onStateWait();
					break;
				}

				if(
					currentState != nextState &&
					processResult == ThreadProcessResult.NoErrors
				) {reProcess = true;}

			}while(reProcess);
		}finally {
			locker.unlock();
		}

		cleanProcessTasks();


		statusProcess();

		if(!isWorkerThreadAvailable()) {
			if(isStatusTransmit()) {
				statusLocker.lock();
				try {
					Stream.of(statusRepeaters.values())
					.filter(new Predicate<StatusRepeaterEntry>() {
						@Override
						public boolean test(StatusRepeaterEntry entry) {
							return entry.isKeepaliveTransmitted();
						}
					})
					.findFirst()
					.ifPresent(new Consumer<StatusRepeaterEntry>() {
						@Override
						public void accept(StatusRepeaterEntry t) {
							sendStatusLogoff();
							isStatusLoggedin = false;
						}
					});
				}finally {
					statusLocker.unlock();
				}
			}

			setWorkerThreadTerminateRequest(true);
		}

		return processResult;
	}

	@Override
	public void updateCache(@NonNull String myCallsign, @NonNull InetAddress gatewayAddress) {
		removeUserRoutingCache(myCallsign, gatewayAddress);
	}

	public boolean setProperties(Properties properties) {
		if(properties == null) {return false;}

		setTrustAddress(properties.getProperty("route.jptrust.serveraddress", defaultTrustAddress));

		try {
			setTrustPort(Integer.parseInt(properties.getProperty("route.jptrust.serverport", String.valueOf(defaultTrustPort))));
		}catch(NumberFormatException ex) {
			setTrustPort(defaultTrustPort);
		}

		return true;
	}

	public UUID requestAreaPositionQuery(char[] yourCall, Header header) {
		assert yourCall != null && yourCall.length == 8;
		if(yourCall == null || yourCall.length != 8) {return null;}

		if(!this.isRunning()) {return null;}

		final String yourCallsign = String.valueOf(yourCall);

		if(!CallSignValidator.isValidRepeaterCallsign(yourCallsign)) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not execute query, illegal repeater callsign = " + yourCallsign + ".");

			return null;
		}

		final AreaPositionQuery request = new AreaPositionQueryRequest();
		request.setYourCallsign(yourCall);

		final TaskEntry taskEntry = new TaskEntry(yourCallsign, request);
		taskEntry.setHeader(header != null ? header.clone() : null);

		locker.lock();
		try {
			processTasks.put(taskEntry.getId(), taskEntry);
		}finally {
			locker.unlock();
		}

		wakeupProcessThread();

		return taskEntry.getId();
	}
/*
	public JpTrustCommand getAreaPositionQueryResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		final AreaPositionQueryResponse resp = getQuery(id, AreaPositionQueryResponse.class);

		return resp;
	}
*/
/*
	public UUID requestGatewayUpdate(char[] repeaterCallsign) {
		assert repeaterCallsign != null && repeaterCallsign.length == 8;
		if(repeaterCallsign == null || repeaterCallsign.length != 8) {return null;}

		if(!this.isRunning()) {return null;}

		final String gatewayCallsign = String.valueOf(repeaterCallsign);

		if(!CallSignValidator.isValidGatewayCallsign(repeaterCallsign)) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not execute query, illegal japan gateway callsign = " + gatewayCallsign + ".");

			return null;
		}

		final GatewayIPUpdateRequest request = new GatewayIPUpdateRequest();
		request.setRepeater1Callsign(repeaterCallsign);

		final TaskEntry taskEntry = new TaskEntry(gatewayCallsign, request);

		locker.lock();
		try {
			this.processTasks.put(taskEntry.getId(), taskEntry);
		}finally {
			locker.unlock();
		}

		return taskEntry.getId();
	}
*/
/*
	public JpTrustCommand getGatewayIPUpdateResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		GatewayIPUpdateResponse resp = getQuery(id, GatewayIPUpdateResponse.class);

		return resp;
	}
*/
	public UUID requestPositionQuery(char[] yourCallsign, Header header) {
		assert yourCallsign != null && yourCallsign.length == 8;
		if(yourCallsign == null || yourCallsign.length != 8) {return null;}

		if(!this.isRunning()) {return null;}

		final String yourCall = String.valueOf(yourCallsign);

		if(!CallSignValidator.isValidUserCallsign(yourCall)) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Could not execute query, illegal japan user callsign = " + yourCall + "."
				);
			}

			return null;
		}

		final PositionQueryRequest request = new PositionQueryRequest();
		request.setYourCallsign(yourCallsign);

		final TaskEntry taskEntry = new TaskEntry(yourCall, request);
		taskEntry.setHeader(header != null ? header.clone() : null);

		locker.lock();
		try {
			this.processTasks.put(taskEntry.getId(), taskEntry);
		}finally {
			locker.unlock();
		}

		wakeupProcessThread();

		return taskEntry.getId();
	}
/*
	public JpTrustCommand getPositionQueryResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		final PositionQueryResponse resp = getQuery(id, PositionQueryResponse.class);

		return resp;
	}
*/
	public UUID requestTableUpdate(char[] myCallsign, char[] repeater1Callsign, char[] repeater2Callsign) {
		if(
			!DSTARUtils.isValidCallsignFullLength(myCallsign, repeater1Callsign, repeater2Callsign)
		) {return null;}

		if(!this.isRunning()) {return null;}

		if(
			!CallSignValidator.isValidUserCallsign(myCallsign) ||
			!CallSignValidator.isValidRepeaterCallsign(repeater1Callsign) ||
			!CallSignValidator.isValidGatewayCallsign(repeater2Callsign)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					"Could not execute query, Illegal callsign detected.\n    " +
					"MY:" + String.valueOf(myCallsign) + "/" +
					"RPT1:" + String.valueOf(repeater1Callsign) + "/" +
					"RPT2:" + String.valueOf(repeater2Callsign)
				);
			}

			return null;
		}

		final TableUpdateRequest request = new TableUpdateRequest();
		request.setRepeater2Callsign(repeater2Callsign);
		request.setRepeater1Callsign(repeater1Callsign);
		request.setMyCallsign(myCallsign);

		final TaskEntry taskEntry = new TaskEntry(String.valueOf(myCallsign), request);

		locker.lock();
		try {
			processTasks.put(taskEntry.getId(), taskEntry);
		}finally {
			locker.unlock();
		}

		wakeupProcessThread();

		return taskEntry.getId();
	}
/*
	public JpTrustCommand getTableUpdateResponse_(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		final TableUpdateResponse resp = getQuery(id, TableUpdateResponse.class);

		return resp;
	}
*/
	public JpTrustProtocolProcessorReceiveEventHandler getReceiveEventHandler() {
		return receiveEventHandler;
	}

	public void setReceiveEventHandler(JpTrustProtocolProcessorReceiveEventHandler receiveEventHandler) {
		this.receiveEventHandler = receiveEventHandler;
	}

	@Override
	public boolean kickWatchdog(String callsign, String statusMessage) {
		if(!CallSignValidator.isValidRepeaterCallsign(callsign))
			return false;

		if(isStatusTransmit())
			updateWatchdogStatusRepeater(callsign, statusMessage);

		return true;
	}

	@Override
	public UUID positionUpdate(
		final int frameID,
		final String myCall, final String myCallExt, final String yourCall,
		final String repeater1, final String repeater2,
		final byte flag1, final byte flag2, final byte flag3
	) {

		if(!DSTARUtils.isValidCallsignFullLength(myCall, repeater1, repeater2)) {return null;}

		final String gatewayCallsign = DSTARUtils.formatFullCallsign(getGatewayCallsign(), ' ');
		final String repeater1Callsign = DSTARUtils.formatFullCallsign(repeater1, ' ');
		final String repeater2Callsign = DSTARUtils.formatFullCallsign(repeater2, ' ');
		if(!gatewayCallsign.equals(repeater1Callsign) || !repeater1Callsign.equals(repeater2Callsign)){
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Illegal callsign RPT1/RPT2 " + repeater1 + "/" + repeater2 + " requested by " + myCall + "."
				);
			}
			return null;
		}

		return this.requestTableUpdate(
				myCall.toCharArray(),
				repeater1.toCharArray(),
				repeater2.toCharArray()
		);
	}

	@Override
	public boolean sendStatusAtPTTOn(
		final int frameID,
		@NonNull final String myCall, @NonNull final String myCallExt, @NonNull final String yourCall,
		@NonNull final String repeater1, @NonNull final String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		@NonNull final String networkDestination, @NonNull final String txMessage,
		final double latitude, final double longitude
	) {
		if(
			!DSTARUtils.isValidCallsignFullLength(myCall, repeater1, repeater2) ||
			!DSTARUtils.isValidCallsignShortLegth(myCallExt) ||
			!CallSignValidator.isValidRepeaterCallsign(repeater1) ||
			(
				!CallSignValidator.isValidGatewayCallsign(repeater2) &&
				!CallSignValidator.isValidRepeaterCallsign(repeater2)
			)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Failed status transmit," +
					"Illegal callsign " +
					"MY:" + myCall + "/" + myCallExt + ",UR:" + yourCall + "/" +
					"RPT1:" + repeater1 + "/RPT2:" + repeater2 + "."
				);
			}

			return false;
		}

		if(isStatusTransmit() && isStatusLoggedin && createStatusFrameEntry(frameID)) {
			final boolean dstAvailable =
				CallSignValidator.isValidCQCQCQ(yourCall) &&
				networkDestination != null && !DSTARDefines.EmptyLongCallsign.equals(networkDestination);

			final StatusPTTOn status = createStatusHeader(new StatusPTTOn());
			ArrayUtil.copyOf(status.getRepeater1Callsign(), repeater1.toCharArray());
			ArrayUtil.copyOf(status.getRepeater2Callsign(), repeater2.toCharArray());

			if(dstAvailable)
				ArrayUtil.copyOf(status.getYourCallsign(), networkDestination.toCharArray());
			else
				ArrayUtil.copyOf(status.getYourCallsign(), yourCall.toCharArray());

			ArrayUtil.copyOf(status.getMyCallsignLong(), myCall.toCharArray());
			ArrayUtil.copyOf(status.getMyCallsignShort(), myCallExt.toCharArray());
			ArrayUtil.copyOf(status.getShortMessage(), txMessage.toCharArray());
			status.setLatitude(latitude);
			status.setLongitude(longitude);

			return sendStatusPacket(status, 1);
		}
		else
			return true;
	}

	@Override
	public boolean sendStatusUpdate(
		final int frameID,
		@NonNull final String myCall, @NonNull final String myCallExt, @NonNull final String yourCall,
		@NonNull final String repeater1, @NonNull final String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		@NonNull final String networkDestination, @NonNull final String txMessage,
		final double latitude, final double longitude
	) {
		if(
			!DSTARUtils.isValidCallsignFullLength(myCall, repeater1, repeater2) ||
			!DSTARUtils.isValidCallsignShortLegth(myCallExt) ||
			!CallSignValidator.isValidRepeaterCallsign(repeater1) ||
			(
				!CallSignValidator.isValidGatewayCallsign(repeater2) &&
				!CallSignValidator.isValidRepeaterCallsign(repeater2)
			)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Failed status transmit," +
					"Illegal callsign " +
					"MY:" + myCall + "/" + myCallExt + ",UR:" + yourCall + "/" +
					"RPT1:" + repeater1 + "/RPT2:" + repeater2 + "."
				);
			}

			return false;
		}

		if(isStatusTransmit() && isStatusLoggedin && updateStatusFrameEntry(frameID)) {
			final boolean dstAvailable =
				CallSignValidator.isValidCQCQCQ(yourCall) &&
				networkDestination != null && !DSTARDefines.EmptyLongCallsign.equals(networkDestination);

			final StatusUpdate status = createStatusHeader(new StatusUpdate());
			ArrayUtil.copyOf(status.getRepeater1Callsign(), repeater1.toCharArray());
			ArrayUtil.copyOf(status.getRepeater2Callsign(), repeater2.toCharArray());

			if(dstAvailable)
				ArrayUtil.copyOf(status.getYourCallsign(), networkDestination.toCharArray());
			else
				ArrayUtil.copyOf(status.getYourCallsign(), yourCall.toCharArray());

			ArrayUtil.copyOf(status.getMyCallsignLong(), myCall.toCharArray());
			ArrayUtil.copyOf(status.getMyCallsignShort(), myCallExt.toCharArray());
			ArrayUtil.copyOf(status.getShortMessage(), txMessage.toCharArray());
			status.setLatitude(latitude);
			status.setLongitude(longitude);

			return sendStatusPacket(status, 1);
		}
		else
			return true;
	}

	@Override
	public boolean sendStatusAtPTTOff(
		final int frameID,
		@NonNull final String myCall, @NonNull final String myCallExt, @NonNull final String yourCall,
		@NonNull final String repeater1, @NonNull final String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		@NonNull final String networkDestination, @NonNull final String txMessage,
		final double latitude, final double longitude,
		final int numDvFrames, final int numDvSlientFrames, final int numBitErrors
	) {
		if(
			!DSTARUtils.isValidCallsignFullLength(myCall, repeater1, repeater2) ||
			!DSTARUtils.isValidCallsignShortLegth(myCallExt) ||
			!CallSignValidator.isValidRepeaterCallsign(repeater1) ||
			(
				!CallSignValidator.isValidGatewayCallsign(repeater2) &&
				!CallSignValidator.isValidRepeaterCallsign(repeater2)
			)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Failed status transmit," +
					"Illegal callsign " +
					"MY:" + myCall + "/" + myCallExt + ",UR:" + yourCall + "/" +
					"RPT1:" + repeater1 + "/RPT2:" + repeater2 + "."
				);
			}

			return false;
		}

		if(isStatusTransmit() && isStatusLoggedin && removeStatusFrameEntry(frameID)) {
			final boolean dstAvailable =
				CallSignValidator.isValidCQCQCQ(yourCall) &&
				networkDestination != null && !DSTARDefines.EmptyLongCallsign.equals(networkDestination);

			final StatusPTTOff status = createStatusHeader(new StatusPTTOff());
			ArrayUtil.copyOf(status.getRepeater1Callsign(), repeater1.toCharArray());
			ArrayUtil.copyOf(status.getRepeater2Callsign(), repeater2.toCharArray());

			if(dstAvailable)
				ArrayUtil.copyOf(status.getYourCallsign(), networkDestination.toCharArray());
			else
				ArrayUtil.copyOf(status.getYourCallsign(), yourCall.toCharArray());

			ArrayUtil.copyOf(status.getMyCallsignLong(), myCall.toCharArray());
			ArrayUtil.copyOf(status.getMyCallsignShort(), myCallExt.toCharArray());
			ArrayUtil.copyOf(status.getShortMessage(), txMessage.toCharArray());
			status.setLatitude(latitude);
			status.setLongitude(longitude);

			return sendStatusPacket(status, 1);
		}
		else
			return true;
	}

	@Override
	public PositionUpdateInfo getPositionUpdateCompleted(UUID taskid) {
		if(taskid == null) {return null;}

		final TaskEntry taskEntry = removeAndGetCompleteTaskEntry(taskid, TableUpdateResponse.class);
		if(taskEntry == null) {return null;}

		final TableUpdateResponse resp = getResponse(taskEntry, TableUpdateResponse.class);
		if(resp == null) {return null;}

		RoutingServiceResult rs = RoutingServiceResult.Failed;
		if(resp.getResult() == JpTrustResult.Success)
			rs = RoutingServiceResult.Success;
		else if(resp.getResult() == JpTrustResult.NoDATA)
			rs = RoutingServiceResult.NotFound;
		else
			rs = RoutingServiceResult.Failed;

		final PositionUpdateInfo result =
			new PositionUpdateInfo(
				taskEntry.getTargetCallsign(),
				rs
			);

		return result;
	}

	@Override
	public UUID findRepeater(String repeaterCall, Header header) {
		if(!DSTARUtils.isValidCallsignFullLength(repeaterCall)) {return null;}

		return this.requestAreaPositionQuery(repeaterCall.toCharArray(), header);
	}

	@Override
	public RepeaterRoutingInfo getRepeaterInfo(UUID taskid) {
		if(taskid == null) {return null;}

		final TaskEntry taskEntry = removeAndGetCompleteTaskEntry(taskid, AreaPositionQueryResponse.class);
		if(taskEntry == null) {return null;}

		final AreaPositionQueryResponse resp = getResponse(taskEntry, AreaPositionQueryResponse.class);
		if(resp == null) {return null;}

		final RepeaterRoutingInfo result = new RepeaterRoutingInfo();
		result.setGatewayCallsign(String.valueOf(resp.getRepeater1Callsign()));
		result.setRepeaterCallsign(String.valueOf(resp.getRepeater2Callsign()));
		result.setGatewayAddress(resp.getGatewayAddress());

		if(
			CallSignValidator.isValidGatewayCallsign(resp.getRepeater1Callsign()) &&
			CallSignValidator.isValidRepeaterCallsign(resp.getRepeater2Callsign()) &&
			resp.getGatewayAddress() != null &&
			resp.getResult() == JpTrustResult.Success
		)
			result.setRoutingResult(RoutingServiceResult.Success);
		else if(resp.getResult() == JpTrustResult.NoDATA)
			result.setRoutingResult(RoutingServiceResult.NotFound);
		else
			result.setRoutingResult(RoutingServiceResult.Failed);

		return result;
	}

	@Override
	public UUID findUser(String userCall, Header header) {
		if(!DSTARUtils.isValidCallsignFullLength(userCall)) {return null;}

		return this.requestPositionQuery(userCall.toCharArray(), header);
	}

	@Override
	public UserRoutingInfo getUserInfo(UUID taskid) {
		if(taskid == null) {return null;}

		final TaskEntry taskEntry = removeAndGetCompleteTaskEntry(taskid, PositionQueryResponse.class);
		if(taskEntry == null) {return null;}

		final PositionQueryResponse resp = getResponse(taskEntry, PositionQueryResponse.class);
		if(resp == null) {return null;}

		final UserRoutingInfo result = new UserRoutingInfo();
//		result.setMyCallsign(String.valueOf(resp.getMyCallsign()));
		result.setYourCallsign(String.valueOf(resp.getYourCallsign()));
		char[] gatewayCall = resp.getRepeater1Callsign().clone();
//		assert gatewayCall != null && gatewayCall.length == 8;
		gatewayCall[DSTARDefines.CallsignFullLength - 1] = 'G';
		result.setGatewayCallsign(String.valueOf(gatewayCall));
		result.setRepeaterCallsign(String.valueOf(resp.getRepeater2Callsign()));
		result.setGatewayAddress(resp.getGatewayAddress());

		if(
			CallSignValidator.isValidUserCallsign(resp.getYourCallsign()) &&
			CallSignValidator.isValidGatewayCallsign(gatewayCallsign) &&
			CallSignValidator.isValidRepeaterCallsign(resp.getRepeater2Callsign()) &&
			resp.getGatewayAddress() != null &&
			resp.getResult() == JpTrustResult.Success
		)
			result.setRoutingResult(RoutingServiceResult.Success);
		else if(resp.getResult() == JpTrustResult.NoDATA)
			result.setRoutingResult(RoutingServiceResult.NotFound);
		else
			result.setRoutingResult(RoutingServiceResult.Failed);

		return result;
	}

	@Override
	public boolean isServiceTaskCompleted(UUID taskid) {
		if(taskid == null) {return false;}

		locker.lock();
		try {
			final TaskEntry taskEntry = processTasks.get(taskid);
			if(taskEntry == null) {return false;}

			if(taskEntry.getTaskStatus() == TaskStatus.Complete)
				return true;
			else
				return false;
		}finally {
			locker.unlock();
		}
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted() {
		return getServiceTaskCompleted(null);
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted(UUID taskid) {
		locker.lock();
		try {
			for(TaskEntry entry : this.processTasks.values()) {

				if(
					(taskid == null || entry.getId().equals(taskid)) &&
					entry.getTaskStatus() == TaskStatus.Complete
				) {
					JpTrustCommand response = entry.getResponseCommand();

					if(response == null) {continue;}

					if(response instanceof AreaPositionQueryResponse)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.FindRepeater);
					else if(response instanceof PositionQueryResponse)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.FindUser);
					else if(response instanceof TableUpdateResponse)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.PositionUpdate);
				}
			}
		}finally {
			locker.unlock();
		}

		return null;
	}

	@Override
	public boolean setProperties(RoutingServiceProperties properties) {
		if(properties == null) {return false;}

		setTrustAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				trustAddressPropertyName,
				defaultTrustAddress
			)
		);

		setTrustPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(), trustPortPropertyName, defaultTrustPort)
		);

		setKeepAliveSeconds(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				keepAliveSecondsPropertyName, defaultKeepAliveSeconds)
			);
		if(getKeepAliveSeconds() < 10) {setKeepAliveSeconds(10);}

		setDisableLogTransport(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				disableLogTransportPropertyName, defaultDisableLogTransport
			)
		);

		setUseProxyGateway(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				useProxyGatewayPropertyName, useProxyGatewayDefault
			)
		);

		setProxyGatewayAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				proxyGatewayAddressPropertyName, proxyGatewayAddressDefault
			)
		);

		setProxyPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				proxyPortPropertyName, proxyPortDefault
			)
		);

		logTransporter.setEnableLogTransport(!isDisableLogTransport());
		logTransporter.setTrustServerAddress(getTrustAddress());
		logTransporter.setGatewayCallsign(getGatewayCallsign());
		if(isUseProxyGateway())
			logTransporter.setProxyServerAddress(getProxyGatewayAddress());

		setQueryID(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				queryIDPropertyName, queryIDDefault
			)
		);

		setStatusTransmit(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				statusTransmitPropertyName, statusTransmitDefault
			)
		);

		setStatusServerAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				statusServerAddressPropertyName, statusServerAddressDefault
			)
		);

		setStatusServerPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				statusServerPortPropertyName, statusServerPortDefault
			)
		);

		return true;
	}

	@Override
	public RoutingServiceProperties getProperties(RoutingServiceProperties properties) {
		if(properties == null) {return null;}

		properties.getConfigurationProperties().setProperty(
			trustAddressPropertyName, getTrustAddress()
		);

		properties.getConfigurationProperties().setProperty(
			trustPortPropertyName, String.valueOf(getTrustPort())
		);

		properties.getConfigurationProperties().setProperty(
			disableLogTransportPropertyName, String.valueOf(isDisableLogTransport())
		);

		properties.getConfigurationProperties().setProperty(
			keepAliveSecondsPropertyName, String.valueOf(getKeepAliveSeconds())
		);

		return properties;
	}

	@Override
	public RoutingServiceTypes getServiceType() {
		return RoutingServiceTypes.JapanTrust;
	}

	@Override
	public final org.jp.illg.dstar.service.web.model.RoutingServiceStatusData createStatusDataInternal() {
		final JpTrustClientServiceStatusData status =
			new JpTrustClientServiceStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	public Class<? extends org.jp.illg.dstar.service.web.model.RoutingServiceStatusData> getStatusDataType() {
		return JpTrustClientServiceStatusData.class;
	}

	@Override
	protected boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeJpTrustClientService(this);
	}

	@Override
	public int getCountUserRecords() {
		return -1;
	}

	@Override
	public int getCountRepeaterRecords() {
		return -1;
	}

	@Override
	public boolean findUserRecord(
		@NonNull String userCallsignRegex,
		@NonNull QueryCallback<List<QueryUserResult>> callback
	) {
		return false;
	}

	@Override
	public boolean findRepeaterRecord(
		@NonNull String areaRepeaterCallsignRegex,
		@NonNull QueryCallback<List<QueryRepeaterResult>> callback
	) {
		return false;
	}

	@Override
	protected List<RoutingServiceServerStatus> getServerStatus() {

		final RoutingServiceServerStatus status =
			new RoutingServiceServerStatus(
				getServiceType(),
				getServiceStatusInternal(),
				isUseProxyGateway(),
				getProxyGatewayAddress(),
				getProxyPort(),
				getTrustAddress(),
				getTrustPort()
			);

		final List<RoutingServiceServerStatus> statusList = new ArrayList<>(1);

		statusList.add(status);

		return statusList;
	}

	private boolean isUseGatewayIPUpdate() {
		return isUseProxyGateway() || (!isUseProxyGateway() && getKeepAliveSeconds() >= 7200);
	}

	private ThreadProcessResult onStateInitialize() {
		if(isUseProxyGateway())
			toWaitState(10, TimeUnit.SECONDS, ProcessStates.GatewayIPUpdateRequest);
		else
			toWaitState(1, TimeUnit.SECONDS, ProcessStates.TaskEntryWait);

		processStateRetryCount = 0;

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateGatewayIPUpdateRequest() {
		if(isStateChanged) {
			if(isUseProxyGateway())
				trustAddressResolver.setHostname(getProxyGatewayAddress());
			else
				trustAddressResolver.setHostname(getTrustAddress());

			trustAddressResolver.getCurrentHostAddress()
			.ifPresentOrElse(new Consumer<InetAddress>() {
				@Override
				public void accept(InetAddress trustAddress) {
					JpTrustCommand request = new GatewayIPUpdateRequest();
					request.setRepeater1Callsign(getGatewayCallsign().toCharArray());

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader +
							"Send GatewayIPUpdate to " +
							(isUseProxyGateway() ? "proxy gateway" : "japan trust") +" server " + trustAddress + ".\n    " + request.toString()
						);
					}

					if(
						writeUDPPacket(
							trustChannel.getKey(),
							new InetSocketAddress(trustAddress, isUseProxyGateway() ? getProxyPort() : getTrustPort()),
							ByteBuffer.wrap(request.assembleCommandData())
						)
					) {
						processStateTimekeeper.setTimeoutTime(
							isUseProxyGateway() ? trustServerTimeoutMillisForProxy : trustServerTimeoutMillisForSingle,
							TimeUnit.MILLISECONDS
						);
						processStateTimekeeper.updateTimestamp();
					}
					else {
						if(processStateRetryCount < trustServerRetryLimit) {
							//0.1秒後に再試行
							toWaitState(100, TimeUnit.MILLISECONDS, ProcessStates.GatewayIPUpdateRequest);

							processStateRetryCount++;
						}
						else {
							if(log.isWarnEnabled()) {
								log.warn(
									logHeader +
									"Failed update gateway ip process. could not send packet to " +
									(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server."
								);
							}

							nextState = ProcessStates.TaskEntryWait;
							processStateRetryCount = 0;

							gatewayipUpdateIntervalTimeKeeper.setTimeoutTime(10, TimeUnit.MINUTES);
							gatewayipUpdateIntervalTimeKeeper.updateTimestamp();
						}
					}
				}
			}, new Runnable() {
				@Override
				public void run() {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Failed update gateway ip process. could not resolve dns for " +
							(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server."
						);
					}

					nextState = ProcessStates.TaskEntryWait;
					processStateRetryCount = 0;

					gatewayipUpdateIntervalTimeKeeper.setTimeoutTime(10, TimeUnit.MINUTES);
					gatewayipUpdateIntervalTimeKeeper.updateTimestamp();
				}
			});


		}
		else {
			GatewayIPUpdateResponse response = null;

			for(final Iterator<JpTrustCommand> it = this.recvCommands.iterator();it.hasNext();) {
				final JpTrustCommand recvCommand = it.next();
				it.remove();

				if(
					recvCommand.getCommandType() == CommandType.GatewayIPUpdate &&
					recvCommand instanceof GatewayIPUpdateResponse
				) {response = (GatewayIPUpdateResponse)recvCommand;}
			}

			if(response != null) {
				if(log.isTraceEnabled()) {
					log.trace(
						logHeader +
						"Receive GatewayIPUpdate response from " +
						(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server.\n    " + response.toString()
					);
				}

				if(response.getGatewayAddress() != null)
					setGlobalIP(new GlobalIPInfo(response.getGatewayAddress()));

				//成功
				nextState = ProcessStates.TaskEntryWait;
				processStateRetryCount = 0;

				trustAddressResolver.notifyAliveHostAddress();

				gatewayipUpdateIntervalTimeKeeper.setTimeoutTime(getKeepAliveSeconds(), TimeUnit.SECONDS);
				gatewayipUpdateIntervalTimeKeeper.updateTimestamp();
			}
			else if(processStateTimekeeper.isTimeout()) {
				//レスポンスが返ってこずにタイムアウト
				if(processStateRetryCount < trustServerRetryLimit) {
					toWaitState(100, TimeUnit.MILLISECONDS, ProcessStates.GatewayIPUpdateRequest);
					processStateRetryCount++;
				}
				else {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Failed update gateway ip process. no response from " +
							(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server."
						);
					}

					nextState = ProcessStates.TaskEntryWait;
					processStateRetryCount = 0;

					trustAddressResolver.notifyDeadHostAddress();
					trustAddressResolver.nextHostAddress();

					gatewayipUpdateIntervalTimeKeeper.setTimeoutTime(3, TimeUnit.MINUTES);
					gatewayipUpdateIntervalTimeKeeper.updateTimestamp();
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateTaskEntryWait() {
		if(isStateChanged) {

		}
		else {
			final Optional<TaskEntry> newTask =
				Stream.of(processTasks.values())
				.filter(new Predicate<TaskEntry>() {
					@Override
					public boolean test(TaskEntry task) {
						return task.getTaskStatus() == TaskStatus.Incomplete;
					}
				})
				.min(ComparatorCompat.comparingLong(new ToLongFunction<TaskEntry>() {
					@Override
					public long applyAsLong(TaskEntry task) {
						return task.getCreatedTimestamp();
					}
				}));

			if(newTask.isPresent() && trustRateLimiter.tryAcquire()) {
				processingTask = newTask.get();
				processingTask.setTaskStatus(TaskStatus.Processing);
				processingTask.getActivityTimer().updateTimestamp();
				nextState = ProcessStates.SendCommand;
				processStateRetryCount = 0;
			}
			else if(
				isUseGatewayIPUpdate() &&
				gatewayipUpdateIntervalTimeKeeper.isTimeout() &&
				trustRateLimiter.tryAcquire()
			) {
				gatewayipUpdateIntervalTimeKeeper.updateTimestamp();
				nextState = ProcessStates.GatewayIPUpdateRequest;
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSendCommand() {

		if(isStateChanged) {

			//キャッシュを捜索
			final JpTrustCommand cache =
				!gwRegistRequestReceived ? findActiveCache(processingTask.getRequestCommand()) : null;

			//キャッシュから見つかった場合には、キャッシュから返答する
			if(cache != null) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "return response from cache memory.\n    " + cache);

				processingTask.setResponseCommand(cache);
				processingTask.setTaskStatus(TaskStatus.Complete);
				dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

				processingTask = null;

				nextState = ProcessStates.TaskEntryWait;
				processStateRetryCount = 0;
			}
			else {
				if(isUseProxyGateway())
					trustAddressResolver.setHostname(getProxyGatewayAddress());
				else
					trustAddressResolver.setHostname(getTrustAddress());

				trustAddressResolver.getCurrentHostAddress()
				.ifPresentOrElse(new Consumer<InetAddress>() {
					@Override
					public void accept(InetAddress trustAddress) {
						//コマンドIDを生成
						if(getQueryID() != 0x0)
							commandID = getQueryID();
						else {
							if(log.isWarnEnabled())
								log.warn(logHeader + "QueryID is not set, using random queryID.");

							commandID = DSTARUtils.generateQueryID();
						}

						processingTask.getRequestCommand().setCommandIDInteger(commandID);

						final ByteBuffer txBuffer =
							ByteBuffer.wrap(processingTask.getRequestCommand().assembleCommandData());

						if(
							(isDebugQueryFailTest() && queryTxCount % 2 == 1) ||
							writeUDPPacket(
								trustChannel.getKey(),
								new InetSocketAddress(trustAddress, isUseProxyGateway() ? getProxyPort() : getTrustPort()),
								txBuffer
							)
						) {
							if(isUseProxyGateway())
								processStateTimekeeper.setTimeoutMillis(trustServerTimeoutMillisForProxy);
							else
								processStateTimekeeper.setTimeoutMillis(trustServerTimeoutMillisForSingle);

							processStateTimekeeper.updateTimestamp();

							if(log.isDebugEnabled()) {
								txBuffer.rewind();

								if(log.isDebugEnabled()) {
									log.debug(
										logHeader + "Send JpTrustCommand to " +
										(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server.\n" +
										"    " + processingTask.getRequestCommand().toString() + "\n" +
										FormatUtil.byteBufferToHexDump(txBuffer, 4)
									);
								}
							}
						}
						else {
							if(log.isDebugEnabled())
								log.debug(logHeader + "fail writeUDP().");

							processingTask.setTaskStatus(TaskStatus.Complete);
							dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

							processingTask = null;
							nextState = ProcessStates.TaskEntryWait;
						}

						queryTxCount++;
					}
				}, new Runnable() {
					@Override
					public void run() {
						if(log.isDebugEnabled())
							log.debug(logHeader + "Failed resolve trust server.");

						processingTask.setTaskStatus(TaskStatus.Complete);
						dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

						processingTask = null;
						nextState = ProcessStates.TaskEntryWait;
					}
				});
			}

		}
		else {
			if(processStateTimekeeper.isTimeout()) {
				if(
					!isDebugQueryFailTest() &&
					processStateRetryCount < trustServerRetryLimit
				) {
					toWaitState(100, TimeUnit.MILLISECONDS, ProcessStates.SendCommand);

					processStateRetryCount++;
				}
				else {
					trustAddressResolver.notifyDeadHostAddress();
					trustAddressResolver.nextHostAddress();

					//キャッシュを探して、無ければ諦める
					if(processingTask.getRequestCommand() instanceof PositionQueryRequest) {
						findUserRoutingCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getYourCallsign()),
							true
						)
						.ifPresentOrElse(
							new Consumer<PositionQueryResponse>() {
								@Override
								public void accept(PositionQueryResponse t) {
									processingTask.setResponseCommand(t);

									if(log.isInfoEnabled()) {
										log.info(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout and supplemented position query request command for user " +
											String.valueOf(t.getYourCallsign()) + " from cache memory."
										);
									}
								}
							},
							new Runnable() {
								@Override
								public void run() {
									processingTask.setResponseCommand(new PositionQueryResponse());

									if(log.isWarnEnabled()) {
										log.warn(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout user position query and user " +
											String.valueOf(processingTask.getRequestCommand().getYourCallsign()) +
											" was not found in cache memory."
										);
									}
								}
							}
						);
					}
					else if(processingTask.getRequestCommand() instanceof AreaPositionQueryRequest) {
						findRepeaterRoutingCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getYourCallsign()),
							true
						)
						.ifPresentOrElse(
							new Consumer<AreaPositionQueryResponse>() {
								@Override
								public void accept(AreaPositionQueryResponse t) {
									processingTask.setResponseCommand(t);

									if(log.isInfoEnabled()) {
										log.info(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout and supplemented area position query request command for repeater " +
											String.valueOf(t.getYourCallsign()) + " from cache memory."
										);
									}
								}
							},
							new Runnable() {
								public void run() {
									processingTask.setResponseCommand(new AreaPositionQueryResponse());

									if(log.isWarnEnabled()) {
										log.warn(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout area position query and repeater " +
											String.valueOf(processingTask.getRequestCommand().getYourCallsign()) +
											" was not found in cache memory."
										);
									}
								}
							}
						);
					}
					else if(processingTask.getRequestCommand() instanceof TableUpdateRequest) {
						findTableUpdateCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getMyCallsign()),
							String.valueOf(processingTask.getRequestCommand().getRepeater1Callsign()),
							true
						)
						.ifPresentOrElse(
							new Consumer<TableUpdateResponse>() {
								@Override
								public void accept(TableUpdateResponse t) {
									processingTask.setResponseCommand(t);

									if(log.isInfoEnabled()) {
										log.info(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout and supplemented table update request command for user " +
											String.valueOf(t.getMyCallsign()) + " from cache memory."
										);
									}
								}
							},
							new Runnable() {
								public void run() {
									processingTask.setResponseCommand(new TableUpdateResponse());

									if(log.isWarnEnabled()) {
										log.warn(
											logHeader +
											(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
											" server timeout table update request and user " +
											String.valueOf(processingTask.getRequestCommand().getMyCallsign()) +
											" was not found in cache memory."
										);
									}
								}
							}
						);
					}
					else {
						if(log.isWarnEnabled()) {
							log.warn(
								logHeader +
								(isUseProxyGateway() ? "Proxy gateway" : "Japan trust") +
								" server timeout and not returned response."
							);
						}

						processTasks.remove(this.processingTask.getId());
					}

					processingTask.setTaskStatus(TaskStatus.Complete);
					dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

					processingTask = null;
					nextState = ProcessStates.TaskEntryWait;
					processStateRetryCount = 0;

					gwRegistRequestReceived = false;
				}
			}
			else {
				for(final Iterator<JpTrustCommand> it = recvCommands.iterator();it.hasNext();) {
					final JpTrustCommand recvCommand = it.next();
					it.remove();

					if(recvCommand.getCommandIDInteger() != commandID) {
						if(log.isWarnEnabled()) {
							log.warn(
								logHeader +
								"Illegal response received, command id mismatch!\n" +
								"    " + recvCommand.toString()
							);
						}

						continue;
					}

					trustAddressResolver.notifyAliveHostAddress();

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader + "Receive JpTrustCommand from " +
							(isUseProxyGateway() ? "proxy gateway" : "japan trust") + " server " +
							recvCommand.getRemoteAddress() + ".\n    " +  recvCommand.toString()
						);
					}

					//GW登録要求を受信した場合には、GWIPを登録する
					if(
						!isUseProxyGateway() &&
						processingTask.getRequestCommand() instanceof TableUpdateRequest &&
						recvCommand instanceof TableUpdateResponse &&
						recvCommand.getResult() == JpTrustResult.GWRegistRequest &&
						!gwRegistRequestReceived
					) {
						if(log.isInfoEnabled())
							log.info(logHeader + "GW IP regist request received, going gw ip regist process.");

						//差し戻し
						processingTask.setTaskStatus(TaskStatus.Incomplete);
						processingTask = null;

						gwRegistRequestReceived = true;

						nextState = ProcessStates.GatewayIPUpdateRequest;
						processStateRetryCount = 0;

						break;
					}
					else if(
						(
							processingTask.getRequestCommand() instanceof AreaPositionQueryRequest &&
							recvCommand instanceof AreaPositionQueryResponse
						) ||
						(
							processingTask.getRequestCommand() instanceof GatewayIPUpdateRequest &&
							recvCommand instanceof GatewayIPUpdateResponse
						) ||
						(
							processingTask.getRequestCommand() instanceof PositionQueryRequest &&
							recvCommand instanceof PositionQueryResponse
						) ||
						(
							processingTask.getRequestCommand() instanceof TableUpdateRequest &&
							recvCommand instanceof TableUpdateResponse
						)
					) {
						processingTask.setResponseCommand(recvCommand);
						processingTask.setTaskStatus(TaskStatus.Complete);

						if(recvCommand.getResult() == JpTrustResult.Success) {
							//ゲートウェイのアドレスを更新
							if(
								recvCommand instanceof TableUpdateResponse &&
								recvCommand.getGatewayAddress() != null
							) {
								setGlobalIP(new GlobalIPInfo(recvCommand.getGatewayAddress()));
							}

							//ルーティングキャッシュへ追加
							addRoutingCache(recvCommand);

							if(	// ログ転送をリクエスト
								processingTask.getHeader() != null &&
								(
									recvCommand instanceof AreaPositionQueryResponse ||
									recvCommand instanceof PositionQueryResponse
								)
							) {
								logTransporter.addLogTransportEntry(processingTask.getHeader(), recvCommand);
							}
						}

						dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

						processingTask = null;
						nextState = ProcessStates.TaskEntryWait;
						processStateRetryCount = 0;

						gwRegistRequestReceived = false;

						break;
					}
				}
			}
		}

		if(processingTask != null)
			processingTask.getActivityTimer().updateTimestamp();

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(processStateTimekeeper.isTimeout()) {
			nextState = callbackState;
		}

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(long time, TimeUnit timeUnit, ProcessStates callbackState) {
		assert time > 0 && timeUnit != null;

		if(callbackState == null) {callbackState = ProcessStates.Initialize;}

		nextState = ProcessStates.TimeWait;

		processStateTimekeeper.setTimeoutTime(time, timeUnit);
		processStateTimekeeper.updateTimestamp();

		this.callbackState = callbackState;
	}

	private void cleanProcessTasks() {
		locker.lock();
		try {
			for(final Iterator<TaskEntry> it = this.processTasks.values().iterator();it.hasNext();) {
				final TaskEntry taskEntry = it.next();

				// ignore check processing task
				if(taskEntry == this.processingTask) {continue;}

				if(
					taskEntry.getTaskStatus() == TaskStatus.Complete &&
					taskEntry.getActivityTimer().isTimeout(60, TimeUnit.SECONDS)
				) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Query task removed for completed task, gateway was not read this task.");

					it.remove();
				}
				else if(taskEntry.getActivityTimer().isTimeout(30, TimeUnit.SECONDS)) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Query task timeout at illegal state = " + taskEntry.getTaskStatus());

					it.remove();
				}
			}
		}finally {
			locker.unlock();
		}
	}

	/**
	 * 受信バッファーからコマンドデータを解析抽出する
	 */
	private boolean analyzePacket(List<JpTrustCommand> receiveCommands) {
		assert receiveCommands != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for(Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator(); itBufferBytes.hasNext();) {
					final PacketInfo packetInfo = itBufferBytes.next();
					final int bufferLength = packetInfo.getPacketBytes();
					itBufferBytes.remove();

					if(bufferLength <= 0) {continue;}

					ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
					for(int i = 0; i < bufferLength; i++) {receivePacket.put(buffer.getBuffer().get());}
					BufferState.toREAD(receivePacket, BufferState.WRITE);

					boolean match = false;
					do {
						JpTrustCommand parsedCommand = null;
						if(
							//ゲートウェイIP更新リクエストへの返答か？
							(parsedCommand = parsePacket(gatewayIPUpdateResponse, receivePacket)) != null ||
							//管理サーバへ問い合わせへの返答か？
							(parsedCommand = parsePacket(positionQueryResponse, receivePacket)) != null ||
							//管理サーバへ問い合わせ(ゲート指定)への返答か？
							(parsedCommand = parsePacket(areaPositionQueryResponse, receivePacket)) != null ||
							//テーブル書き換え要求への返答か？
							(parsedCommand = parsePacket(tableUpdateResponse,receivePacket)) != null
						) {
							parsedCommand.setRemoteAddress(buffer.getRemoteAddress());

							receiveCommands.add(parsedCommand.clone());

							update = match = true;
						}else {
							match = false;
						}
					}while(match);
				}

				buffer.setUpdate(false);

			}finally {
				buffer.getLocker().unlock();
			}
		}

		return update;
	}

	/**
	 * コマンドデータを分解する
	 * @param command コマンドインスタンス
	 * @param buffer 受信バッファ
	 * @return データがあって分解できればコマンドを返す。分解できなければnull
	 */
	private JpTrustCommand parsePacket(JpTrustCommand command, ByteBuffer buffer) {
		assert command != null && buffer != null;

		command.clear();
		command = command.parseCommandData(buffer);
		if(command != null) {
			command.updateTimestamp();

			return command;
		}else {
			return null;
		}
	}

	private void closeTrustChannel(){
		if(this.trustChannel != null && this.trustChannel.getChannel().isOpen()) {
			try {
				this.trustChannel.getChannel().close();
				this.trustChannel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}

	private boolean addRoutingCache(final JpTrustCommand recvCommand) {
		boolean result = false;

		//キャッシュに追加
		if(recvCommand instanceof TableUpdateResponse) {
			final TableUpdateResponse resp = (TableUpdateResponse)recvCommand;

			final int cacheLimitTimeSeconds =
				baseCacheTimeLimitSeconds +
				new Random(System.currentTimeMillis()).nextInt(20) + 1;

			final TableUpdateCacheEntry entry =
				new TableUpdateCacheEntry(
					String.valueOf(resp.getMyCallsign()),
					String.valueOf(resp.getRepeater1Callsign()),
					String.valueOf(resp.getRepeater2Callsign()),
					resp.getGatewayAddress(),
					resp.getResult(),
					TimeUnit.SECONDS.toMillis(cacheLimitTimeSeconds)
				);

			result = addTableUpdateCache(entry);
		}
		else if(recvCommand instanceof AreaPositionQueryResponse) {
			final AreaPositionQueryResponse resp = (AreaPositionQueryResponse)recvCommand;

			final int cacheLimitTimeSeconds =
				baseCacheTimeLimitSeconds +
				new Random(System.currentTimeMillis()).nextInt(10) + 1;

			final AreaPositionCacheEntry entry =
				new AreaPositionCacheEntry(
					String.valueOf(resp.getYourCallsign()),
					String.valueOf(resp.getRepeater1Callsign()),
					String.valueOf(resp.getRepeater2Callsign()),
					resp.getGatewayAddress(),
					resp.getResult(),
					TimeUnit.SECONDS.toMillis(cacheLimitTimeSeconds)
				);

			result = addRepeaterRoutingCache(entry);
		}
		else if(recvCommand instanceof PositionQueryResponse) {
			final PositionQueryResponse resp = (PositionQueryResponse)recvCommand;

			final int cacheLimitTimeSeconds =
				baseCacheTimeLimitSeconds +
				new Random(System.currentTimeMillis()).nextInt(10) + 1;

			final PositionCacheEntry entry =
				new PositionCacheEntry(
					String.valueOf(resp.getYourCallsign()),
					String.valueOf(resp.getRepeater1Callsign()),
					String.valueOf(resp.getRepeater2Callsign()),
					resp.getGatewayAddress(),
					resp.getResult(),
					TimeUnit.SECONDS.toMillis(cacheLimitTimeSeconds)
				);

			result = addUserRoutingCache(entry);
		}

		return result;
	}

	private boolean addUserRoutingCache(PositionCacheEntry info) {
		assert info != null;

		for(final Iterator<PositionCacheEntry> it = userRoutingCache.iterator(); it.hasNext();) {
			final PositionCacheEntry data = it.next();

			if(info.getYourCallsign().equals(data.getYourCallsign())) {it.remove();}
		}

		while(userRoutingCache.size() >= cacheEntryLimit) {
			Stream.of(userRoutingCache)
			.min(ComparatorCompat.comparingLong(new ToLongFunction<PositionCacheEntry>() {
				@Override
				public long applyAsLong(PositionCacheEntry cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}
			}))
			.ifPresent(new Consumer<PositionCacheEntry>() {
				@Override
				public void accept(PositionCacheEntry cache) {
					userRoutingCache.remove(cache);
				}
			});
		}

		info.getTimeKeeper().updateTimestamp();

		return userRoutingCache.add(info);
	}

	private boolean removeUserRoutingCache(final String userCallsign, final InetAddress gatewayAddress){
		assert userCallsign != null;

		for(
			PositionCacheEntry entry :
				findUserRoutingCache(userCallsign)
				.filter(new Predicate<PositionCacheEntry>() {
					@Override
					public boolean test(PositionCacheEntry cache) {
						return !cache.getGatewayIP().equals(gatewayAddress);
					}
				})
				.toList()
		) {
			userRoutingCache.remove(entry);
		}

		return true;
	}

	private @NonNull Stream<PositionCacheEntry> findUserRoutingCache(final String userCallsign){
		assert userCallsign != null;

		return Stream.of(userRoutingCache)
			.filter(new Predicate<PositionCacheEntry>() {
				@Override
				public boolean test(PositionCacheEntry value) {
					return userCallsign.equals(String.valueOf(value.getYourCallsign()));
				}
			});
	}

	private @NonNull Optional<PositionQueryResponse> findUserRoutingCacheResponse(
		final String userCallsign,
		final boolean allowTimeoutCache
	){
		return findUserRoutingCache(userCallsign)
			.filter(new Predicate<PositionCacheEntry>() {
				@Override
				public boolean test(PositionCacheEntry cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.comparingLong(new ToLongFunction<PositionCacheEntry>() {
				@Override
				public long applyAsLong(PositionCacheEntry cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}
			}))
			.map(new Function<PositionCacheEntry, PositionQueryResponse>(){
				@Override
				public PositionQueryResponse apply(PositionCacheEntry cache) {
					final PositionQueryResponse response = new PositionQueryResponse();

					ArrayUtil.copyOf(response.getYourCallsign(), cache.getYourCallsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater1Callsign(), cache.getRepeater1Callsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater2Callsign(), cache.getRepeater2Callsign().toCharArray());
					response.setGatewayAddress(cache.getGatewayIP());
					response.setResult(cache.getQueryResult());

					return response;
				}
			});
	}

	private boolean addRepeaterRoutingCache(AreaPositionCacheEntry info) {
		assert info != null;

		for(final Iterator<AreaPositionCacheEntry> it = repeaterRoutingCache.iterator(); it.hasNext();) {
			final AreaPositionCacheEntry data = it.next();

			if(info.getYourCallsign().equals(data.getYourCallsign())) {it.remove();}
		}

		while(repeaterRoutingCache.size() >= cacheEntryLimit) {
			Stream.of(repeaterRoutingCache)
			.min(ComparatorCompat.comparingLong(new ToLongFunction<AreaPositionCacheEntry>() {
				@Override
				public long applyAsLong(AreaPositionCacheEntry cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}
			}))
			.ifPresent(new Consumer<AreaPositionCacheEntry>() {
				@Override
				public void accept(AreaPositionCacheEntry cache) {
					repeaterRoutingCache.remove(cache);
				}
			});
		}

		info.getTimeKeeper().updateTimestamp();

		return repeaterRoutingCache.add(info);
	}

	private @NonNull Stream<AreaPositionCacheEntry> findRepeaterRoutingCache(final String repeaterCallsign){
		assert repeaterCallsign != null;

		return Stream.of(repeaterRoutingCache)
			.filter(new Predicate<AreaPositionCacheEntry>() {
				@Override
				public boolean test(AreaPositionCacheEntry value) {
					return repeaterCallsign.equals(String.valueOf(value.getYourCallsign()));
				}
			});
	}

	private @NonNull Optional<AreaPositionQueryResponse> findRepeaterRoutingCacheResponse(
		final String repeaterCallsign,
		final boolean allowTimeoutCache
	){
		return findRepeaterRoutingCache(repeaterCallsign)
			.filter(new Predicate<AreaPositionCacheEntry>() {
				@Override
				public boolean test(AreaPositionCacheEntry cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.<AreaPositionCacheEntry>comparingLong(
				new ToLongFunction<AreaPositionCacheEntry>() {
					@Override
					public long applyAsLong(AreaPositionCacheEntry cache) {
						return cache.getTimeKeeper().getTimestampMilis();
					}
				}
			))
			.map(new Function<AreaPositionCacheEntry, AreaPositionQueryResponse>() {
				@Override
				public AreaPositionQueryResponse apply(AreaPositionCacheEntry cache) {
					final AreaPositionQueryResponse response = new AreaPositionQueryResponse();

					ArrayUtil.copyOf(response.getYourCallsign(), cache.getYourCallsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater1Callsign(), cache.getRepeater1Callsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater2Callsign(), cache.getRepeater2Callsign().toCharArray());
					response.setGatewayAddress(cache.getGatewayIP());
					response.setResult(cache.getQueryResult());

					return response;
				}
			});
	}

	private boolean addTableUpdateCache(TableUpdateCacheEntry info) {
		assert info != null;

		for(final Iterator<TableUpdateCacheEntry> it = tableUpdateCache.iterator(); it.hasNext();) {
			final TableUpdateCacheEntry data = it.next();

			if(info.getMyCallsign().equals(data.getMyCallsign())) {it.remove();}
		}

		while(tableUpdateCache.size() >= cacheEntryLimit) {
			Stream.of(tableUpdateCache)
			.min(ComparatorCompat.comparingLong(new ToLongFunction<TableUpdateCacheEntry>() {
				@Override
				public long applyAsLong(TableUpdateCacheEntry cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}
			}))
			.ifPresent(new Consumer<TableUpdateCacheEntry>() {
				@Override
				public void accept(TableUpdateCacheEntry cache) {
					tableUpdateCache.remove(cache);
				}
			});
		}

		info.getTimeKeeper().updateTimestamp();

		return tableUpdateCache.add(info);
	}

	private @NonNull Stream<TableUpdateCacheEntry> findTableUpdateCache(
		final String userCallsign, final String repeaterCallsign
	){
		return Stream.of(tableUpdateCache)
			.filter(new Predicate<TableUpdateCacheEntry>() {
				@Override
				public boolean test(TableUpdateCacheEntry value) {
					final boolean match =
						(
							userCallsign == null ||
							userCallsign.equals(String.valueOf(value.getMyCallsign()))
						) &&
						(
							repeaterCallsign == null ||
							repeaterCallsign.equals(String.valueOf(value.getRepeater1Callsign()))
						);

					return match;
				}
			});
	}

	private @NonNull Optional<TableUpdateResponse> findTableUpdateCacheResponse(
		final String userCallsign, final String repeaterCallsign,
		final boolean allowTimeoutCache
	){
		return findTableUpdateCache(userCallsign, repeaterCallsign)
			.filter(new Predicate<TableUpdateCacheEntry>() {
				@Override
				public boolean test(TableUpdateCacheEntry cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.<TableUpdateCacheEntry>comparingLong(
				new ToLongFunction<TableUpdateCacheEntry>() {
					@Override
					public long applyAsLong(TableUpdateCacheEntry cache) {
						return cache.getTimeKeeper().getTimestampMilis();
					}
				}
			))
			.map(new Function<TableUpdateCacheEntry, TableUpdateResponse>(){
				@Override
				public TableUpdateResponse apply(TableUpdateCacheEntry cache) {
					final TableUpdateResponse response = new TableUpdateResponse();

					ArrayUtil.copyOf(response.getMyCallsign(), cache.getMyCallsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater1Callsign(), cache.getRepeater1Callsign().toCharArray());
					ArrayUtil.copyOf(response.getRepeater2Callsign(), cache.getRepeater2Callsign().toCharArray());
					response.setGatewayAddress(cache.getGatewayIP());
					response.setResult(cache.getQueryResult());

					return response;
				}
			});
	}

	private JpTrustCommand findActiveCache(final JpTrustCommand requestCommand) {
		Optional<JpTrustCommand> cache = Optional.empty();

		if(processingTask.getRequestCommand() instanceof TableUpdateRequest) {
			final String myCallsign =
				String.valueOf(processingTask.getRequestCommand().getMyCallsign());
			final String repeaterCallsign =
				String.valueOf(processingTask.getRequestCommand().getRepeater1Callsign());

			cache = findTableUpdateCacheResponse(myCallsign, repeaterCallsign, false)
			.map(new Function<TableUpdateResponse, JpTrustCommand>(){
				@Override
				public JpTrustCommand apply(TableUpdateResponse t) {
					return t;
				}
			});
		}
		else if(processingTask.getRequestCommand() instanceof PositionQueryRequest) {
			final String yourCallsign =
				String.valueOf(processingTask.getRequestCommand().getYourCallsign());

			cache = findUserRoutingCacheResponse(yourCallsign, false)
			.map(new Function<PositionQueryResponse, JpTrustCommand>() {
				@Override
				public JpTrustCommand apply(PositionQueryResponse t) {
					return t;
				}
			});
		}
		else if(processingTask.getRequestCommand() instanceof AreaPositionQueryRequest) {
			final String repeaterCallsign =
				String.valueOf(processingTask.getRequestCommand().getYourCallsign());

			cache = findRepeaterRoutingCacheResponse(repeaterCallsign, false)
			.map(new Function<AreaPositionQueryResponse, JpTrustCommand>() {
				@Override
				public JpTrustCommand apply(AreaPositionQueryResponse t) {
					return t;
				}
			});
		}

		return cache.isPresent() ? cache.get() : null;
	}

	private void statusProcess() {
		if(
			isStatusTransmit() &&
			statusProcessIntervalTimekeeper.isTimeout(10, TimeUnit.SECONDS)
		) {
			statusLocker.lock();
			try {
				for(
					final Iterator<StatusRepeaterEntry> it = statusRepeaters.values().iterator();
					it.hasNext();
				) {
					final StatusRepeaterEntry entry = it.next();

					if(entry.getWatchdogTimekeeper().isTimeout()) {
						it.remove();

						//管理しているレピータが無くなったら、ログオフを送信する
						if(statusRepeaters.size() == 0) {sendStatusLogoff();}
					}
					else if(entry.getKeepaliveTimekeeper().isTimeout(5, TimeUnit.MINUTES)) {
						sendStatusKeepalive(entry);
						entry.setKeepaliveTransmitted(true);

						entry.getKeepaliveTimekeeper().updateTimestamp();
					}
				}

				//タイムアウトしたフレームを削除
				for(
					final Iterator<StatusFrameEntry> it = statusFrameEntries.values().iterator();
					it.hasNext();
				) {
					final StatusFrameEntry entry = it.next();

					if(entry.activityTimekeeper.isTimeout(1, TimeUnit.HOURS)) {
						it.remove();

						if(log.isDebugEnabled())
							log.debug(logHeader + "Remove timeout status frame entry = " + String.format("0x%04X", entry.frameID));
					}
				}
			}finally {
				statusLocker.unlock();
			}

			statusProcessIntervalTimekeeper.updateTimestamp();
		}
	}

	private void updateWatchdogStatusRepeater(final String repeaterCallsign, final String message) {
		statusLocker.lock();
		try {
			final StatusRepeaterEntry entry = statusRepeaters.get(repeaterCallsign);
			if(entry != null) {
				entry.getWatchdogTimekeeper().updateTimestamp();
			}
			else {
				if(statusRepeaters.size() == 0) {
					sendStatusLogin();
					isStatusLoggedin = true;
				}

				final StatusRepeaterEntry newEntry = new StatusRepeaterEntry(repeaterCallsign, 600, 0);
				newEntry.setMessage(message != null ? message : "");
				statusRepeaters.put(
					repeaterCallsign, newEntry
				);
			}
		}finally {
			statusLocker.unlock();
		}
	}

	private boolean sendStatusLogin() {
		final StatusLogin login = createStatusHeader(new StatusLogin());
		ArrayUtil.copyOf(login.getUserID(), createStatusUserID().toCharArray());

		return sendStatusPacket(login, 1);
	}

	private boolean sendStatusLogoff() {
		final StatusLogoff logoff = createStatusHeader(new StatusLogoff());
		ArrayUtil.copyOf(logoff.getCallsign(), DSTARUtils.formatFullCallsign(getGatewayCallsign(), ' ').toCharArray());

		return sendStatusPacket(logoff, 2);
	}

	private String createStatusUserID() {
		return DSTARUtils.formatFullCallsign(getGatewayCallsign(), ' ').trim() + "-" + String.format("%05d", id);
	}

	private boolean sendStatusKeepalive(final StatusRepeaterEntry entry) {
		final StatusKeepAlive keepalive = createStatusHeader(new StatusKeepAlive());
		ArrayUtil.copyOf(keepalive.getModuleName(), entry.getRepeaterCallsign().toCharArray());
		ArrayUtil.copyOf(
			keepalive.getCallsign(),
			DSTARUtils.formatFullCallsign(getGatewayCallsign(), ' ').toCharArray()
		);
		ArrayUtil.copyOf(keepalive.getVersion(), entry.getMessage().toCharArray());

		return sendStatusPacket(keepalive, 1);
	}

	private <ST extends StatusBase> ST createStatusHeader(final ST packet) {
		packet.setIpAddress(getGlobalIP() != null ? getGlobalIP().getGlobalIP() : null);
		packet.setEntryUpdateTime(System.currentTimeMillis() / 1000);

		return packet;
	}

	private <ST extends StatusBase> boolean sendStatusPacket(
		final ST statusPacket, final int transmitCount
	) {
		if(trustChannel == null) {return false;}

		final ByteBuffer buffer = statusPacket.assemblePacket();
		if(buffer == null) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Failed assemble packet..." + statusPacket.toString());
			}
			return false;
		}

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"Sending " + statusPacket.getClass().getSimpleName() +  " packet to dstatus server...\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
			buffer.rewind();
		}

		final InetSocketAddress dstAddress =
			new InetSocketAddress(getStatusServerAddress(), getStatusServerPort());
		if(dstAddress.isUnresolved()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Failed to resolve dstatus server address..." + dstAddress);

			return false;
		}

		boolean success = true;
		for(int c = 0; c < transmitCount; c++) {
			buffer.rewind();

			if(
				!writeUDPPacket(
					trustChannel.getKey(),
					dstAddress,
					buffer
				)
			) {success = false;}
		}

		return success;
	}

	private boolean createStatusFrameEntry(final int frameID) {
		statusLocker.lock();
		try {
			StatusFrameEntry frameEntry = statusFrameEntries.get(frameID);

			if(frameEntry != null) {return false;}

			frameEntry = new StatusFrameEntry(frameID);
			frameEntry.activityTimekeeper.updateTimestamp();

			return statusFrameEntries.put(frameID, frameEntry) == null;
		}finally {
			statusLocker.unlock();
		}
	}

	private boolean updateStatusFrameEntry(final int frameID) {
		statusLocker.lock();
		try {
			StatusFrameEntry frameEntry = statusFrameEntries.get(frameID);

			if(frameEntry != null) {
				frameEntry.activityTimekeeper.updateTimestamp();

				return true;
			}
			else {
				return false;
			}
		}finally {
			statusLocker.unlock();
		}
	}

	private boolean removeStatusFrameEntry(final int frameID) {
		statusLocker.lock();
		try {
			return statusFrameEntries.remove(frameID) != null;
		}finally {
			statusLocker.unlock();
		}
	}

	private TaskEntry removeAndGetCompleteTaskEntry(final UUID id, final Class<?> queryClass) {
		locker.lock();
		try {
			TaskEntry taskEntry = processTasks.get(id);

			if(
				taskEntry == null ||
				taskEntry.getTaskStatus() != TaskStatus.Complete ||
				taskEntry.getResponseCommand() == null ||
				taskEntry.getResponseCommand().getClass() != queryClass
			) {return null;}

			processTasks.remove(id);

			return taskEntry;

		}finally {
			locker.unlock();
		}
	}

	private<T extends JpTrustCommand> T getResponse(final TaskEntry taskEntry, Class<T> queryClass){
		try {
			return queryClass.cast(taskEntry.getResponseCommand());
		}catch(ClassCastException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Class cast exception occurred.", ex);

			return null;
		}
	}

	private RoutingServiceStatus getServiceStatusInternal() {
		if(this.isRunning()) {
			switch(currentState) {
			case Initialize:
				return RoutingServiceStatus.InitializingService;

			case GatewayIPUpdateRequest:
			case TimeWait:
				return RoutingServiceStatus.TemporaryDisabled;

			case TaskEntryWait:
			case SendCommand:
				return RoutingServiceStatus.InService;

			default:
				return RoutingServiceStatus.OutOfService;
			}
		}
		else
			return RoutingServiceStatus.OutOfService;
	}
}
