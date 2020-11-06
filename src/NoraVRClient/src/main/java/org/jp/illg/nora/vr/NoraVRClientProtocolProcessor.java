package org.jp.illg.nora.vr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.nora.vr.model.NoraVRRoute;
import org.jp.illg.nora.vr.protocol.model.AccessLog;
import org.jp.illg.nora.vr.protocol.model.AccessLog.AccessLogEntry;
import org.jp.illg.nora.vr.protocol.model.AccessLog.AccessLogRoute;
import org.jp.illg.nora.vr.protocol.model.AccessLogGet;
import org.jp.illg.nora.vr.protocol.model.ConfigurationSet;
import org.jp.illg.nora.vr.protocol.model.LoginAck;
import org.jp.illg.nora.vr.protocol.model.LoginChallengeCode;
import org.jp.illg.nora.vr.protocol.model.LoginHashCode;
import org.jp.illg.nora.vr.protocol.model.LoginUser;
import org.jp.illg.nora.vr.protocol.model.LoginUser2;
import org.jp.illg.nora.vr.protocol.model.Logout;
import org.jp.illg.nora.vr.protocol.model.Nak;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.nora.vr.protocol.model.NoraVRPacket;
import org.jp.illg.nora.vr.protocol.model.NoraVRVoicePacket;
import org.jp.illg.nora.vr.protocol.model.Ping;
import org.jp.illg.nora.vr.protocol.model.ReflectorLink;
import org.jp.illg.nora.vr.protocol.model.ReflectorLinkGet;
import org.jp.illg.nora.vr.protocol.model.RepeaterInfo;
import org.jp.illg.nora.vr.protocol.model.RepeaterInfoGet;
import org.jp.illg.nora.vr.protocol.model.RoutingService;
import org.jp.illg.nora.vr.protocol.model.RoutingServiceGet;
import org.jp.illg.nora.vr.protocol.model.UserList;
import org.jp.illg.nora.vr.protocol.model.UserList.UserListEntry;
import org.jp.illg.nora.vr.protocol.model.UserListGet;
import org.jp.illg.util.Timer;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRClientProtocolProcessor {

	private static final int loginRetryLimit = 10;
	private static final int voicePacketsQueueLimit = 100;
	private static final int accessLogLimit = 20;

	@Getter
	private static final int supportProtocolVersion = 2;


	private enum ProtocolState{
		Initialize,
		LoginUser,
		LoginHash,
		SetConfig,
		GetRepeaterInfo,
		GetReflectorLink,
		GetRoutingService,
		GetAccessLog,
		GetUserList,
		MainState,
		Logout,
		Wait,
		LoginFailed,
		ConnectionFailed,
		SetConfigUpdate,
		;
	}

	private static class UserListBlockEntry {

		@Getter
		private long requestNo;

		@Getter
		private UserList[] blocks;

		@Getter
		private Timer activityTimer;


		public UserListBlockEntry(final UserList cmd) {
			super();

			requestNo = cmd.getRequestID();
			blocks = new UserList[cmd.getBlockTotal()];
			activityTimer = new Timer();

			addUserList(cmd);
		}

		public boolean addUserList(final UserList cmd) {
			if(cmd.getBlockTotal() >= cmd.getBlockIndex() && cmd.getBlockIndex() >= 1) {
				blocks[cmd.getBlockIndex() - 1] = cmd;

				return true;
			}
			else
				return false;
		}
	}

	private static class AccessLogBlockEntry {

		@Getter
		private long requestID;

		@Getter
		private AccessLog[] blocks;

		@Getter
		private Timer activityTimer;


		public AccessLogBlockEntry(final AccessLog cmd) {
			super();

			requestID = cmd.getRequestID();
			blocks = new AccessLog[cmd.getBlockTotal()];
			activityTimer = new Timer();

			addUserList(cmd);
		}

		public boolean addUserList(final AccessLog cmd) {
			if(cmd.getBlockTotal() >= cmd.getBlockIndex() && cmd.getBlockIndex() >= 1) {
				blocks[cmd.getBlockIndex() - 1] = cmd;

				return true;
			}
			else
				return false;
		}
	}

	@Getter
	private NoraVRClientConnectionState connectionState;

	@Getter
	private String loginUserCallsign;

	@Getter
	private String loginPassword;

	@Getter
	private String serverAddress;

	@Getter
	private int serverPort;

	@Getter
	private NoraVRConfiguration serverConfig;

	@Getter
	private NoraVRConfiguration clientConfig;

	@Getter
	private long clientCode;

	@Getter
	private int protocolVersion;

	@Getter
	private String gatewayCallsign;

	@Getter
	private String repeaterCallsign;

	@Getter
	private String linkedReflectorCallsign;

	@Getter
	private String routingServiceName;

	@Getter
	private String repeaterName;

	@Getter
	private String repeaterLocation;

	@Getter
	private double repeaterFrequencyMHz;

	@Getter
	private double repeaterFrequencyOffsetMHz;

	@Getter
	private double repeaterServiceRange;

	@Getter
	private double repeaterAgl;

	@Getter
	private String repeaterUrl;

	@Getter
	private String repeaterDescription1;

	@Getter
	private String repeaterDescription2;

	private DatagramChannel socket;
	private NoraVRClientTranceiver tranceiver;

	private ExecutorService workerExecutor;
	private final NoraVREventListener eventListener;

	private final String applicationName;
	private final String applicationVersion;

	private final Queue<NoraVRPacket> receiveQueue;
	private final Lock receiveQueueLocker;

	private final Lock mainLoopLocker;
	private final Condition mainLoopCond;

	private long loginChallengeCode;

	private final Timer receiveKeepaliveTimekeeper;
	private final Timer transmitKeepaliveTimekeeper;

	private final Queue<NoraVRVoicePacket<?>> writeVoicePackets;
	private final Queue<NoraVRVoicePacket<?>> readVoicePackets;
	private final Lock rwVoicePacketsLocker;

	private String nakReason;

	private ProtocolState currentState;
	private ProtocolState nextState;
	private ProtocolState callbackState;

	private final Timer stateTimeKeeper;
	private int stateRetryCount;

	private boolean isStateChanged;

	private boolean configurationSetRequest;

	private final Timer linkedReflectorCallsignRequestIntervalTimekeeper;
	private final Timer currentRoutingServiceRequestIntervalTimekeeper;

	private int preferredProtocolVersion;

	private final List<NoraVRAccessLog> accessLog;
	private final Map<Long, AccessLogBlockEntry> accessLogEntries;
	private final Timer accessLogEntriesCheckIntervalTimekeeper;

	private final List<NoraVRUser> userList;
	private final Map<Long, UserListBlockEntry> userListEntries;
	private final Timer userListEntriesCheckIntervalTimerkeeper;


	public NoraVRClientProtocolProcessor(
		@NonNull final Lock mainLoopLocker,
		@NonNull final Condition mainLoopCondition,
		final NoraVREventListener eventListener,
		final String applicationName,
		final String applicationVersion
	) {
		super();

		this.mainLoopLocker = mainLoopLocker;
		this.mainLoopCond = mainLoopCondition;

		this.eventListener = eventListener;
		this.applicationName = applicationName != null ? applicationName : "";
		this.applicationVersion = applicationVersion != null ? applicationVersion : "";

		socket = null;
		tranceiver = null;

		workerExecutor = null;

		receiveQueue = new LinkedList<NoraVRPacket>();
		receiveQueueLocker = new ReentrantLock();

		loginChallengeCode = 0x0;

		receiveKeepaliveTimekeeper = new Timer();
		transmitKeepaliveTimekeeper = new Timer();

		readVoicePackets = new LinkedList<NoraVRVoicePacket<?>>();
		writeVoicePackets = new LinkedList<NoraVRVoicePacket<?>>();
		rwVoicePacketsLocker = new ReentrantLock();

		nakReason = "";

		currentState = ProtocolState.Initialize;
		nextState = ProtocolState.Initialize;
		callbackState = ProtocolState.Initialize;
		stateTimeKeeper = new Timer();

		stateRetryCount = 0;
		isStateChanged = false;

		configurationSetRequest = false;

		linkedReflectorCallsignRequestIntervalTimekeeper = new Timer();

		currentRoutingServiceRequestIntervalTimekeeper = new Timer();

		preferredProtocolVersion = 0;

		accessLog = new LinkedList<>();
		accessLogEntries = new HashMap<>();
		accessLogEntriesCheckIntervalTimekeeper = new Timer();

		userList = new LinkedList<>();
		userListEntries = new HashMap<>();
		userListEntriesCheckIntervalTimerkeeper = new Timer();

		connectionState = NoraVRClientConnectionState.WorkerStoppped;
		loginUserCallsign = "";
		loginPassword = "";
		serverAddress = "";
		serverPort = 0;
		serverConfig = null;
		clientConfig = null;
		clientCode = 0x0;

		protocolVersion = supportProtocolVersion;
		gatewayCallsign = DSTARDefines.EmptyLongCallsign;
		repeaterCallsign = DSTARDefines.EmptyLongCallsign;
		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		routingServiceName = "";
		repeaterName = "";
		repeaterLocation = "";
		repeaterFrequencyMHz = 0.0d;
		repeaterFrequencyOffsetMHz = 0.0d;
		repeaterServiceRange = 0.0d;
		repeaterAgl = 0.0d;
		repeaterUrl = "";
		repeaterDescription1 = "";
		repeaterDescription2 = "";
	}

	public boolean connect(
		@NonNull final ExecutorService workerExecutor,
		@NonNull final String loginUserCallsign,
		@NonNull final String loginPassword,
		@NonNull final String serverAddress,
		final int serverPort,
		@NonNull final NoraVRConfiguration clientConfig
	) {
		return connect(
			workerExecutor,
			loginUserCallsign,loginPassword,
			serverAddress, serverPort,
			supportProtocolVersion,
			clientConfig
		);
	}

	public boolean connect(
		@NonNull final ExecutorService workerExecutor,
		@NonNull final String loginUserCallsign,
		@NonNull final String loginPassword,
		@NonNull final String serverAddress,
		final int serverPort,
		final int protocolVersion,
		@NonNull final NoraVRConfiguration clientConfig
	) {
		stop();

		this.workerExecutor = workerExecutor;

		if(
			loginUserCallsign == null || "".equals(loginUserCallsign) ||
			loginPassword == null ||
			serverAddress == null || "".equals(serverAddress) ||
			serverPort <= 0 || serverPort > 65535 ||
			protocolVersion > supportProtocolVersion ||
			protocolVersion < 1 ||
			clientConfig == null
		) {return false;}

		this.loginUserCallsign = loginUserCallsign;
		this.loginPassword = loginPassword;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;

		this.preferredProtocolVersion = protocolVersion;

		this.clientConfig = clientConfig;

		return start();
	}

	public void disconnect() {
		stop();
	}

	public boolean writeVoicePacket(@NonNull final NoraVRVoicePacket<?> voice) {
		rwVoicePacketsLocker.lock();
		try {
			writeVoicePackets.add(voice);
		}finally {rwVoicePacketsLocker.unlock();}

		mainLoopLocker.lock();
		try {
			mainLoopCond.signalAll();
		}finally {
			mainLoopLocker.unlock();
		}

		return true;
	}

	public NoraVRVoicePacket<?> readVoicePacket() {
		rwVoicePacketsLocker.lock();
		try {
			if(readVoicePackets.isEmpty()) {return null;}

			return readVoicePackets.poll();
		}finally {rwVoicePacketsLocker.unlock();}
	}

	public boolean updateClientConfiguration(@NonNull final NoraVRConfiguration clientConfig) {
		if(
			!isRunning() ||
			getConnectionState() != NoraVRClientConnectionState.ConnectionEstablished ||
			configurationSetRequest
		) {
			return false;
		}


		this.clientConfig = clientConfig;

		configurationSetRequest = true;

		return true;
	}

	private boolean start() {
		stop();

		try {
			socket = DatagramChannel.open();
			socket.configureBlocking(false);
			socket.socket().setReuseAddress(true);

			socket.socket().bind(new InetSocketAddress(0));
		} catch (IOException ex) {
			return false;
		}

		if(log.isTraceEnabled()) {
			log.trace("Socket port " + socket.socket().getLocalAddress());
		}

		tranceiver =
			new NoraVRClientTranceiver(
				socket, receiveQueue, receiveQueueLocker, mainLoopLocker, mainLoopCond
			);
		if(!tranceiver.start()) {
			stop();

			return false;
		}

		return true;
	}

	private void stop() {
		if(tranceiver != null) {tranceiver.stop();}

		if(socket != null) {
			try {
				socket.close();
			} catch (IOException ex) {
				if(log.isDebugEnabled())
					log.debug("Error occurred channel close()", ex);
			}
		}

		connectionState = NoraVRClientConnectionState.WorkerStoppped;
	}

	public boolean isRunning() {
		return tranceiver != null && tranceiver.isRunning();
	}

	public boolean process(final boolean workerThreadAvailable)
		throws InterruptedException, ExecutionException
	{
		boolean workerStopRequest = false;

		//NoraVRからのパケットを処理
		processReceivePacket();

		if(!processStates(workerThreadAvailable)) {workerStopRequest = true;}

		if(connectionState == NoraVRClientConnectionState.ConnectionEstablished) {
			// Ping送信
			if(transmitKeepaliveTimekeeper.isTimeout(5, TimeUnit.SECONDS)) {
				transmitKeepaliveTimekeeper.updateTimestamp();

				final Ping ping = new Ping();
				ping.setClientCode(clientCode);

				writePacket(ping, getServerSocketAddress());
			}
		}

		checkAccessLogEntries();
		checkUserListEntries();

		return !workerStopRequest;
	}

	private void processReceivePacket() {
		NoraVRPacket receivePacket = null;

		while((receivePacket = readPacket()) != null) {
			switch(receivePacket.getCommandType()) {
			case LOGIN_CC:
				onReceiveLoginChallengeCode(receivePacket);
				break;

			case LOGINACK:
				onReceiveLoginAck(receivePacket);
				break;

			case ACK:
				onReceiveAck(receivePacket);
				break;

			case NAK:
				onReceiveNak(receivePacket);
				break;

			case PONG:
				onReceivePong(receivePacket);
				break;

			case VTPCM:
			case VTOPUS:
			case VTAMBE:
				onReceiveVoice(receivePacket);
				break;

			case RINFO:
				onReceiveRepeaterInfo(receivePacket);
				break;

			case RLINK:
				onReceiveReflectorLink(receivePacket);
				break;

			case RSRV:
				onReceiveRoutingService(receivePacket);
				break;

			case USLST:
				onReceiveUserList(receivePacket);
				break;

			case ACLOG:
				onReceiveAccessLog(receivePacket);
				break;

			default:
				if(log.isDebugEnabled())
					log.debug("Illegal spec packet received.\n    " + receivePacket);
				break;
			}
		}
	}

	private boolean processStates(final boolean workerThreadAvailable)
		throws ExecutionException, InterruptedException {

		boolean workerStopRequest = false;
		boolean reProcess = false;
		do {
			reProcess = false;

			isStateChanged = currentState != nextState;
			if(log.isTraceEnabled() && isStateChanged)
				log.trace("State change " + currentState + "->" + nextState);

			currentState = nextState;

			switch(currentState) {
			case Initialize:
				if(!workerThreadAvailable) {
					workerStopRequest = true;
				} else {onStateInitialize();}
				break;

			case LoginUser:
				if(!workerThreadAvailable) {
					workerStopRequest = true;
				} else {onStateLoginUser();}
				break;

			case LoginHash:
				if(!workerThreadAvailable) {
					workerStopRequest = true;
				} else {onStateLoginHash();}
				break;

			case SetConfig:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateSetConfig();}
				break;

			case GetRepeaterInfo:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateGetRepeaterInfo();}
				break;

			case GetReflectorLink:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateGetReflectorLink();}
				break;

			case GetRoutingService:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateGetRoutingService();}
				break;

			case GetAccessLog:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateGetAccessLog();}
				break;

			case GetUserList:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateGetUserList();}
				break;

			case MainState:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateMainState();}
				break;

			case Logout:
				if(!onStateLogout()) {workerStopRequest = true;}
				break;

			case Wait:
				onStateWait();
				break;

			case LoginFailed:
				if(!onStateLoginFailed(workerThreadAvailable)) {workerStopRequest = true;}
				break;

			case ConnectionFailed:
				if(!onStateConnectionFailed(workerThreadAvailable)) {workerStopRequest = true;}
				break;

			case SetConfigUpdate:
				if(!workerThreadAvailable) {
					nextState = ProtocolState.Logout;
				} else {onStateSetConfigUpdate();}
				break;
			}

			if(currentState != nextState) {reProcess = true;}

		}while(reProcess);

		return !workerStopRequest;
	}

	private void onStateInitialize() {
		if(tranceiver.isInitialized()) {
			stateRetryCount = 0;

			toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.LoginUser);
		} else
			toWaitState(500, TimeUnit.MILLISECONDS, ProtocolState.Initialize);
	}

	private void onStateLoginUser() {
		if(isStateChanged) {
			connectionState = NoraVRClientConnectionState.Connecting;

			NoraVRPacket loginPacket = null;
			if(preferredProtocolVersion <= 1 || (stateRetryCount > 0 && stateRetryCount % 5 == 0)) {
				final LoginUser loginUser = new LoginUser();
				loginUser.setLoginUserName(loginUserCallsign);

				loginPacket = loginUser;
			}
			else {
				final LoginUser2 loginUser = new LoginUser2();
				loginUser.setLoginUserName(loginUserCallsign);
				loginUser.setProtocolVersion((byte)preferredProtocolVersion);
				loginUser.setApplicationName(applicationName);
				loginUser.setApplicationVersion(applicationVersion);

				loginPacket = loginUser;
			}
			writePacket(loginPacket, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)){
			if(stateRetryCount < loginRetryLimit) {
				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.LoginUser);

				stateRetryCount++;
			}
			else {
				sendLogout();

				stateRetryCount = 0;

				nextState = ProtocolState.ConnectionFailed;
				nakReason = "Login timeout, server did not respond.";
			}
		}
	}

	private void onStateLoginHash() {
		if(isStateChanged) {
			final byte[] hashCode = calcHash(loginChallengeCode, loginPassword);
			final LoginHashCode loginHash = new LoginHashCode();
			for(
				int i = 0;
				i < loginHash.getHashCode().length &&
				i < hashCode.length;
				i++
			) {
				loginHash.getHashCode()[i] = hashCode[i];
			}

			writePacket(loginHash, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)){
			if(stateRetryCount < loginRetryLimit) {
				toWaitState(500, TimeUnit.MILLISECONDS, ProtocolState.LoginUser);

				stateRetryCount++;
			}
			else {
				sendLogout();

				stateRetryCount = 0;

				nextState = ProtocolState.ConnectionFailed;
				nakReason = "Login timeout, server did not respond.";
			}
		}
	}

	private void onStateSetConfig() {
		if(isStateChanged) {
			clientConfig.setAccessLogNotify(true);
			clientConfig.setLocalUserNotify(true);
			clientConfig.setRemoteUserNotify(true);

			final ConfigurationSet confset = new ConfigurationSet();
			confset.setClientCode(clientCode);
			confset.setServerConfiguration(clientConfig);

			writePacket(confset, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)){
			if(stateRetryCount < 4) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.SetConfig);
			}
			else {
				stateRetryCount = 0;

				nextState = ProtocolState.GetReflectorLink;
			}
		}
	}

	private void onStateGetReflectorLink() {
		if(isStateChanged) {
			final ReflectorLinkGet rlinkGet = new ReflectorLinkGet();
			rlinkGet.setClientCode(clientCode);

			writePacket(rlinkGet, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)) {
			if(stateRetryCount < 1) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.GetReflectorLink);
			}
			else {
				stateRetryCount = 0;

				if(protocolVersion <= 1)
					nextState = ProtocolState.MainState;
				else
					nextState = ProtocolState.GetRepeaterInfo;
			}
		}
	}

	private void onStateGetRepeaterInfo() {
		if(isStateChanged) {
			final RepeaterInfoGet cmd = new RepeaterInfoGet();
			cmd.setClientCode(clientCode);

			writePacket(cmd, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)) {
			if(stateRetryCount < 1) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.GetRepeaterInfo);
			}
			else {
				stateRetryCount = 0;
				nextState = ProtocolState.GetRoutingService;
			}
		}
	}

	private void onStateGetRoutingService() {
		if(isStateChanged) {
			final RoutingServiceGet routingServiceGet = new RoutingServiceGet();
			routingServiceGet.setClientCode(clientCode);

			writePacket(routingServiceGet, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)) {
			if(stateRetryCount < 1) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.GetRoutingService);
			}
			else {
				stateRetryCount = 0;

				nextState = ProtocolState.GetAccessLog;
			}
		}
	}

	private void onStateGetAccessLog() {
		if(isStateChanged) {
			final AccessLogGet cmd = new AccessLogGet();
			cmd.setClientCode(clientCode);
			cmd.setRequestID(new Random().nextInt(0xFFFF) + 1);

			writePacket(cmd, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)) {
			if(stateRetryCount < 1) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.GetAccessLog);
			}
			else {
				stateRetryCount = 0;

				nextState = ProtocolState.GetUserList;
			}
		}
	}

	private void onStateGetUserList() {
		if(isStateChanged) {
			final UserListGet cmd = new UserListGet();
			cmd.setClientCode(clientCode);
			cmd.setRequestNo(new Random(System.currentTimeMillis()).nextInt(0xFFFF) + 1);

			writePacket(cmd, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)) {
			if(stateRetryCount < 1) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.GetUserList);
			}
			else {
				stateRetryCount = 0;

				nextState = ProtocolState.MainState;
			}
		}
	}

	private void onStateMainState() {
		if(isStateChanged) {
			stateTimeKeeper.updateTimestamp();
			receiveKeepaliveTimekeeper.updateTimestamp();
		}
		else if(receiveKeepaliveTimekeeper.isTimeout(30, TimeUnit.SECONDS)) {
			nextState = ProtocolState.ConnectionFailed;

			nakReason = "Keepalive timeout.";
		}
		else if(configurationSetRequest) {
			nextState = ProtocolState.SetConfig;
		}
		else {
			NoraVRVoicePacket<?> voicePacket = null;
			do {
				rwVoicePacketsLocker.lock();
				try {
					voicePacket = writeVoicePackets.poll();
				}finally {rwVoicePacketsLocker.unlock();}

				if(voicePacket != null) {
					voicePacket.setClientCode(clientCode);

					writePacket(voicePacket, getServerSocketAddress());
				}
			}while(voicePacket != null);
		}

		//10秒ごとにリフレクターのリンク状態を要求する
		if(linkedReflectorCallsignRequestIntervalTimekeeper.isTimeout()) {
			linkedReflectorCallsignRequestIntervalTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			linkedReflectorCallsignRequestIntervalTimekeeper.updateTimestamp();

			final ReflectorLinkGet rlinkGet = new ReflectorLinkGet();
			rlinkGet.setClientCode(clientCode);

			writePacket(rlinkGet, getServerSocketAddress());
		}

		if(currentRoutingServiceRequestIntervalTimekeeper.isTimeout(30, TimeUnit.SECONDS)) {
			currentRoutingServiceRequestIntervalTimekeeper.updateTimestamp();

			if(protocolVersion >= 2) {
				final RoutingServiceGet routingServiceGet = new RoutingServiceGet();
				routingServiceGet.setClientCode(clientCode);

				writePacket(routingServiceGet, getServerSocketAddress());
			}
		}
	}

	private boolean onStateLogout() {
		if(isStateChanged) {
			final Logout logout = new Logout();
			logout.setClientCode(clientCode);

			writePacket(logout, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}else if(stateTimeKeeper.isTimeout(100, TimeUnit.MILLISECONDS)) {
			return false;
		}

		return true;
	}

	private boolean onStateLoginFailed(final boolean workerThreadAvailable)
		throws ExecutionException, InterruptedException {
		if(isStateChanged) {
			connectionState = NoraVRClientConnectionState.ConnectionFailed;

			if(
				eventListener != null &&
				workerExecutor.submit(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return eventListener.loginFailed(nakReason);
					}
				}).get()
			) {
				toWaitState(10, TimeUnit.SECONDS, ProtocolState.LoginUser);
			}
		}
		else if(!workerThreadAvailable){
			return false;
		}

		return true;
	}

	private boolean onStateConnectionFailed(final boolean workerThreadAvailable)
		throws ExecutionException, InterruptedException {
		if(isStateChanged) {
			connectionState = NoraVRClientConnectionState.ConnectionFailed;

			if(eventListener != null) {
				if(workerExecutor.submit(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return eventListener.connectionFailed(nakReason);
					}
				}).get()) {
					toWaitState(10, TimeUnit.SECONDS, ProtocolState.LoginUser);
				}
				else {
					clearLoginUserList();

					workerExecutor.submit(new Runnable() {
						@Override
						public void run() {
							eventListener.routingService("");
						}
					});
					workerExecutor.submit(new Runnable() {
						@Override
						public void run() {
							eventListener.reflectorLink(DSTARDefines.EmptyLongCallsign);
						}
					});
				}
			}
		}
		else if(!workerThreadAvailable){
			return false;
		}

		return true;
	}

	private void onStateSetConfigUpdate() {
		if(isStateChanged) {
			clientConfig.setAccessLogNotify(true);
			clientConfig.setLocalUserNotify(true);
			clientConfig.setRemoteUserNotify(true);

			final ConfigurationSet confset = new ConfigurationSet();
			confset.setClientCode(clientCode);
			confset.setServerConfiguration(clientConfig);

			writePacket(confset, getServerSocketAddress());

			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout(2, TimeUnit.SECONDS)){
			if(stateRetryCount < 4) {
				stateRetryCount++;

				toWaitState(100, TimeUnit.MILLISECONDS, ProtocolState.SetConfigUpdate);
			}
			else {
				stateRetryCount = 0;

				nextState = ProtocolState.MainState;
			}
		}
	}

	private void onReceiveAck(final NoraVRPacket packet) {
		switch(currentState) {
		case SetConfig:
		case SetConfigUpdate:
			if(eventListener != null) {
				workerExecutor.submit(new Runnable() {
					@Override
					public void run() {
						eventListener.configurationSet(clientConfig);
					}
				});
			}

			if(currentState == ProtocolState.SetConfig) {
				connectionState = NoraVRClientConnectionState.ConnectionEstablished;

				nextState = ProtocolState.GetReflectorLink;
			}
			else
				nextState = ProtocolState.MainState;

			break;

		default:
			break;
		}
	}

	private void onReceiveNak(final NoraVRPacket packet) {
		final Nak nakPacket = (Nak)packet;

		nakReason = nakPacket.getReason();

		switch(currentState) {
		case LoginUser:
			if(log.isErrorEnabled())
				log.error("Login failed, Nak returned from server(Reason:" + nakPacket.getReason() + ")");

			sendLogout();

			nextState = ProtocolState.LoginFailed;
			break;

		case LoginHash:
			if(log.isErrorEnabled())
				log.error("Login failed, Nak returned from server(Reason:" + nakPacket.getReason() + ")");

			sendLogout();

			nextState = ProtocolState.LoginFailed;
			break;

		case SetConfig:
		case GetReflectorLink:
		case GetRepeaterInfo:
		case GetRoutingService:
		case GetAccessLog:
		case GetUserList:
		case SetConfigUpdate:
			if(log.isErrorEnabled())
				log.error("State " + currentState + " failed, Nak returned from server(Reason:" + nakPacket.getReason() + ")");

			sendLogout();

			nextState = ProtocolState.ConnectionFailed;
			break;

		case MainState:
			if(log.isErrorEnabled())
				log.error("Keepalive failed, Nak returned from server(Reason:" + nakPacket.getReason() + ")");

			sendLogout();

			nextState = ProtocolState.ConnectionFailed;
			break;

		default:
			break;
		}
	}

	private void onReceivePong(final NoraVRPacket packet) {
		if(connectionState == NoraVRClientConnectionState.ConnectionEstablished) {
			receiveKeepaliveTimekeeper.updateTimestamp();
		}
	}

	private void onReceiveLoginChallengeCode(final NoraVRPacket packet) {
		if(currentState == ProtocolState.LoginUser) {
			final LoginChallengeCode receivePacket = (LoginChallengeCode)packet;

			loginChallengeCode = receivePacket.getChallengeCode();

			nextState = ProtocolState.LoginHash;
		}
	}

	private void onReceiveLoginAck(final NoraVRPacket packet) {
		final LoginAck loginAck = (LoginAck)packet;

		if(currentState == ProtocolState.LoginHash) {
			clientCode = loginAck.getClientCode();
			serverConfig = loginAck.getServerConfiguration();
			protocolVersion = loginAck.getProtocolVersion() == 0 ? 1 : loginAck.getProtocolVersion();
			gatewayCallsign = loginAck.getGatewayCallsign();
			repeaterCallsign = loginAck.getRepeaterCallsign();


			if(
				protocolVersion < 1 ||
				protocolVersion > supportProtocolVersion
			) {
				sendLogout();

				nakReason = "Login failed, Incompatible with protocol version " + protocolVersion + ".";

				nextState = ProtocolState.LoginFailed;
			}
			else if(clientConfig.isRfNode() && !serverConfig.isRfNode()) {
				//RFノードが許可されていない場合
				//ログアウトを送信
				sendLogout();

				nakReason = "Login failed, RF node is not permitted.";

				nextState = ProtocolState.LoginFailed;
			}
			else if(
				(clientConfig.isSupportedCodecAMBE() && !serverConfig.isSupportedCodecAMBE()) ||
				(clientConfig.isSupportedCodecPCM() && !serverConfig.isSupportedCodecPCM()) ||
				(clientConfig.isSupportedCodecOpus64k() && !serverConfig.isSupportedCodecOpus64k()) ||
				(clientConfig.isSupportedCodecOpus24k() && !serverConfig.isSupportedCodecOpus24k()) ||
				(clientConfig.isSupportedCodecOpus8k() && !serverConfig.isSupportedCodecOpus8k()) ||
				(clientConfig.isSupportedCodecAMBE() && !serverConfig.isSupportedCodecAMBE())
			){
				//要求するコーデックをサポートしていない場合
				//ログアウトを送信
				sendLogout();

				nakReason = "Login failed, Requested codec is not supported in server.";

				nextState = ProtocolState.LoginFailed;
			}
			else {
				if(log.isDebugEnabled())
					log.debug("Login complete, connection established, protocol version = " + protocolVersion);

				if(eventListener != null) {
					workerExecutor.submit(new Runnable() {
						@Override
						public void run() {
							eventListener.loginSuccess(protocolVersion);
						}
					});
				}

				nextState = ProtocolState.SetConfig;
			}
		}
	}

	private void onReceiveVoice(final NoraVRPacket packet) {
		final NoraVRVoicePacket<?> voicePacket = (NoraVRVoicePacket<?>)packet;

		rwVoicePacketsLocker.lock();
		try {
			while(readVoicePackets.size() >= voicePacketsQueueLimit) {
				readVoicePackets.poll();
			}

			readVoicePackets.add(voicePacket);
		}finally {rwVoicePacketsLocker.unlock();}
	}

	private void onReceiveRepeaterInfo(final NoraVRPacket packet) {
		final RepeaterInfo repeaterInfo = (RepeaterInfo)packet;

		repeaterName = repeaterInfo.getName() != null ? repeaterInfo.getName() : "";
		repeaterLocation = repeaterInfo.getLocation() != null ? repeaterInfo.getLocation() : "";
		repeaterFrequencyMHz = repeaterInfo.getFrequencyMHz() > 0.0d ? repeaterInfo.getFrequencyMHz() : 0.0d;
		repeaterFrequencyMHz = repeaterInfo.getFrequencyOffsetMHz();
		repeaterServiceRange = repeaterInfo.getServiceRangeKm() > 0.0d ? repeaterInfo.getServiceRangeKm() : 0.0d;
		repeaterAgl = repeaterInfo.getAgl();
		repeaterUrl = repeaterInfo.getUrl() != null ? repeaterInfo.getUrl() : "";
		repeaterDescription1 = repeaterInfo.getDescription1() != null ? repeaterInfo.getDescription1() : "";
		repeaterDescription2 = repeaterInfo.getDescription2() != null ? repeaterInfo.getDescription2() : "";

		if(eventListener != null) {
			workerExecutor.submit(new Runnable() {
				@Override
				public void run() {
					eventListener.repeaterInformation(
						repeaterCallsign,
						repeaterName,
						repeaterLocation,
						repeaterFrequencyMHz,
						repeaterFrequencyOffsetMHz,
						repeaterServiceRange,
						repeaterAgl,
						repeaterUrl,
						repeaterDescription1,
						repeaterDescription2
					);
				}
			});
		}

		if(currentState == ProtocolState.GetRepeaterInfo) {
			nextState = ProtocolState.GetRoutingService;
		}
	}

	private void onReceiveReflectorLink(final NoraVRPacket packet) {
		final ReflectorLink reflectorLink = (ReflectorLink)packet;

		final String reflectorCallsign = reflectorLink.getLinkedReflectorCallsign();

		if(reflectorCallsign != null && !reflectorCallsign.equals(linkedReflectorCallsign)) {
			if(log.isDebugEnabled())
				log.debug("Receive notify reflector link status = " + reflectorCallsign);

			if(eventListener != null) {
				workerExecutor.submit(new Runnable() {
					@Override
					public void run() {
						eventListener.reflectorLink(reflectorCallsign);
					}
				});
			}

			linkedReflectorCallsign = reflectorCallsign;
		}

		if(currentState == ProtocolState.GetReflectorLink) {
			if(protocolVersion <= 1)
				nextState = ProtocolState.MainState;
			else
				nextState = ProtocolState.GetRepeaterInfo;
		}
		else if (currentState == ProtocolState.MainState) {
			linkedReflectorCallsignRequestIntervalTimekeeper.updateTimestamp();
		}
	}

	private void onReceiveRoutingService(final NoraVRPacket packet) {
		final RoutingService routingService = (RoutingService)packet;

		final String routingServiceName =
			routingService.getRoutingServiceName();

		if(routingServiceName != null && !routingServiceName.equals(this.routingServiceName)) {
			if(log.isDebugEnabled())
				log.debug("Receive notify routing service = " + routingServiceName);

			if(eventListener != null) {
				workerExecutor.submit(new Runnable() {
					@Override
					public void run() {
						eventListener.routingService(routingServiceName);
					}
				});
			}

			this.routingServiceName = routingServiceName;
		}

		if(currentState == ProtocolState.GetRoutingService) {
			nextState = ProtocolState.GetAccessLog;
		}
		else if(currentState == ProtocolState.MainState) {
			currentRoutingServiceRequestIntervalTimekeeper.updateTimestamp();
		}
	}

	private void onReceiveUserList(final NoraVRPacket packet) {
		final UserList userListPacket = (UserList)packet;

		if(userListPacket.getRequestID() == 0x0) {
			updateLoginUserList(userListPacket.getUserList());
		}
		else {
			UserListBlockEntry entry =
				userListEntries.get(userListPacket.getRequestID());
			if(entry == null) {
				entry = new UserListBlockEntry(userListPacket);
				entry.getActivityTimer().updateTimestamp();

				userListEntries.put(userListPacket.getRequestID(), entry);
			}
			else {
				if(entry.getBlocks().length <= userListPacket.getBlockIndex() && userListPacket.getBlockIndex() >= 1) {
					entry.getBlocks()[userListPacket.getBlockIndex() - 1] = userListPacket;

					entry.getActivityTimer().updateTimestamp();
				}
			}

			for(final Iterator<UserListBlockEntry> it = userListEntries.values().iterator(); it.hasNext();) {
				final UserListBlockEntry b = it.next();

				//全てのブロックが揃っているか
				if(Stream.of(b.getBlocks()).allMatch(new Predicate<UserList>() {
					@Override
					public boolean test(UserList user) {
						return user != null;
					}
				})) {
					it.remove();

					final List<UserListEntry> users = new LinkedList<>();
					for(final UserList block : entry.getBlocks()) {
						users.addAll(block.getUserList());
					}

					updateLoginUserList(users);

					if(currentState == ProtocolState.GetUserList) {
						nextState = ProtocolState.MainState;
					}
				}
			}
		}
	}

	private void onReceiveAccessLog(final NoraVRPacket packet) {
		final AccessLog accessLogPacket = (AccessLog)packet;

		// リクエストIDが0x0の場合は通知なので、即更新
		if(accessLogPacket.getRequestID() == 0x0) {
			updateAccessLog(accessLogPacket.getLogs());
		}
		else {
			AccessLogBlockEntry entry =
				accessLogEntries.get(accessLogPacket.getRequestID());
			if(entry == null) {
				entry = new AccessLogBlockEntry(accessLogPacket);
				entry.getActivityTimer().updateTimestamp();

				accessLogEntries.put(accessLogPacket.getRequestID(), entry);
			}
			else if(entry.addUserList(accessLogPacket)){
				entry.getActivityTimer().updateTimestamp();
			}

			for(final Iterator<AccessLogBlockEntry> it = accessLogEntries.values().iterator(); it.hasNext();) {
				final AccessLogBlockEntry b = it.next();

				//全てのブロックが揃っていれば、受信済みのアクセスログをまとめて更新
				if(Stream.of(b.getBlocks()).allMatch(new Predicate<AccessLog>() {
					@Override
					public boolean test(AccessLog user) {
						return user != null;
					}
				})) {
					it.remove();

					final List<AccessLogEntry> logs = new LinkedList<>();
					for(final AccessLog block : b.getBlocks()) {
						logs.addAll(block.getLogs());
					}

					updateAccessLog(logs);

					if(currentState == ProtocolState.GetAccessLog) {
						nextState = ProtocolState.GetUserList;
					}
				}
			}
		}
	}

	private void onStateWait() {
		if(stateTimeKeeper.isTimeout()) {nextState = callbackState;}
	}

	private void toWaitState(long waitTime, TimeUnit timeUnit, ProtocolState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = ProtocolState.Wait;
		this.callbackState = callbackState;
	}

	private InetSocketAddress getServerSocketAddress() {
		return new InetSocketAddress(serverAddress, serverPort);
	}

	private NoraVRPacket readPacket() {
		receiveQueueLocker.lock();
		try {
			if(receiveQueue.isEmpty()) {return null;}

			return receiveQueue.poll();
		}finally {receiveQueueLocker.unlock();}
	}

	private boolean writePacket(
		final NoraVRPacket packet, final InetSocketAddress destinationAddress
	) {
		if(socket == null || !socket.isOpen() || destinationAddress.isUnresolved()) {
			return false;
		}

		final ByteBuffer transmitBuffer = packet.assemblePacket();

		if(transmitBuffer == null) {return false;}

		return tranceiver.write(transmitBuffer, destinationAddress);
	}

	private static byte[] calcHash(final long loginChallengeCode, final String loginPassword) {

		byte[] hashCalcSrc = new byte[4 + loginPassword.length()];

		long challengeCode = loginChallengeCode;
		for(int c = 0; c < 4; c++) {
			hashCalcSrc[3 - c]= (byte)(challengeCode & 0xFF);
			challengeCode = challengeCode >> 8;
		}

		for(int i = 0; i < loginPassword.length(); i++) {
			hashCalcSrc[i + 4] = (byte)loginPassword.charAt(i);
		}

		return calcSHA256(hashCalcSrc);
	}

	private static byte[] calcSHA256(byte[] input) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		}catch(NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}

		if(input != null)
			md.update(input);

		return md.digest();
	}

	private void updateLoginUserList(final List<UserListEntry> users) {
		for(final UserListEntry user : users) {
			if(user.getFlag().isLoginUser()) {
				//Login
				if(!Stream.of(userList).anyMatch(new Predicate<NoraVRUser>() {
					@Override
					public boolean test(NoraVRUser u) {
						return u.getCallsignLong().equals(user.getMyCallsign());
					}
				})) {
					userList.add(
						new NoraVRUser(
							user.getFlag().isRemoteUser(),
							user.getMyCallsign(), user.getMyCallsignShort()
						)
					);
				}
			}
			else {
				//Logout
				for(final Iterator<NoraVRUser> it = userList.iterator(); it.hasNext();) {
					final NoraVRUser u = it.next();

					if(u.getCallsignLong().equals(user.getMyCallsign())){
						it.remove();
					}
				}
			}
		}

		final List<NoraVRUser> usersForEvent = new ArrayList<>(userList);
		if(eventListener != null) {
			workerExecutor.submit(new Runnable() {
				@Override
				public void run() {
					eventListener.userList(usersForEvent);
				}
			});
		}
	}

	private void clearLoginUserList() {
		userList.clear();
		updateLoginUserList(Collections.emptyList());
	}

	private void updateAccessLog(final List<AccessLogEntry> logs) {
		for(final AccessLogEntry newLog : logs) {
			while(accessLog.size() >= accessLogLimit) {
				final Optional<NoraVRAccessLog> oldLog =
					Stream.of(accessLog).min(ComparatorCompat.comparingLong(new ToLongFunction<NoraVRAccessLog>() {
						@Override
						public long applyAsLong(NoraVRAccessLog log) {
							return log.getAccessTime();
						}
					}));

				if(!oldLog.isPresent()) {break;}

				accessLog.remove(oldLog.get());
			}

			for(final Iterator<NoraVRAccessLog> it = accessLog.iterator(); it.hasNext();) {
				final NoraVRAccessLog log = it.next();

				if(log.getMyCallsignLong().equals(newLog.getMyCallsign()))
					it.remove();
			}

			NoraVRRoute route = NoraVRRoute.Unknown;
			if(newLog.getFlag().getRoute() == AccessLogRoute.LocalToGateway)
				route = NoraVRRoute.LocalToGateway;
			else if(newLog.getFlag().getRoute() == AccessLogRoute.GatewayToLocal)
				route = NoraVRRoute.GatewayToLocal;
			else if(newLog.getFlag().getRoute() == AccessLogRoute.LocalToLocal)
				route = NoraVRRoute.LocalToLocal;

			accessLog.add(
				new NoraVRAccessLog(
					newLog.getTimestamp(),
					route,
					newLog.getYourCallsign(),
					newLog.getMyCallsign(), newLog.getMyCallsignShort()
				)
			);
		}

		if(logs.size() >= 1) {
			final List<NoraVRAccessLog> sortedAccessLog =
				Stream.of(accessLog).sorted(ComparatorCompat.comparingLong(new ToLongFunction<NoraVRAccessLog>() {
					@Override
					public long applyAsLong(NoraVRAccessLog log) {
						return log.getAccessTime();
					}
				}).reversed()).toList();

			if(eventListener != null) {
				workerExecutor.submit(new Runnable() {
					@Override
					public void run() {
						eventListener.accessLog(sortedAccessLog);
					}
				});
			}
		}
	}

	private boolean sendLogout() {
		final Logout logout = new Logout();
		logout.setClientCode(clientCode);

		return writePacket(logout, getServerSocketAddress());
	}

	private void checkUserListEntries() {
		if(userListEntriesCheckIntervalTimerkeeper.isTimeout()) {
			userListEntriesCheckIntervalTimerkeeper.setTimeoutTime(
				new Random(System.currentTimeMillis()).nextInt(3) + 1, TimeUnit.SECONDS
			);
			userListEntriesCheckIntervalTimerkeeper.updateTimestamp();

			for(final Iterator<UserListBlockEntry> it = userListEntries.values().iterator(); it.hasNext();) {
				final UserListBlockEntry b = it.next();

				if(b.getActivityTimer().isTimeout(
					new Random(System.currentTimeMillis() ^ 0x65ad65d4).nextInt(5) + 5, TimeUnit.SECONDS
				)) {
					//タイムアウト
					it.remove();

					if(currentState == ProtocolState.MainState) {
						if(log.isDebugEnabled())
							log.debug("Retry due to missing user list block...");

						final UserListGet cmd = new UserListGet();
						cmd.setClientCode(clientCode);
						cmd.getFlag().setLocalUser(true);
						cmd.getFlag().setRemoteUser(true);

						writePacket(cmd, getServerSocketAddress());
					}
				}
			}
		}

	}

	private void checkAccessLogEntries() {
		if(accessLogEntriesCheckIntervalTimekeeper.isTimeout()) {
			accessLogEntriesCheckIntervalTimekeeper.setTimeoutTime(
				new Random(System.currentTimeMillis() ^ 0x6545a6ca).nextInt(3) + 1, TimeUnit.SECONDS
			);
			accessLogEntriesCheckIntervalTimekeeper.updateTimestamp();

			for(final Iterator<AccessLogBlockEntry> it = accessLogEntries.values().iterator(); it.hasNext();) {
				final AccessLogBlockEntry b = it.next();

				if(b.getActivityTimer().isTimeout(
					new Random(System.currentTimeMillis()).nextInt(5) + 5, TimeUnit.SECONDS
				)) {
					//タイムアウト
					it.remove();

					if(currentState == ProtocolState.MainState) {
						if(log.isDebugEnabled())
							log.debug("Retry due to missing access log block...");

						final AccessLogGet cmd = new AccessLogGet();
						cmd.setClientCode(clientCode);

						writePacket(cmd, getServerSocketAddress());
					}
				}
			}
		}
	}
}
