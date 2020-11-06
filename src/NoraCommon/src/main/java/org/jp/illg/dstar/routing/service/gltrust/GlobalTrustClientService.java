package org.jp.illg.dstar.routing.service.gltrust;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.jp.illg.dstar.routing.service.gltrust.model.AreaPositionQuery;
import org.jp.illg.dstar.routing.service.gltrust.model.CommandType;
import org.jp.illg.dstar.routing.service.gltrust.model.GlobalTrustCommand;
import org.jp.illg.dstar.routing.service.gltrust.model.PositionQuery;
import org.jp.illg.dstar.routing.service.gltrust.model.TableUpdate;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGlobalTrustClientHandler;
import org.jp.illg.dstar.service.web.model.GlobalTrustClientServiceStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.BufferState;
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
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalTrustClientService
extends RoutingServiceBase<BufferEntry>
implements RoutingService, WebRemoteControlGlobalTrustClientHandler
{

	/**
	 * キャッシュ生存秒数
	 */
	private static final long baseCacheTimeLimitSeconds = 30;

	/**
	 * 管理サーバ応答タイムアウト(ms)
	 */
	private static final long trustServerTimeoutMillis = 1000;

	/**
	 * サーバリトライ回数
	 */
	private static final int trustServerRetryLimit = 1;

	private static final int cacheEntryLimit = 20;

	private final String logHeader;

/*
	protected class ReceiveBufferEntry extends BufferEntry
	{
		public ReceiveBufferEntry() {
			super();
		}
	}
*/

	private static enum TaskStatus{
		Incomplete,
		Processing,
		Complete,
		;
	}

	private class TaskEntry{
		private UUID id;
		private TaskStatus taskStatus;
		private long createdTimestamp;
		private long completedTimestamp;
		private GlobalTrustCommand requestCommand;
		private GlobalTrustCommand responseCommand;

		@Getter
		private String targetCallsign;

		@Setter
		@Getter
		private Header header;

		private TaskEntry() {
			super();
			this.setId(UUID.randomUUID());
			this.setTaskStatus(TaskStatus.Incomplete);
			this.setCreatedTimestamp(System.currentTimeMillis());
			this.setCompletedTimestamp(0);
		}

		public TaskEntry(
			@NonNull String targetCallsign, @NonNull final GlobalTrustCommand requestCommand
		) {
			this();

			this.targetCallsign = targetCallsign;
			this.setRequestCommand(requestCommand);
		}

		public UUID getId() {
			return id;
		}

		private void setId(UUID id) {
			this.id = id;
		}

		public TaskStatus getTaskStatus() {
			return taskStatus;
		}

		public void setTaskStatus(TaskStatus taskStatus) {
			this.taskStatus = taskStatus;
			if(taskStatus == TaskStatus.Complete)
				this.updateCompletedTimestamp();
		}

		public GlobalTrustCommand getRequestCommand() {
			return requestCommand;
		}

		public void setRequestCommand(GlobalTrustCommand requestCommand) {
			this.requestCommand = requestCommand;
		}

		public GlobalTrustCommand getResponseCommand() {
			return responseCommand;
		}

		public void setResponseCommand(GlobalTrustCommand responseCommand) {
			this.responseCommand = responseCommand;
		}

		public long getCreatedTimestamp() {
			return createdTimestamp;
		}

		private void setCreatedTimestamp(long createdTimestamp) {
			this.createdTimestamp = createdTimestamp;
		}

		public long getCompletedTimestamp() {
			return completedTimestamp;
		}

		private void setCompletedTimestamp(long completedTimestamp) {
			this.completedTimestamp = completedTimestamp;
		}

		public void updateCompletedTimestamp() {
			this.setCompletedTimestamp(System.currentTimeMillis());
		}
	}

	@Data
	@AllArgsConstructor
	private static class CacheEntry<T>{
		private final long createTime;
		private final Timer timeKeeper;
		private final T command;

		public CacheEntry(@NonNull T command, final long timeoutMillis) {
			super();

			createTime = System.currentTimeMillis();
			timeKeeper = new Timer(timeoutMillis);
			timeKeeper.updateTimestamp();
			this.command = command;
		}
	}


	private SocketIOEntryUDP trustChannel;

	private static enum ProcessStates{
		Initialize,
		Idle,
		SendCommand,
		TimeWait,
		;
	}
	private ProcessStates callbackState;
	private ProcessStates currentState;
	private ProcessStates nextState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	private final Timer processStateTimekeeper;
	private int processStateRetryCount;

	private final Map<UUID, TaskEntry> processTasks;
	private TaskEntry processingTask;

	private List<GlobalTrustCommand> recvCommands;

	private final List<CacheEntry<TableUpdate>> positionUpdateCache;
	private final List<CacheEntry<PositionQuery>> userRoutingCache;
	private final List<CacheEntry<AreaPositionQuery>> repeaterRoutingCache;
	private final Lock routingCacheLocker;


	private final PositionQuery positionQueryResponse = new PositionQuery();
	private final AreaPositionQuery areaPositionQueryResponse = new AreaPositionQuery();
	private final TableUpdate tableUpdateResponse = new TableUpdate();

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String trustAddress;
	public static final String trustAddressPropertyName = "ServerAddress";
	private static final String trustAddressDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int trustHeardPort;
	public static final String trustHeardPortPropertyName = "ServerHeardPort";
	private static final int trustHeardPortDefault = 12346;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int trustQueryPort;
	public static final String trustQueryPortPropertyName = "ServerQueryPort";
	private static final int trustQueryPortDefault = 12345;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int keepAliveSeconds;
	public static final String keepAliveSecondsPropertyName = "KeepAliveSeconds";
	private static final int defaultKeepAliveSeconds = 60;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean logTransport;
	public static final String logTransportPropertyName = "LogTransport";
	private static final boolean defaultLogTransport = true;
/*
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
*/

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetAddress gatewayGlobalAddress;

	private final DNSRoundrobinUtil trustAddressResolver;

	private int processingFrameID;


	public GlobalTrustClientService(
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

	public GlobalTrustClientService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull DSTARGateway gateway,
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
			GlobalTrustClientService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteAddressOnly
		);

		logHeader = this.getClass().getSimpleName() + " : ";

		positionUpdateCache = new LinkedList<>();
		userRoutingCache = new LinkedList<>();
		repeaterRoutingCache = new LinkedList<>();
		routingCacheLocker = new ReentrantLock();

		currentState = ProcessStates.Initialize;
		nextState = ProcessStates.Initialize;
		callbackState = ProcessStates.Initialize;

		processStateTimekeeper = new Timer();

		trustChannel = null;
		setTrustAddress(trustAddressDefault);
		setTrustHeardPort(trustHeardPortDefault);
		setTrustQueryPort(trustQueryPortDefault);

		setLogTransport(defaultLogTransport);

		setKeepAliveSeconds(defaultKeepAliveSeconds);
/*
		setUseProxyGateway(useProxyGatewayDefault);
		setProxyGatewayAddress(proxyGatewayAddressDefault);
		setProxyPort(proxyPortDefault);
*/
		recvCommands = new LinkedList<GlobalTrustCommand>();

		processTasks = new LinkedHashMap<UUID, TaskEntry>();
		processingTask = null;

		trustAddressResolver = new DNSRoundrobinUtil();

		processingFrameID = 0x0000;

		setGatewayGlobalAddress(null);
	}

	@Override
	protected List<RoutingServiceServerStatus> getServerStatus() {

		final RoutingServiceServerStatus status =
			new RoutingServiceServerStatus(
				getServiceType(),
				getServiceStatusInternal(),
				false,
				"",
				-1,
				getTrustAddress(),
				getTrustQueryPort()
			);

		final List<RoutingServiceServerStatus> statusList = new ArrayList<>(1);

		statusList.add(status);

		return statusList;
	}

	private void toWaitState(long time, TimeUnit timeUnit, ProcessStates callbackState) {
		assert time > 0 && timeUnit != null;

		if(callbackState == null) {callbackState = ProcessStates.Initialize;}

		nextState = ProcessStates.TimeWait;

		processStateTimekeeper.setTimeoutTime(time, timeUnit);
		processStateTimekeeper.updateTimestamp();

		this.callbackState = callbackState;
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
						trustChannel = getSocketIO().registUDP(
							new InetSocketAddress(0), GlobalTrustClientService.this.getHandler(),
							GlobalTrustClientService.this.getClass().getSimpleName() + "@" +
							getTrustAddress() + ":" + getTrustHeardPort() + "(" + getTrustQueryPort() + ")"
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

		return true;
	}

	public void stop() {
		super.stop();

		closeTrustChannel();
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		return ThreadProcessResult.NoErrors;

	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeTrustChannel();

		synchronized(processTasks) {
			currentState = ProcessStates.Initialize;

			this.recvCommands.clear();

			this.processTasks.clear();
			this.processingTask = null;
		}
	}

	@Override
	protected boolean isCanSleep() {
		synchronized(processTasks) {
			for(final TaskEntry entry : processTasks.values()) {
				if(entry.getTaskStatus() != TaskStatus.Complete)
					return false;
			}
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
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
		final StringBuffer sb = new StringBuffer(this.getClass().getSimpleName() + " socket error.");
		if(localAddress != null) {sb.append("\nLocal=" + localAddress.toString());}
		if(remoteAddress != null) {sb.append("\nRemote=" + remoteAddress.toString());}

		if(log.isWarnEnabled())
			log.warn(logHeader + sb.toString(), ex);
	}

	@Override
	public ThreadProcessResult processRoutingService() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		//バッファを解析してコマンドを抽出
		analyzePacket(this.recvCommands);

//		cleanupReceiveBufferEntry();


		boolean reProcess;
		do {
			reProcess = false;

			setStateChanged(currentState != nextState);
			currentState = nextState;

			switch(currentState) {
			case Initialize:
				processResult = onStateInitialize();
				break;

			case Idle:{
				processResult = onIdle();
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

		cleanProcessTasks();

		return processResult;
	}

	@Override
	public void updateCache(@NonNull String myCallsign, @NonNull InetAddress gatewayAddress) {

	}

	public UUID requestAreaPositionQuery(char[] yourCall, Header header) {
		assert yourCall != null && yourCall.length == 8;
		if(yourCall == null || yourCall.length != 8) {return null;}

		if(!this.isRunning()) {return null;}

		final AreaPositionQuery request = new AreaPositionQuery();
		request.setYourCallsign(yourCall);

		final TaskEntry taskEntry = new TaskEntry(String.valueOf(yourCall), request);
		taskEntry.setHeader(header != null ? header.clone(): null);

		synchronized(this.processTasks) {
			this.processTasks.put(taskEntry.getId(), taskEntry);
		}

		return taskEntry.getId();
	}
/*
	public GlobalTrustCommand getAreaPositionQueryResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		AreaPositionQuery resp = getQuery(id, AreaPositionQuery.class);

		return resp;
	}
*/
	public UUID requestPositionQuery(char[] yourCallsign, Header header) {
		assert yourCallsign != null && yourCallsign.length == 8;
		if(yourCallsign == null || yourCallsign.length != 8) {return null;}

		if(!this.isRunning()) {return null;}

		final PositionQuery request = new PositionQuery();
		request.setYourCallsign(yourCallsign);

		final TaskEntry taskEntry = new TaskEntry(String.valueOf(yourCallsign), request);
		taskEntry.setHeader(header != null ? header.clone() : null);

		synchronized(this.processTasks) {
			this.processTasks.put(taskEntry.getId(), taskEntry);
		}

		return taskEntry.getId();
	}
/*
	public GlobalTrustCommand getPositionQueryResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		PositionQuery resp = getQuery(id, PositionQuery.class);

		return resp;
	}
*/
	public UUID requestTableUpdate(char[] myCallsign, char[] repeater1Callsign, char[] repeater2Callsign) {
		if(
			!DSTARUtils.isValidCallsignFullLength(myCallsign, repeater1Callsign, repeater2Callsign)
		) {return null;}

		if(!this.isRunning()) {return null;}

		final TableUpdate request = new TableUpdate();
		request.setRepeater2Callsign(repeater2Callsign);
		request.setRepeater1Callsign(repeater1Callsign);
		request.setMyCallsign(myCallsign);

		final TaskEntry taskEntry = new TaskEntry(String.valueOf(myCallsign), request);

		synchronized(this.processTasks) {
			this.processTasks.put(taskEntry.getId(), taskEntry);
		}

		return taskEntry.getId();
	}
/*
	public GlobalTrustCommand getTableUpdateResponse(UUID id) {
		assert id != null;
		if(id == null) {return null;}

		TableUpdate resp = getQuery(id, TableUpdate.class);

		return resp;
	}
*/

	@Override
	public boolean kickWatchdog(String callsign, String statusMessage) {
		if(log.isDebugEnabled())
			log.debug(logHeader + getServiceType().getTypeName() + " is not specification watchdog.");

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

		return this.requestTableUpdate(
				myCall.toCharArray(),
				repeater1.toCharArray(),
				repeater2.toCharArray()
		);
	}

	@Override
	public boolean sendStatusUpdate(
		final int frameID,
		final String myCall, String myCallExt,
		final String yourCall,
		final String repeater1, String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		final String networkDestination,
		final String txMessage,
		final double latitude,
		final double longitude
	) {
		if(log.isDebugEnabled())
			log.debug(logHeader + getServiceType().getTypeName() + " is not send heard with transmit message.");

		return true;
	}

	@Override
	public boolean sendStatusAtPTTOn(
		final int frameID,
		final String myCall, String myCallExt,
		final String yourCall,
		final String repeater1, String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		final String networkDestination,
		final String txMessage,
		final double latitude,
		final double longitude
	) {
		if(log.isDebugEnabled())
			log.debug(logHeader + getServiceType().getTypeName() + " is not send heard with transmit message.");

		return true;
	}

	@Override
	public boolean sendStatusAtPTTOff(
		final int frameID,
		final String myCall, String myCallExt,
		final String yourCall,
		final String repeater1, String repeater2,
		final byte flag1, final byte flag2, final byte flag3,
		final String networkDestination,
		final String txMessage,
		final double latitude,
		final double longitude,
		final int numDvFrames,
		final int numDvSlientFrames,
		final int numBitErrors
	) {
		if(log.isDebugEnabled())
			log.debug(logHeader + getServiceType().getTypeName() + " is not supported send heard with status message.");

		return true;
	}

	@Override
	public PositionUpdateInfo getPositionUpdateCompleted(UUID taskid) {
		if(taskid == null) {return null;}

		final TaskEntry taskEntry = removeGetTaskEntry(taskid, TableUpdate.class);
		if(taskEntry == null) {return null;}

		final TableUpdate resp = getResponse(taskEntry, TableUpdate.class);
		if(resp == null) {return null;}

		RoutingServiceResult routingResult = RoutingServiceResult.Failed;
		if(resp.isValid())
			routingResult = RoutingServiceResult.Success;
		else
			routingResult = RoutingServiceResult.Failed;

		final PositionUpdateInfo result =
			new PositionUpdateInfo(taskEntry.getTargetCallsign(), routingResult);

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

		final TaskEntry taskEntry = removeGetTaskEntry(taskid, AreaPositionQuery.class);
		if(taskEntry == null) {return null;}

		final AreaPositionQuery resp = getResponse(taskEntry, AreaPositionQuery.class);

		final RepeaterRoutingInfo result = new RepeaterRoutingInfo();
		if(resp != null) {
			result.setGatewayCallsign(String.valueOf(resp.getRepeater1Callsign()));
			result.setRepeaterCallsign(String.valueOf(resp.getRepeater2Callsign()));
			result.setGatewayAddress(resp.getGatewayAddress());

			if(
				resp.isValid() &&
				CallSignValidator.isValidGatewayCallsign(resp.getRepeater1Callsign()) &&
				CallSignValidator.isValidRepeaterCallsign(resp.getRepeater2Callsign()) &&
				resp.getGatewayAddress() != null
			)
				result.setRoutingResult(RoutingServiceResult.Success);
			else
				result.setRoutingResult(RoutingServiceResult.NotFound);
		}
		else {
			result.setRoutingResult(RoutingServiceResult.Failed);
		}

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

		final TaskEntry taskEntry = removeGetTaskEntry(taskid, PositionQuery.class);
		if(taskEntry == null) {return null;}

		final PositionQuery resp = getResponse(taskEntry, PositionQuery.class);

		final UserRoutingInfo result = new UserRoutingInfo();
		if(resp != null) {
//			result.setMyCallsign(String.valueOf(resp.getMyCallsign()));
			result.setYourCallsign(String.valueOf(resp.getYourCallsign()));
			char[] gatewayCall = resp.getRepeater1Callsign().clone();
//			assert gatewayCall != null && gatewayCall.length == 8;
			gatewayCall[DSTARDefines.CallsignFullLength - 1] = 'G';
			result.setGatewayCallsign(String.valueOf(gatewayCall));
			result.setRepeaterCallsign(String.valueOf(resp.getRepeater2Callsign()));
			result.setGatewayAddress(resp.getGatewayAddress());

			if(
				resp.isValid() &&
				CallSignValidator.isValidUserCallsign(resp.getYourCallsign()) &&
				resp.getGatewayAddress() != null
			)
				result.setRoutingResult(RoutingServiceResult.Success);
			else
				result.setRoutingResult(RoutingServiceResult.NotFound);
		}
		else {
			result.setRoutingResult(RoutingServiceResult.Failed);
		}

		return result;
	}

	@Override
	public boolean isServiceTaskCompleted(UUID taskid) {
		if(taskid == null) {return false;}

		synchronized(this.processTasks) {
			TaskEntry taskEntry = this.processTasks.get(taskid);
			if(taskEntry == null) {return false;}

			if(taskEntry.getTaskStatus() == TaskStatus.Complete)
				return true;
			else
				return false;
		}
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted() {
		return getServiceTaskCompleted(null);
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted(UUID taskid) {
		synchronized(this.processTasks) {
			for(TaskEntry entry : this.processTasks.values()) {

				if(
					(taskid == null || entry.getId().equals(taskid)) &&
					entry.getTaskStatus() == TaskStatus.Complete
				) {
					GlobalTrustCommand response = entry.getResponseCommand();

					if(response == null) {continue;}

					if(response instanceof AreaPositionQuery)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.FindRepeater);
					else if(response instanceof PositionQuery)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.FindUser);
					else if(response instanceof TableUpdate)
						return new RoutingCompletedTaskInfo(entry.getId(), RoutingServiceTasks.PositionUpdate);
				}
			}
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
						trustAddressDefault
				)
		);

		setTrustHeardPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				trustHeardPortPropertyName,
				trustHeardPortDefault)
		);

		setTrustQueryPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				trustQueryPortPropertyName,
				trustQueryPortDefault
			)
		);

		setKeepAliveSeconds(
			PropertyUtils.getInteger(
					properties.getConfigurationProperties(),
					keepAliveSecondsPropertyName, defaultKeepAliveSeconds)
			);
		if(getKeepAliveSeconds() < 10) {setKeepAliveSeconds(10);}

		setLogTransport(
			PropertyUtils.getBoolean(
					properties.getConfigurationProperties(),
					logTransportPropertyName, defaultLogTransport
			)
		);
