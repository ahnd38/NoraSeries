package org.jp.illg.dstar.service.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.service.web.model.ClientEntry;
import org.jp.illg.dstar.service.web.model.ClientState;
import org.jp.illg.dstar.service.web.model.UserEntry;
import org.jp.illg.dstar.service.web.model.WebRemoteControlErrorCode;
import org.jp.illg.dstar.service.web.model.WebRemoteUserGroup;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.dstar.service.web.util.WebRemoteControlUserFileReader;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.HashUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebRemoteClientManager {

	/**
	 * クライアントエントリを掃除する間隔(分)
	 */
	private static final int cleanupIntervalTimeMinutes = 10;

	@Data
	private static class DataSet<K, V>{
		private K key;
		private V value;
		public DataSet(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

	@Getter
	@Setter
	private int connectionLimit;
	private static final int connectionLimitDefault = 100;


	private static final String logTag = WebRemoteClientManager.class.getSimpleName() + " : ";

	private final Lock locker;

	private final ApplicationInformation<?> applicationVersion;
	private final ThreadUncaughtExceptionListener exceptionListener;

	private final ExecutorService workerExecutor;

	private final SocketIOServer server;

	private final Map<UUID, ClientEntry> clients;

	private final Timer cleanupIntervalTimekeeper;

	private final String userListFilePath;

	private final EventListener<SocketIOClient> loginEventListener;
	private final EventListener<SocketIOClient> logoutEventListener;

	private final DashboardEventListener<Object> connectListener = new DashboardEventListener<Object>() {
		@Override
		public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
			onConnected(client);
		}
	};

	private final DashboardEventListener<Object> disconnectListener = new DashboardEventListener<Object>() {
		@Override
		public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
			onDisconnected(client);
		}
	};

	private final DashboardEventListener<Object> pingListener = new DashboardEventListener<Object>() {
		@Override
		public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
			onPingReceived(client);
		}
	};

	private final DashboardEventListener<Properties> requestPasswordTokenEventListener =
		new DashboardEventListener<Properties>() {
			@Override
			public void onEvent(
				SocketIOClient client, Properties data, AckRequest ackSender
			) {
				onReceiveRequestPasswordTokenEvent(client, data, ackSender);
			}
		};

	private final DashboardEventListener<Properties> requestLoginEventListener =
		new DashboardEventListener<Properties>() {
			@Override
			public void onEvent(
				SocketIOClient client, Properties data, AckRequest ackSender
			) {
				onReceiveRequestLoginEvent(client, data, ackSender);
			}
		};

	private final DashboardEventListener<Properties> requestLogoutEventListener =
		new DashboardEventListener<Properties>() {
			@Override
			public void onEvent(
				SocketIOClient client, Properties data, AckRequest ackSender
			) {
				onReceiveRequestLogoutEvent(client, data, ackSender);
			}
		};

	public WebRemoteClientManager(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final SocketIOServer server,
		@NonNull final String userListFilePath,
		@NonNull final EventListener<SocketIOClient> loginEventListener,
		@NonNull final EventListener<SocketIOClient> logoutEventListener
	) {
		super();

		this.exceptionListener = exceptionListener;
		this.applicationVersion = applicationVersion;
		this.workerExecutor = workerExecutor;
		this.server = server;
		this.userListFilePath = userListFilePath;
		this.loginEventListener = loginEventListener;
		this.logoutEventListener = logoutEventListener;
		this.connectionLimit = connectionLimitDefault;

		locker = new ReentrantLock();

		clients = new HashMap<>();

		cleanupIntervalTimekeeper = new Timer();
		cleanupIntervalTimekeeper.updateTimestamp();

		initialize();
	}

	public boolean verifyToken(@NonNull final String token) {
		return verifyToken(token, Collections.emptyList()) == WebRemoteControlErrorCode.NoError;
	}

	public boolean isAuthenticated(@NonNull final SocketIOClient client) {
		final ClientEntry clientEntry = findClient(client);

		return clientEntry != null && clientEntry.getClientState() == ClientState.Authenticated;
	}

	public boolean hasUserAuthority(
		@NonNull final SocketIOClient client, @NonNull WebRemoteUserGroup authority
	) {
		final ClientEntry clientEntry = findClient(client);
		if(clientEntry == null) {return false;}

		clientEntry.getLocker().lock();
		try {
			return
				clientEntry.getClientState() == ClientState.Authenticated &&
				clientEntry.getGroup().getLevel() <= authority.getLevel();
		}finally {
			clientEntry.getLocker().unlock();
		}
	}

	public int getClientCount() {
		locker.lock();
		try {
			return clients.size();
		}finally {
			locker.unlock();
		}
	}

	public ClientEntry findClient(final SocketIOClient client) {
		locker.lock();
		try {
			final Optional<ClientEntry> result = Stream.of(clients.values())
				.filter(new Predicate<ClientEntry>() {
					@Override
					public boolean test(ClientEntry entry) {
						return client == entry.getSocketIOClient();
					}
				})
				.findFirst();

			return result.isPresent() ? result.get() : null;
		}finally {
			locker.unlock();
		}
	}

	private void initialize() {
		server.addConnectListener(
			new DashboardEventListenerBuilder<>(getClass(), connectListener)
			.setExceptionListener(exceptionListener)
			.createConnectListener()
		);

		server.addDisconnectListener(
			new DashboardEventListenerBuilder<>(getClass(), disconnectListener)
			.setExceptionListener(exceptionListener)
			.createDisconnectListener()
		);

		server.addPingListener(
			new DashboardEventListenerBuilder<>(getClass(), pingListener)
			.setExceptionListener(exceptionListener)
			.createPingListener()
		);

		server.addEventListener(
			"request_password_token",
			Properties.class,
			new DashboardEventListenerBuilder<>(
				getClass(), "request_password_token", requestPasswordTokenEventListener
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		server.addEventListener(
			"request_login",
			Properties.class,
			new DashboardEventListenerBuilder<>(
				getClass(), "request_login", requestLoginEventListener
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		server.addEventListener(
			"request_logout",
			Properties.class,
			new DashboardEventListenerBuilder<>(
				getClass(), "request_logout", requestLogoutEventListener
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);
	}

	private void onConnected(final SocketIOClient client) {
		final ClientEntry clientEntry = new ClientEntry(client);

		addClient(clientEntry);

		if(log.isDebugEnabled()) {
			log.debug(
				logTag +
				"Client connected...SesstionId:" + client.getSessionId() + "/RemoteAddress:" + client.getRemoteAddress()
			);
		}

		cleanupClient();
	}

	private void onDisconnected(final SocketIOClient client) {
		ClientEntry userEntry = null;

		locker.lock();
		try {
			userEntry = findClient(client);
			if(userEntry == null) {return;}

			userEntry.getLocker().lock();
			try {
				if(userEntry.getClientState() == ClientState.Authenticated) {
					workerExecutor.submit(new RunnableTask(exceptionListener) {
						@Override
						public void task() {
							logoutEventListener.event(client, null);
						}
					});

					if(log.isInfoEnabled()) {
						log.info(logTag + "User:" + userEntry.getUsername() + "/Group:" + userEntry.getGroup() + " has logged out.");
					}
				}

				userEntry.setClientState(ClientState.Disconnected);
			}finally{
				userEntry.getLocker().unlock();
			}

			removeClient(client);
		}finally {
			locker.unlock();
		}

		if(log.isDebugEnabled()) {
			log.debug(
				logTag +
				"Client disconnected...SesstionId:" + client.getSessionId() + "/RemoteAddress:" + client.getRemoteAddress()
			);
		}

		cleanupClient();
	}

	private void onPingReceived(final SocketIOClient client) {
		locker.lock();
		try {
			final ClientEntry entry = findClient(client);
			if(entry == null) {return;}

			entry.getLocker().lock();
			try {
				entry.getPingTimekeeper().updateTimestamp();
			}finally {
				entry.getLocker().unlock();
			}
		}finally {
			locker.unlock();
		}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag +
				"Ping received...SesstionId:" + client.getSessionId() + "/RemoteAddress:" + client.getRemoteAddress()
			);
		}

		cleanupClient();
	}

	private void onReceiveRequestPasswordTokenEvent(
		SocketIOClient client, Properties data, AckRequest ackSender
	) {
		final String responseEventName = "response_password_token";

		final ClientEntry clientEntry = findClient(client);
		if(clientEntry == null) {return;}

		sendResponseSuccess(client, responseEventName,
			new DataSet<>("password_token", clientEntry.getPasswordToken())
		);

		if(log.isTraceEnabled()) {
			log.trace(logTag + "Response password token to " + client.getSessionId());
		}
	}

	private void onReceiveRequestLoginEvent(
		SocketIOClient client, Properties data, AckRequest ackSender
	) {
		final String responseEventName = "response_login";

		if(data == null) {
			if(log.isDebugEnabled())
				log.debug(logTag + "data is null");

			sendResponseError(
				client,
				responseEventName,
				WebRemoteControlErrorCode.SystemError
			);

			return;
		}
		else if(getClientCount() >= connectionLimit) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to login, Connection limit exceeded.");

			sendResponseError(
				client,
				responseEventName,
				WebRemoteControlErrorCode.ConnectionLimitExceed
			);

			return;
		}

		final ClientEntry clientEntry = findClient(client);
		if(clientEntry == null) {return;}

		final String receive_username = data.getProperty("username", "");
		final String receive_password = data.getProperty("password", "");
		final String receive_token = data.getProperty("token", "");
		final boolean isTokenPresent = receive_token.length() >= 1;

		if(clientEntry.getClientState() != ClientState.Connected) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to login, Attempted login in an illegal state " + client.getSessionId() + ".");

			sendResponseError(
				client,
				responseEventName,
				WebRemoteControlErrorCode.IllegalState
			);

			return;
		}
		else if(!isTokenPresent && "".equals(receive_username)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to login, User name is empty " + client.getSessionId() + ".");

			sendResponseError(
				client,
				responseEventName,
				WebRemoteControlErrorCode.EmptyUserName
			);

			return;
		}
		else if(!clientEntry.getRequestRateLimitter().tryAcquire()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Failed to login, Request interval exceeded " + client.getSessionId() + ".");

			sendResponseError(
				client,
				responseEventName,
				WebRemoteControlErrorCode.RequestLimitExceed
			);

			return;
		}

		final List<UserEntry> userList =
			WebRemoteControlUserFileReader.readUserFile(userListFilePath);
		if(userList == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read login users list " + userListFilePath + ".");

			sendResponseError(client, responseEventName, WebRemoteControlErrorCode.SystemError);

			return;
		}

		UserEntry userEntry = null;

		if(isTokenPresent) {
			DecodedJWT jwt = null;
			try {
				jwt = JWT.decode(receive_token);
			}catch(JWTDecodeException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "JWT decode error", ex);

				return;
			}
			final String tokenUsername = jwt.getClaim("username").asString();
			final Date tokenExpiresAt = jwt.getExpiresAt();
			userEntry = findUser(userList, receive_username);
			if(userEntry == null) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to login, User " + tokenUsername + " is not found in login user list.");

				sendResponseError(
					client,
					responseEventName,
					WebRemoteControlErrorCode.UserNotFound
				);

				return;
			}
			else if(tokenExpiresAt == null || new Date().after(tokenExpiresAt)) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to login, User " + tokenUsername + " token is expired.");

				sendResponseError(
					client,
					responseEventName,
					WebRemoteControlErrorCode.TokenExpired
				);

				return;
			}

			final boolean isTokenValid = verifyToken(userEntry.getPassword(), receive_token) != null;

			if(!isTokenValid) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to login, User " + tokenUsername + " token is invalid.");

				sendResponseError(
					client,
					responseEventName,
					WebRemoteControlErrorCode.TokenInvalid
				);

				return;
			}

			clientEntry.getLocker().lock();
			try {
				clientEntry.setClientState(ClientState.Authenticated);

				clientEntry.setUsername(userEntry.getUsername());
				clientEntry.setPasswordHash(receive_password);
				clientEntry.setGroup(userEntry.getGroup());
			}finally {
				clientEntry.getLocker().unlock();
			}

			final String token = createToken(
				userEntry.getPassword(),
				7,
				new DataSet<>("username", userEntry.getUsername()),
				new DataSet<>("group", userEntry.getGroup().getTypeName())
			);

			sendResponseSuccess(client, responseEventName,
				new DataSet<>("token", token)
			);
		}
		else {
			userEntry = findUser(userList, receive_username);
			if(userEntry == null) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to login, User " + receive_username + " is not found in login user list.");

				sendResponseError(
					client,
					responseEventName,
					WebRemoteControlErrorCode.UserOrPasswordInvalid
				);

				return;
			}

			final boolean passwordMatched =
				verifyPassword(userEntry, clientEntry, receive_password);

			if(!passwordMatched) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to login, Password is not match for user " + receive_username + ".");

				sendResponseError(
					client,
					responseEventName,
					WebRemoteControlErrorCode.UserOrPasswordInvalid
				);

				return;
			}

			clientEntry.getLocker().lock();
			try {
				clientEntry.setClientState(ClientState.Authenticated);

				clientEntry.setUsername(receive_username);
				clientEntry.setPasswordHash(receive_password);
				clientEntry.setGroup(userEntry.getGroup());
			}finally {
				clientEntry.getLocker().unlock();
			}

			final String token = createToken(
				userEntry.getPassword(),
				7,
				new DataSet<>("username", userEntry.getUsername()),
				new DataSet<>("group", userEntry.getGroup().getTypeName())
			);

			sendResponseSuccess(client, responseEventName,
				new DataSet<>("token", token)
			);
		}

		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				loginEventListener.event(client, null);
			}
		});

		if(log.isInfoEnabled()) {
			log.info(logTag + "User:" + userEntry.getUsername() + "/Group:" + userEntry.getGroup() + " has logged in.");
		}
	}

	private void onReceiveRequestLogoutEvent(
		SocketIOClient client, Properties data, AckRequest ackSender
	) {
		final String responseEventName = "response_logout";

		ClientEntry userEntry = null;

		locker.lock();
		try {
			userEntry = findClient(client);
			if(userEntry == null) {return;}

			userEntry.getLocker().lock();
			try {
				userEntry.setClientState(ClientState.Connected);

				sendResponseSuccess(client, responseEventName);
			}finally {
				userEntry.getLocker().unlock();
			}
		}finally {
			locker.unlock();
		}

		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				logoutEventListener.event(client, null);
			}
		});

		if(log.isInfoEnabled()) {
			log.info(logTag + "User:" + userEntry.getUsername() + "/Group:" + userEntry.getGroup() + " has logged out.");
		}
	}

	private UserEntry findUser(
		final List<UserEntry> userList,
		final String username
	) {
		UserEntry userEntry = null;
		for(final UserEntry ue : userList) {
			if(ue.getUsername().equals(username)){
				userEntry = ue;
				break;
			}
		}

		return userEntry;
	}

	private static boolean sendResponseError(
		final SocketIOClient destinationClient,
		final String eventName,
		final WebRemoteControlErrorCode errorCode
	) {
		return sendResponse(destinationClient, eventName,
			false,
			new DataSet<>("error_code", errorCode.getTypeName()),
			new DataSet<>("error_message", errorCode.getMessage())
		);
	}

	@SafeVarargs
	private static boolean sendResponseSuccess(
		final SocketIOClient destinationClient,
		final String eventName,
		final DataSet<String, String>... dataSets
	) {
		return sendResponse(destinationClient, eventName, true, dataSets);
	}

	@SafeVarargs
	private static boolean sendResponse(
		final SocketIOClient destinationClient,
		final String eventName,
		final boolean isSuccess,
		final DataSet<String, String>... dataSets
	) {
		final Properties prop = new Properties();
		if(dataSets != null) {
			for(final DataSet<String, String> data : dataSets)
				prop.setProperty(data.getKey(), data.getValue());
		}

		prop.setProperty("result", String.valueOf(isSuccess));

		destinationClient.sendEvent(eventName, prop);

		if(log.isTraceEnabled()) {
			log.trace(logTag + "Send event name " + eventName + " to " + destinationClient.getSessionId());
		}

		return true;
	}

	private static boolean verifyPassword(
		final UserEntry userEntry,
		final ClientEntry clientEntry,
		final String passwordHash
	) {
		final String hash = generatePasswordHash(clientEntry.getPasswordToken(), userEntry.getPassword());

		return hash.equals(passwordHash.toLowerCase());
	}

	private static String generatePasswordHash(final String passwordToken, final String password) {
		final byte[] seed = ArrayUtil.concatByteArray(
			passwordToken.getBytes(StandardCharsets.UTF_8),
			password.getBytes(StandardCharsets.UTF_8)
		);
		return FormatUtil.bytesToHex(HashUtil.calcSHA256(seed)).toLowerCase();
	}

	@SafeVarargs
	private final String createToken(
		final String secretKey,
		final long expiresDays,
		final DataSet<String, String>... datasets
	) {
		try {
			final Builder jwtBuilder = JWT.create();

			for(final DataSet<String, String> data : datasets)
				jwtBuilder.withClaim(data.getKey(), data.getValue());

			final LocalDateTime exp = LocalDate.now().plusDays(expiresDays).atStartOfDay();

			return jwtBuilder
				.withExpiresAt(Date.from(exp.toInstant(ZoneId.systemDefault().getRules().getOffset(exp))))
				.withIssuedAt(new Date())
				.withIssuer(applicationVersion.getApplicationName())
				.sign(Algorithm.HMAC256(secretKey));
		} catch (JWTCreationException ex) {
			throw new RuntimeException(ex);
		}
	}

	private WebRemoteControlErrorCode verifyToken(
		@NonNull final String token,
		final List<UserEntry> userList
	) {
		final DecodedJWT jwt = JWT.decode(token);
		final String tokenUsername = jwt.getClaim("username").asString();
		final Date tokenExpiresAt = jwt.getExpiresAt();
		if(tokenExpiresAt == null || new Date().before(tokenExpiresAt))
			return WebRemoteControlErrorCode.TokenExpired;

		final List<UserEntry> users =
			userList != null && userList.size() > 0 ?
				userList : WebRemoteControlUserFileReader.readUserFile(userListFilePath);

		final UserEntry userEntry = findUser(users, tokenUsername);
		if(userEntry == null) {return WebRemoteControlErrorCode.UserNotFound;}

		return verifyToken(userEntry.getPassword(), token) != null ?
			WebRemoteControlErrorCode.NoError : WebRemoteControlErrorCode.TokenInvalid;
	}

	private final DecodedJWT verifyToken(
		final String secretKey,
		final String token
	) {
		try {
			final JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secretKey))
				.withIssuer(applicationVersion.getApplicationName())
				.build();

			final DecodedJWT jwt = verifier.verify(token);

			return jwt;
		} catch (JWTVerificationException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Failed to verify token.", ex);

			return null;
		}
	}

	private boolean addClient(final ClientEntry clientEntry) {
		locker.lock();
		try {
			return clients.put(clientEntry.getId(), clientEntry) == null;
		}finally {
			locker.unlock();
		}
	}

	private boolean removeClient(final SocketIOClient client) {
		locker.lock();
		try {
			ClientEntry targetEntry = null;
			for(final ClientEntry e : clients.values()) {
				if(e.getSocketIOClient() == client) {
					targetEntry = e;
					break;
				}
			}

			return targetEntry != null && removeClient(targetEntry);
		}finally {
			locker.unlock();
		}
	}

	private boolean removeClient(final ClientEntry clientEntry) {
		locker.lock();
		try {
			return clients.remove(clientEntry.getId()) != null;
		}finally {
			locker.unlock();
		}
	}

	private void cleanupClient() {
		locker.lock();
		try {
			if(!cleanupIntervalTimekeeper.isTimeout(cleanupIntervalTimeMinutes, TimeUnit.MINUTES))
				return;

			cleanupIntervalTimekeeper.updateTimestamp();

			for(final Iterator<ClientEntry> it = clients.values().iterator(); it.hasNext();) {
				final ClientEntry entry = it.next();

				entry.getLocker().lock();
				try {
					if(entry.getPingTimekeeper().isTimeout(1, TimeUnit.HOURS)) {it.remove();}
				}finally {
					entry.getLocker().unlock();
				}
			}
		}finally {
			locker.unlock();
		}
	}
}
