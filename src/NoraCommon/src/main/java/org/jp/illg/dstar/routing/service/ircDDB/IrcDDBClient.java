package org.jp.illg.dstar.routing.service.ircDDB;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.routing.service.ircDDB.db.IRCDDBDatabaseController;
import org.jp.illg.dstar.routing.service.ircDDB.db.define.IRCDDBTableID;
import org.jp.illg.dstar.routing.service.ircDDB.define.IRCDDBAppState;
import org.jp.illg.dstar.routing.service.ircDDB.define.IRCDDBClientEvent;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAnnounceTask;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppLoginUserEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterIPEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterUserEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryResult;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryState;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryTask;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBQueryType;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.irc.IRCApplication;
import org.jp.illg.util.irc.IRCClient;
import org.jp.illg.util.irc.model.IRCMessage;
import org.jp.illg.util.irc.model.IRCMessageQueue;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IrcDDBClient extends ThreadBase implements IRCApplication {

	private static final int numberOfTables = 2;

	private static final Pattern tablePattern;
	private static final Pattern datePattern;
	private static final Pattern timePattern;
	private static final Pattern dbPattern;

	private static final Pattern descriptionNonValid;
	private static final Pattern urlNonValid;

	private String logTag;

	private final ExecutorService workerExecutor;

	private final IrcDDBRoutingService service;

	@Getter
	@Setter
	private WebRemoteControlService webRemoteControlService;

	private boolean isStatusChangedToWebRemoteControl;
	private final Timer webRemoteControlNotifyIntervalTimeKeeper;

	@Getter(AccessLevel.PRIVATE)
	@Setter
	private IRCClient ircClient;

	private final IRCDDBDatabaseController database;

	private IRCMessageQueue sendQ;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String currentServer;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String myNick;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private IRCDDBAppState state;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int timer;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String updateChannel;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String channelTopic;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean initReady;

	private final Map<String, IRCDDBAppLoginUserEntry> loginUsers;
	private final Lock loginUsersLocker;

	private final Map<String, IRCDDBAnnounceTask> infoQTH;
	private final Lock infoQTHLocker;

	private final Map<String, IRCDDBAnnounceTask> infoURL;
	private final Lock infoURLLocker;

	private final Map<String, IRCDDBAnnounceTask> infoQRG;
	private Lock infoQRGLocker;

	private final Map<String, IRCDDBAnnounceTask> infoWatchdog;
	private final Lock infoWatchdogLocker;

	private final Queue<IRCDDBQueryTask> queryTasks;
	private final Lock queryTasksLocker;

	private final Map<Integer, Date> latestTimes;
	private final Lock latestTimesLocker;

	private int sendlistTableID;

	private final String debugServerUser;

	private final EventListener<IRCDDBClientEvent> eventListener;

	static {
		tablePattern = Pattern.compile("^[0-9]$");
		datePattern = Pattern.compile("^20[0-9][0-9]-((1[0-2])|(0[1-9]))-((3[01])|([12][0-9])|(0[1-9]))$");
		timePattern = Pattern.compile("^((2[0-3])|([01][0-9])):[0-5][0-9]:[0-5][0-9]$");
		dbPattern = Pattern.compile("^[0-9A-Z_]{8}$");

		descriptionNonValid = Pattern.compile("[^a-zA-Z0-9 +&(),./'-]");
		urlNonValid = Pattern.compile("[^\\p{Graph}]");
	}

	public IrcDDBClient(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull IrcDDBRoutingService service,
		@NonNull IRCDDBDatabaseController database,
		@NonNull String updateChannel,
		@NonNull String debugServerUser,
		@NonNull EventListener<IRCDDBClientEvent> eventListener
	) {
		super(exceptionListener, IrcDDBClient.class.getSimpleName(), TimeUnit.SECONDS.toMillis(1));

		logTag = IrcDDBClient.class.getSimpleName() + " : ";

		this.eventListener = eventListener;

		this.service = service;
		this.workerExecutor = workerExecutor;

		this.database = database;

		this.debugServerUser = debugServerUser;

		setWebRemoteControlService(null);

		setSendQ(null);

		setCurrentServer("");
		setMyNick("none");

		setState(IRCDDBAppState.WaitForNetworkStart);
		setTimer(0);

		setUpdateChannel(updateChannel);
		setChannelTopic("");

		setInitReady(false);

		loginUsers = new HashMap<String, IRCDDBAppLoginUserEntry>();
		loginUsersLocker = new ReentrantLock();

		infoQTH = new HashMap<String, IRCDDBAnnounceTask>();
		infoQTHLocker = new ReentrantLock();

		infoURL = new HashMap<String, IRCDDBAnnounceTask>();
		infoURLLocker = new ReentrantLock();

		infoQRG = new HashMap<String, IRCDDBAnnounceTask>();
		infoQRGLocker = new ReentrantLock();

		infoWatchdog = new HashMap<String, IRCDDBAnnounceTask>();
		infoWatchdogLocker = new ReentrantLock();

		queryTasks = new LinkedList<>();
		queryTasksLocker = new ReentrantLock();

		latestTimes = new HashMap<>(2);
		latestTimesLocker = new ReentrantLock();

		sendlistTableID = 0;

		webRemoteControlNotifyIntervalTimeKeeper = new Timer();
		webRemoteControlNotifyIntervalTimeKeeper.updateTimestamp();
		isStatusChangedToWebRemoteControl = false;

		userListReset();
	}

	/**
	 * 指定されたクエリが完了しているか確認する
	 * @param queryTaskID クエリID
	 * @return 完了していればtrue
	 */
	public boolean isQueryTaskCompleted(UUID queryTaskID) {
		if (queryTaskID == null) {
			return false;
		}

		return getQueryTaskCompleted(queryTaskID, true).isPresent();
	}

	/**
	 * 指定されたクエリが完了していれば取得する
	 * @param queryTaskID クエリID
	 * @return クエリタスク情報、完了していない場合はempty
	 */
	public Optional<IRCDDBQueryTask> getQueryTaskCompleted(UUID queryTaskID) {
		if (queryTaskID == null) {
			return Optional.empty();
		}

		return getQueryTaskCompleted(queryTaskID, false);
	}

	/**
	 * 指定されたクエリが完了しているか確認、もしくは取得する
	 * @param queryTaskID クエリID
	 * @param peek クエリを取得する際に削除しない(削除しなければクエリタスク情報を再度取得可能)
	 * @return クエリタスク情報、完了していない場合はempty
	 */
	public Optional<IRCDDBQueryTask> getQueryTaskCompleted(UUID queryTaskID, boolean peek) {
		if (queryTaskID == null) {
			return Optional.empty();
		}

		IRCDDBQueryTask queryTask = null;
		queryTasksLocker.lock();
		try {
			for (IRCDDBQueryTask task : queryTasks) {
				if (task.getTaskid().equals(queryTaskID)) {
					queryTask = task;
					break;
				}
			}
			if (!peek && queryTask != null)
				queryTasks.remove(queryTask);
		} finally {
			queryTasksLocker.unlock();
		}

		if (queryTask != null && queryTask.getQueryState() == IRCDDBQueryState.Completed)
			return Optional.of(queryTask);
		else
			return Optional.empty();
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask() {
		return getCompletedQueryTask(null, null, false);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(boolean peek) {
		return getCompletedQueryTask(null, null, peek);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(UUID filterTaskid) {
		return getCompletedQueryTask(filterTaskid, null, false);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(UUID filterTaskid, boolean peek) {
		return getCompletedQueryTask(filterTaskid, null, peek);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(IRCDDBQueryType filterQueryType) {
		return getCompletedQueryTask(null, filterQueryType, false);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(IRCDDBQueryType filterQueryType, boolean peek) {
		return getCompletedQueryTask(null, filterQueryType, peek);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(UUID filterTaskid, IRCDDBQueryType filterQueryType) {
		return getCompletedQueryTask(filterTaskid, filterQueryType, false);
	}

	public Optional<IRCDDBQueryTask> getCompletedQueryTask(UUID filterTaskid, IRCDDBQueryType filterQueryType,
		boolean peek) {
		IRCDDBQueryTask completedTask = null;

		queryTasksLocker.lock();
		try {
			for (IRCDDBQueryTask queryTask : queryTasks) {
				if ((filterTaskid == null ||
					queryTask.getTaskid().equals(filterTaskid)) &&
					queryTask.getQueryState() == IRCDDBQueryState.Completed &&
					(filterQueryType == null || filterQueryType == queryTask.getQueryType())
				) {
					completedTask = queryTask;
					break;
				}
			}
			if (!peek && completedTask != null)
				queryTasks.remove(completedTask);
		} finally {
			queryTasksLocker.unlock();
		}

		if (completedTask != null)
			return Optional.of(completedTask);
		else
			return Optional.empty();
	}

	public List<IRCDDBQueryTask> getCompletedQueryTasks() {
		return getCompletedQueryTasks(null, false);
	}

	public List<IRCDDBQueryTask> getCompletedQueryTasks(boolean peek) {
		return getCompletedQueryTasks(null, peek);
	}

	public List<IRCDDBQueryTask> getCompletedQueryTasks(IRCDDBQueryType filterQueryType, boolean peek) {
		List<IRCDDBQueryTask> completedTasks = new ArrayList<>();

		queryTasksLocker.lock();
		try {
			Optional<IRCDDBQueryTask> query = Optional.empty();
			while ((query = getCompletedQueryTask(null, filterQueryType, peek)).isPresent()) {
				completedTasks.add(query.get());
			}
		} finally {
			queryTasksLocker.unlock();
		}

		return completedTasks;
	}

	public Optional<UUID> findUser(String usrCall) {
		if (!CallSignValidator.isValidUserCallsign(usrCall)) {
			return Optional.empty();
		}

		final IRCDDBQueryTask queryTask = new IRCDDBQueryTask(IRCDDBQueryType.FindUser);
		queryTask.setQueryCallsign(usrCall);
		queryTask.setQueryState(IRCDDBQueryState.QueryAdded);

		queryTasksLocker.lock();
		try {
			queryTasks.add(queryTask);
		} finally {
			queryTasksLocker.unlock();
		}

		return Optional.of(queryTask.getTaskid());
	}

	public Optional<UUID> sendHeard(
		String myCall, String myCallExt, String yourCall, String rpt1, String rpt2,
		byte flag1, byte flag2, byte flag3,
		String destination, String tx_msg, String tx_stats
	) {

		final IRCDDBQueryTask queryTask = new IRCDDBQueryTask(IRCDDBQueryType.SendHeard);
		queryTask.setQueryCallsign(myCall);

		queryTask.setMyCallsign(myCall);
		queryTask.setMyCallsignAdd(myCallExt);
		queryTask.setYourCallsign(yourCall);
		queryTask.setRepeaterCallsign(rpt1);
		queryTask.setGatewayCallsign(rpt2);
		queryTask.setFlag1(flag1);
		queryTask.setFlag2(flag2);
		queryTask.setFlag3(flag3);
		queryTask.setDestination(destination);
		queryTask.setTxMessage(tx_msg);
		queryTask.setTxStatus(tx_stats);

		queryTask.setQueryState(IRCDDBQueryState.QueryAdded);

		queryTasksLocker.lock();
		try {
			queryTasks.add(queryTask);
		} finally {
			queryTasksLocker.unlock();
		}

		return Optional.of(queryTask.getTaskid());
	}

	public boolean rptrQTH(
		@NonNull String callsign,
		final double latitude, final double longitude,
		final String desc1, final String desc2,
		final String infoURL
	) {

		if (!CallSignValidator.isValidRepeaterCallsign(callsign))
			return false;

		final String cs = callsign.replace(' ', '_');

		String pos =
			String.format("%+09.5f %+010.5f", latitude, longitude);

		String d1 =
			descriptionNonValid.matcher(desc1 != null ? desc1 : "").replaceAll("");
		String d2 =
			descriptionNonValid.matcher(desc2 != null ? desc2 : "").replaceAll("");
		if(d1.length() > 20) {d1.substring(0, 20);}
		if(d2.length() > 20) {d2.substring(0, 20);}

		d1 = String.format("%-20s", d1);
		d2 = String.format("%-20s", d2);

		pos = pos.replace(',', '.');
		d1 = d1.replace(' ', '_');
		d2 = d2.replace(' ', '_');

		infoQTHLocker.lock();
		try {
			final String message = cs + " " + pos + " " + d1 + " " + d2;

			if (infoQTH.containsKey(cs)) {infoQTH.remove(cs);}

			infoQTH.put(
				cs,
				new IRCDDBAnnounceTask(cs, message, TimeUnit.SECONDS.toSeconds(5))
			);

			if(log.isTraceEnabled()) {log.trace(logTag + "QTH: " + message);}

		} finally {
			infoQTHLocker.unlock();
		}

		String url =
			urlNonValid.matcher(infoURL != null ? infoURL : "").replaceAll("");
		if (url.length() > 120) {url = url.substring(0, 120);}

		if (url.length() > 0) {
			infoURLLocker.lock();
			try {
				String message = cs + " " + url;

				if (this.infoURL.containsKey(cs)) {this.infoURL.remove(cs);}

				this.infoURL.put(
					cs,
					new IRCDDBAnnounceTask(cs, message, TimeUnit.SECONDS.toSeconds(5))
				);

				if (log.isTraceEnabled()) {log.trace(logTag + "URL: " + message);}

			} finally {
				infoURLLocker.unlock();
			}
		}

		return true;
	}

	public boolean rptrQRG(
		final String callsign,
		final double txFrequency, final double duplexShift,
		final double range, final double agl
	) {

		if (!CallSignValidator.isValidUserCallsign(callsign))
			return false;

		final String cs = callsign.replace(' ', '_');

		final String qrg =
			String.format(
				"%011.5f %+010.5f %06.2f %06.1f",
				txFrequency / 1000000, duplexShift, range / 1609.344, agl
			)
			.replace(',', '.');

		infoQRGLocker.lock();
		try {
			String message = cs + " " + qrg;

			if (infoQRG.containsKey(cs)) {infoQRG.remove(cs);}

			infoQRG.put(
				cs,
				new IRCDDBAnnounceTask(cs, message, TimeUnit.SECONDS.toSeconds(5))
			);

			if (log.isTraceEnabled()) {log.trace(logTag + "QRG: " + message);}

		} finally {
			infoQRGLocker.unlock();
		}

		return true;
	}

	public void kickWatchdog(String callsign, String s) {
		if (!CallSignValidator.isValidRepeaterCallsign(callsign))
			return;

		String cs = callsign.replace(' ', '_');

		String info = s != null ? s : "";
		Pattern nonValid = Pattern.compile("[^\\p{Graph}]");
		info = nonValid.matcher(info).replaceAll("");

		if (info.length() > 0) {
			infoWatchdogLocker.lock();
			try {
				String message = cs + " " + info;

				if (infoWatchdog.containsKey(cs)) {
					infoWatchdog.get(cs).setMessage(message);

					return;
				}
				else {
					infoWatchdog.put(
						cs,
						new IRCDDBAnnounceTask(cs, message, TimeUnit.SECONDS.toSeconds(60))
					);
				}
			} finally {
				infoWatchdogLocker.unlock();
			}
		}
	}

	public int getConnectionState() {
		return getState().getStateNumber();
	}

	public Optional<InetAddress> getIPAddressFromIrcNick(String nick) {
		if (nick == null || "".equals(nick)) {
			return Optional.empty();
		}

		loginUsersLocker.lock();
		try {
			IRCDDBAppLoginUserEntry user = loginUsers.get(nick);
			if (user == null) {
				return Optional.empty();
			}

			InetAddress ip = null;
			try {
				ip = InetAddress.getByName(user.getHost());
			} catch (UnknownHostException ex) {
				if (log.isWarnEnabled())
					log.warn(logTag + "Could not resolve host address = " + user.getHost() + ".");
			}

			return Optional.ofNullable(ip);
		} finally {
			loginUsersLocker.unlock();
		}
	}

	@Override
	public boolean start() {

		if(getIrcClient() != null) {
			logTag =
				IrcDDBClient.class.getSimpleName() +
				"(" + getIrcClient().getHost() + ":" + getIrcClient().getPort() + ") : ";
		}

		return super.start();
	}

	@Override
	public void stop() {
		super.stop();
	}

	@Override
	public void setCurrentNick(String nick) {
		setMyNick(nick);

		if (log.isTraceEnabled()) {
			log.trace(logTag + "IRCDDBApp::setCurrentNick " + nick);
		}
	}

	@Override
	public void userJoin(String nick, String name, String host) {

		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(host);
		}catch(UnknownHostException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not resolve host " + host + ".");
		}
		final int p = nick.indexOf("-");
		final String zoneRepeaterCallsign =
			(4 <= p && 7 >= p) ?
				DSTARUtils.formatFullLengthCallsign(
					(nick.substring(0, p).toUpperCase(Locale.ENGLISH))
				) : DSTARDefines.EmptyLongCallsign;
		if(
			addr != null &&
			CallSignValidator.isValidUserCallsign(zoneRepeaterCallsign)
		) {
			database.updateIP(new Date(), zoneRepeaterCallsign, addr.getHostAddress());
		}
		else {
			if(log.isTraceEnabled())
				log.trace(logTag + "Skip update ip record user nick = " + nick);
		}

		loginUsersLocker.lock();
		try {
			final String lnick = nick.toLowerCase();

			final IRCDDBAppLoginUserEntry u = new IRCDDBAppLoginUserEntry(lnick, name, host);

			if (loginUsers.containsKey(lnick)) {loginUsers.remove(lnick);}

			loginUsers.put(lnick, u);

			if (log.isTraceEnabled())
				log.trace(logTag + "UserJoin / Name:" + u.getNick() + "(" + u.getHost() + ") / Total:" + loginUsers.size() + "users.");
		} finally {
			loginUsersLocker.unlock();
		}
	}

	@Override
	public void userLeave(String nick) {
		loginUsersLocker.lock();
		try {
			String lnick = nick.toLowerCase();

			if (loginUsers.containsKey(lnick)) {loginUsers.remove(lnick);}

			if(log.isTraceEnabled())
				log.trace(logTag + "UserLeave / Name: " + nick + " / Total:" + loginUsers.size() + "users.");

			if (getCurrentServer().length() > 0) {
				if (!loginUsers.containsKey(getMyNick())) {
					if (log.isTraceEnabled())
						log.trace(logTag + "IRCDDBApp::userLeave: could not find own nick");

					return;
				}

				final IRCDDBAppLoginUserEntry me = loginUsers.get(getMyNick());

				if (!me.isOp() && getCurrentServer().equals(lnick)) {
					setState(IRCDDBAppState.ChooseServer);
					setTimer(200);
					setInitReady(false);
				}
			}
		} finally {
			loginUsersLocker.unlock();
		}
	}

	@Override
	public void userListReset() {
		loginUsersLocker.lock();
		try {
			loginUsers.clear();
		} finally {
			loginUsersLocker.unlock();
		}
	}

	@Override
	public void setTopic(String topic) {
		setChannelTopic(topic);
	}

	@Override
	public void userChanOp(String nick, boolean op) {

		loginUsersLocker.lock();
		try {
			String lnick = nick.toLowerCase();

			if (loginUsers.containsKey(lnick))
				loginUsers.get(lnick).setOp(op);

		} finally {
			loginUsersLocker.unlock();
		}
	}

	@Override
	public void msgChannel(IRCMessage m) {
		if (m.getPrefixNick().startsWith("s-") && (m.getParamCount() >= 2))
			processUpdate(m.getParam(1));
	}

	@Override
	public void msgQuery(IRCMessage m) {
		if (m.getPrefixNick().startsWith("s-") && (m.getParamCount() >= 2))
		{
			String msg = m.getParam(1);
			Scanner tkz = null;
			try {
				tkz = new Scanner(msg);

				if (!tkz.hasNext()) {
					return;
				}

				String cmd = tkz.next();

				if ("UPDATE".equals(cmd)) {
					processUpdate(IRCUtils.getRemainTokens(tkz));
				}
				else if ("LIST_END".equalsIgnoreCase(cmd)) {
					if (getState() == IRCDDBAppState.WaitSendList) {
						setState(IRCDDBAppState.CheckSendList);
					}
				}
				else if ("LIST_MORE".equalsIgnoreCase(cmd)) {
					if (getState() == IRCDDBAppState.WaitSendList) {
						setState(IRCDDBAppState.RequestSendList);
					}
				}
				else if ("NOT_FOUND".equalsIgnoreCase(cmd)) {
					String callsign = processNotFound(IRCUtils.getRemainTokens(tkz));
					if (callsign != null && !"".equals(callsign)) {
						callsign = callsign.replace('_', ' ');

						queryTasksLocker.lock();
						try {
							for (IRCDDBQueryTask queryTask : queryTasks) {
								if (callsign.equals(queryTask.getQueryCallsign()) &&
									queryTask.getQueryState() == IRCDDBQueryState.Processing) {
									queryTask.updateActivityTime();
									queryTask.setQueryResult(IRCDDBQueryResult.NotFound);
									queryTask.setQueryState(IRCDDBQueryState.Completed);

									dispatchEvent(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());
									break;
								}
							}
						} finally {
							queryTasksLocker.unlock();
						}
					}
				}
			} finally {
				if (tkz != null) {
					tkz.close();
				}
			}
		}
	}

	@Override
	public void setSendQ(IRCMessageQueue s) {
		this.sendQ = s;
	}

	@Override
	public Optional<IRCMessageQueue> getSendQ() {
		return Optional.ofNullable(this.sendQ);
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		if(!database.isRunning())
			return threadFatalError("Database is not running.");

		updateLatestTimesFromDatabase();

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {

		final IRCDDBAppState prevState = getState();

		if (getTimer() > 0) {
			setTimer(getTimer() - 1);
		}

		switch (getState()) {
		case WaitForNetworkStart:
			if (getSendQ().isPresent()) {
				setState(IRCDDBAppState.ConnectToDB);
			}
			break;

		case ConnectToDB:
			setState(IRCDDBAppState.ChooseServer);
			setTimer(200);
			break;

		case ChooseServer:
			if (log.isTraceEnabled())
				log.trace(logTag + "State = " + getState() + " choose new 's-' user.");

			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}
			else {
				if (findServerUser(debugServerUser)) {
					if(log.isInfoEnabled())
						log.info(logTag + "Choose server " + getCurrentServer());

					sendlistTableID = numberOfTables;

					setState(IRCDDBAppState.CheckSendList);
				}
				else if (getTimer() == 0) {
					setState(IRCDDBAppState.DisconnectFromDB);

					final IRCMessage m = new IRCMessage("QUIT");

					m.addParam("no op user with 's-' found.");

					getSendQ().ifPresent(new Consumer<IRCMessageQueue>() {
						@Override
						public void accept(IRCMessageQueue q) {
							q.putMessage(m);
						}
					});
				}
			}
			break;

		case CheckSendList:
			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}
			else {
				sendlistTableID--;
				if (sendlistTableID < 0) {
					setState(IRCDDBAppState.EndOfSendList);
				}
				else {
					if(log.isTraceEnabled())
						log.trace(logTag + "State = " + getState() + " / TableID = " + sendlistTableID);

					setState(IRCDDBAppState.RequestSendList);
					setTimer(900);
				}
			}
			break;

		case RequestSendList:
			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}
			else {
				if (needsDatabaseUpdate(sendlistTableID)) {
					final IRCMessage m = new IRCMessage(
						getCurrentServer(),
						"SENDLIST"
							+ getTableIDString(sendlistTableID, true) + " " + getLastEntryTime(sendlistTableID)
						);

					getSendQ().ifPresent(new Consumer<IRCMessageQueue>() {
						@Override
						public void accept(IRCMessageQueue q) {
							q.putMessage(m);
						}
					});

					setState(IRCDDBAppState.WaitSendList);
				} else {
					setState(IRCDDBAppState.CheckSendList);
				}
			}
			break;

		case WaitSendList:
			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}
			else if (getTimer() == 0) {
				setState(IRCDDBAppState.DisconnectFromDB);
				final IRCMessage m = new IRCMessage("QUIT");

				m.addParam("timeout SENDLIST");

				getSendQ().ifPresent(new Consumer<IRCMessageQueue>() {
					@Override
					public void accept(IRCMessageQueue q) {
						q.putMessage(m);
					}
				});

			}
			break;

		case EndOfSendList:
			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}
			else {
				if(log.isTraceEnabled())
					log.trace(logTag + "State = " + getState() + " initialization completed.");

				if(log.isInfoEnabled()) {
					log.info(logTag + "Database synchronization completed :D");
				}

				setInitReady(true);
				setState(IRCDDBAppState.Standby);
			}
			break;

		case Standby:
			if (!getSendQ().isPresent()) {
				setState(IRCDDBAppState.DisconnectFromDB);
			}

			AnnounceInformation(infoQTH, infoQTHLocker, "IRCDDB RPTRQTH: ");

			AnnounceInformation(infoURL, infoURLLocker, "IRCDDB RPTRURL: ");

			AnnounceInformation(infoQRG, infoQRGLocker, "IRCDDB RPTRQRG: ");

			AnnounceInformation(infoWatchdog, infoWatchdogLocker, "IRCDDB RPTRSW: ");

			break;

		case DisconnectFromDB:
			setState(IRCDDBAppState.WaitForNetworkStart);
			setTimer(0);
			setInitReady(false);
			break;

		}

		if (getState() != prevState)
			isStatusChangedToWebRemoteControl = true;

		queryTasksLocker.lock();
		try {
			if (getState() == IRCDDBAppState.Standby) {
				for (Iterator<IRCDDBQueryTask> it = queryTasks.iterator(); it.hasNext();) {
					final IRCDDBQueryTask queryTask = it.next();

					boolean taskExecuted = false;

					final Optional<IRCMessageQueue> q = getSendQ();

					switch(queryTask.getQueryState()) {
					case Processing:
						if (queryTask.isTimeoutActivityTime(TimeUnit.SECONDS.toMillis(5))) {
							queryTask.setQueryResult(IRCDDBQueryResult.Failed);
							queryTask.setQueryState(IRCDDBQueryState.Completed);

							workerExecutor.submit(new RunnableTask(getExceptionListener()) {
								@Override
								public void task() {
									eventListener.event(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());
								}
							});

							if(log.isWarnEnabled()) {
								log.warn(logTag + "Query task timeout = " + queryTask.getQueryType() + "(" + queryTask.getTaskid() + ")");
							}
						}
						break;

					case QueryAdded:
						if(!q.isPresent()) {
							if(log.isWarnEnabled())
								log.warn(logTag + "Failed query task, send queue is unavailable.");

							queryTask.setQueryResult(IRCDDBQueryResult.Failed);
							queryTask.setQueryState(IRCDDBQueryState.Completed);

							dispatchEvent(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());

							taskExecuted = true;
						}
						else if (queryTask.getQueryType() == IRCDDBQueryType.FindUser) {
							final String srv = getCurrentServer();

							queryTask.updateActivityTime();

							final String userCallsign = queryTask.getQueryCallsign().replace(" ", "_");

							if (srv.length() > 0 && getState().getStateNumber() >= 6 && q.isPresent()) {

								final IRCMessage m = new IRCMessage(srv, "FIND " + userCallsign);
								q.get().putMessage(m);

								queryTask.setQueryState(IRCDDBQueryState.Processing);

								taskExecuted = true; //処理は1タスクづつ
							}
							else {
								queryTask.setQueryResult(IRCDDBQueryResult.Failed);
								queryTask.setQueryState(IRCDDBQueryState.Completed);

								dispatchEvent(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());
							}
						}
						else if (queryTask.getQueryType() == IRCDDBQueryType.SendHeard) {
							queryTask.updateActivityTime();

							String my = queryTask.getMyCallsign();
							String myext = queryTask.getMyCallsignAdd();
							String ur = queryTask.getYourCallsign();
							String r1 = queryTask.getRepeaterCallsign();
							String r2 = queryTask.getGatewayCallsign();
							String dest = queryTask.getDestination();
							byte flag1 = queryTask.getFlag1();
							byte flag2 = queryTask.getFlag2();
							byte flag3 = queryTask.getFlag3();
							String tx_msg = queryTask.getTxMessage();
							String tx_stats = queryTask.getTxStatus();

							final Pattern nonValid = Pattern.compile("[^A-Z0-9/]");

							my = nonValid.matcher(my).replaceAll("_");
							myext = nonValid.matcher(myext).replaceAll("_");
							ur = nonValid.matcher(ur).replaceAll("_");
							r1 = nonValid.matcher(r1).replaceAll("_");
							r2 = nonValid.matcher(r2).replaceAll("_");
							dest = nonValid.matcher(dest).replaceAll("_");

							final boolean statsMsg = (tx_stats.length() > 0);

							final String srv = getCurrentServer();

							if (srv.length() > 0 && getState().getStateNumber() >= 6 && q.isPresent()) {
								final StringBuilder cmd = new StringBuilder("UPDATE ");

								cmd.append(IRCUtils.getCurrentTime());

								cmd.append(" ");

								cmd.append(my);
								cmd.append(" ");
								cmd.append(r1);
								cmd.append(" ");
								if (!statsMsg) {
									cmd.append("0 ");
								}
								cmd.append(r2);
								cmd.append(" ");
								cmd.append(ur);
								cmd.append(" ");

								final String flags = String.format("%02X %02X %02X", flag1, flag2, flag3);

								cmd.append(flags);
								cmd.append(" ");
								cmd.append(myext);

								if (statsMsg) {
									cmd.append(" # ");
									cmd.append(tx_stats);
								} else {
									cmd.append(" 00 ");
									cmd.append(dest);

									if (tx_msg.length() == 20) {
										cmd.append(" ");
										cmd.append(tx_msg);
									}
								}

								final IRCMessage m = new IRCMessage(srv, cmd.toString());

								q.get().putMessage(m);

								queryTask.setQueryResult(IRCDDBQueryResult.Success);
								queryTask.setQueryState(IRCDDBQueryState.Completed);

								dispatchEvent(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());

								taskExecuted = true;
							}
						}
						break;

					case Completed:
					case Unknown:
						break;
					default:
						break;
					}

					if(taskExecuted) {break;}
				}
			}

			for (Iterator<IRCDDBQueryTask> it = queryTasks.iterator(); it.hasNext();) {
				final IRCDDBQueryTask queryTask = it.next();

				if (queryTask.isTimeoutActivityTime(TimeUnit.SECONDS.toMillis(60))) {
					it.remove();
				}

			}
		} finally {
			queryTasksLocker.unlock();
		}

		//ダッシュボードに変更を通知
		if(
			isStatusChangedToWebRemoteControl &&
			webRemoteControlNotifyIntervalTimeKeeper.isTimeout(5, TimeUnit.SECONDS)
		) {
			isStatusChangedToWebRemoteControl = false;
			webRemoteControlNotifyIntervalTimeKeeper.updateTimestamp();

			notifyStatusChangedToWebRemoteControlService();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {

	}

	private String processNotFound(String msg) {
		String retval = null;

		int tableID = 0;
		String tk;

		Scanner tkz = new Scanner(msg);
		try {
			if (!tkz.hasNext())
				return null;

			tk = tkz.next();

			if (tablePattern.matcher(tk).matches()) {
				try {
					tableID = Integer.parseInt(tk);
				} catch (NumberFormatException ex) {
					return null;
				}

				if ((tableID < 0) || (tableID >= numberOfTables)) {
					if(log.isTraceEnabled())
						log.trace(logTag + "Invalid table ID " + tableID);

					return null;
				}

				if (!tkz.hasNext())
					return null;
				else
					tk = tkz.next();
			}

			if (tableID == 0) {
				if (!dbPattern.matcher(tk).matches())
					return null;

				retval = tk;
			}
		} finally {
			if (tkz != null) {
				tkz.close();
			}
		}

		return retval;
	}

	private void processUpdate(String msg) {
		int tableID = 0;
		String tk;

		Scanner tkz = new Scanner(msg);
		try {
			if (!tkz.hasNext())
				return;
			else
				tk = tkz.next();

			if (tablePattern.matcher(tk).matches()) {
				try {
					tableID = Integer.parseInt(tk);
				} catch (NumberFormatException ex) {
					if(log.isTraceEnabled())
						log.trace(logTag + "Invalid tableID " + tk + ", can't converted to number.");

					return;
				}

				if ((tableID < 0) || (tableID >= numberOfTables)) {
					if(log.isTraceEnabled())
						log.trace(logTag + "Invalid table ID " + tableID);

					return;
				}

				if (!tkz.hasNext())
					return;
				else
					tk = tkz.next();
			}

			if (datePattern.matcher(tk).matches()) {
				if (!tkz.hasNext())
					return;

				String timeToken = tkz.next();

				if (!timePattern.matcher(timeToken).matches())
					return;

				final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				df.setTimeZone(TimeZone.getTimeZone("UTC"));

				Date dt;
				try {
					dt = df.parse(tk + " " + timeToken);
				} catch (ParseException ex) {
					if(log.isTraceEnabled())
						log.trace(logTag + "Invalid datetime format " + tk + " " + timeToken + ", Can't convert to datetime.");

					return;
				}

				latestTimesLocker.lock();
				try {
					final Date latest = getLatestEntryTime(tableID);
					if(latest.before(dt)) {latestTimes.put(tableID, dt);}
				}finally {
					latestTimesLocker.unlock();
				}

				if (
					tableID == IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable.getTableID() ||
					tableID == IRCDDBTableID.UserVSAreaRepeaterTable.getTableID()
				) {
					if (!tkz.hasNext())
						return;

					final String key = tkz.next();

					if (!dbPattern.matcher(key).matches())
						return;

					if (!tkz.hasNext())
						return;

					final String value = tkz.next();

					if (!dbPattern.matcher(value).matches())
						return;

					if(log.isTraceEnabled())
						log.trace(logTag + "[UPDATE] TABLE " + tableID + " " + key + " " + value + " " + df.format(dt));

					isStatusChangedToWebRemoteControl = true;

					if (tableID == IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable.getTableID()) {
						database.updateRepeater(dt, key, value);
					}
					else if (tableID == IRCDDBTableID.UserVSAreaRepeaterTable.getTableID()) {
						updateUserTable(dt, key, value);
					}
				}
			}
		} finally {
			if (tkz != null) {
				tkz.close();
			}
		}
	}

	private void updateUserTable(final Date recordTime, final String key, final String value) {
		final String userCallsign =
			DSTARUtils.formatFullLengthCallsign(
				key.replace('_', ' ').toUpperCase(Locale.ENGLISH)
			);
		final String areaRepeaterCallsign =
			DSTARUtils.formatFullLengthCallsign(
				value.replace('_', ' ').toUpperCase(Locale.ENGLISH)
			);

		database.updateUser(recordTime, userCallsign, areaRepeaterCallsign);

		if(isInitReady()) {
			Optional<IRCDDBAppRepeaterEntry> dbRepeaterResult =
				database.findRepeater(areaRepeaterCallsign);

			boolean found = false;
			String zoneRepeaterCallsign = DSTARDefines.EmptyLongCallsign;
			String gatewayAddress = "";

			if(dbRepeaterResult.isPresent()) {
				final IRCDDBAppRepeaterEntry o = dbRepeaterResult.get();
				zoneRepeaterCallsign = o.getZoneRepeaterCallsign();

				//ip_addr = getIPAddress(o.getZoneRepeaterCallsign());

				final Optional<IRCDDBAppRepeaterIPEntry> dbIP =
					database.findIP(o.getZoneRepeaterCallsign());
				gatewayAddress = dbIP.isPresent() ? dbIP.get().getIpAddress() : null;

				found = gatewayAddress != null;
			}

			queryTasksLocker.lock();
			try {
				for (IRCDDBQueryTask queryTask : queryTasks) {
					if (queryTask.getQueryState() == IRCDDBQueryState.Processing &&
						queryTask.getQueryCallsign().equals(userCallsign)
					) {
						if (found) {
							queryTask.setDataTimestamp(recordTime);
							queryTask.setRepeaterCallsign(areaRepeaterCallsign);
							queryTask.setGatewayCallsign(zoneRepeaterCallsign);
							queryTask.setYourCallsign(userCallsign);
							queryTask.setGatewayAddress(gatewayAddress);

							queryTask.updateActivityTime();
							queryTask.setQueryResult(IRCDDBQueryResult.Success);
						}
						else {
							queryTask.setQueryResult(IRCDDBQueryResult.NotFound);
						}

						queryTask.setQueryState(IRCDDBQueryState.Completed);

						dispatchEvent(IRCDDBClientEvent.TaskComplete, queryTask.getTaskid());

						break;
					}
				}
			} finally {
				queryTasksLocker.unlock();
			}
		}
	}

	private boolean findServerUser(final String debugServerUser) {
		boolean found = false;

		loginUsersLocker.lock();
		try {
			if(!"".equals(debugServerUser)) {
				for (IRCDDBAppLoginUserEntry u : loginUsers.values()) {
					if(!u.getNick().equals(getMyNick()) && u.isOp() && u.getNick().equals(debugServerUser)) {
						setCurrentServer(u.getNick());
						found = true;
						break;
					}
				}

				if(!found && log.isWarnEnabled())
					log.warn(logTag + "Debug server user " + debugServerUser + " is not found.");
			}

			if (found) {return true;}

			final List<IRCDDBAppLoginUserEntry> serverUsers =
				Stream.of(loginUsers.values())
				.filter(new Predicate<IRCDDBAppLoginUserEntry>() {
					@Override
					public boolean test(IRCDDBAppLoginUserEntry u) {
						return u.getNick().startsWith("s-") && u.isOp() && !getMyNick().equals(u.getNick());
					}
				}).toList();

			if(serverUsers.isEmpty()) {return false;}

			final Random r = new Random();
			final int randomUserIndex = serverUsers.size() >= 2 ? r.nextInt(serverUsers.size()) : 0;
			final int userIndex = serverUsers.size() > randomUserIndex ? randomUserIndex : 0;
			final IRCDDBAppLoginUserEntry serverUser = serverUsers.get(userIndex);
			setCurrentServer(serverUser.getNick());

			found = true;

		} finally {
			loginUsersLocker.unlock();
		}

		return found;
	}

	private String getTableIDString(int tableID, boolean spaceBeforeNumber) {
		if (tableID == 0) {
			return "";
		} else if ((tableID > 0) && (tableID < numberOfTables)) {
			if (spaceBeforeNumber) {
				return String.format(" %d", tableID);
			} else {
				return String.format("%d ", tableID);
			}
		} else {
			return " TABLE_ID_OUT_OF_RANGE ";
		}
	}

	private void AnnounceInformation(Map<String, IRCDDBAnnounceTask> tasks, Lock tasksLock, String header) {
		assert tasks != null && tasksLock != null && header != null;

		tasksLock.lock();
		try {
			for (Iterator<IRCDDBAnnounceTask> it = tasks.values().iterator(); it.hasNext();) {
				final IRCDDBAnnounceTask task = it.next();

				if (task.isAnnounceTime()) {
					it.remove();

					final IRCMessage m = new IRCMessage(getCurrentServer(), header + task.getMessage());

					getSendQ().ifPresent(new Consumer<IRCMessageQueue>() {
						@Override
						public void accept(IRCMessageQueue q) {
							q.putMessage(m);
						}
					});
				}
			}
		} finally {
			tasksLock.unlock();
		}
	}

	private static boolean needsDatabaseUpdate(int tableID) {
		return
			tableID == IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable.getTableID() ||
			tableID == IRCDDBTableID.UserVSAreaRepeaterTable.getTableID();
	}

	private String getLastEntryTime(int tableID) {
		final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		final Date latest = getLatestEntryTime(tableID);

		return df.format(latest);
	}

	private Date getLatestEntryTime(final int tableID) {

		latestTimesLocker.lock();
		try {
			Date latest = latestTimes.get(tableID);
			if(latest == null) {
				long requestTime = 0;
				if(tableID == IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable.getTableID())
					requestTime = TimeUnit.DAYS.toMillis(30 * 12 * 10);
				else if(tableID == IRCDDBTableID.UserVSAreaRepeaterTable.getTableID())
					requestTime = TimeUnit.DAYS.toMillis(30);
				else
					requestTime = 0;

				latest = new Date(System.currentTimeMillis() - requestTime);

				latestTimes.put(tableID, latest);
			}

			return latest;
		}finally {
			latestTimesLocker.unlock();
		}
	}

	private void updateLatestTimesFromDatabase() {
		latestTimesLocker.lock();
		try {
			for(final IRCDDBTableID tableID : IRCDDBTableID.values()) {
				switch(tableID) {
				case AreaRepeaterVSZoneRepeaterTable:
					database.findRepeaterLatest()
					.ifPresent(new Consumer<IRCDDBAppRepeaterEntry>() {
						@Override
						public void accept(IRCDDBAppRepeaterEntry t) {
							latestTimes.put(
								IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable.getTableID(),
								t.getLastChanged()
							);
						}
					});
					break;

				case UserVSAreaRepeaterTable:
					database.findUserLatest()
					.ifPresent(new Consumer<IRCDDBAppRepeaterUserEntry>() {
						@Override
						public void accept(IRCDDBAppRepeaterUserEntry t) {
							latestTimes.put(
								IRCDDBTableID.UserVSAreaRepeaterTable.getTableID(),
								t.getUpdateTime()
							);
						}
					});
					break;

				case ZoneRepeaterVSIPAddressTable:
					database.findIPLatest()
					.ifPresent(new Consumer<IRCDDBAppRepeaterIPEntry>() {
						@Override
						public void accept(IRCDDBAppRepeaterIPEntry t) {
							latestTimes.put(
								IRCDDBTableID.UserVSAreaRepeaterTable.getTableID(),
								t.getUpdateTime()
							);
						}
					});
					break;

				default:
					break;
				}
			}
		}finally {
			latestTimesLocker.unlock();
		}
	}

	private void notifyStatusChangedToWebRemoteControlService() {
		final WebRemoteControlService service = getWebRemoteControlService();

		if (service != null) {
			workerExecutor.submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					service.notifyRoutingServiceStatusChanged(IrcDDBClient.this.service);
				}
			});
		}
	}

	private void dispatchEvent(final IRCDDBClientEvent eventType, final UUID id) {
		workerExecutor.submit(new RunnableTask(getExceptionListener()) {
			@Override
			public void task() {
				eventListener.event(eventType, id);
			}
		});
	}
}