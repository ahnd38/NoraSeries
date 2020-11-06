package org.jp.illg.dstar.routing.service.ircDDB;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.GlobalIPInfo;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.define.RoutingServiceStatus;
import org.jp.illg.dstar.routing.model.PositionUpdateInfo;
import org.jp.illg.dstar.routing.model.QueryCallback;
import org.jp.illg.dstar.routing.model.QueryRepeaterResult;
import org.jp.illg.dstar.routing.model.QueryUserResult;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.RoutingCompletedTaskInfo;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;
import org.jp.illg.dstar.routing.model.RoutingServiceStatusData;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.routing.service.ircDDB.db.IRCDDBDatabaseController;
import org.jp.illg.dstar.routing.service.ircDDB.db.IRCDDBDatabaseType;
import org.jp.illg.dstar.routing.service.ircDDB.define.IRCDDBClientEvent;
import org.jp.illg.dstar.routing.service.ircDDB.model.HeardEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterIPEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterUserEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryResult;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryState;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryTask;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryType;
import org.jp.illg.dstar.routing.service.ircDDB.model.QueryTask;
import org.jp.illg.dstar.routing.service.ircDDB.model.QueryTaskStatus;
import org.jp.illg.dstar.routing.service.ircDDB.model.ServerEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.ServerProperties;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlIrcDDBRoutingHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRoutingServiceHandler;
import org.jp.illg.dstar.service.web.model.IrcDDBRoutingServiceStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.irc.IRCClient;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IrcDDBRoutingService
implements RoutingService, WebRemoteControlIrcDDBRoutingHandler
{

	/**
	 * 単一クエリの制限時間(秒)
	 */
	private static final int queryTimeLimitSeconds = 8;

	@Getter
	private static final int maxServers = 10;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Setter
	private boolean ircDDBDebug;
	public boolean getIrcDDBDebug() {return ircDDBDebug;}
	private static final boolean ircDDBDebugDefault = false;
	public static boolean getIrcDDBDebugDefault() {return ircDDBDebugDefault;}
	@Getter
	private static final String ircDDBDebugPropertyName = "Debug";

	@Getter
	private static final String ircDDBServerAddressDefault = "";
	@Getter
	private static final String ircDDBServerAddressPropertyName = "ServerAddress";

	@Getter
	private static final int ircDDBServerPortDefault = 9007;
	@Getter
	private static final String ircDDBServerPortPropertyName = "ServerPort";

	@Getter
	private static final String ircDDBServerPasswordDefault = "secret";
	@Getter
	private static final String ircDDBServerPasswordPropertyName = "ServerPassword";

	@Getter
	private static final String ircDDBCallsignDefault = "nocall";
	@Getter
	private static final String ircDDBCallsignPropertyName = "Callsign";

	@Getter
	private static final String ircDDBChannelDefault = "#dstar";
	@Getter
	private static final String ircDDBChannelPropertyName = "Channel";

	@Getter
	private static final String ircDDBDebugChannelDefault = "none";
	@Getter
	private static final String ircDDBDebugChannelPropertyName = "DebugChannel";

	@Getter
	private static final String ircDDBDebugServerUserDefault = "";
	@Getter
	private static final String ircDDBDebugServerUserPropertyName = "DebugServerUser";

	private static final String logTag = IrcDDBRoutingService.class.getSimpleName() + " : ";

	private final Lock locker = new ReentrantLock();

	@SuppressWarnings("unused")
	private final UUID systemID;

	private final DSTARGateway gateway;

	private final ExecutorService workerExecutor;

	private final List<ServerProperties> serverProperties;
	private final List<ServerEntry> servers;

	private final List<QueryTask> queryTasks;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private WebRemoteControlService webRemoteControlService;

	private final List<HeardEntry> suppressionHeardEntries;

	private final Map<DSTARRepeater, Timer> watchdogTimers;

	private IRCDDBDatabaseController database;

	private final ApplicationInformation<?> applicationVersion;

	private final EventListener<RoutingServiceEvent> eventListener;

	private final EventListener<IRCDDBClientEvent> clientEventHandler = new EventListener<IRCDDBClientEvent>() {
		@Override
		public void event(IRCDDBClientEvent event, Object attachment) {
			workerExecutor.submit(new RunnableTask(exceptionListener) {
				@Override
				public void task() {
					final List<UUID> completeIDs = getCompleteQueryTasks();
					for(final UUID id : completeIDs)
						eventListener.event(RoutingServiceEvent.TaskComplete, id);
				}
			});
		}
	};

	public IrcDDBRoutingService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		final SocketIO socketIO
	) {
		super();

		this.systemID = systemID;

		this.exceptionListener = exceptionListener;

		this.gateway = gateway;

		this.workerExecutor = workerExecutor;

		this.applicationVersion = applicationVersion;
		this.eventListener = eventListener;

		webRemoteControlService = null;

		watchdogTimers = new ConcurrentHashMap<>(10);

		serverProperties = new ArrayList<>(maxServers);
		servers = new ArrayList<>(maxServers);

		queryTasks = new ArrayList<>(16);

		suppressionHeardEntries = new ArrayList<>(16);

		setIrcDDBDebug(getIrcDDBDebugDefault());
	}

	public IrcDDBRoutingService(
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

	@Override
	public boolean setProperties(RoutingServiceProperties properties) {
		final Properties ircddbProperties = properties.getConfigurationProperties();

		boolean success = true;

		locker.lock();
		try {
			for(int i = 0; i < maxServers; i++) {
				final boolean debug =
					PropertyUtils.getBoolean(
						ircddbProperties,
						getIrcDDBDebugPropertyName() + (i == 0 ? "" : i),
						getIrcDDBDebugDefault()
					);
				final String serverAddress =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBServerAddressPropertyName() + (i == 0 ? "" : i),
						getIrcDDBServerAddressDefault()
					);
				final int serverPort =
					PropertyUtils.getInteger(
						ircddbProperties,
						getIrcDDBServerPortPropertyName() + (i == 0 ? "" : i),
						getIrcDDBServerPortDefault()
					);
				final String serverPassword =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBServerPasswordPropertyName() + (i == 0 ? "" : i),
						getIrcDDBServerPasswordDefault()
					);
				final String callsign =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBCallsignPropertyName() + (i == 0 ? "" : i),
						getIrcDDBCallsignDefault()
					);
				final String channel =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBChannelPropertyName() + (i == 0 ? "" : i),
						getIrcDDBChannelDefault()
					);
				String debugChannel =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBDebugChannelPropertyName() + (i == 0 ? "" : i),
						getIrcDDBDebugChannelDefault()
					);
				if (debugChannel.equals(getIrcDDBDebugChannelDefault())) {debugChannel = null;}
				final String debugServerUser =
					PropertyUtils.getString(
						ircddbProperties,
						getIrcDDBDebugServerUserPropertyName() + (i == 0 ? "" : i),
						getIrcDDBDebugServerUserDefault()
					);


				if(!CallSignValidator.isValidUserCallsign(DSTARUtils.formatFullLengthCallsign(callsign))) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Illegal callsign " + callsign + ".");

					continue;
				}
				else if(getIrcDDBServerAddressDefault().equals(serverAddress)) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Must set ircDDB server address " + serverAddress + ".");

					continue;
				}
				else if (channel.equals(debugChannel)) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Channel and debug channel must not have same value.");

					continue;
				}

				final ServerProperties prop = new ServerProperties(
					debug, serverAddress, serverPort, serverPassword, callsign.toLowerCase().trim(), channel, debugChannel,
					debugServerUser
				);

				serverProperties.add(prop);
			}

			if(serverProperties.size() < 1) {
				if(log.isErrorEnabled())
					log.error(logTag + "Must have at least one server configuration.");

				success = false;
			}
			else if(log.isInfoEnabled()){
				final StringBuilder sb = new StringBuilder("IrcDDB server properties.\n");
				int i = 0;
				for(final Iterator<ServerProperties> it = serverProperties.iterator(); it.hasNext();) {
					final ServerProperties prop = it.next();

					sb.append("    [");
					sb.append(++i);
					sb.append("] ");

					sb.append("ServerAddress : ");
					sb.append(prop.getServerAddress());
					sb.append(":");
					sb.append(prop.getServerPort());

					sb.append(" / ");

					sb.append("Channel : ");
					sb.append(prop.getChannel());
					if(prop.getDebugChannel() != null) {
						sb.append("(");
						sb.append(prop.getDebugChannel());
						sb.append(")");
					}

					sb.append(" / ");

					sb.append("Debug : ");
					sb.append(prop.isDebug());

					if(it.hasNext()) {sb.append('\n');}
				}

				log.info(logTag + sb.toString());
			}

			setIrcDDBDebug(
				PropertyUtils.getBoolean(
					ircddbProperties,
					getIrcDDBDebugPropertyName(),
					getIrcDDBDebugDefault()
				)
			);
		}finally {
			locker.unlock();
		}

		return success;
	}

	@Override
	public RoutingServiceProperties getProperties(RoutingServiceProperties properties) {
		if(properties == null) {return null;}

		Properties ircddbProperties = properties.getConfigurationProperties();

		ircddbProperties.put(getIrcDDBDebugChannelPropertyName(), String.valueOf(getIrcDDBDebug()));

		return properties;
	}

	@Override
	public boolean start() {
		if(isRunning()) {stop();}

		boolean bootSuccess = true;

		Security.setProperty("networkaddress.cache.ttl", "10");

		final String version = getApplicationName() + " " + getApplicationVersion();

		locker.lock();
		try {
			database = new IRCDDBDatabaseController(
				workerExecutor, IRCDDBDatabaseType.InMemory,
				getApplicationName(),
				"ircddb_db"
			);

			if(!database.start()) {
				stop();

				if(log.isErrorEnabled()) {
					log.error(logTag + "Could not start or restore ircddb database.");
				}

				return false;
			}
			else {database.restore();}

			for(final ServerProperties prop : serverProperties) {
				String ircName = prop.getCallsign();

				String nicks[] = new String[4];

				if (
					prop.getCallsign() == null ||
					"".equals(prop.getCallsign()) ||
					prop.getCallsign().equals(getIrcDDBCallsignDefault())
				) {
					final Random r = new Random(System.currentTimeMillis());

					for(int c = 0; c < nicks.length; c++)
						nicks[c] = "guest-" + String.valueOf(r.nextInt(1000));
				} else {
					for(int c = 0; c < nicks.length; c++)
						nicks[c] = prop.getCallsign() + "-" + String.valueOf(c + 1);
				}

				final IrcDDBClient ircDDB =
					new IrcDDBClient(
						exceptionListener, workerExecutor, this, database, prop.getChannel(),
						prop.getDebugServerUser(),
						clientEventHandler
					);
				ircDDB.setWebRemoteControlService(webRemoteControlService);

				final IRCClient ircClient =
					new IRCClient(
						exceptionListener,
						ircDDB, prop.getServerAddress(), prop.getServerPort(),
						prop.getChannel(), prop.getDebugChannel(),
						ircName, nicks,
						prop.getServerPassword(),
						prop.isDebug(), version
					);

				ircDDB.setIrcClient(ircClient);

				if(!ircDDB.start() || !ircClient.start()) {
					bootSuccess = false;

					ircDDB.stop();
					ircClient.stop();

					if(log.isErrorEnabled())
						log.error(logTag + "Could not start ircddb client.");

					break;
				}

				servers.add(new ServerEntry(ircDDB, ircClient, prop));
			}

			if(!bootSuccess) {
				stop();

				return false;
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public void stop() {
		locker.lock();
		try {
			for(final Iterator<ServerEntry> it = servers.iterator(); it.hasNext();) {
				final ServerEntry server = it.next();
				it.remove();

				if(server.getDdbClient().isRunning())
					server.getDdbClient().stop();

				if(server.getIrcClient().isRunning())
					server.getIrcClient().stop();
			}

			if(database != null) {
				if(database.isRunning()) {database.backup();}

				database.stop();
			}
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean isRunning() {
		boolean isRunning = false;

		locker.lock();
		try {
			isRunning = Stream.of(servers)
			.anyMatch(new Predicate<ServerEntry>() {
				@Override
				public boolean test(ServerEntry entry) {
					return entry.getDdbClient().isRunning() && entry.getIrcClient().isRunning();
				}
			});
		}finally {
			locker.unlock();
		}

		return isRunning;
	}

	@Override
	public RoutingServiceTypes getServiceType() {
		return RoutingServiceTypes.ircDDB;
	}

	@Override
	public void updateCache(@NonNull String myCallsign, @NonNull InetAddress gatewayAddress) {

	}

	@Override
	public boolean kickWatchdog(String callsign, String statusMessage) {
		if(!isRunning()) {return false;}

		final DSTARRepeater repeater = gateway.getRepeater(callsign);
		if(repeater == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not found repeater callsign = " + callsign + ".");

			return false;
		}

		locker.lock();
		try {
			boolean sendRepeaterInformation = false;

			Timer watchdogTimer = watchdogTimers.get(repeater);
			if(watchdogTimer == null) {
				watchdogTimer = new Timer();
				watchdogTimer.updateTimestamp();

				watchdogTimers.put(repeater, watchdogTimer);

				sendRepeaterInformation = true;
			}
			else if(watchdogTimer.isTimeout(10, TimeUnit.MINUTES)) {
				sendRepeaterInformation = true;
			}
			watchdogTimer.updateTimestamp();

			for(final ServerEntry server : servers) {
				server.getDdbClient().kickWatchdog(callsign, (statusMessage != null ? statusMessage : ""));

				if(sendRepeaterInformation) {
					if(repeater.getFrequency() > 0.0d) {
						server.getDdbClient().rptrQRG(
							callsign, repeater.getFrequency(), repeater.getFrequencyOffset(), 0.0d, 0.0d
						);
					}

					if(repeater.getLatitude() != 0.0d && repeater.getLongitude() != 0.0d) {
						server.getDdbClient().rptrQTH(
							callsign,
							repeater.getLatitude(), repeater.getLongitude(),
							repeater.getDescription1(), repeater.getDescription2(),
							repeater.getUrl()
						);
					}
				}
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public UUID positionUpdate(
		final int frameID,
		final String myCall, final String myCallExt, final String yourCall,
		final String repeater1, final String repeater2,
		final byte flag1, final byte flag2, final byte flag3
	) {
		if(!isRunning()) {
			return null;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(myCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:myCall " + (myCall != null ? myCall : "null")
				);
			}
			return null;
		}
		else if(!DSTARUtils.isValidCallsignShortLegth(myCallExt)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:myCallExt " + (myCallExt != null ? myCallExt : "null")
				);
			}
			return null;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(yourCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:yourCall " + (yourCall != null ? yourCall : "null")
				);
			}
			return null;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater1)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:repeater1 " + (repeater1 != null ? repeater1 : "null")
				);
			}
			return null;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater2)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:repeater2 " + (repeater2 != null ? repeater2 : "null")
				);
			}
			return null;
		}

		final UUID queryID = UUID.randomUUID();

		final Map<ServerEntry, QueryTaskStatus> queries = new HashMap<>(maxServers);

		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				if(
					!server.getDdbClient().isRunning() ||
					!server.getDdbClient().isInitReady() ||
					!server.getIrcClient().isRunning() ||
					!server.getIrcClient().isConnected()
				) {continue;}

				server.getDdbClient().sendHeard(
					myCall, myCallExt,
					yourCall,
					repeater1,
					DSTARUtils.formatFullCallsign(repeater2, 'G'),
					flag1, flag2, flag3,
					DSTARDefines.EmptyLongCallsign.replace(' ', '_'), "", ""
				)
				.ifPresent(new Consumer<UUID>() {
					@Override
					public void accept(UUID id) {
						queries.put(server, new QueryTaskStatus(id, server.getDdbClient()));
					}
				});
			}
		}finally {
			locker.unlock();
		}

		if(queries.isEmpty()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "There is no routing service available.");

			return null;
		}

		final QueryTask queryTask =
			new QueryTask(queryID, IRCDDBQueryType.SendHeard, queries);

		locker.lock();
		try {
			if(
				!queryTasks.add(queryTask) ||
				!addSuppressionHeard(frameID)
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not add query task.");

				return null;
			}
		}finally {
			locker.unlock();
		}

		if(log.isDebugEnabled()) {
			log.debug(logTag + "Add query task " + queryTask.getQueryType() + "(" + queryID + ")");
		}

		return queryID;
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
		if(!isRunning()) {
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(myCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:myCall " + (myCall != null ? myCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignShortLegth(myCallExt)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:myCallExt " + (myCallExt != null ? myCallExt : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(yourCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:yourCall " + (yourCall != null ? yourCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater1)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:repeater1 " + (repeater1 != null ? repeater1 : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater2)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " + this.getClass().getSimpleName() +
					"::sendHeard:repeater2 " + (repeater2 != null ? repeater2 : "null")
				);
			}
			return false;
		}

		final String networkDest = DSTARUtils.formatFullLengthCallsign(networkDestination).replace(' ', '_');
		final StringBuilder txMsg = new StringBuilder("");
		for (int i = 0; DSTARDefines.DvShortMessageLength > i; i++) {
			char c = (txMessage != null && txMessage.length() > i) ? txMessage.charAt(i) : '_';
			if (c > 32 && c < 127)
				txMsg.append(c);
			else
				txMsg.append('_');
		}

		final String txMessageFormated = String.format("%s", txMsg.toString());

		boolean failure = false;
		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				if(
					!server.getDdbClient().isRunning() ||
					!server.getDdbClient().isInitReady() ||
					!server.getIrcClient().isRunning() ||
					!server.getIrcClient().isConnected()
				) {continue;}

				final Optional<UUID> queryID =
					server.getDdbClient().sendHeard(
						myCall, myCallExt,
						yourCall,
						repeater1,
						DSTARUtils.formatFullCallsign(repeater2, 'G'),
						flag1, flag2, flag3,
						networkDest, txMessageFormated, ""
					);

				if(!queryID.isPresent()) {failure = true;}
			}
		}finally {
			locker.unlock();
		}

	return !failure;
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
		if(!isRunning()) {
			return false;
		}
		else if(isSuppressionHeard(frameID)) {
			if(log.isTraceEnabled())
				log.trace(logTag + "FrameID:" + String.format("0x%04X", frameID) + " is suppressed for send status update.");

			return true;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(myCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:myCall " + (myCall != null ? myCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignShortLegth(myCallExt)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:myCallExt " + (myCallExt != null ? myCallExt : "null")
				);
			}
			return false;
		}

		if(!DSTARUtils.isValidCallsignFullLength(yourCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:yourCall " + (yourCall != null ? yourCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater1)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:repeater1 " + (repeater1 != null ? repeater1 : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater2)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:repeater2 " + (repeater2 != null ? repeater2 : "null")
				);
			}
			return false;
		}

		final String networkDest =
			DSTARUtils.formatFullLengthCallsign(networkDestination).replace(' ', '_');
		final StringBuilder txMsg = new StringBuilder("");
		for (int i = 0; DSTARDefines.DvShortMessageLength > i; i++) {
			char c = (txMessage != null && txMessage.length() > i) ? txMessage.charAt(i) : '_';
			if (c > 32 && c < 127)
				txMsg.append(c);
			else
				txMsg.append('_');
		}

		final String txMessageFormated = txMsg.toString();

		boolean failure = false;
		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				if(
					!server.getDdbClient().isRunning() ||
					!server.getDdbClient().isInitReady() ||
					!server.getIrcClient().isRunning() ||
					!server.getIrcClient().isConnected()
				) {continue;}

				final Optional<UUID> queryID =
					server.getDdbClient().sendHeard(
						myCall, myCallExt,
						yourCall,
						repeater1,
						DSTARUtils.formatFullCallsign(repeater2, 'G'),
						flag1, flag2, flag3,
						networkDest, txMessageFormated, ""
					);

				if(!queryID.isPresent()) {failure = true;}
			}
		}finally {
			locker.unlock();
		}

	return !failure;
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
		if(!isRunning()) {
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(myCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:myCall " + (myCall != null ? myCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignShortLegth(myCallExt)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:myCallExt " + (myCallExt != null ? myCallExt : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(yourCall)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:yourCall " + (yourCall != null ? yourCall : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater1)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:repeater1 " + (repeater1 != null ? repeater1 : "null")
				);
			}
			return false;
		}
		else if(!DSTARUtils.isValidCallsignFullLength(repeater2)){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Bad callsign length at " +
					this.getClass().getSimpleName() + "::sendHeard:repeater2 " + (repeater2 != null ? repeater2 : "null")
				);
			}
			return false;
		}

		final String networkDest = DSTARUtils.formatFullLengthCallsign(networkDestination).replace(' ', '_');

		final StringBuilder status = new StringBuilder();
		status.append(String.format("%04x", numDvFrames));

		if(numDvSlientFrames >= 0) {
			status.append(String.format(
				"%02x",
				(numDvSlientFrames != 0 && numDvFrames != 0) ? ((numDvSlientFrames * 100) / numDvFrames) : 0
			));

			if(numBitErrors >= 0) {
				status.append(String.format(
					"%02x",
					(numBitErrors != 0 && numDvFrames != 0) ? ((numBitErrors * 125) / (numDvFrames * 3)) : 0
				));

			}else {status.append("__");}
		}else {status.append("____");}

		status.append("____________");

		boolean failure = false;
		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				if(
					!server.getDdbClient().isRunning() ||
					!server.getDdbClient().isInitReady() ||
					!server.getIrcClient().isRunning() ||
					!server.getIrcClient().isConnected()
				) {continue;}

				final Optional<UUID> queryID =
					server.getDdbClient().sendHeard(
						myCall, myCallExt,
						yourCall,
						repeater1,
						DSTARUtils.formatFullCallsign(repeater2, 'G'),
						flag1, flag2, flag3,
						networkDest, "", status.toString()
					);

				if(!queryID.isPresent()) {failure = true;}
			}
		}finally {
			locker.unlock();
		}

		return !failure;
	}

	@Override
	public PositionUpdateInfo getPositionUpdateCompleted(@NonNull UUID taskid) {
		final Optional<IRCDDBQueryTask> taskOp = getQueryTaskResultCompleted(true, taskid);
		if(!taskOp.isPresent()) {return null;}
		final IRCDDBQueryTask taskResult = taskOp.get();

		RoutingServiceResult rs = RoutingServiceResult.Failed;
		if(taskResult.getQueryResult() == IRCDDBQueryResult.Success)
			rs = RoutingServiceResult.Success;
		else if(taskResult.getQueryResult() == IRCDDBQueryResult.NotFound)
			rs = RoutingServiceResult.NotFound;
		else
			rs = RoutingServiceResult.Failed;

		final PositionUpdateInfo result =
			new PositionUpdateInfo(taskResult.getQueryCallsign(), rs);

		return result;
	}

	@Override
	public UUID findRepeater(String repeaterCallsign, Header header) {
		if(!isRunning() || !CallSignValidator.isValidRepeaterCallsign(repeaterCallsign))
			return null;

		final UUID queryID = UUID.randomUUID();

		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				final Optional<IRCDDBAppRepeaterEntry> databaseResult =
					database.findRepeater(repeaterCallsign);

				QueryTask queryTask;

				if(databaseResult.isPresent()) {
					final IRCDDBAppRepeaterEntry dbResult = databaseResult.get();
					final String zoneRepeaterCallsign =
						DSTARUtils.formatFullLengthCallsign(dbResult.getZoneRepeaterCallsign());

					final Optional<IRCDDBAppRepeaterIPEntry> gatewayAddress =
						database.findIP(DSTARUtils.formatFullCallsign(zoneRepeaterCallsign, ' '));

					final IRCDDBQueryTask queryResult = new IRCDDBQueryTask(IRCDDBQueryType.FindRepeater);
					queryResult.setQueryResult(
						gatewayAddress.isPresent() ? IRCDDBQueryResult.Success : IRCDDBQueryResult.NotFound
					);
					queryResult.setQueryState(IRCDDBQueryState.Completed);
					queryResult.setDataTimestamp(dbResult.getLastChanged());
					queryResult.setRepeaterCallsign(
						DSTARUtils.formatFullLengthCallsign(dbResult.getAreaRepeaterCallsign())
					);
					queryResult.setGatewayCallsign(
						DSTARUtils.formatFullCallsign(dbResult.getZoneRepeaterCallsign(), 'G')
					);
					queryResult.setGatewayAddress(
						gatewayAddress.isPresent() ? gatewayAddress.get().getIpAddress() : null
					);

					queryTask = new QueryTask(queryID, IRCDDBQueryType.FindRepeater, queryResult);
				}
				else {
					final IRCDDBQueryTask queryResult = new IRCDDBQueryTask(IRCDDBQueryType.FindRepeater);
					queryResult.setQueryResult(IRCDDBQueryResult.NotFound);
					queryResult.setQueryState(IRCDDBQueryState.Completed);

					queryTask = new QueryTask(queryID, IRCDDBQueryType.FindRepeater, queryResult);
				}

				locker.lock();
				try {
					if(queryTask == null || !queryTasks.add(queryTask)) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Could not add query task.");

						throw new RuntimeException("Could not add find repeater query task.");
					}
				}finally {
					locker.unlock();
				}

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						(queryTask.getDatabaseResult() != null ? "[CacheResult] " : "[QueryExecute] ") +
						"Add query task " + queryTask.getQueryType() + "(" + queryID + ")");
				}
			}
		});



		return queryID;
	}

	@Override
	public RepeaterRoutingInfo getRepeaterInfo(UUID taskid) {
		if(!isRunning()) {return null;}

		RepeaterRoutingInfo routingResult = null;

		final Optional<IRCDDBQueryTask> query =
			getQueryTaskResultCompleted(true, taskid);

		if(query.isPresent()) {
			InetAddress gatewayAddress = null;
			try {
				gatewayAddress = InetAddress.getByName(query.get().getGatewayAddress());
			}catch(UnknownHostException ex) {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Unknown host address " + query.get().getGatewayAddress() + ".\n" + query.toString(),
						ex
					);
				}
			}

			routingResult = new RepeaterRoutingInfo();
			switch(query.get().getQueryResult()) {
			case Success:
				routingResult.setRoutingResult(RoutingServiceResult.Success);
				break;
			case NotFound:
				routingResult.setRoutingResult(RoutingServiceResult.NotFound);
				break;
			default:
				routingResult.setRoutingResult(RoutingServiceResult.Failed);
				break;
			}
			routingResult.setRepeaterCallsign(query.get().getRepeaterCallsign());
			routingResult.setGatewayCallsign(query.get().getGatewayCallsign());
			routingResult.setGatewayAddress(gatewayAddress);
			routingResult.setTimestamp(
				query.get().getDataTimestamp() != null ?
					query.get().getDataTimestamp().getTime() : 0
			);
		}

		return routingResult != null ? routingResult : null;
	}

	@Override
	public UUID findUser(@NonNull final String userCallsign, final Header header) {
		if(!isRunning() || !CallSignValidator.isValidUserCallsign(userCallsign))
			return null;

		final UUID queryID = UUID.randomUUID();

		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				//データベースを検索
				final Optional<IRCDDBAppRepeaterUserEntry> dbUserResult =
					database.findUser(userCallsign);
				final Optional<IRCDDBAppRepeaterEntry> dbRepeaterResult =
					dbUserResult.isPresent() ?
						database.findRepeater(dbUserResult.get().getAreaRepeaterCallsign()) : Optional.empty();

				final Optional<IRCDDBAppRepeaterIPEntry> gatewayAddress =
					dbRepeaterResult.isPresent() ?
						database.findIP(DSTARUtils.formatFullCallsign(
							dbRepeaterResult.get().getZoneRepeaterCallsign(), ' ')
						) : Optional.empty();

				QueryTask queryTask = null;

				if(gatewayAddress.isPresent()) {
					final IRCDDBQueryTask queryResult =
						new IRCDDBQueryTask(IRCDDBQueryType.FindUser);

					queryResult.setQueryCallsign(userCallsign);
					queryResult.setQueryState(IRCDDBQueryState.Completed);
					queryResult.setQueryResult(IRCDDBQueryResult.Success);

					queryResult.setDataTimestamp(dbUserResult.get().getUpdateTime());
//					queryResult.setMyCallsign(String.valueOf(header.getMyCallsign()));
					queryResult.setYourCallsign(userCallsign);
					queryResult.setRepeaterCallsign(
						DSTARUtils.formatFullLengthCallsign(dbRepeaterResult.get().getAreaRepeaterCallsign())
					);
					queryResult.setGatewayCallsign(
						DSTARUtils.formatFullCallsign(dbRepeaterResult.get().getZoneRepeaterCallsign(), 'G')
					);
					queryResult.setGatewayAddress(gatewayAddress.get().getIpAddress());

					queryTask = new QueryTask(queryID, IRCDDBQueryType.FindUser, queryResult);
				}
				else {
					//DBに該当データが見つからない場合、各DDBにクエリ発行
					final Map<ServerEntry, QueryTaskStatus> queries = new HashMap<>(maxServers);

					locker.lock();
					try {
						for(final ServerEntry server : servers) {
							if(
								!server.getDdbClient().isRunning() ||
								!server.getDdbClient().isInitReady() ||
								!server.getIrcClient().isRunning() ||
								!server.getIrcClient().isConnected()
							) {continue;}

							server.getDdbClient().findUser(userCallsign)
							.ifPresent(new Consumer<UUID>() {
								@Override
								public void accept(UUID id) {
									queries.put(server, new QueryTaskStatus(id, server.getDdbClient()));
								}
							});
						}
					}finally {
						locker.unlock();
					}

					if(queries.isEmpty()) {
						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Can't run query for user " + userCallsign + " because there are no valid services."
							);
						}

						final IRCDDBQueryTask queryResult =
							new IRCDDBQueryTask(IRCDDBQueryType.FindUser);
						queryResult.setQueryCallsign(userCallsign);
						queryResult.setQueryState(IRCDDBQueryState.Completed);
						queryResult.setQueryResult(IRCDDBQueryResult.Failed);

						queryTask = new QueryTask(queryID, IRCDDBQueryType.FindUser, queryResult);
					}
					else {
						queryTask = new QueryTask(queryID, IRCDDBQueryType.FindUser, queries);
					}
				}

				locker.lock();
				try {
					if(queryTask == null || !queryTasks.add(queryTask)) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Could not add query task.");

						throw new RuntimeException("Could not add find user query task.");
					}
				}finally {
					locker.unlock();
				}

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						(queryTask.getDatabaseResult() != null ? "[CacheResult] " : "[QueryExecute] ") +
						"Add query task " + queryTask.getQueryType() + "(" + queryID + ")");
				}
			}
		});


		return queryID;
	}

	@Override
	public UserRoutingInfo getUserInfo(UUID taskid) {
		if(!isRunning() || taskid == null) {return null;}

		UserRoutingInfo routingResult = null;

		final Optional<IRCDDBQueryTask> query =
			getQueryTaskResultCompleted(true, taskid);

		if(query.isPresent()) {
			InetAddress gatewayAddress = null;
			try {
				gatewayAddress = InetAddress.getByName(query.get().getGatewayAddress());
			}catch(UnknownHostException ex) {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Unknown host address " + query.get().getGatewayAddress() + ".\n" + query.toString(),
						ex
					);
				}
			}

			routingResult = new UserRoutingInfo();
			switch(query.get().getQueryResult()) {
			case Success:
				routingResult.setRoutingResult(RoutingServiceResult.Success);
				break;
			case NotFound:
				routingResult.setRoutingResult(RoutingServiceResult.NotFound);
				break;
			default:
				routingResult.setRoutingResult(RoutingServiceResult.Failed);
				break;
			}
			routingResult.setYourCallsign(query.get().getYourCallsign());
			routingResult.setRepeaterCallsign(query.get().getRepeaterCallsign());
			routingResult.setGatewayCallsign(query.get().getGatewayCallsign());
			routingResult.setGatewayAddress(gatewayAddress);
			routingResult.setTimestamp(
				query.get().getDataTimestamp() != null ?
					query.get().getDataTimestamp().getTime() : 0
			);
		}

		return routingResult;
	}

	@Override
	public boolean isServiceTaskCompleted(@NonNull UUID taskid) {
		final boolean isCompleted =
			getQueryTaskResultCompleted(false, taskid).isPresent();

		if(isCompleted) {
			if(log.isTraceEnabled()) {
				log.trace(logTag + "Return complete task " + taskid + ".");
			}
		}

		return isCompleted;
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted() {

		Optional<RoutingCompletedTaskInfo> result;

		locker.lock();
		try {
			result =
				Stream.of(getQueryTaskResult(false, null, null, false, true, false, false))
				.min(ComparatorCompat.comparingLong(
					new ToLongFunction<IRCDDBQueryTask>(){
						@Override
						public long applyAsLong(IRCDDBQueryTask task){
							return task.getCreatedTime();
						}
					}
				))
				.map(new Function<IRCDDBQueryTask, RoutingCompletedTaskInfo>(){
					@Override
					public RoutingCompletedTaskInfo apply(IRCDDBQueryTask t) {
						final RoutingCompletedTaskInfo info =
							new RoutingCompletedTaskInfo(
								t.getTaskid(),
								t.getQueryType().getRoutingServiceType()
							);

						return info;
					}
				});
		}finally {
			locker.unlock();
		}

		return result.isPresent() ? result.get() : null;
	}

	@Override
	public RoutingCompletedTaskInfo getServiceTaskCompleted(@NonNull UUID taskid) {

		Optional<RoutingCompletedTaskInfo> result;

		locker.lock();
		try {
			result =
				Stream.of(getQueryTaskResult(false, taskid, null, false, true, false, false))
				.findSingle()
				.map(new Function<IRCDDBQueryTask, RoutingCompletedTaskInfo>(){
					@Override
					public RoutingCompletedTaskInfo apply(IRCDDBQueryTask t) {
						final RoutingCompletedTaskInfo info =
							new RoutingCompletedTaskInfo(
								t.getTaskid(),
								t.getQueryType().getRoutingServiceType()
							);

						return info;
					}
				});
		}finally {
			locker.unlock();
		}

		if(result.isPresent()) {
			if(log.isTraceEnabled()) {
				log.trace(logTag + "Return complete task " + taskid + ".");
			}
		}

		return result.isPresent() ? result.get() : null;
	}

	@Override
	public Optional<GlobalIPInfo> getGlobalIPAddress() {

		Optional<InetAddress> ip = Optional.empty();

		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				ip =
					server.getDdbClient()
					.getIPAddressFromIrcNick(server.getIrcClient().getCurrentNick());

				if(ip.isPresent()) {break;}
			}
		}finally {
			locker.unlock();
		}

		return Optional.ofNullable(ip.isPresent() ? new GlobalIPInfo(ip.get()) : null);
	}

	@Override
	public RoutingServiceStatusReport getRoutingServiceStatusReport() {
		final RoutingServiceStatusReport report =
			new RoutingServiceStatusReport(getServiceType(), getServerStatus());

		return report;
	}

	@Override
	public org.jp.illg.dstar.service.web.model.RoutingServiceStatusData createStatusData() {
		final IrcDDBRoutingServiceStatusData status =
			new IrcDDBRoutingServiceStatusData(getWebSocketRoomId());
		status.setRoutingServiceType(getServiceType());
		status.setRoutingServiceStatus(getServerStatus());

		status.setUserRecords(getCountUserRecords());
		status.setRepeaterRecords(getCountRepeaterRecords());

		return status;
	}

	@Override
	public Class<? extends org.jp.illg.dstar.service.web.model.RoutingServiceStatusData> getStatusDataType() {
		return IrcDDBRoutingServiceStatusData.class;
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGatewayCallsign(), getServiceType().getTypeName()
		);
	}

	@Override
	public boolean initializeWebRemoteControl(WebRemoteControlService webRemoteControlService) {
		this.webRemoteControlService = webRemoteControlService;

		if(webRemoteControlService.initializeIrcDDBClientService(this)) {
			locker.lock();
			try {
				for(final ServerEntry server : servers)
					server.getDdbClient().setWebRemoteControlService(webRemoteControlService);
			}finally {
				locker.unlock();
			}

			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public WebRemoteControlRoutingServiceHandler getWebRemoteControlHandler() {
		return this;
	}

	@Override
	public int getCountUserRecords() {
		return (int)database.countUserRecords();
	}

	@Override
	public int getCountRepeaterRecords() {
		return (int)database.countRepeaterRecords();
	}

	@Override
	public boolean findUserRecord(
		@NonNull final String userCallsign,
		@NonNull final QueryCallback<List<QueryUserResult>> callback
	) {
		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				final List<QueryUserResult> results = new ArrayList<>(1);

				final Optional<IRCDDBAppRepeaterUserEntry> dbUserResult =
					database.findUser(userCallsign);
				final Optional<IRCDDBAppRepeaterEntry> dbRepeaterResult =
					dbUserResult.isPresent() ?
						database.findRepeater(dbUserResult.get().getAreaRepeaterCallsign()) : Optional.empty();

				final Optional<IRCDDBAppRepeaterIPEntry> gatewayAddress =
					dbRepeaterResult.isPresent() ?
						database.findIP(DSTARUtils.formatFullCallsign(
							dbRepeaterResult.get().getZoneRepeaterCallsign(), ' ')
						) : Optional.empty();

				InetAddress ip = null;
				if(gatewayAddress.isPresent()) {
					try {
						ip = InetAddress.getByName(gatewayAddress.get().getIpAddress());
					}catch(UnknownHostException ex) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Unknown host = " + gatewayAddress + ".");
					}
				}

				if(ip != null) {
					final QueryUserResult result =
						new QueryUserResult(
							userCallsign,
							DSTARUtils.formatFullLengthCallsign(
								dbRepeaterResult.get().getAreaRepeaterCallsign().toUpperCase(Locale.ENGLISH)
							),
							DSTARUtils.formatFullCallsign(
								dbRepeaterResult.get().getZoneRepeaterCallsign().toUpperCase(Locale.ENGLISH),
								'G'
							),
							ip
						);

					results.add(result);
				}

				callback.result(results);
			}
		});

		return true;
	}

	@Override
	public boolean findRepeaterRecord(
		@NonNull String areaRepeaterCallsign,
		@NonNull QueryCallback<List<QueryRepeaterResult>> callback
	) {
		workerExecutor.submit(new RunnableTask(exceptionListener) {
			@Override
			public void task() {
				final List<QueryRepeaterResult> results = new ArrayList<>(1);

				final Optional<IRCDDBAppRepeaterEntry> dbRepeaterResult =
						database.findRepeater(areaRepeaterCallsign);

				final Optional<IRCDDBAppRepeaterIPEntry> gatewayAddress =
					dbRepeaterResult.isPresent() ?
						database.findIP(DSTARUtils.formatFullCallsign(
							dbRepeaterResult.get().getZoneRepeaterCallsign(), ' ')
						) : Optional.empty();

				InetAddress ip = null;
				if(gatewayAddress.isPresent()) {
					try {
						ip = InetAddress.getByName(gatewayAddress.get().getIpAddress());
					}catch(UnknownHostException ex) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Unknown host = " + gatewayAddress + ".");
					}
				}

				if(ip != null) {
					final QueryRepeaterResult result =
						new QueryRepeaterResult(
							DSTARUtils.formatFullLengthCallsign(
								dbRepeaterResult.get().getAreaRepeaterCallsign().toUpperCase(Locale.ENGLISH)
							),
							DSTARUtils.formatFullCallsign(
								dbRepeaterResult.get().getZoneRepeaterCallsign().toUpperCase(Locale.ENGLISH),
								'G'
							),
							ip
						);

					results.add(result);
				}

				callback.result(results);
			}
		});

		return true;
	}

	@Override
	public InetSocketAddress[] getServerAddress() {
		final List<InetSocketAddress> results = new ArrayList<>(maxServers);

		locker.lock();
		try {
			for(final ServerEntry server : servers) {
				InetSocketAddress ip = null;
				try {
					ip = new InetSocketAddress(
						server.getIrcClient().getHost(), server.getIrcClient().getPort()
					);
				}catch(Exception ex) {
					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Could not resolve irc server address = " +
							server.getIrcClient().getHost() + ":" + server.getIrcClient().getPort()
						);
					}
				}

				if(ip != null) {results.add(ip);}
			}
		}finally {
			locker.unlock();
		}

		return results.toArray(new InetSocketAddress[0]);
	}

	@Override
	public String getServerConnectionStatus() {

		boolean isConnected = false;
		for(final ServerEntry server : servers) {
			if(server.getIrcClient().isConnected()) {
				isConnected = true;
				break;
			}
		}

		return isConnected ? "Connected" : "Disconnected";
	}

	@Override
	public RoutingServiceStatusData getServiceStatus() {

		final RoutingServiceStatusData result =
			new RoutingServiceStatusData(getServiceType(), getServerStatus());

		return result;
	}

	private boolean addSuppressionHeard(final int frameID) {
		boolean success = false;

		locker.lock();
		try {
			cleanupSuppressionHeard();

			for(Iterator<HeardEntry> it = suppressionHeardEntries.iterator(); it.hasNext();) {
				final HeardEntry entry = it.next();

				if(entry.getFrameID() == frameID) {it.remove();}
			}

			success = suppressionHeardEntries.add(new HeardEntry(frameID));
		}finally {
			locker.unlock();
		}

		if(success && log.isTraceEnabled())
			log.trace(logTag + "add suppression heard entry = frameID:" + String.format("0x%04X", frameID));

		return success;
	}

	private boolean isSuppressionHeard(final int frameID) {
		boolean suppression = false;

		locker.lock();
		try {
			cleanupSuppressionHeard();

			for(Iterator<HeardEntry> it = suppressionHeardEntries.iterator(); it.hasNext();) {
				final HeardEntry entry = it.next();

				if(entry.getFrameID() == frameID) {
					it.remove();

					suppression = true;
				}
			}
		}finally {
			locker.unlock();
		}

		return suppression;
	}

	private void cleanupSuppressionHeard() {
		locker.lock();
		try {
			for(Iterator<HeardEntry> it = suppressionHeardEntries.iterator(); it.hasNext();) {
				final HeardEntry entry = it.next();

				if(entry.getInactivityTimer().isTimeout(10, TimeUnit.SECONDS)) {
					it.remove();
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private Optional<IRCDDBQueryTask> getQueryTaskResultCompleted(
		final boolean removeMatchedTask,
		@NonNull final UUID queryID
	){
		Optional<IRCDDBQueryTask> result;

		locker.lock();
		try {
			final List<IRCDDBQueryTask> results =
				getQueryTaskResult(
					removeMatchedTask, queryID,
					null,
					false, true,
					true, false
				);

			result = Optional.ofNullable(!results.isEmpty() ? results.get(0) : null);
		}finally {
			locker.unlock();
		}

		return result;
	}

	private List<IRCDDBQueryTask> getQueryTaskResult(
		final boolean removeMatchedTask,
		final UUID queryID,
		final IRCDDBQueryType queryType,
		final boolean isIgnoreComplete,
		final boolean isComplete,
		final boolean isIgnoreTimeout,
		final boolean isTimeout
	){
		List<IRCDDBQueryTask> results;

		locker.lock();
		try {
			final List<QueryTask> tasks =
				findQueryTask(
					queryID, queryType,
					isIgnoreComplete, isComplete,
					isIgnoreTimeout, isTimeout
				).toList();
/*
			if(!tasks.isEmpty() && log.isTraceEnabled()) {
				final StringBuilder sb = new StringBuilder("getQueryTaskResult\n");
				for(Iterator<QueryTask> it = tasks.iterator(); it.hasNext();) {
					final QueryTask task = it.next();

					if(task.getQueryTimer().isTimeout(queryTimeLimitSeconds, TimeUnit.SECONDS)) {
						sb.append("[X]");
					}

					sb.append(task.toString(4));

					if(it.hasNext()) {sb.append('\n');}
				}

				log.trace(logTag + sb.toString());
			}
*/
			if(tasks.isEmpty()) {return Collections.emptyList();}

			results = convert(Stream.of(tasks)).toList();

			//マッチしたタスクを削除する
			if(removeMatchedTask && !tasks.isEmpty()) {
				for(final QueryTask task : tasks) {
					removeQueryTask(task.getQueryID());
				}
			}
		}finally {
			locker.unlock();
		}

		return results;
	}

	private void checkQueryTasks() {
		locker.lock();
		try {
			for(final QueryTask task : queryTasks) {
				for(final QueryTaskStatus status : task.getQueries().values()) {
					status.getDdbClient().getCompletedQueryTask(status.getQueryID(), task.getQueryType())
					.ifPresent(new Consumer<IRCDDBQueryTask>() {
						@Override
						public void accept(IRCDDBQueryTask result) {
							status.setResult(result);
							status.setComplete(true);
						}
					});
				}

				if(!task.isComplete()) {
					final boolean isComplete =
						Stream.of(task.getQueries().values())
						.allMatch(new Predicate<QueryTaskStatus>() {
							@Override
							public boolean test(QueryTaskStatus status) {
								return status.isComplete();
							}
						});

					task.setComplete(isComplete);

					if(isComplete) {
						if(log.isDebugEnabled()) {
							log.debug(
								logTag +
								"Complete query task = " + task.getQueryType() + "(" + task.getQueryID() + ")\n" +
								task.toString(4)
							);
						}
					}
				}

			}
		}finally {
			locker.unlock();
		}
	}

	private List<UUID> getCompleteQueryTasks() {
		locker.lock();
		try {
			return findQueryTask(null, null, false, true, true, false)
			.map(new Function<QueryTask, UUID>() {
				@Override
				public UUID apply(QueryTask t) {
					return t.getQueryID();
				}
			}).toList();
		}finally {
			locker.unlock();
		}
	}

	private Stream<QueryTask> findQueryTask(
		final UUID queryID,
		final IRCDDBQueryType queryType,
		final boolean isIgnoreComplete,
		final boolean isComplete,
		final boolean isIgnoreTimeout,
		final boolean isTimeout
	) {
		cleanupQueryTasks();

		checkQueryTasks();

		Stream<QueryTask> results;

		locker.lock();
		try {
			results =
				Stream.of(queryTasks)
				.filter(new Predicate<QueryTask>() {
					@Override
					public boolean test(QueryTask task) {
						return
							(queryType == null || task.getQueryType() == queryType) &&
							(queryID == null || task.getQueryID() == queryID) &&
							(isIgnoreComplete || isComplete == task.isComplete()) &&
							(
								isIgnoreTimeout ||
								isTimeout == task.getQueryTimer().isTimeout(queryTimeLimitSeconds, TimeUnit.SECONDS)
							);
					}
				});
		}finally {
			locker.unlock();
		}

		return results;
	}

	private boolean removeQueryTask(final UUID taskID) {
		boolean isRemoved = false;

		locker.lock();
		try {
			for(final Iterator<QueryTask> it = queryTasks.iterator(); it.hasNext();) {
				final QueryTask task = it.next();

				if(task.getQueryID().equals(taskID)) {
					it.remove();

					if(log.isDebugEnabled()) {
						log.debug(logTag + "Task " + task.getQueryType() + "(" + taskID + ")" + " is removed.");
					}

					isRemoved = true;

					break;
				}
			}
		}finally {
			locker.unlock();
		}

		return isRemoved;
	}

	private void cleanupQueryTasks() {
		locker.lock();
		try {
			for(final Iterator<QueryTask> it = queryTasks.iterator(); it.hasNext();) {
				final QueryTask task = it.next();

				if(task.getQueryTimer().isTimeout(1, TimeUnit.MINUTES)) {

					it.remove();

					if(!task.isComplete()) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Deleted a task whose query was not completed.");
					}
					else {
						if(log.isInfoEnabled()) {
							log.info(
								logTag +
								"Deleted a uncollected task type = " + task.getQueryType() + "(" + task.getQueryID() + ")"
							);
						}
					}
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private static Stream<IRCDDBQueryTask> convert(final Stream<QueryTask> tasks){
		return tasks
		.map(new Function<QueryTask, IRCDDBQueryTask>(){
			@Override
			public IRCDDBQueryTask apply(QueryTask task) {
				Optional<IRCDDBQueryTask> result;

				result =
					task.isComplete() && task.getDatabaseResult() != null ?
						Optional.of(task.getDatabaseResult()) :
						Stream.of(task.getQueries().values())
						.filter(new Predicate<QueryTaskStatus>() {
							@Override
							public boolean test(QueryTaskStatus status) {
								return
									status.isComplete() &&
									status.getResult().getQueryResult() == IRCDDBQueryResult.Success;
							}
						})
						.max(ComparatorCompat.comparingLong(new ToLongFunction<QueryTaskStatus>() {
							@Override
							public long applyAsLong(QueryTaskStatus status) {
								return status.getResult().getDataTimestamp() != null ?
									status.getResult().getDataTimestamp().getTime() : 0L;
							}
						}))
						.map(new Function<QueryTaskStatus, IRCDDBQueryTask>(){
							@Override
							public IRCDDBQueryTask apply(QueryTaskStatus status) {
								//IDをDDB固有のIDから、このサービス固有のIDへ変更
								status.getResult().setTaskid(task.getQueryID());

								return status.getResult();
							}
						});

				final boolean allQueryFailed =
					task.getDatabaseResult() != null ? false :
						Stream.of(task.getQueries().values())
						.allMatch(new Predicate<QueryTaskStatus>() {
							@Override
							public boolean test(QueryTaskStatus status) {
								return
									status.isComplete() &&
									status.getResult().getQueryResult() != IRCDDBQueryResult.Success;
							}
						});

				final boolean includeNotFound =
					task.getDatabaseResult() != null ? false :
						Stream.of(task.getQueries().values())
						.anyMatch(new Predicate<QueryTaskStatus>() {
							@Override
							public boolean test(QueryTaskStatus status) {
								return
									status.isComplete() &&
									status.getResult().getQueryResult() == IRCDDBQueryResult.NotFound;
							}
						});

				if(
					allQueryFailed ||
					task.getQueryTimer().isTimeout(queryTimeLimitSeconds, TimeUnit.SECONDS)
				) {
					final IRCDDBQueryTask failedResult = new IRCDDBQueryTask(task.getQueryType());
					failedResult.setTaskid(task.getQueryID());
					failedResult.setQueryState(IRCDDBQueryState.Completed);
					failedResult.setQueryResult(
						includeNotFound ? IRCDDBQueryResult.NotFound : IRCDDBQueryResult.Failed
					);

					result = Optional.of(failedResult);
				}

				return result.isPresent() ? result.get() : null;
			}
		})
		.filter(new Predicate<IRCDDBQueryTask>() {
			@Override
			public boolean test(IRCDDBQueryTask task) {
				return task != null;
			}
		});
	}

	private RoutingServiceStatus getServiceStatusInternal(final ServerEntry entry) {
		if(!isRunning()) {return RoutingServiceStatus.OutOfService;}

		switch(entry.getDdbClient().getState()) {
		case WaitForNetworkStart:
		case ConnectToDB:
		case ChooseServer:
			return RoutingServiceStatus.InitializingService;

		case CheckSendList:
		case RequestSendList:
		case WaitSendList:
		case EndOfSendList:
			return RoutingServiceStatus.DatabaseSyncing;

		case Standby:
			return RoutingServiceStatus.InService;

		default:
			return RoutingServiceStatus.OutOfService;
		}
	}

	protected List<RoutingServiceServerStatus> getServerStatus() {

		List<RoutingServiceServerStatus> statusList;

		locker.lock();
		try {
			statusList = new ArrayList<>(servers.size());

			for(final ServerEntry server : servers) {
				final RoutingServiceServerStatus status =
					new RoutingServiceServerStatus(
						getServiceType(),
						getServiceStatusInternal(server),
						false,
						"",
						-1,
						server.getIrcClient().getHost(),
						server.getIrcClient().getPort()
					);
				statusList.add(status);
			}
		}finally {
			locker.unlock();
		}

		return statusList;
	}

	@Override
	public String getApplicationName() {
		return applicationVersion.getApplicationName();
	}

	@Override
	public String getApplicationVersion() {
		return applicationVersion.getApplicationVersion();
	}
}