/*
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
*/
		return true;
	}

	@Override
	public RoutingServiceProperties getProperties(RoutingServiceProperties properties) {
		if(properties == null) {return null;}

		properties.getConfigurationProperties().setProperty(trustAddressPropertyName, getTrustAddress());

		properties.getConfigurationProperties().setProperty(trustHeardPortPropertyName, String.valueOf(getTrustHeardPort()));

		properties.getConfigurationProperties().setProperty(trustQueryPortPropertyName, String.valueOf(getTrustQueryPort()));

		properties.getConfigurationProperties().setProperty(logTransportPropertyName, String.valueOf(isLogTransport()));

		properties.getConfigurationProperties().setProperty(keepAliveSecondsPropertyName, String.valueOf(getKeepAliveSeconds()));

		return properties;
	}

	@Override
	public RoutingServiceTypes getServiceType() {
		return RoutingServiceTypes.GlobalTrust;
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
	public final org.jp.illg.dstar.service.web.model.RoutingServiceStatusData createStatusDataInternal() {
		final GlobalTrustClientServiceStatusData status =
			new GlobalTrustClientServiceStatusData(getWebSocketRoomId());


		return status;
	}

	@Override
	public Class<? extends org.jp.illg.dstar.service.web.model.RoutingServiceStatusData> getStatusDataType() {
		return GlobalTrustClientServiceStatusData.class;
	}

	@Override
	protected boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeGlobalTrustClientService(this);
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

	private<T extends GlobalTrustCommand> T getResponse(
		final TaskEntry taskEntry, Class<T> queryClass
	){
		if(taskEntry.getResponseCommand() == null) {return null;}

		try {
			return queryClass.cast(taskEntry.getResponseCommand());
		}catch(ClassCastException ex) {
			log.debug(logHeader + "Class cast exception occurred.", ex);
			return null;
		}
	}

	private TaskEntry removeGetTaskEntry(final UUID id, final Class<?> queryClass) {
		synchronized(this.processTasks) {
			final TaskEntry taskEntry = this.processTasks.get(id);

			if(
				taskEntry == null ||
				taskEntry.getTaskStatus() != TaskStatus.Complete ||
				taskEntry.getResponseCommand() == null ||
				taskEntry.getResponseCommand().getClass() != queryClass
			) {return null;}

			processTasks.remove(id);

			return taskEntry;
		}
	}

	private void closeTrustChannel(){
		if(this.trustChannel != null && this.trustChannel.getChannel().isOpen()) {
			try {
				this.trustChannel.getChannel().close();
				this.trustChannel = null;
			}catch(IOException ex) {
				log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}

	private ThreadProcessResult onStateInitialize() {
		toWaitState(5, TimeUnit.SECONDS, ProcessStates.Idle);
		processStateRetryCount = 0;

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onIdle() {
		if(isStateChanged()) {

		}
		else {
			TaskEntry processTask = null;
			synchronized(processTasks) {
				for(
					Iterator<TaskEntry> it = this.processTasks.values().iterator();
					it.hasNext();
				) {
					TaskEntry taskEntry = it.next();
					if(taskEntry.getTaskStatus() == TaskStatus.Incomplete)
						processTask = taskEntry;
					else
						continue;
				}
			}

			if(processTask != null) {
				processTask.setTaskStatus(TaskStatus.Processing);

				processingTask = processTask;
				nextState = ProcessStates.SendCommand;
				processStateRetryCount = 0;
			}

		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSendCommand() {
		if(isStateChanged()) {

			//キャッシュを検索
			final GlobalTrustCommand cache = findActiveCache(processingTask.getRequestCommand());
			if(cache != null) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "return response from cache memory.\n    " + cache);

				processingTask.setResponseCommand(cache);
				processingTask.setTaskStatus(TaskStatus.Complete);

				dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

				processingTask = null;
				nextState = ProcessStates.Idle;
				processStateRetryCount = 0;
			}
			else {
				//コマンドIDを生成
				processingFrameID = DSTARUtils.generateFrameID();
				processingTask.getRequestCommand().setCommandIDInteger(processingFrameID);

				trustAddressResolver.setHostname(getTrustAddress());
				trustAddressResolver.getCurrentHostAddress()
				.ifPresentOrElse(new Consumer<InetAddress>() {
					@Override
					public void accept(InetAddress trustAddress) {

						//ポート番号選択
						int serverPort = 0;
						if(processingTask.getRequestCommand().getCommandType() == CommandType.PositionUpdate)
							serverPort = getTrustHeardPort();
						else
							serverPort = getTrustQueryPort();

						if(
							writeUDPPacket(
								trustChannel.getKey(),
								new InetSocketAddress(trustAddress, serverPort),
								ByteBuffer.wrap(processingTask.getRequestCommand().assembleCommandData())
							)
						) {
							processStateTimekeeper.setTimeoutMillis(trustServerTimeoutMillis);
							processStateTimekeeper.updateTimestamp();

							if(log.isTraceEnabled()) {
								log.trace(
									logHeader + "Send GlobalTrustCommand to trust server.\n    " +
									processingTask.getRequestCommand().toString()
								);
							}
						}
						else {
							if(log.isDebugEnabled())
								log.debug(logHeader + "fail writeUDP() and delete task.");

							processingTask.setTaskStatus(TaskStatus.Complete);
							dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());
							processingTask = null;
							nextState = ProcessStates.Idle;
						}
					}
				}, new Runnable() {
					@Override
					public void run() {
						if(log.isDebugEnabled())
							log.debug(logHeader + "Failed resolve trust server.");

						processingTask.setTaskStatus(TaskStatus.Complete);
						dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());
						processingTask = null;
						nextState = ProcessStates.Idle;
					}
				});
			}
		}
		else {
			if(processStateTimekeeper.isTimeout()) {
				if(processStateRetryCount < trustServerRetryLimit) {
					toWaitState(2, TimeUnit.SECONDS, ProcessStates.SendCommand);

					processStateRetryCount++;
				}
				else {
					//キャッシュを探して、無ければ諦める
					if(processingTask.getRequestCommand() instanceof PositionQuery) {
						findUserRoutingCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getYourCallsign()),
							true
						)
						.ifPresentOrElse(
							new Consumer<PositionQuery>() {
								@Override
								public void accept(PositionQuery t) {
									processingTask.setResponseCommand(t);

									if(log.isInfoEnabled()) {
										log.info(logHeader +
											"Trust server timeout, Completion user " +
											String.valueOf(t.getYourCallsign()) + " from cache memory."
										);
									}
								}
							},
							new Runnable() {
								@Override
								public void run() {
									processingTask.setResponseCommand(new PositionQuery());

									if(log.isWarnEnabled()) {
										log.warn(logHeader +
											"Trust server timeout and did not hit user " +
											String.valueOf(processingTask.getRequestCommand().getYourCallsign()) +
											" from cache memory."
										);
									}
								}
							}
						);
					}
					else if(processingTask.getRequestCommand() instanceof AreaPositionQuery) {
						findRepeaterRoutingCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getYourCallsign()),
							true
						)
						.ifPresentOrElse(
							new Consumer<AreaPositionQuery>() {
								@Override
								public void accept(AreaPositionQuery t) {
									processingTask.setResponseCommand(t);

									if(log.isInfoEnabled()) {
										log.info(logHeader +
											"Trust server timeout, Completion repeater " +
											String.valueOf(t.getYourCallsign()) + " from cache memory."
										);
									}
								}
							},
							new Runnable() {
								public void run() {
									processingTask.setResponseCommand(new AreaPositionQuery());

									if(log.isWarnEnabled()) {
										log.warn(logHeader +
											"Trust server timeout and did not hit repeater " +
											String.valueOf(processingTask.getRequestCommand().getYourCallsign()) +
											" from cache memory."
										);
									}
								}
							}
						);
					}
					else if(processingTask.getRequestCommand() instanceof TableUpdate) {
						findPositionUpdateCacheResponse(
							String.valueOf(processingTask.getRequestCommand().getMyCallsign()),
							String.valueOf(processingTask.getRequestCommand().getRepeater1Callsign()),
							true
						)
						.ifPresentOrElse(new Consumer<TableUpdate>() {
							@Override
							public void accept(TableUpdate t) {
								processingTask.setResponseCommand(t);

								if(log.isInfoEnabled()) {
									log.info(logHeader +
										"Trust server timeout, Completion repeater " +
										String.valueOf(t.getMyCallsign()) + " from cache memory."
									);
								}
							}
						}, new Runnable() {
							@Override
							public void run() {
								processingTask.setResponseCommand(new TableUpdate());

								if(log.isWarnEnabled()) {
									log.warn(logHeader +
										"Trust server timeout and did not hit repeater " +
										String.valueOf(processingTask.getRequestCommand().getMyCallsign()) +
										" from cache memory."
									);
								}
							}
						});


						processingTask.setResponseCommand(new TableUpdate());

						if(log.isWarnEnabled()) {
							log.warn(
								logHeader +
								"Trust server timeout and failed table update process, did not returned response."
							);
						}
					}
					else {
						if(log.isWarnEnabled())
							log.warn(logHeader + "Trust server timeout. not returned response.");

						synchronized(processTasks) {
							this.processTasks.remove(this.processingTask.getId());
						}
					}

					processingTask.setTaskStatus(TaskStatus.Complete);
					dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

					processingTask = null;
					nextState = ProcessStates.Idle;
					processStateRetryCount = 0;
				}
			}
			else {
				synchronized(this.recvCommands) {
					for(Iterator<GlobalTrustCommand> it = this.recvCommands.iterator();it.hasNext();) {
						GlobalTrustCommand recvCommand = it.next();
						it.remove();

						if(log.isTraceEnabled()) {
							log.trace(
								logHeader + "Receive GlobalTrustCommand from server " +
								recvCommand.getRemoteAddress() + ".\n    " +  recvCommand.toString()
							);
						}

						if(
							(
								(
									processingTask.getRequestCommand() instanceof AreaPositionQuery &&
									recvCommand instanceof AreaPositionQuery
								) ||
								(
									processingTask.getRequestCommand() instanceof PositionQuery &&
									recvCommand instanceof PositionQuery
								) ||
								(
									processingTask.getRequestCommand() instanceof TableUpdate &&
									recvCommand instanceof TableUpdate
								)
							) && recvCommand.getCommandIDInteger() == processingFrameID
						) {
							processingTask.setResponseCommand(recvCommand);
							processingTask.setTaskStatus(TaskStatus.Complete);

							if(recvCommand.isValid()) {
								//キャッシュに追加
								if(recvCommand instanceof AreaPositionQuery) {
									addRepeaterRoutingCache((AreaPositionQuery)recvCommand);
								}
								else if(recvCommand instanceof PositionQuery) {
									addUserRoutingCache((PositionQuery)recvCommand);
								}
								else if(recvCommand instanceof TableUpdate) {
									addPositionUpdateCache((TableUpdate)recvCommand);
								}

								//グローバルIPをセット
								if(recvCommand instanceof TableUpdate && recvCommand.getGatewayAddress() != null)
									setGlobalIP(new GlobalIPInfo(recvCommand.getGatewayAddress()));
							}

							dispatchEvent(RoutingServiceEvent.TaskComplete, processingTask.getId());

							processingTask = null;
							nextState = ProcessStates.Idle;
							processStateRetryCount = 0;

							break;
						}
					}
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(processStateTimekeeper.isTimeout()) {
			nextState = callbackState;
		}

		return ThreadProcessResult.NoErrors;
	}

	/**
	 * Delete process tasks
	 * if process created from over 60 seconds or
	 * process completed from over 10 seconds.
	 */
	private void cleanProcessTasks() {
		synchronized(this.processTasks) {
			for(Iterator<TaskEntry> it = this.processTasks.values().iterator();it.hasNext();) {
				TaskEntry taskEntry = it.next();

				// not check processing task
				if(taskEntry == this.processingTask) {continue;}

				if(
						(System.currentTimeMillis() > (taskEntry.getCreatedTimestamp() + TimeUnit.SECONDS.toMillis(60))) ||
						(
							taskEntry.getTaskStatus() == TaskStatus.Complete &&
							(System.currentTimeMillis() > (taskEntry.getCompletedTimestamp() + TimeUnit.SECONDS.toMillis(10)))
						)
				) {it.remove();}
			}
		}
	}

	/**
	 * 受信バッファーからコマンドデータを解析抽出する
	 */
	private boolean analyzePacket(List<GlobalTrustCommand> receiveCommands) {
		assert receiveCommands != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			BufferEntry buffer = opEntry.get();

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
						GlobalTrustCommand parsedCommand = null;
						if(
							//管理サーバへ問い合わせへの返答か？
							(parsedCommand = parsePacket(positionQueryResponse, receivePacket)) != null ||
							//管理サーバへ問い合わせ(ゲート指定)への返答か？
							(parsedCommand = parsePacket(areaPositionQueryResponse, receivePacket)) != null ||
							//テーブル書き換え要求への返答か？
							(parsedCommand = parsePacket(tableUpdateResponse,receivePacket)) != null
						) {
							parsedCommand.setRemoteAddress(buffer.getRemoteAddress());
							synchronized(receiveCommands) {
								receiveCommands.add(parsedCommand.clone());
							}
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
	private GlobalTrustCommand parsePacket(GlobalTrustCommand command, ByteBuffer buffer) {
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

	private boolean addUserRoutingCache(final PositionQuery info) {
		assert info != null;

		routingCacheLocker.lock();
		try {
			while(userRoutingCache.size() >= cacheEntryLimit) {userRoutingCache.remove(0);}

			for(Iterator<CacheEntry<PositionQuery>> it = userRoutingCache.iterator(); it.hasNext();) {
				final CacheEntry<PositionQuery> data = it.next();

				if(Arrays.equals(info.getYourCallsign(), data.getCommand().getYourCallsign())) {it.remove();}
			}

			return userRoutingCache.add(
				new CacheEntry<PositionQuery>(info, TimeUnit.SECONDS.toMillis(baseCacheTimeLimitSeconds))
			);
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Stream<CacheEntry<PositionQuery>> findUserRoutingCache(final String userCallsign){
		assert userCallsign != null;

		routingCacheLocker.lock();
		try {
			final Stream<CacheEntry<PositionQuery>> result =
				Stream.of(userRoutingCache)
				.filter(new Predicate<CacheEntry<PositionQuery>>() {
					@Override
					public boolean test(CacheEntry<PositionQuery> value) {
						return userCallsign.equals(String.valueOf(value.getCommand().getYourCallsign()));
					}
				});

			return result;
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Optional<PositionQuery> findUserRoutingCacheResponse(
		final String userCallsign,
		final boolean allowTimeoutCache
	) {
		return findUserRoutingCache(userCallsign)
			.filter(new Predicate<CacheEntry<PositionQuery>>() {
				@Override
				public boolean test(CacheEntry<PositionQuery> cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.comparingLong(new ToLongFunction<CacheEntry<PositionQuery>>() {
				@Override
				public long applyAsLong(CacheEntry<PositionQuery> cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}

			}))
			.map(new Function<CacheEntry<PositionQuery>, PositionQuery>(){
				@Override
				public PositionQuery apply(CacheEntry<PositionQuery> cache) {
					return cache.getCommand();
				}
			});
	}

	private boolean addRepeaterRoutingCache(final AreaPositionQuery info) {
		assert info != null;

		routingCacheLocker.lock();
		try {
			while(repeaterRoutingCache.size() >= cacheEntryLimit) {repeaterRoutingCache.remove(0);}

			for(Iterator<CacheEntry<AreaPositionQuery>> it = repeaterRoutingCache.iterator(); it.hasNext();) {
				final CacheEntry<AreaPositionQuery> data = it.next();

				if(Arrays.equals(info.getYourCallsign(), data.getCommand().getYourCallsign())) {it.remove();}
			}

			return repeaterRoutingCache.add(
				new CacheEntry<>(info, TimeUnit.SECONDS.toMillis(baseCacheTimeLimitSeconds))
			);
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Stream<CacheEntry<AreaPositionQuery>> findRepeaterRoutingCache(final String repeaterCallsign){
		assert repeaterCallsign != null;

		routingCacheLocker.lock();
		try {
			Stream<CacheEntry<AreaPositionQuery>> result =
				Stream.of(repeaterRoutingCache)
				.filter(new Predicate<CacheEntry<AreaPositionQuery>>() {
					@Override
					public boolean test(CacheEntry<AreaPositionQuery> value) {
						return repeaterCallsign.equals(String.valueOf(value.getCommand().getYourCallsign()));
					}
				});

			return result;
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Optional<AreaPositionQuery> findRepeaterRoutingCacheResponse(
		final String repeaterCallsign,
		final boolean allowTimeoutCache
	) {
		return findRepeaterRoutingCache(repeaterCallsign)
			.filter(new Predicate<CacheEntry<AreaPositionQuery>>() {
				@Override
				public boolean test(CacheEntry<AreaPositionQuery> cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.comparingLong(new ToLongFunction<CacheEntry<AreaPositionQuery>>() {
				@Override
				public long applyAsLong(CacheEntry<AreaPositionQuery> cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}

			}))
			.map(new Function<CacheEntry<AreaPositionQuery>, AreaPositionQuery>(){
				@Override
				public AreaPositionQuery apply(CacheEntry<AreaPositionQuery> cache) {
					return cache.getCommand();
				}
			});
	}

	private boolean addPositionUpdateCache(TableUpdate info) {

		routingCacheLocker.lock();
		try {
			while(positionUpdateCache.size() >= cacheEntryLimit) {positionUpdateCache.remove(0);}

			for(Iterator<CacheEntry<TableUpdate>> it = positionUpdateCache.iterator(); it.hasNext();) {
				final CacheEntry<TableUpdate> data = it.next();

				if(Arrays.equals(info.getMyCallsign(), data.getCommand().getMyCallsign())) {it.remove();}
			}

			return positionUpdateCache.add(
				new CacheEntry<>(info, TimeUnit.SECONDS.toMillis(baseCacheTimeLimitSeconds))
			);
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Stream<CacheEntry<TableUpdate>> findPositionUpdateCache(
		final String userCallsign, final String repeaterCallsign
	){

		routingCacheLocker.lock();
		try {
			final Stream<CacheEntry<TableUpdate>> result =
				Stream.of(positionUpdateCache)
				.filter(new Predicate<CacheEntry<TableUpdate>>() {
					@Override
					public boolean test(CacheEntry<TableUpdate> value) {
						final boolean match =
							(
								userCallsign == null ||
								userCallsign.equals(String.valueOf(value.getCommand().getMyCallsign()))
							) &&
							(
								repeaterCallsign == null ||
								repeaterCallsign.equals(String.valueOf(value.getCommand().getRepeater1Callsign()))
							);

						return match;
					}
				});

			return result;
		}finally {
			routingCacheLocker.unlock();
		}
	}

	private Optional<TableUpdate> findPositionUpdateCacheResponse(
		final String userCallsign, final String repeaterCallsign,
		final boolean allowTimeoutCache
	) {
		return findPositionUpdateCache(userCallsign, repeaterCallsign)
			.filter(new Predicate<CacheEntry<TableUpdate>>() {
				@Override
				public boolean test(CacheEntry<TableUpdate> cache) {
					return allowTimeoutCache || !cache.getTimeKeeper().isTimeout();
				}
			})
			.max(ComparatorCompat.comparingLong(new ToLongFunction<CacheEntry<TableUpdate>>() {
				@Override
				public long applyAsLong(CacheEntry<TableUpdate> cache) {
					return cache.getTimeKeeper().getTimestampMilis();
				}

			}))
			.map(new Function<CacheEntry<TableUpdate>, TableUpdate>(){
				@Override
				public TableUpdate apply(CacheEntry<TableUpdate> cache) {
					return cache.getCommand();
				}
			});
	}

	private GlobalTrustCommand findActiveCache(final GlobalTrustCommand requestCommand) {
		Optional<GlobalTrustCommand> result = Optional.empty();

		if(processingTask.getRequestCommand() instanceof TableUpdate) {
			result = findPositionUpdateCacheResponse(
				String.valueOf(requestCommand.getMyCallsign()),
				String.valueOf(requestCommand.getRepeater1Callsign()),
				false
			).map(new Function<TableUpdate, GlobalTrustCommand>(){
				@Override
				public GlobalTrustCommand apply(TableUpdate t) {
					return t;
				}
			});
		}
		else if(processingTask.getRequestCommand() instanceof PositionQuery) {
			result = findUserRoutingCacheResponse(
				String.valueOf(requestCommand.getYourCallsign()),
				false
			).map(new Function<PositionQuery, GlobalTrustCommand>(){
				@Override
				public GlobalTrustCommand apply(PositionQuery t) {
					return t;
				}
			});
		}
		else if(processingTask.getRequestCommand() instanceof AreaPositionQuery) {
			result = findRepeaterRoutingCacheResponse(
				String.valueOf(requestCommand.getYourCallsign()),
				false
			).map(new Function<AreaPositionQuery, GlobalTrustCommand>(){
				@Override
				public GlobalTrustCommand apply(AreaPositionQuery t) {
					return t;
				}
			});
		}

		return result.isPresent() ? result.get() : null;
	}

	private RoutingServiceStatus getServiceStatusInternal() {
		if(this.isRunning()) {
			switch(currentState) {
			case Initialize:
				return RoutingServiceStatus.InitializingService;

			case TimeWait:
				return RoutingServiceStatus.TemporaryDisabled;

			case Idle:
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

