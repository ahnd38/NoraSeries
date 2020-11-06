package org.jp.illg.nora.vr.protocol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.repeater.modem.model.User;
import org.jp.illg.dstar.repeater.modem.model.UserLocationType;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.vr.NoraVRClientManager;
import org.jp.illg.nora.vr.NoraVRUtil;
import org.jp.illg.nora.vr.model.NoraVRClientEntry;
import org.jp.illg.nora.vr.model.NoraVRClientState;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.nora.vr.model.NoraVRLoginUserEntry;
import org.jp.illg.nora.vr.protocol.model.AccessLog;
import org.jp.illg.nora.vr.protocol.model.AccessLog.AccessLogEntry;
import org.jp.illg.nora.vr.protocol.model.AccessLog.AccessLogEntryFlag;
import org.jp.illg.nora.vr.protocol.model.AccessLog.AccessLogRoute;
import org.jp.illg.nora.vr.protocol.model.AccessLogGet;
import org.jp.illg.nora.vr.protocol.model.Ack;
import org.jp.illg.nora.vr.protocol.model.ConfigurationSet;
import org.jp.illg.nora.vr.protocol.model.LoginAck;
import org.jp.illg.nora.vr.protocol.model.LoginChallengeCode;
import org.jp.illg.nora.vr.protocol.model.LoginHashCode;
import org.jp.illg.nora.vr.protocol.model.LoginUser;
import org.jp.illg.nora.vr.protocol.model.LoginUser2;
import org.jp.illg.nora.vr.protocol.model.Logout;
import org.jp.illg.nora.vr.protocol.model.Nak;
import org.jp.illg.nora.vr.protocol.model.NoraVRCommandType;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.nora.vr.protocol.model.NoraVRPacket;
import org.jp.illg.nora.vr.protocol.model.NoraVRVoicePacket;
import org.jp.illg.nora.vr.protocol.model.Ping;
import org.jp.illg.nora.vr.protocol.model.Pong;
import org.jp.illg.nora.vr.protocol.model.ReflectorLink;
import org.jp.illg.nora.vr.protocol.model.ReflectorLinkGet;
import org.jp.illg.nora.vr.protocol.model.RepeaterInfo;
import org.jp.illg.nora.vr.protocol.model.RepeaterInfoGet;
import org.jp.illg.nora.vr.protocol.model.RoutingService;
import org.jp.illg.nora.vr.protocol.model.RoutingServiceGet;
import org.jp.illg.nora.vr.protocol.model.UserList;
import org.jp.illg.nora.vr.protocol.model.UserList.UserListEntry;
import org.jp.illg.nora.vr.protocol.model.UserList.UserListEntryFlag;
import org.jp.illg.nora.vr.protocol.model.UserListGet;
import org.jp.illg.nora.vr.protocol.model.VTAMBE;
import org.jp.illg.nora.vr.protocol.model.VTOPUS;
import org.jp.illg.nora.vr.protocol.model.VTPCM;
import org.jp.illg.nora.vr.protocol.model.VoiceTransferBase;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.HashUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Collector;
import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.BiConsumer;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.Supplier;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRProtocolProcessor
extends SocketIOHandlerWithThread<BufferEntry> {

	private static long clientKeepaliveTimeoutSeconds = 60;
	private static long clientTransmitKeepalivePeriodSeconds = 10;

	private static final int rwPacketsLimit = 500;

	private static final int accessLogLimit = 20;

	@Getter
	private final static int supportedProtocolVersion = 2;

	private final String logHeader;

	private final NoraVRProtocolEventListener eventListener;

	private final ExecutorService workerExecutor;

	private SocketIOEntryUDP channel;

	private final Queue<NoraVRPacket> readPackets;

	private final NoraVRClientManager clientManager;

	private final Timer clientsCleanupPeriodTimekeeper;

	private final List<NoraVRLoginUserEntry> authLoginUsers;

	@Setter
	private WebRemoteControlService webRemoteControlService;

	@Getter
	private final DSTARGateway gateway;

	@Getter
	private final DSTARRepeater repeater;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int NoraVRPort;
	public static final String NoraVRPortPropertyName = "NoraVRPort";
	private static final int NoraVRPortDefault = 56156;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String NoraVRloginPassword;
	public static final String noraVRLoginPasswordPropertyName = "NoraVRLoginPassword";
	public static final String noraVRLoginPasswordDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int NoraVRClientConnectionLimit;
	public static final String noravrClientConnectionLimitPropertyName = "NoraVRClientConnectionLimit";
	public static final int noravrClientConnectionLimitDefault = 10;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private NoraVRConfiguration serverConfiguration;

	@Getter
	private final String gatewayCallsign;

	@Getter
	private final String repeaterCallsign;

	private final Ack noravrAck = new Ack();
	private final Nak noravrNak = new Nak();
	private final LoginUser noravrLoginUser = new LoginUser();
	private final LoginUser2 noravrLoginUser2 = new LoginUser2();
	private final Logout noravrLogout = new Logout();
	private final LoginChallengeCode noravrLoginChallengeCode = new LoginChallengeCode();
	private final LoginHashCode noravrLoginHashCode = new LoginHashCode();
	private final LoginAck noravrLoginAck = new LoginAck();
	private final ConfigurationSet noravrConfigurationSet = new ConfigurationSet();
	private final Ping noravrPing = new Ping();
	private final VTPCM noravrVTPCM = new VTPCM();
	private final VTOPUS noravrVTOPUS = new VTOPUS();
	private final VTAMBE noravrVTAMBE = new VTAMBE();
	private final RepeaterInfoGet noravrRepeaterInfoGet = new RepeaterInfoGet();
	private final ReflectorLinkGet noravrReflectorLinkGet = new ReflectorLinkGet();
	private final RoutingServiceGet noravrRoutingServiceGet = new RoutingServiceGet();
	private final AccessLogGet noravrAccessLogGet = new AccessLogGet();
	private final UserListGet noravrUserListGet = new UserListGet();

	private int currentUplinkID;
	private final Timer uplinkTimekeeper;
	private NoraVRClientEntry lastUplinkSrcClient;
	private int lastUplinkFrameID;
	private boolean execEchoback;

	private int currentDownlinkID;
	private final Timer downlinkTimekeeper;
	private boolean downlinkAMBEProcessing;
	private boolean downlinkPCMProcessing;
	private boolean downlinkOpus64kProcessing;
	private boolean downlinkOpus24kProcessing;
	private boolean downlinkOpus8kProcessing;

	private final Queue<NoraVRPacket> uplinkPackets;
	private final Queue<NoraVRPacket> downlinkPackets;
	private final Lock updownLinkPacketsLocker;

	private String linkedReflectorCallsign;
	private final Timer linkedReflectorProcessTimekeeper;

	private org.jp.illg.dstar.model.RoutingService currentRoutingService;
	private final Timer currentRoutingServiceProcessTimekeeper;

	private List<ReflectorRemoteUserEntry> remoteUsers;
	private final Lock remoteUsersLocker;

	private final List<User> userList;
	private final Timer userListProcessTimekeeper;

	private final List<AccessLogEntry> accessLog;


	public NoraVRProtocolProcessor(
		final ThreadUncaughtExceptionListener exceptionListener,
		final NoraVRProtocolEventListener eventListener,
		@NonNull final ExecutorService workerExecutor,
		final int noraVRPort,
		@NonNull final String gatewayCallsign, @NonNull final String repeaterCallsign,
		@NonNull final String loginPassword, final int connectionLimit,
		@NonNull final NoraVRConfiguration serverConfiguration,
		@NonNull final List<NoraVRLoginUserEntry> loginUsers,
		@NonNull final DSTARGateway gateway,
		@NonNull final DSTARRepeater repeater
	) {
		this(
			exceptionListener,
			eventListener,
			workerExecutor,
			noraVRPort,
			gatewayCallsign, repeaterCallsign,
			loginPassword, connectionLimit,
			serverConfiguration,
			loginUsers,
			gateway,
			repeater,
			null
		);
	}

	public NoraVRProtocolProcessor(
		final ThreadUncaughtExceptionListener exceptionListener,
		final NoraVRProtocolEventListener eventListener,
		@NonNull final ExecutorService workerExecutor,
		final int noraVRPort,
		@NonNull final String gatewayCallsign, @NonNull final String repeaterCallsign,
		@NonNull final String loginPassword, final int connectionLimit,
		@NonNull final NoraVRConfiguration serverConfiguration,
		@NonNull final List<NoraVRLoginUserEntry> loginUsers,
		@NonNull final DSTARGateway gateway,
		@NonNull final DSTARRepeater repeater,
		final SocketIO socketIO
	) {
		super(
			exceptionListener,
			NoraVRProtocolProcessor.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteAddressPort
		);

		logHeader = NoraVRProtocolProcessor.class.getSimpleName() + " : ";

		this.eventListener = eventListener;
		this.workerExecutor = workerExecutor;

		this.gateway = gateway;
		this.repeater = repeater;

		this.authLoginUsers = loginUsers;

		//コールサインチェック
		if(!CallSignValidator.isValidGatewayCallsign(gatewayCallsign)) {
			final String message = "Illegal gateway callsign " + gatewayCallsign + ".";
			if(log.isErrorEnabled()) {log.error(logHeader + message);}
			throw new IllegalArgumentException(message);
		}
		else if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
			final String message = "Illegal repeater callsign " + repeaterCallsign + ".";
			if(log.isErrorEnabled()) {log.error(logHeader + message);}
			throw new IllegalArgumentException(message);
		}

		this.gatewayCallsign = gatewayCallsign;
		this.repeaterCallsign = repeaterCallsign;

		clientManager = new NoraVRClientManager(clientKeepaliveTimeoutSeconds);

		readPackets = new LinkedList<NoraVRPacket>();

		clientsCleanupPeriodTimekeeper = new Timer(1, TimeUnit.SECONDS);

		currentUplinkID = 0x0;
		uplinkTimekeeper = new Timer();

		currentDownlinkID = 0x0;
		downlinkTimekeeper = new Timer();

		uplinkPackets = new LinkedList<NoraVRPacket>();
		downlinkPackets = new LinkedList<NoraVRPacket>();
		updownLinkPacketsLocker = new ReentrantLock();

		webRemoteControlService = null;

		if(noraVRPort >= 1024 && noraVRPort <= 65535)
			setNoraVRPort(noraVRPort);
		else {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Illegal NoraVR port number = " + noraVRPort +
					", Set to default value = " + NoraVRPortDefault + "."
				);
			}
			setNoraVRPort(NoraVRPortDefault);
		}

		setNoraVRloginPassword(loginPassword);

		setNoraVRClientConnectionLimit(noravrClientConnectionLimitDefault);

		setServerConfiguration(serverConfiguration);

		linkedReflectorCallsign = null;
		linkedReflectorProcessTimekeeper = new Timer(1, TimeUnit.SECONDS);

		currentRoutingService = null;
		currentRoutingServiceProcessTimekeeper = new Timer(3, TimeUnit.SECONDS);

		remoteUsers = null;
		remoteUsersLocker = new ReentrantLock();

		userList = new LinkedList<>();
		userListProcessTimekeeper = new Timer(2, TimeUnit.SECONDS);

		accessLog = new LinkedList<>();
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Accepted event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);

		}

		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Connected event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);
		}

		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Disconnected event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);
		}
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress,
		Exception ex
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Error event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
				, ex
			);
		}
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
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
						channel =
							getSocketIO().registUDP(
								new InetSocketAddress(getNoraVRPort()),
								NoraVRProtocolProcessor.this.getHandler(),
								NoraVRProtocolProcessor.this.getClass().getSimpleName() + "@" + getNoraVRPort()
							);
					}
				}
			) ||
			channel == null
		) {
			this.stop();

			closeChannel(channel);

			return false;
		}

		return true;
	}

	public NoraVRPacket readPacket() {
		updownLinkPacketsLocker.lock();
		try {
			return !uplinkPackets.isEmpty() ? uplinkPackets.poll() : null;
		}finally {updownLinkPacketsLocker.unlock();}
	}

	public boolean writePacket(@NonNull final NoraVRPacket packet) {
		if(
			packet.getCommandType() != NoraVRCommandType.VTPCM &&
			packet.getCommandType() != NoraVRCommandType.VTOPUS &&
			packet.getCommandType() != NoraVRCommandType.VTAMBE
		) {throw new IllegalArgumentException();}

		boolean isSuccess = false;

		updownLinkPacketsLocker.lock();
		try {
			isSuccess = downlinkPackets.add(packet);
		}finally {updownLinkPacketsLocker.unlock();}

		if(isSuccess) {wakeupProcessThread();}

		return isSuccess;
	}

	public List<NoraVRLoginUserEntry> getAuthLoginUsers() {
		return new ArrayList<>(authLoginUsers);
	}

	public List<NoraVRClientEntry> getLoginUsers() {
		return clientManager.getAllClients();
	}

	public boolean updateRemoteUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {
		remoteUsersLocker.lock();
		try {
			remoteUsers = users;
		}finally {
			remoteUsersLocker.unlock();
		}

		return true;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeChannel(channel);
	}

	@Override
	protected ThreadProcessResult processThread() {

		//クライアントからの受信パケットを解析してキューに保存する
		parseNoraVRPacket();

		//クライアントからの受信パケットを順次処理する
		for(Iterator<NoraVRPacket> it = readPackets.iterator(); it.hasNext();) {
			final NoraVRPacket receivePacket = it.next();
			it.remove();

			switch(receivePacket.getCommandType()) {
			case LOGINUSR:
				onReceiveLoginUser(receivePacket);
				break;

			case LGINUSR2:
				onReceiveLoginUser2(receivePacket);
				break;

			case LOGOUT:
				onReceiveLogout(receivePacket);
				break;

			case LOGIN_HS:
				onReceiveLoginHashCode(receivePacket);
				break;

			case PING:
				onReceivePing(receivePacket);
				break;

			case CONFSET:
				onReceiveConfigurationSet(receivePacket);
				break;

			case VTPCM:
			case VTOPUS:
			case VTAMBE:
				onReceiveVoiceTransfer(receivePacket);
				break;

			case RLINKGET:
				onReceiveReflectorLinkGet(receivePacket);
				break;

			case RSRVGET:
				onReceiveRoutingServiceGet(receivePacket);
				break;

			case RINFOGET:
				onReceiveRepeaterInfoGet(receivePacket);
				break;

			case ACLOGGET:
				onReceiveAccessLogGet(receivePacket);
				break;

			case USLSTGET:
				onReceiveUserListGet(receivePacket);
				break;

			default:
				if(log.isDebugEnabled()) {
					log.debug(logHeader + "Illegal packet received.\n" + receivePacket.toString(4));
				}
				break;
			}
		}

		//
		processDownlinkPackets();

		//
		if(
			currentDownlinkID != 0x0 &&
			downlinkTimekeeper.isTimeout()
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logHeader + "Timeout downlink frameID = " + String.format("0x%04X", currentDownlinkID) + ".\n" +
					"    ProcessingCodec:" +
					(getServerConfiguration().isSupportedCodecAMBE() ? "AMBE=" + downlinkAMBEProcessing + "/" : "") +
					(getServerConfiguration().isSupportedCodecPCM() ? "PCM=" + downlinkPCMProcessing + "/" : "") +
					(getServerConfiguration().isSupportedCodecOpus64k() ? "Opus64k=" + downlinkOpus64kProcessing + "/" : "") +
					(getServerConfiguration().isSupportedCodecOpus24k() ? "Opus24k=" + downlinkOpus24kProcessing + "/" : "") +
					(getServerConfiguration().isSupportedCodecOpus8k() ? "Opus8k=" + downlinkOpus8kProcessing + "/" : "")
				);
			}

			currentDownlinkID = 0x0;
			downlinkAMBEProcessing = false;
			downlinkPCMProcessing = false;
			downlinkOpus64kProcessing = false;
			downlinkOpus24kProcessing = false;
			downlinkOpus8kProcessing = false;
		}
		if(
			currentUplinkID != 0x0 &&
			uplinkTimekeeper.isTimeout()
		) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Timeout uplink frameID = " + String.format("0x%04X", currentUplinkID) + ".");

			currentUplinkID = 0x0;
		}


		processLinkedReflector();
		processCurrentRoutingService();
		processLoginUsers();

		processTransmitKeepalive();

		//タイムアウトしたクライアントを整理する
		cleanupClients();

		return ThreadProcessResult.NoErrors;
	}

	/**
	 * 接続が完了していて一定時間Pingがないクライアントに対してPongを送る
	 */
	private void processTransmitKeepalive() {

		clientManager.findClient(-1, null, null, -1, NoraVRClientState.ConnectionEstablished)
		.filter(new Predicate<NoraVRClientEntry>() {
			@Override
			public boolean test(NoraVRClientEntry client) {
				return client.getTransmitKeepaliveTimeKeeper().isTimeout();
			}
		})
		.forEach(new Consumer<NoraVRClientEntry>() {
			@Override
			public void accept(NoraVRClientEntry client) {

				client.getLocker().lock();
				try {
					sendPacketToClient(
						client,
						createPongPacket(client.getClientID(), client.getRemoteHostAddress())
					);

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader +
							"Sending Pong to transmit keepalive timeouted client " +
							client.getLoginCallsign() + client.getRemoteHostAddress()
						);
					}

					client.getTransmitKeepaliveTimeKeeper().setTimeoutTime(
						clientTransmitKeepalivePeriodSeconds, TimeUnit.SECONDS
					);
					client.getTransmitKeepaliveTimeKeeper().updateTimestamp();

				}finally {
					client.getLocker().unlock();
				}
			}
		});
	}

	/**
	 * レピータのリフレクタリンク先が変更されたらクライアント側に通知する
	 */
	private void processLinkedReflector() {
		if(linkedReflectorProcessTimekeeper.isTimeout()) {
			linkedReflectorProcessTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
			linkedReflectorProcessTimekeeper.updateTimestamp();

			final String currentLinkedReflector = repeater.getLinkedReflectorCallsign();
			if(currentLinkedReflector == null) {return;}

			if(!currentLinkedReflector.equals(linkedReflectorCallsign)) {

				clientManager.findClient(-1, null, null, -1, NoraVRClientState.ConnectionEstablished)
				.forEach(new Consumer<NoraVRClientEntry>() {
					@Override
					public void accept(NoraVRClientEntry client) {

						client.getLocker().lock();
						try {
							sendPacketToClient(
								client,
								createReflectorLinkPacket(
									client.getClientID(),
									currentLinkedReflector,
									client.getRemoteHostAddress()
								)
							);
						}finally {
							client.getLocker().unlock();
						}
					}
				});

				linkedReflectorCallsign = currentLinkedReflector;
			}
		}
	}

	private void processLoginUsers() {
		if(userListProcessTimekeeper.isTimeout()) {
			userListProcessTimekeeper.setTimeoutTime(2, TimeUnit.SECONDS);
			userListProcessTimekeeper.updateTimestamp();


			final Queue<UserListEntry> remoteChangedUsers = new LinkedList<>();

			List<ReflectorRemoteUserEntry> remoteUsers = null;
			remoteUsersLocker.lock();
			try {
				remoteUsers = this.remoteUsers;
				this.remoteUsers = null;
			}finally {
				remoteUsersLocker.unlock();
			}

			if(remoteUsers != null) {
				// Remote Login
				for(final ReflectorRemoteUserEntry remoteUser : remoteUsers) {
					if(
						!Stream.of(userList).anyMatch(new Predicate<User>() {
							@Override
							public boolean test(User user) {
								return
									user.getLocationType() == UserLocationType.Remote &&
									user.getCallsign().equals(remoteUser.getUserCallsign());
							}
						})
					) {
						userList.add(
							new User(
								UserLocationType.Remote,
								remoteUser.getUserCallsign(), remoteUser.getUserCallsignShort()
							)
						);
						remoteChangedUsers.add(
							new UserListEntry(
								new UserListEntryFlag(false, true, true),
								remoteUser.getUserCallsign(), remoteUser.getUserCallsignShort()
							)
						);
					}
				}

				// Remote Logout
				for(final Iterator<User> it = userList.iterator(); it.hasNext();) {
					final User user = it.next();

					if(
						user.getLocationType() == UserLocationType.Remote &&
						!Stream.of(remoteUsers).anyMatch(new Predicate<ReflectorRemoteUserEntry>() {
							@Override
							public boolean test(ReflectorRemoteUserEntry remoteUser) {
								return user.getCallsign().equals(remoteUser.getUserCallsign());
							}
						})
					) {
						it.remove();

						remoteChangedUsers.add(
							new UserListEntry(
								new UserListEntryFlag(false, true, false),
								user.getCallsign(), user.getCallsignShort()
							)
						);
					}
				}
			}

			final Queue<UserListEntry> localChangedUsers = new LinkedList<>();

			final List<NoraVRClientEntry> localUsers = clientManager.getAllClients();
			if(localUsers != null) {
				// Local Login
				for(final NoraVRClientEntry localUser : localUsers) {
					if(
						!Stream.of(userList).anyMatch(new Predicate<User>() {
							@Override
							public boolean test(User user) {
								return
									user.getLocationType() == UserLocationType.Local &&
									user.getCallsign().equals(localUser.getLoginCallsign());
							}
						})
					) {
						userList.add(
							new User(
								UserLocationType.Local,
								localUser.getLoginCallsign(), DSTARDefines.EmptyShortCallsign
							)
						);
						localChangedUsers.add(
							new UserListEntry(
								new UserListEntryFlag(true, false, true),
								localUser.getLoginCallsign(), DSTARDefines.EmptyShortCallsign
							)
						);
					}
				}

				// Local Logout
				for(final Iterator<User> it = userList.iterator(); it.hasNext();) {
					final User user = it.next();

					if(
						user.getLocationType() == UserLocationType.Local &&
						!Stream.of(localUsers).anyMatch(new Predicate<NoraVRClientEntry>() {
							@Override
							public boolean test(NoraVRClientEntry localUser) {
								return user.getCallsign().equals(localUser.getLoginCallsign());
							}
						})
					) {
						it.remove();

						localChangedUsers.add(
							new UserListEntry(
								new UserListEntryFlag(true, false, false),
								user.getCallsign(), user.getCallsignShort()
							)
						);
					}
				}
			}

			if(remoteChangedUsers.isEmpty() && localChangedUsers.isEmpty()) {return;}

			final long requestID = 0x0;
//				new Random(System.currentTimeMillis()).nextInt(0xFFFFFF) + 0x1;

			clientManager.findClient(-1, null, null, 2, NoraVRClientState.ConnectionEstablished)
			.forEach(new Consumer<NoraVRClientEntry>() {
				@Override
				public void accept(NoraVRClientEntry client) {
					client.getLocker().lock();
					try {
						final Queue<UserListEntry> remoteUsers =
							new LinkedList<>(
								client.getConfiguration().isRemoteUserNotify() ? remoteChangedUsers : Collections.emptyList()
							);

						final Queue<UserListEntry> localUsers =
							new LinkedList<>(
								client.getConfiguration().isLocalUserNotify() ?
									Stream.of(localChangedUsers).filter(new Predicate<UserListEntry>() {
										@Override
										public boolean test(UserListEntry user) {
											return !user.getMyCallsign().equals(client.getLoginCallsign());
										}
									}).toList()
									: Collections.emptyList()
							);

						final List<UserList> cmds = new LinkedList<>();
						do {
							final UserList cmd = new UserList();
							while(
								cmd.getUserList().size() < 10 &&
								(!remoteUsers.isEmpty() || !localUsers.isEmpty())
							) {
								UserListEntry e = remoteUsers.poll();
								if(e == null) {
									e = localUsers.poll();

									//自分自身は除外する
									if(e.getMyCallsign().equals(client.getLoginCallsign())) {continue;}
								}

								cmd.getUserList().add(e);
							}

							cmds.add(cmd);

						}while(!remoteUsers.isEmpty() || !localUsers.isEmpty());

						int i = 0;
						for(final UserList cmd : cmds) {
							cmd.setClientCode(client.getClientID());
							cmd.setRequestID(requestID);
							cmd.setBlockIndex(++i);
							cmd.setBlockTotal(cmds.size());

							sendPacketToClient(client, cmd);
						}

					}finally {
						client.getLocker().unlock();
					}
				}
			});
		}
	}

	private void addAccessLog(
		final String repeater1Callsign,
		final String repeater2Callsign,
		final String yourCallsign,
		final String myCallsign,
		final String myCallsignShort
	) {
		AccessLogEntry entry = null;
		for(final Iterator<AccessLogEntry> it = accessLog.iterator(); it.hasNext();) {
			final AccessLogEntry log = it.next();

			if(log.getMyCallsign().equals(myCallsign)) {
				it.remove();
				entry = log;

				break;
			}
		}

		if(entry == null) {
			entry = new AccessLogEntry();

			entry.setMyCallsign(myCallsign);
			entry.setMyCallsignShort(myCallsignShort);

			while(accessLog.size() >= accessLogLimit) {
				final Optional<AccessLogEntry> oldEntry = Stream.of(accessLog).min(
					ComparatorCompat.comparingLong(new ToLongFunction<AccessLogEntry>() {
						@Override
						public long applyAsLong(AccessLogEntry t) {
							return t.getTimestamp();
						}
					})
				);
				if(oldEntry.isPresent())
					accessLog.remove(oldEntry.get());
				else
					break;
			}
		}

		entry.setYourCallsign(yourCallsign);
		entry.setMyCallsignShort(myCallsignShort);
		entry.setTimestamp(System.currentTimeMillis());
		AccessLogRoute route = AccessLogRoute.Unknown;
		if(
			repeater1Callsign.charAt(repeater1Callsign.length() - 1) != 'G' &&
			repeater2Callsign.charAt(repeater2Callsign.length() - 1) == 'G'
		) {
			route = AccessLogRoute.LocalToGateway;
		}
		else if(
			repeater1Callsign.charAt(repeater1Callsign.length() - 1) == 'G' &&
			repeater2Callsign.charAt(repeater2Callsign.length() - 1) != 'G'
		) {
			route = AccessLogRoute.GatewayToLocal;
		}
		else {
			route = AccessLogRoute.LocalToLocal;
		}
		entry.setFlag(new AccessLogEntryFlag(route));

		accessLog.add(entry);

		final AccessLog cmd = new AccessLog(entry);

		clientManager.findClient(-1, null, null, 2, NoraVRClientState.ConnectionEstablished)
		.forEach(new Consumer<NoraVRClientEntry>() {
			@Override
			public void accept(NoraVRClientEntry client) {
				if(client.getConfiguration().isAccessLogNotify()) {
					client.getLocker().lock();
					try {
						cmd.setClientCode(client.getClientID());
						cmd.setRequestID(0x0);

						sendPacketToClient(client, cmd);
					}finally {
						client.getLocker().unlock();
					}
				}
			}
		});
	}

	private void processCurrentRoutingService() {
		if(currentRoutingServiceProcessTimekeeper.isTimeout(3, TimeUnit.SECONDS)) {
			currentRoutingServiceProcessTimekeeper.updateTimestamp();

			final org.jp.illg.dstar.model.RoutingService service =
				getRepeater().getRoutingService();

			if(service != currentRoutingService) {
				clientManager.findClient(-1, null, null, 2, NoraVRClientState.ConnectionEstablished)
				.forEach(new Consumer<NoraVRClientEntry>() {
					@Override
					public void accept(NoraVRClientEntry client) {

						client.getLocker().lock();
						try {
							sendPacketToClient(
								client,
								createRoutingServicePacket(
									client.getClientID(),
									service,
									client.getRemoteHostAddress()
								)
							);
						}finally {
							client.getLocker().unlock();
						}
					}
				});
			}

			currentRoutingService = service;
		}
	}

	private void processDownlinkPackets() {

		updownLinkPacketsLocker.lock();
		try {
			for(Iterator<NoraVRPacket> it = downlinkPackets.iterator(); it.hasNext();) {
				final NoraVRPacket packet = it.next();
				it.remove();

				if(
					packet.getCommandType() != NoraVRCommandType.VTPCM &&
					packet.getCommandType() != NoraVRCommandType.VTOPUS &&
					packet.getCommandType() != NoraVRCommandType.VTAMBE
				) {continue;}

				final NoraVRVoicePacket<?> voice = (NoraVRVoicePacket<?>)packet;
				if(voice.getFrameID() == 0x0) {continue;}

				if(
					currentDownlinkID == 0x0 &&
					!voice.isEndSequence()
				) {
					if(currentDownlinkID == 0x0 && log.isDebugEnabled())
						log.debug(logHeader + "Start downlink frameID = " + String.format("0x%04X", voice.getFrameID()) + ".");

					currentDownlinkID = voice.getFrameID();
					downlinkTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
					downlinkTimekeeper.updateTimestamp();

					if(getServerConfiguration().isSupportedCodecAMBE()) {downlinkAMBEProcessing = true;}
					if(getServerConfiguration().isSupportedCodecPCM()) {downlinkPCMProcessing = true;}
					if(getServerConfiguration().isSupportedCodecOpus64k()) {downlinkOpus64kProcessing = true;}
					if(getServerConfiguration().isSupportedCodecOpus24k()) {downlinkOpus24kProcessing = true;}
					if(getServerConfiguration().isSupportedCodecOpus8k()) {downlinkOpus8kProcessing = true;}

					final RepeaterControlFlag repeaterFlag = RepeaterControlFlag.getTypeByValue(voice.getFlags()[0]);
					if(
						repeaterFlag == RepeaterControlFlag.NOTHING_NULL ||
						repeaterFlag == RepeaterControlFlag.AUTO_REPLY
					) {
						addAccessLog(
							voice.getRepeater1Callsign(),
							voice.getRepeater2Callsign(),
							voice.getYourCallsign(),
							voice.getMyCallsignLong(),
							voice.getMyCallsignShort()
						);
					}
				}

				if(currentDownlinkID == voice.getFrameID()){
					downlinkTimekeeper.updateTimestamp();

					for(final NoraVRClientEntry client : clientManager.getAllClients()) {

						client.getLocker().lock();
						try {
							if(
								client.getDownlinkCodec() == voice.getCodecType() &&
								!client.isConnectionFailed() &&
								client.getClientState() == NoraVRClientState.ConnectionEstablished &&
								(
									client != lastUplinkSrcClient ||
									(
										client == lastUplinkSrcClient &&
										(
											(voice.getFrameID() == lastUplinkFrameID && execEchoback) ||
											voice.getFrameID() != lastUplinkFrameID
										)
									)
								)
							) {
								voice.setClientCode(client.getClientID());

								sendPacketToClient(client, voice.clone());
							}
						}finally {
							client.getLocker().unlock();
						}
					}

					if(voice.isEndSequence()) {
						switch(voice.getCodecType()) {
						case AMBE:
							downlinkAMBEProcessing = false;
							break;
						case PCM:
							downlinkPCMProcessing = false;
							break;
						case Opus64k:
							downlinkOpus64kProcessing = false;
							break;
						case Opus24k:
							downlinkOpus24kProcessing = false;
							break;
						case Opus8k:
							downlinkOpus8kProcessing = false;
							break;
						default:
							if(log.isDebugEnabled())
								log.debug(logHeader + "Illegal codec type = " + voice.getCodecType());

							break;
						}

						if(
							(
								!getServerConfiguration().isSupportedCodecAMBE() ||
								!downlinkAMBEProcessing
							) &&
							(
								!getServerConfiguration().isSupportedCodecPCM() ||
								!downlinkPCMProcessing
							) &&
							(
								!getServerConfiguration().isSupportedCodecOpus64k() ||
								!downlinkOpus64kProcessing
							) &&
							(
								!getServerConfiguration().isSupportedCodecOpus24k() ||
								!downlinkOpus24kProcessing
							) &&
							(
								!getServerConfiguration().isSupportedCodecOpus8k() ||
								!downlinkOpus8kProcessing
							)
						) {
							if(log.isDebugEnabled())
								log.debug(logHeader + "End downlink frameID = " + String.format("0x%04X", currentDownlinkID) + ".");

							currentDownlinkID = 0x0;
						}
					}
				}
			}
		}finally {
			updownLinkPacketsLocker.unlock();
		}
	}

	private void cleanupClients() {
		if(clientsCleanupPeriodTimekeeper.isTimeout()) {
			clientsCleanupPeriodTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
			clientsCleanupPeriodTimekeeper.updateTimestamp();

			final List<NoraVRClientEntry> removeEntries = new ArrayList<>();

			for(final NoraVRClientEntry client : clientManager.getAllClients()) {

				client.getLocker().lock();
				try {
					final boolean timeout = client.getKeepaliveTimeKeeper().isTimeout();

					if(client.isConnectionFailed() && timeout) {

						//コネクションが失われてからの猶予期間を過ぎたので、クライアントリストから削除する
						removeEntries.add(client);

						if(log.isDebugEnabled()) {
							log.debug(logHeader + "Client connection failed, removed from client list.\n" + client.toString(4));
						}
					}
					else if(!client.isConnectionFailed() && timeout) {
						//コネクションがタイムアウトしたので、コネクションが失われたことをマークする
						client.setConnectionFailed(true);

						switch(client.getClientState()) {
//							case ConnectionEstablished:
//								client.setClientState(NoraVRClientState.ConnectionFailed);
//								break;

						case LoginChallenge:
							client.setClientState(NoraVRClientState.LoginFailed);
							break;

						default:
							break;
						}

						client.getKeepaliveTimeKeeper().setTimeoutTime(1, TimeUnit.SECONDS);
						client.getKeepaliveTimeKeeper().updateTimestamp();
					}
				}finally {
					client.getLocker().unlock();
				}
			}

			for(final NoraVRClientEntry client : removeEntries) {
				clientManager.removeClient(client);

				//ログアウトイベントをコール
				if(eventListener != null) {
					workerExecutor.submit(new RunnableTask(getExceptionListener()) {
						@Override
						public void task() {
							eventListener.onClientLogoutEvent(client);
						}
					});
				}
			}
		}
	}

	private void onReceiveLoginUser(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.LOGINUSR)
			return;

		final String loginCallsign = ((LoginUser)packet).getLoginUserName();
		//ログインコールサインが誤ったものであれば拒否する
		if(!CallSignValidator.isValidUserCallsign(loginCallsign)) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Invalid login callsign = " + loginCallsign + ".")
			);
			return;
		}
		//既にログインされていれば拒否する
		else if(clientManager.isClientConnected(loginCallsign)) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Already connected login callsign = " + loginCallsign + ".")
			);
			return;
		}
		else if(clientManager.getClientCount() >= getNoraVRClientConnectionLimit()) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Client connection limit exceed.")
			);
			return;
		}

		//クライアント登録
		final NoraVRClientEntry client =
			clientManager.createClient(loginCallsign, "", "", packet.getRemoteHostAddress());

		client.setProtocolVersion(1);

		client.getKeepaliveTimeKeeper().setTimeoutTime(5, TimeUnit.SECONDS);
		client.getKeepaliveTimeKeeper().updateTimestamp();

		//Login CCを返信
		sendPacketToClient(client, createLoginChallengeCodePacket(client));

		//ログインイベントをコール
		if(eventListener != null) {
			workerExecutor.submit(new RunnableTask() {
				@Override
				public void task() {
					eventListener.onClientLoginEvent(client);
				}
			});
		}
	}

	private void onReceiveLoginUser2(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.LGINUSR2)
			return;

		final String loginCallsign = ((LoginUser2)packet).getLoginUserName();
		//ログインコールサインが誤ったものであれば拒否する
		if(!CallSignValidator.isValidUserCallsign(loginCallsign)) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Invalid login callsign = " + loginCallsign + ".")
			);
			return;
		}
		//既にログインされていれば拒否する
		else if(clientManager.isClientConnected(loginCallsign)) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Already connected login callsign = " + loginCallsign + ".")
			);
			return;
		}
/*
		//非対応プロトコルバージョンであれば拒否する
		else if(supportedProtocolVersion < ((LoginUser2)packet).getProtocolVersion()){
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(
					packet.getRemoteHostAddress(),
					"Protocol version " + ((LoginUser2)packet).getProtocolVersion() + " is not supported."
				)
			);
			return;
		}
*/
		//クライアント接続数がリミットを超えていれば接続拒否する
		else if(clientManager.getClientCount() >= getNoraVRClientConnectionLimit()) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Client connection limit exceed.")
			);
			return;
		}

		//クライアント登録
		final NoraVRClientEntry client =
			clientManager.createClient(
				loginCallsign,
				((LoginUser2)packet).getApplicationName(),
				((LoginUser2)packet).getApplicationVersion(),
				packet.getRemoteHostAddress()
			);

		final int preferredProtocolVersion =
			((LoginUser2)packet).getProtocolVersion() <= 0 ? 1 : ((LoginUser2)packet).getProtocolVersion();
		client.setProtocolVersion(
			preferredProtocolVersion <= supportedProtocolVersion ? preferredProtocolVersion : supportedProtocolVersion
		);

		client.getKeepaliveTimeKeeper().setTimeoutTime(5, TimeUnit.SECONDS);
		client.getKeepaliveTimeKeeper().updateTimestamp();

		//Login CCを返信
		sendPacketToClient(client, createLoginChallengeCodePacket(client));

		//ログインイベントをコール
		if(eventListener != null) {
			workerExecutor.submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					eventListener.onClientLoginEvent(client);
				}
			});
		}
	}

	private void onReceiveLogout(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.LOGOUT)
			return;

		final long clientID = ((Logout)packet).getClientCode();

		NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Logout failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed() && client.getClientState() == NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Logout failed, Connection timeout.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Logout failed, Illegal state for client connection.")
				);
				return;
			}

			clientManager.removeClient(client);

			//Ackを返信
			sendPacketToClient(client, createAckPacket(client.getRemoteHostAddress()));

			//ログアウトイベントをコール
			if(eventListener != null) {
				workerExecutor.submit(new RunnableTask(getExceptionListener()) {
					@Override
					public void task() {
						eventListener.onClientLogoutEvent(client);
					}
				});
			}

			if(log.isDebugEnabled())
				log.debug(logHeader + "Logout client " + client.getLoginCallsign() + ".");
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveLoginHashCode(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.LOGIN_HS)
			return;

		final NoraVRClientEntry client =
			clientManager.findClientSingle(packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Login failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed() && client.getClientState() == NoraVRClientState.LoginFailed) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Login failed, Login timeout.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.LoginChallenge) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Login failed, Illegal state for client login.")
				);
				return;
			}

			Optional<NoraVRLoginUserEntry> userEntry =
				Stream.of(authLoginUsers)
				.filter(new Predicate<NoraVRLoginUserEntry>() {
					@Override
					public boolean test(NoraVRLoginUserEntry entry) {
						return client.getLoginCallsign().trim().equals(entry.getLoginCallsign());
					}
				}).findFirst();

			//ログインパスワードが設定されていれば照合する
			if(
				(userEntry.isPresent() && !"".equals(userEntry.get().getLoginPassword())) ||
				(!userEntry.isPresent() && !"".equals(getNoraVRloginPassword()))
			){
				//ハッシュ値を確認
				final boolean validHash =
					isMatchHashCode(
						client.getLoginChallengeCode(),
						userEntry.isPresent() ?
							userEntry.get().getLoginPassword() : getNoraVRloginPassword(),
						((LoginHashCode)packet).getHashCode()
					);

				if(!validHash) {
					client.setClientState(NoraVRClientState.LoginFailed);
					client.setConnectionFailed(true);

					client.getKeepaliveTimeKeeper().setTimeoutTime(1, TimeUnit.SECONDS);
					client.getKeepaliveTimeKeeper().updateTimestamp();

					sendPacketToRemoteHost(
						packet.getRemoteHostAddress(),
						createNakPacket(packet.getRemoteHostAddress(), "Login failed, Password is not matched.")
					);
					return;
				}
			}

			//タイムスタンプ更新
			client.getKeepaliveTimeKeeper().setTimeoutTime(
				clientKeepaliveTimeoutSeconds, TimeUnit.SECONDS
			);
			client.getKeepaliveTimeKeeper().updateTimestamp();

			client.getTransmitKeepaliveTimeKeeper().setTimeoutTime(
				clientTransmitKeepalivePeriodSeconds, TimeUnit.SECONDS
			);
			client.getTransmitKeepaliveTimeKeeper().updateTimestamp();

			//コネクション確立
			client.setClientState(NoraVRClientState.ConnectionEstablished);

			//Login Ackを返信
			sendPacketToClient(client, createLoginAckPacket(client));

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader + "Client " + client.getLoginCallsign() + "@" + client.getRemoteHostAddress() + " loggedin," +
					" Total " + clientManager.getClientCount() + "clients."
					);
			}
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceivePing(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.PING)
			return;

		final long clientID = ((Ping)packet).getClientCode();

		NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Ping failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed() && client.getClientState() == NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Ping failed, Connection timeout.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Ping failed, Illegal state for client connection.")
				);
				return;
			}

			//タイムスタンプを更新
			client.getKeepaliveTimeKeeper().updateTimestamp();
			client.getTransmitKeepaliveTimeKeeper().setTimeoutTime(
				clientTransmitKeepalivePeriodSeconds, TimeUnit.SECONDS
			);
			client.getTransmitKeepaliveTimeKeeper().updateTimestamp();

			//Pongを返信
			sendPacketToClient(client, createPongPacket(clientID, client.getRemoteHostAddress()));

		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveConfigurationSet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.CONFSET)
			return;

		final long clientID = ((ConfigurationSet)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		final NoraVRConfiguration config =
			((ConfigurationSet)packet).getServerConfiguration();

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Configuration set failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Configuration set failed, already connection failed.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Configuration set failed, Illegal state for client connection.")
				);
				return;
			}

			Optional<NoraVRLoginUserEntry> userEntry =
				Stream.of(authLoginUsers)
				.filter(new Predicate<NoraVRLoginUserEntry>() {
					@Override
					public boolean test(NoraVRLoginUserEntry entry) {
						return client.getLoginCallsign().trim().equals(entry.getLoginCallsign());
					}
				}).findFirst();

			//サーバーがRFノードを許可しない設定で、RFノードクライアントが接続しようとした場合に拒否
			if(
				(userEntry.isPresent() && !userEntry.get().isAllowRFNode() && config.isRfNode()) ||
				(!userEntry.isPresent() && !getServerConfiguration().isRfNode() && config.isRfNode())
			) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Configuration set failed, RF node connection is not allowed."
					)
				);
				return;
			}

			//クライアントの設定を更新
			client.setConfiguration(config);

			//使用コーデックを決定
			if(config.isSupportedCodecOpus8k())
				client.setDownlinkCodec(NoraVRCodecType.Opus8k);
			else if(config.isSupportedCodecOpus24k())
				client.setDownlinkCodec(NoraVRCodecType.Opus24k);
			else if(config.isSupportedCodecOpus64k())
				client.setDownlinkCodec(NoraVRCodecType.Opus64k);
			else if(config.isSupportedCodecPCM())
				client.setDownlinkCodec(NoraVRCodecType.PCM);
			else if(config.isSupportedCodecAMBE())
				client.setDownlinkCodec(NoraVRCodecType.AMBE);
			else
				client.setDownlinkCodec(null);

			//Ackを返信
			sendPacketToClient(client, createAckPacket(client.getRemoteHostAddress()));

		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveVoiceTransfer(@NonNull NoraVRPacket packet) {
		if(
			packet.getCommandType() != NoraVRCommandType.VTPCM &&
			packet.getCommandType() != NoraVRCommandType.VTOPUS &&
			packet.getCommandType() != NoraVRCommandType.VTAMBE
		) {return;}

		final long clientID = ((VoiceTransferBase<?>)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(
			client == null ||
			client.getClientState() != NoraVRClientState.ConnectionEstablished
		) {return;}

		client.getLocker().lock();
		try {
			final VoiceTransferBase<?> voice = (VoiceTransferBase<?>)packet;

			if(
				voice.getFrameID() == 0x0 ||
				(voice.getCodecType() == NoraVRCodecType.AMBE && !serverConfiguration.isSupportedCodecAMBE()) ||
				(voice.getCodecType() == NoraVRCodecType.PCM && !serverConfiguration.isSupportedCodecPCM()) ||
				(
					voice.getCodecType() == NoraVRCodecType.Opus &&
					!serverConfiguration.isSupportedCodecOpus64k() &&
					!serverConfiguration.isSupportedCodecOpus24k() &&
					!serverConfiguration.isSupportedCodecOpus8k()
				)
			) {return;}

			boolean validPacket = false;

			if(
				currentUplinkID == 0x00 &&
				!voice.isEndSequence() &&
				checkHeaders(voice)
			) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Start uplink frameID = " + String.format("0x%04X", voice.getFrameID()) + ".");

				currentUplinkID = voice.getFrameID();

				uplinkTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
				uplinkTimekeeper.updateTimestamp();

				lastUplinkFrameID = voice.getFrameID();
				lastUplinkSrcClient = client;

				execEchoback = client.getConfiguration() != null && client.getConfiguration().isEchoback();

				validPacket = true;

				addAccessLog(
					voice.getRepeater1Callsign(),
					voice.getRepeater2Callsign(),
					voice.getYourCallsign(),
					voice.getMyCallsignLong(),
					voice.getMyCallsignShort()
				);
			}
			else if(currentUplinkID == voice.getFrameID()) {
				if(!voice.isEndSequence()) {
					uplinkTimekeeper.updateTimestamp();
				}
				else {
					if(log.isDebugEnabled())
						log.debug(logHeader + "End uplink frameID = " + String.format("0x%04X", currentUplinkID) + ".");

					currentUplinkID = 0x0;
				}

				validPacket = true;
			}
			else {
				validPacket = false;
			}

			if(validPacket) {
				updownLinkPacketsLocker.lock();
				try {
					uplinkPackets.add(packet);
				}finally {updownLinkPacketsLocker.unlock();}
			}
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveReflectorLinkGet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.RLINKGET)
			return;

		final long clientID = ((ReflectorLinkGet)packet).getClientCode();

		NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, already connection failed.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, Illegal state for client connection.")
				);
				return;
			}

			//RelectorLinkを返信
			sendPacketToClient(
				client,
				createReflectorLinkPacket(
					client.getClientID(),
					repeater.getLinkedReflectorCallsign(),
					client.getRemoteHostAddress()
				)
			);
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveRoutingServiceGet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.RSRVGET)
			return;

		final long clientID = ((RoutingServiceGet)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, already connection failed.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Reflector link get failed, Illegal state for client connection."
					)
				);
				return;
			}
			// プロトコルバージョン確認
			else if(client.getProtocolVersion() < NoraVRCommandType.RSRVGET.getProtocolVersion()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Current your protocol version < " + NoraVRCommandType.RSRVGET.getProtocolVersion() + "."
					)
				);
				return;
			}

			//RoutingServiceを返信
			sendPacketToClient(
				client,
				createRoutingServicePacket(
					clientID, currentRoutingService, client.getRemoteHostAddress()
				)
			);
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveRepeaterInfoGet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.RINFOGET)
			return;

		final long clientID = ((RepeaterInfoGet)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Reflector information get failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Reflector link get failed, already connection failed.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Reflector link get failed, Illegal state for client connection."
					)
				);
				return;
			}
			// プロトコルバージョン確認
			else if(client.getProtocolVersion() < NoraVRCommandType.RINFOGET.getProtocolVersion()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Current your protocol version < " + NoraVRCommandType.RINFOGET.getProtocolVersion() + "."
					)
				);
				return;
			}

			//RoutingServiceを返信
			sendPacketToClient(
				client,
				createRepeaterInfoPacket(
					clientID, getRepeater(), client.getRemoteHostAddress()
				)
			);
		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveAccessLogGet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.ACLOGGET)
			return;

		final long clientID = ((AccessLogGet)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "Access log get command failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "Access log get command failed, your connection was marked fail.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Access log get command failed, Illegal state for client connection."
					)
				);
				return;
			}
			// プロトコルバージョン確認
			else if(client.getProtocolVersion() < NoraVRCommandType.ACLOGGET.getProtocolVersion()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Current your protocol version < " + NoraVRCommandType.ACLOGGET.getProtocolVersion() + "."
					)
				);
				return;
			}

			final List<AccessLog> cmds = new LinkedList<>();
			final Queue<AccessLogEntry> logs =
				Stream.of(accessLog).sorted(ComparatorCompat.comparingLong(new ToLongFunction<AccessLogEntry>() {
					@Override
					public long applyAsLong(AccessLogEntry log) {
						return log.getTimestamp();
					}
				}).reversed())
				.collect(new Collector<AccessLogEntry, LinkedList<AccessLogEntry>, Queue<AccessLogEntry>>() {
					@Override
					public Supplier<LinkedList<AccessLogEntry>> supplier() {
						return new Supplier<LinkedList<AccessLogEntry>>() {
							@Override
							public LinkedList<AccessLogEntry> get() {
								return new LinkedList<>();
							}
						};
					}

					@Override
					public BiConsumer<LinkedList<AccessLogEntry>, AccessLogEntry> accumulator() {
						return new BiConsumer<LinkedList<AccessLogEntry>, AccessLogEntry>(){
							@Override
							public void accept(LinkedList<AccessLogEntry> list, AccessLogEntry user) {
								list.add(user);
							}
						};
					}

					@Override
					public Function<LinkedList<AccessLogEntry>, Queue<AccessLogEntry>> finisher() {
						return new Function<LinkedList<AccessLogEntry>, Queue<AccessLogEntry>>(){
							@Override
							public Queue<AccessLogEntry> apply(LinkedList<AccessLogEntry> t) {
								return t;
							}
						};
					}
				});

			int logNo = 0;
			do {
				final AccessLog cmd = new AccessLog();
				while(
					cmd.getLogs().size() < AccessLog.maxLogEntry &&
					!logs.isEmpty()
				) {
					final AccessLogEntry log = logs.poll().clone();
					log.setLogNo(++logNo);

					cmd.getLogs().add(log);
				}

				cmds.add(cmd);

			}while(!logs.isEmpty());

			final long requestID = ((AccessLogGet)packet).getRequestID();

			int i = 0;
			for(final AccessLog cmd : cmds) {
				cmd.setClientCode(client.getClientID());
				cmd.setRequestID(requestID);
				cmd.setBlockIndex(++i);
				cmd.setBlockTotal(cmds.size());

				sendPacketToClient(client, cmd);
			}

		}finally {
			client.getLocker().unlock();
		}
	}

	private void onReceiveUserListGet(@NonNull NoraVRPacket packet) {
		if(packet.getCommandType() != NoraVRCommandType.USLSTGET)
			return;

		final long clientID = ((UserListGet)packet).getClientCode();

		final NoraVRClientEntry client =
			clientManager.findClientSingle(clientID, packet.getRemoteHostAddress());

		if(client == null) {
			sendPacketToRemoteHost(
				packet.getRemoteHostAddress(),
				createNakPacket(packet.getRemoteHostAddress(), "User list get command failed, Not found client.")
			);
			return;
		}

		client.getLocker().lock();
		try {
			if(client.isConnectionFailed()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(packet.getRemoteHostAddress(), "User list get command failed, your connection was marked fail.")
				);
				return;
			}
			//ログイン状態を確認
			else if(client.getClientState() != NoraVRClientState.ConnectionEstablished) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"User list get command failed, Illegal state for client connection."
					)
				);
				return;
			}
			// プロトコルバージョン確認
			else if(client.getProtocolVersion() < NoraVRCommandType.USLSTGET.getProtocolVersion()) {
				sendPacketToRemoteHost(
					packet.getRemoteHostAddress(),
					createNakPacket(
						packet.getRemoteHostAddress(),
						"Current your protocol version < " + NoraVRCommandType.USLSTGET.getProtocolVersion() + "."
					)
				);
				return;
			}

			final Queue<UserListEntry> localUsers =
				Stream.of(userList)
				.filter(new Predicate<User>() {
					@Override
					public boolean test(User user) {
						return
							user.getLocationType() == UserLocationType.Local &&
							!client.getLoginCallsign().equals(user.getCallsign());
					}
				})
				.map(new Function<User, UserListEntry>() {
					@Override
					public UserListEntry apply(User user) {
						return new UserListEntry(
							new UserListEntryFlag(
								user.getLocationType() == UserLocationType.Local,
								user.getLocationType() == UserLocationType.Remote,
								true
							),
							user.getCallsign(), user.getCallsignShort()
						);
					}
				})
				.collect(new Collector<UserListEntry, LinkedList<UserListEntry>, Queue<UserListEntry>>() {
					@Override
					public Supplier<LinkedList<UserListEntry>> supplier() {
						return new Supplier<LinkedList<UserListEntry>>() {
							@Override
							public LinkedList<UserListEntry> get() {
								return new LinkedList<>();
							}
						};
					}

					@Override
					public BiConsumer<LinkedList<UserListEntry>, UserListEntry> accumulator() {
						return new BiConsumer<LinkedList<UserListEntry>, UserList.UserListEntry>(){
							@Override
							public void accept(LinkedList<UserListEntry> list, UserListEntry user) {
								list.add(user);
							}
						};
					}

					@Override
					public Function<LinkedList<UserListEntry>, Queue<UserListEntry>> finisher() {
						return new Function<LinkedList<UserListEntry>, Queue<UserListEntry>>(){
							@Override
							public Queue<UserListEntry> apply(LinkedList<UserListEntry> t) {
								return t;
							}
						};
					}
				});

			final Queue<UserListEntry> remoteUsers =
				Stream.of(userList)
				.filter(new Predicate<User>() {
					@Override
					public boolean test(User user) {
						return
							user.getLocationType() == UserLocationType.Remote &&
							!client.getLoginCallsign().equals(user.getCallsign());
					}
				})
				.map(new Function<User, UserListEntry>() {
					@Override
					public UserListEntry apply(User user) {
						return new UserListEntry(
							new UserListEntryFlag(
								user.getLocationType() == UserLocationType.Local,
								user.getLocationType() == UserLocationType.Remote,
								true
							),
							user.getCallsign(), user.getCallsignShort()
						);
					}
				})
				.collect(new Collector<UserListEntry, LinkedList<UserListEntry>, Queue<UserListEntry>>() {
					@Override
					public Supplier<LinkedList<UserListEntry>> supplier() {
						return new Supplier<LinkedList<UserListEntry>>() {
							@Override
							public LinkedList<UserListEntry> get() {
								return new LinkedList<>();
							}
						};
					}

					@Override
					public BiConsumer<LinkedList<UserListEntry>, UserListEntry> accumulator() {
						return new BiConsumer<LinkedList<UserListEntry>, UserList.UserListEntry>(){
							@Override
							public void accept(LinkedList<UserListEntry> list, UserListEntry user) {
								list.add(user);
							}
						};
					}

					@Override
					public Function<LinkedList<UserListEntry>, Queue<UserListEntry>> finisher() {
						return new Function<LinkedList<UserListEntry>, Queue<UserListEntry>>(){
							@Override
							public Queue<UserListEntry> apply(LinkedList<UserListEntry> t) {
								return t;
							}
						};
					}
				});

			final List<UserList> cmds = new LinkedList<>();
			do {
				final UserList cmd = new UserList();
				while(
					cmd.getUserList().size() < UserList.maxUserEntry &&
					(!remoteUsers.isEmpty() || !localUsers.isEmpty())
				) {
					UserListEntry e = remoteUsers.poll();
					if(e == null) {e = localUsers.poll();}

					cmd.getUserList().add(e);
				}

				cmds.add(cmd);

			}while(!remoteUsers.isEmpty() || !localUsers.isEmpty());

			final long requestID = ((UserListGet)packet).getRequestNo();

			int i = 0;
			for(final UserList cmd : cmds) {
				cmd.setClientCode(client.getClientID());
				cmd.setRequestID(requestID);
				cmd.setBlockIndex(++i);
				cmd.setBlockTotal(cmds.size());

				sendPacketToClient(client, cmd);
			}
		}finally {
			client.getLocker().unlock();
		}
	}

	private boolean sendPacketToClient(
		@NonNull NoraVRClientEntry client, @NonNull NoraVRPacket packet
	) {
		return sendPacketToRemoteHost(client.getRemoteHostAddress(), packet);
	}

	private boolean sendPacketToRemoteHost(
		@NonNull InetSocketAddress remoteHostAddress, @NonNull NoraVRPacket packet
	) {
		final ByteBuffer buffer = packet.assemblePacket();

		if(buffer == null) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Failed assemble packet.\n" + packet.toString(4));

			return false;
		}

		if(log.isTraceEnabled()) {
			buffer.mark();
			log.trace(
				logHeader + buffer.remaining() + "bytes transmit to " + remoteHostAddress + ".\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
			buffer.reset();
		}

		return writeUDPPacket(channel.getKey(), remoteHostAddress, buffer);
	}

	private NoraVRPacket createLoginChallengeCodePacket(
		@NonNull NoraVRClientEntry client
	) {
		client.setLoginChallengeCode(NoraVRUtil.createLoginChallengeCode());

		final LoginChallengeCode loginChallengeCode = new LoginChallengeCode();
		loginChallengeCode.setRemoteHostAddress(client.getRemoteHostAddress());
		loginChallengeCode.setChallengeCode(client.getLoginChallengeCode());

		return loginChallengeCode;
	}

	private NoraVRPacket createLoginAckPacket(
		@NonNull final NoraVRClientEntry client
	) {
		Optional<NoraVRLoginUserEntry> userEntry =
			Stream.of(authLoginUsers)
			.filter(new Predicate<NoraVRLoginUserEntry>() {
				@Override
				public boolean test(NoraVRLoginUserEntry entry) {
					return client.getLoginCallsign().trim().equals(entry.getLoginCallsign());
				}
			}).findFirst();

		final NoraVRConfiguration serverConfig = new NoraVRConfiguration(getServerConfiguration());
		if(userEntry.isPresent()) {serverConfig.setRfNode(userEntry.get().isAllowRFNode());}

		final LoginAck loginAck = new LoginAck();
		loginAck.setClientCode(client.getClientID());
		loginAck.setServerConfiguration(serverConfig);
		loginAck.setProtocolVersion((byte)client.getProtocolVersion());
		loginAck.setGatewayCallsign(getGatewayCallsign());
		loginAck.setRepeaterCallsign(getRepeaterCallsign());

		return loginAck;
	}

	private NoraVRPacket createAckPacket(
		@NonNull final InetSocketAddress remoteHostAddress
	) {
		final Ack ack = new Ack();
		ack.setRemoteHostAddress(remoteHostAddress);

		return ack;
	}

	private NoraVRPacket createNakPacket(
		@NonNull final InetSocketAddress remoteHostAddress,
		String reason
	) {
		if(reason == null) {reason = "";}

		final Nak nak = new Nak();
		nak.setRemoteHostAddress(remoteHostAddress);
		nak.setReason(reason);

		return nak;
	}

	private NoraVRPacket createPongPacket(
		final long clientCode,
		@NonNull final InetSocketAddress remoteHostAddress
	) {
		final Pong pong = new Pong();
		pong.setClientCode(clientCode);
		pong.setRemoteHostAddress(remoteHostAddress);

		return pong;
	}

	private NoraVRPacket createReflectorLinkPacket(
		final long clientCode,
		final String linkedReflectorCallsign,
		@NonNull final InetSocketAddress remoteHostAddress
	) {
		final ReflectorLink cmd = new ReflectorLink();
		cmd.setClientCode(clientCode);
		cmd.setLinkedReflectorCallsign(linkedReflectorCallsign);
		cmd.setRemoteHostAddress(remoteHostAddress);

		return cmd;
	}

	private NoraVRPacket createRoutingServicePacket(
		final long clientCode,
		final org.jp.illg.dstar.model.RoutingService routingService,
		@NonNull final InetSocketAddress remoteHostAddress
	) {
		final RoutingService cmd = new RoutingService();
		cmd.setClientCode(clientCode);
		cmd.setRoutingServiceName(
			routingService != null ?
				routingService.getServiceType().getTypeName() : RoutingServiceTypes.Unknown.getTypeName()
		);
		cmd.setRemoteHostAddress(remoteHostAddress);

		return cmd;
	}

	private NoraVRPacket createRepeaterInfoPacket(
		final long clientCode,
		final DSTARRepeater repeater,
		@NonNull final InetSocketAddress remoteHostAddress
	) {
		final RepeaterInfo cmd = new RepeaterInfo();
		cmd.setClientCode(clientCode);

		cmd.setCallsign(repeater.getRepeaterCallsign());
		cmd.setName(repeater.getName());
		cmd.setLocation(repeater.getLocation());
		cmd.setFrequencyMHz(
			repeater.getFrequency() != 0.0d ? repeater.getFrequency() / 1000000d : 0.0d
		);
		cmd.setFrequencyOffsetMHz(
			repeater.getFrequencyOffset() != 0.0d ? repeater.getFrequencyOffset() / 1000000d : 0.0d
		);
		cmd.setServiceRangeKm(repeater.getRange());
		cmd.setAgl(repeater.getAgl());
		cmd.setUrl(repeater.getUrl());
		cmd.setDescription1(repeater.getDescription1());
		cmd.setDescription1(repeater.getDescription2());

		cmd.setRemoteHostAddress(remoteHostAddress);

		return cmd;
	}

	private boolean parseNoraVRPacket() {

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

							if (bufferLength <= 0) {continue;}

							ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
							for (int i = 0; i < bufferLength; i++) {
								receivePacket.put(buffer.getBuffer().get());
							}
							BufferState.toREAD(receivePacket, BufferState.WRITE);

							if(log.isTraceEnabled()) {
								StringBuilder sb = new StringBuilder(logHeader);
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

							int localPort = buffer.getLocalAddress().getPort();
							if(localPort == channel.getLocalAddress().getPort()) {
								match = parseNoraVRPacket(buffer, receivePacket, readPackets);
							}

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

	private boolean parseNoraVRPacket(
		BufferEntry buffer, ByteBuffer receivePacket,
		Queue<NoraVRPacket> recvQueue
	) {

		assert buffer != null && receivePacket != null && recvQueue != null;

		boolean match = false;
		NoraVRPacket parsedCommand = null;
		do {
			if (
				(parsedCommand = noravrAck.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrNak.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLoginUser.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLoginUser2.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLogout.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLoginChallengeCode.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLoginHashCode.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrLoginAck.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrConfigurationSet.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrPing.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrVTPCM.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrVTOPUS.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrVTAMBE.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrRepeaterInfoGet.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrReflectorLinkGet.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrRoutingServiceGet.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrAccessLogGet.parsePacket(receivePacket)) != null ||
				(parsedCommand = noravrUserListGet.parsePacket(receivePacket)) != null
			) {
				parsedCommand.setRemoteHostAddress(buffer.getRemoteAddress());
				parsedCommand.setLocalHostAddress(buffer.getLocalAddress());

				while(recvQueue.size() >= rwPacketsLimit) {recvQueue.poll();}
				recvQueue.add(parsedCommand.clone());

				if(log.isTraceEnabled())
					log.trace(logHeader + "Receive NoraVR packet.\n" + parsedCommand.toString(4));

				match = true;
			} else {
				match = false;
			}
		} while (match);

		return match;
	}

	private boolean isMatchHashCode(
		final long loginChallengeCode,
		final String loginPassword,
		final byte[] receiveHashCode
	) {
		if(receiveHashCode == null || receiveHashCode.length != 32)
			return false;

//		final String loginPassword = getNoraVRloginPassword();

		byte[] calcHashSrc = new byte[4 + loginPassword.length()];
		DSTARUtils.writeInt32BigEndian(calcHashSrc, 0, loginChallengeCode);
		for(int i = 0; i < loginPassword.length(); i++) {
			calcHashSrc[i + 4] = (byte)loginPassword.charAt(i);
		}

		byte[] calcHash = HashUtil.calcSHA256(calcHashSrc);

		return Arrays.equals(calcHash, receiveHashCode);
	}

	private boolean checkHeaders(@NonNull final VoiceTransferBase<?> voicePacket) {
		return
			CallSignValidator.isValidRepeaterCallsign(voicePacket.getRepeater1Callsign()) &&
			getRepeaterCallsign().equals(voicePacket.getRepeater1Callsign()) &&
			(
				(
					CallSignValidator.isValidRepeaterCallsign(voicePacket.getRepeater2Callsign()) &&
					getRepeaterCallsign().equals(voicePacket.getRepeater2Callsign())
				) ||
				(
					CallSignValidator.isValidGatewayCallsign(voicePacket.getRepeater2Callsign()) &&
					getGatewayCallsign().equals(voicePacket.getRepeater2Callsign())
				)
			) &&
			CallSignValidator.isValidUserCallsign(voicePacket.getMyCallsignLong()) &&
			CallSignValidator.isValidShortCallsign(voicePacket.getMyCallsignShort());
	}
}
