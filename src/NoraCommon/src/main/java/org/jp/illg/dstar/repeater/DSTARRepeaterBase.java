package org.jp.illg.dstar.repeater;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.routing.RoutingServiceManager;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.function.Consumer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class DSTARRepeaterBase extends ThreadBase
implements DSTARRepeater, WebRemoteControlRepeaterHandler {

	/**
	 * 通常時時処理ループ間隔ミリ秒
	 */
	@Getter(AccessLevel.PROTECTED)
	private final long processLoopPeriodMillisNormalDefault = 20L;

	/**
	 * スリープ時処理ループ間隔ミリ秒
	 */
	@Getter(AccessLevel.PROTECTED)
	private final long processLoopPeriodMillisSleepDefault = 1000L;

	private static class EventEntry{
		@Getter
		private final DStarRepeaterEvent event;
		@Getter
		private final Object attachment;

		public EventEntry(final DStarRepeaterEvent event, final Object attachment) {
			this.event = event;
			this.attachment = attachment;
		}
	}

	private String logTag;

	private DSTARGateway gateway;

	private String repeaterCallsign;

	@Getter(AccessLevel.PROTECTED)
	private final ExecutorService workerExecutor;

	private String linkedReflectorCallsign;

	private RoutingService routingService;

	private boolean routingServiceFixed;

	private boolean statusChanged;

	@Getter
	@Setter
	private String lastHeardCallsign;

	@Getter
	private final UUID systemID;

	@Getter
	@Setter
	/**
	 * ルーティングサービスを使用するか否か
	 */
	private boolean useRoutingService;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	/**
	 * DVシンプレックス/ターミナルモードの際にRPT1/RPT2を書き換える処理を許可する
	 */
	private boolean allowDIRECT;

	@Getter(AccessLevel.PROTECTED)
	/**
	 * DVシンプレックス/ターミナルモードの際に使用するコールサイン
	 */
	private final List<String> directMyCallsigns;

	@Getter
	@Setter
	/**
	 * GW透過モード(未実装)
	 */
	private boolean transparentMode;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	/**
	 * このレピータからゲート超えをした際にリフレクタに接続していた場合は切断を希望するフラグ
	 */
	private boolean autoDisconnectFromReflectorOnTxToG2Route;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int autoDisconnectFromReflectorOutgoingUnusedMinutes;

	@Getter
	@Setter
	private boolean allowIncomingConnection;

	@Getter
	@Setter
	private boolean allowOutgoingConnection;

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
	private double agl;

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
	private double range;

	@Getter
	@Setter
	private double frequency;

	@Getter
	@Setter
	private double frequencyOffset;

	@Getter
	@Setter
	/**
	 * ウェブ遠隔サービス(ダッシュボード)
	 */
	private WebRemoteControlService WebRemoteControlService;

	@Getter(AccessLevel.PROTECTED)
	private final List<RepeaterModem> repeaterModems;

	@Getter(AccessLevel.PROTECTED)
	private final SocketIO socketIO;

	private final Timer watchdogTimekeeper;

	private long requestProcessLoopIntervalTimeMillis;
	private final Timer processLoopIntervalTimeDelayTimer;

	private final Queue<DSTARPacket> toInetPackets;
	private final Lock toInetPacketsLocker;

	protected final EventListener<DStarRepeaterEvent> eventListener;

	private final TaskQueue<EventEntry, Boolean> eventQueue;

	private final Consumer<EventEntry> eventDispacher = new Consumer<EventEntry>() {
		@Override
		public void accept(EventEntry e) {
			eventListener.event(e.getEvent(), e.getAttachment());
		}
	};

	private DSTARRepeaterBase(
		@NonNull UUID systemID,
		@NonNull ThreadUncaughtExceptionListener exceptionListener,
		@NonNull String workerThreadName,
		@NonNull ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		@NonNull SocketIO socketIO
	) {
		super(exceptionListener, workerThreadName);

		logTag = DSTARRepeaterBase.class.getSimpleName() + " : ";

		this.systemID = systemID;

		setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		this.workerExecutor = workerExecutor;
		this.eventListener = eventListener;
		this.socketIO = socketIO;

		eventQueue = new TaskQueue<>(getWorkerExecutor());

		repeaterModems = new LinkedList<>();

		toInetPacketsLocker = new ReentrantLock();
		toInetPackets = new LinkedList<>();

		final Random watchdogRand = new Random();
		watchdogTimekeeper = new Timer(61 + watchdogRand.nextInt(60), TimeUnit.SECONDS);

		lastHeardCallsign = DSTARDefines.EmptyLongCallsign;

		requestProcessLoopIntervalTimeMillis = -1;
		processLoopIntervalTimeDelayTimer = new Timer();

		setTransparentMode(false);

		setAllowDIRECT(false);
		directMyCallsigns = new ArrayList<>(10);

		setUseRoutingService(true);

		setAutoDisconnectFromReflectorOnTxToG2Route(true);
		setAutoDisconnectFromReflectorOutgoingUnusedMinutes(0);

		setScope(AccessScope.Unknown);
		setLatitude(0.0d);
		setLongitude(0.0d);
		setAgl(0.0d);
		setDescription1("");
		setDescription2("");
		setUrl("");
		setName("");
		setLocation("");
		setRange(0.0d);
		setFrequency(0.0d);
		setFrequencyOffset(0.0d);
	}

	protected DSTARRepeaterBase(
		@NonNull final Class<?> repeaterClass,
		@NonNull final UUID systemID,
		@NonNull DSTARGateway gateway,
		@NonNull String repeaterCallsign,
		@NonNull ExecutorService workerExecutor,
		@NonNull final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		this(systemID, gateway, repeaterClass.getSimpleName(), workerExecutor, eventListener, socketIO);

		setGateway(gateway);

		setRepeaterCallsign(repeaterCallsign);
	}

	@Override
	public void wakeupRepeaterWorker() {
		super.wakeupProcessThread();
	}

	@Override
	public RepeaterTypes getRepeaterType() {
		return RepeaterTypes.getTypeByClassName(this.getClass().getName());
	}

	@Override
	public boolean start() {
		if(getWebRemoteControlService() != null && !initializeWebRemote(getWebRemoteControlService())) {
			if(log.isErrorEnabled())
				log.error("Failed to initialize web remote in repeater " + getRepeaterCallsign() + ".");

			return false;
		}

		if(!super.start()) {return false;}

		for(RepeaterModem modem : getRepeaterModems()) {
			if(modem != null && !modem.start()) {
				stop();
				return false;
			}
		}

		return true;
	}

	@Override
	public void stop() {
		super.stop();

		for(RepeaterModem modem : getRepeaterModems()) {
			if(modem != null) {modem.stop();}
		}

		return;
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		return ThreadProcessResult.NoErrors;
	}

	public abstract boolean initializeWebRemote(final WebRemoteControlService service);

	@Override
	public WebRemoteControlRepeaterHandler getWebRemoteControlHandler() {
		return this;
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {
		if(properties == null) {return false;}

		//レピータコールサイン設定
		String repeaterCallsign = properties.getCallsign();
		if(DSTARUtils.isValidCallsignFullLength(repeaterCallsign))
			setRepeaterCallsign(repeaterCallsign);
		else {
			if(log.isWarnEnabled())
				log.warn("Failed set to repeater callsign. Illegal callsign " + properties.getCallsign() + ".");

			return false;
		}

		//デフォルトルーティングサービス設定
		String routingServiceTypeName = properties.getDefaultRoutingService();
		RoutingServiceTypes routingServiceType = null;
		if(routingServiceTypeName != null)
			routingServiceType = RoutingServiceTypes.getTypeByTypeName(routingServiceTypeName);
		else
			routingServiceType = RoutingServiceTypes.Unknown;

		RoutingService routingService = getRoutingService(routingServiceType);
		if(routingService != null) {
			RoutingServiceManager.changeRoutingService(this, routingService);
		}
		else {
			if(log.isWarnEnabled()) {
				log.warn(
					"Could not set default routing service(" +
					(routingServiceTypeName != null ? routingServiceTypeName : "null") + ") to repeater " + getRepeaterCallsign() + "."
				);
			}

			//指定されたルーティングサービスが見つからないので、適当なルーティングサービスがあれば割り当てる
			List<RoutingService> routingServices = getRoutingServiceAll();
			for(final RoutingService targetService : routingServices) {
				if(RoutingServiceManager.changeRoutingService(this, targetService))
					break;
			}
		}

		//ルーティングサービス固定設定
		Boolean routingServiceFixed =
				PropertyUtils.getBoolean(properties.getRoutingServiceFixed());
		if(routingServiceFixed != null)
			setRoutingServiceFixed(routingServiceFixed);
		else {
			if(properties.getRoutingServiceFixed() != null && !"".equals(properties.getRoutingServiceFixed())) {
				if(log.isWarnEnabled()) {
					log.warn(
						"Illegal property value " + properties.getRoutingServiceFixed() +
						"? [PropertyName=RoutingServiceFixed/Repeater=" + getRepeaterCallsign() + "]"
					);
				}
			}
			setRoutingServiceFixed(false);
		}

		setAllowDIRECT(properties.isAllowDIRECT());
		if(properties.getDirectMyCallsigns() != null) {
			directMyCallsigns.addAll(properties.getDirectMyCallsigns());
		}

		setUseRoutingService(properties.isUseRoutingService());
		setAutoDisconnectFromReflectorOnTxToG2Route(
			properties.isAutoDisconnectFromReflectorOnTxToG2Route()
		);
		setAutoDisconnectFromReflectorOutgoingUnusedMinutes(
			properties.getAutoDisconnectFromReflectorOutgoingUnusedMinutes()
		);
		setAllowIncomingConnection(properties.isAllowIncomingConnection());
		setAllowOutgoingConnection(properties.isAllowOutgoingConnection());

		final AccessScope scope =
			AccessScope.getTypeByTypeNameIgnoreCase(properties.getScope());
		setScope(scope);
		setLatitude(properties.getLatitude());
		setLongitude(properties.getLongitude());
		setAgl(properties.getAgl());
		setDescription1(properties.getDescription1());
		setDescription2(properties.getDescription2());
		setUrl(properties.getUrl());
		setName(properties.getName());
		setLocation(properties.getLocation());
		setRange(properties.getRange());
		setFrequency(properties.getFrequency());
		setFrequencyOffset(properties.getFrequencyOffset());

		return true;
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		if(properties == null) {return null;}

		String repeaterCallsign = getRepeaterCallsign();
		if(repeaterCallsign == null) {repeaterCallsign = "";}
		properties.setCallsign(repeaterCallsign);

		for(RepeaterModem modem : getRepeaterModems()) {
			ModemProperties modemProperties = new ModemProperties();

			String modemClassName;
			if(modem != null)
				modemClassName = modem.getClass().getName();
			else
				modemClassName = "";

			modemProperties.setType(ModemTypes.getTypeByClassName(modemClassName).getTypeName());

			properties.addModemProperties(modemProperties);
		}


		return properties;
	}

	@Override
	public DSTARPacket readPacket() {
		toInetPacketsLocker.lock();
		try {
			if(!this.toInetPackets.isEmpty())
				return toInetPackets.poll();
			else
				return null;
		}finally {
			toInetPacketsLocker.unlock();
		}
	}

	@Override
	public boolean hasReadPacket() {
		toInetPacketsLocker.lock();
		try {
			return !toInetPackets.isEmpty();
		}finally {
			toInetPacketsLocker.unlock();
		}
	}

	protected boolean addRepeaterModem(RepeaterModem modem) {
		if(modem == null) {return false;}

		return getRepeaterModems().add(modem);
	}

	protected boolean addToInetPacket(final DSTARPacket packet) {
		boolean isSuccess = false;
		final DSTARPacket copyPacket = packet.clone();

		toInetPacketsLocker.lock();
		try {
			isSuccess = toInetPackets.add(copyPacket);
		}finally {
			toInetPacketsLocker.unlock();
		}

		if(isSuccess && eventListener != null) {
			eventQueue.addEventQueue(
				eventDispacher,
				new EventEntry(DStarRepeaterEvent.ReceivePacket, null),
				getExceptionListener()
			);
		}

		return isSuccess;
	}

	@Override
	public String getRepeaterCallsign() {
		return repeaterCallsign;
	}

	@Override
	public String getLinkedReflectorCallsign() {
		return linkedReflectorCallsign;
	}

	@Override
	public void setLinkedReflectorCallsign(String linkedReflectorCallsign) {
		this.linkedReflectorCallsign = linkedReflectorCallsign;
	}

	@Override
	public RoutingService getRoutingService() {
		return this.routingService;
	}

	@Override
	public void setRoutingService(RoutingService routingService) {
		this.routingService = routingService;
	}

	@Override
	public ThreadProcessResult process() {

		processWatchdog();

		final ThreadProcessResult processResult = processRepeater();

		if(statusChanged) {
			statusChanged = false;

			final WebRemoteControlService service = getWebRemoteControlService();
			if(isEnableWebRemoteControl() && service != null)
				service.notifyRepeaterStatusChanged(getWebRemoteControlHandler());
		}

		processInterval();

		return processResult;
	}

	@Override
	public List<RepeaterModem> getModems() {
		return new ArrayList<RepeaterModem>(getRepeaterModems());
	}

	@Override
	public final RepeaterStatusReport getRepeaterStatusReport(){
		RepeaterStatusReport report = new RepeaterStatusReport();

		report.setRepeaterCallsign(String.valueOf(getRepeaterCallsign()));
		report.setLinkedReflectorCallsign(
			getLinkedReflectorCallsign() != null ? getLinkedReflectorCallsign() : ""
		);
		report.setRoutingService(
			getRoutingService() != null ? getRoutingService().getServiceType() : RoutingServiceTypes.Unknown
		);
		report.setRepeaterType(getRepeaterType());

		report.setLastHeardCallsign(getLastHeardCallsign());

		report.setFrequency(getFrequency());
		report.setFrequencyOffset(getFrequencyOffset());
		report.setRange(getRange());
		report.setLatitude(getLatitude());
		report.setLongitude(getLongitude());
		report.setAgl(getAgl());
		report.setDescription1(getDescription1());
		report.setDescription2(getDescription2());
		report.setUrl(getUrl());
		report.setName(getName());
		report.setLocation(getLocation());
		report.setScope(getScope());

		for(final RepeaterModem modem : getModems())
			report.getModemReports().add(modem.getStatusReport());

		return getRepeaterStatusReportInternal(report);
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGateway().getGatewayCallsign(),
			getRepeaterCallsign()
		);
	}

	@Override
	public RepeaterStatusData createStatusData() {
		final RepeaterStatusData status = createStatusDataInternal();
		if(status == null)
			throw new NullPointerException("Status data must not null.");

		status.setRepeaterType(getRepeaterType());
		status.setRepeaterCallsign(getRepeaterCallsign());
		status.setWebSocketRoomId(getWebSocketRoomId());
		status.setGatewayCallsign(getGateway().getGatewayCallsign());
		status.setLastheardCallsign(getLastHeardCallsign());
		status.setLinkedReflectorCallsign(getLinkedReflectorCallsign());
		final RoutingService routingService = getRoutingService();
		status.setRoutingService(
			routingService != null ? routingService.getServiceType() : RoutingServiceTypes.Unknown
		);
		status.setRoutingServiceFixed(isRoutingServiceFixed());
		status.setUseRoutingService(isUseRoutingService());
		status.setAllowDIRECT(isAllowDIRECT());
		status.setTransparentMode(isTransparentMode());
		status.setAutoDisconnectFromReflectorOnTxToG2Route(isAutoDisconnectFromReflectorOnTxToG2Route());
		status.setScope(getScope());
		status.setLatitude(getLatitude());
		status.setLongitude(getLongitude());
		status.setAgl(getAgl());
		status.setDescriotion1(getDescription1());
		status.setDescription2(getDescription2());
		status.setUrl(getUrl());
		status.setName(getName());
		status.setLocation(getLocation());
		status.setRange(getRange());
		status.setFrequency(getFrequency());
		status.setFrequencyOffset(getFrequencyOffset());

		return status;
	}

	@Override
	public Class<? extends RepeaterStatusData> getStatusDataType() {
		return getStatusDataTypeInternal();
	}

	public boolean isRoutingServiceFixed() {
		return this.routingServiceFixed;
	}

	public void setRoutingServiceFixed(boolean routingServiceFixed) {
		this.routingServiceFixed = routingServiceFixed;
	}

	protected DSTARGateway getGateway() {
		return gateway;
	}

	protected RoutingService getRoutingService(RoutingServiceTypes routingServiceType) {
		return RoutingServiceManager.getService(getSystemID(), routingServiceType);
	}

	protected void notifyStatusChanged() {
		statusChanged = true;
	}

	protected boolean isEnableWebRemoteControl() {
		return getWebRemoteControlService() != null;
	}

	protected List<RoutingService> getRoutingServiceAll() {
		return RoutingServiceManager.getServices(getSystemID());
	}

	protected long getProcessLoopPeriodMillisSleep() {
		return processLoopPeriodMillisSleepDefault;
	}

	protected long getProcessLoopPeriodMillisNormal() {
		return processLoopPeriodMillisNormalDefault;
	}

	protected abstract RepeaterStatusData createStatusDataInternal();
	protected abstract Class<? extends RepeaterStatusData> getStatusDataTypeInternal();

	protected abstract RepeaterStatusReport getRepeaterStatusReportInternal(RepeaterStatusReport report);

	protected abstract ThreadProcessResult processRepeater();

	protected abstract boolean isCanSleep();

	protected abstract boolean isAutoWatchdog();

	private void processInterval() {
		//スリープに入れるのであれば、ループ時間を長くする
		final long requestIntervalTimeMillis =
			isCanSleep() ? getProcessLoopPeriodMillisSleep() : getProcessLoopPeriodMillisNormal();
		if(requestIntervalTimeMillis > 0) {
			final long currentIntervalTimeMillis = getCurrentProcessIntervalTimeMillis();
			long newIntervalTimeMillis = -1;

			if(requestIntervalTimeMillis < currentIntervalTimeMillis) {
				newIntervalTimeMillis = requestIntervalTimeMillis;
				requestProcessLoopIntervalTimeMillis = requestIntervalTimeMillis;
			}
			else if(requestIntervalTimeMillis > currentIntervalTimeMillis){
				if(requestIntervalTimeMillis != requestProcessLoopIntervalTimeMillis) {
					processLoopIntervalTimeDelayTimer.updateTimestamp();

					requestProcessLoopIntervalTimeMillis = requestIntervalTimeMillis;
				}
				else if(processLoopIntervalTimeDelayTimer.isTimeout(1, TimeUnit.SECONDS)) {
					newIntervalTimeMillis = requestProcessLoopIntervalTimeMillis;
				}
			}

			if(newIntervalTimeMillis > 0 && currentIntervalTimeMillis != newIntervalTimeMillis) {
				setProcessLoopIntervalTime(newIntervalTimeMillis, TimeUnit.MILLISECONDS);

				if(log.isDebugEnabled())
					log.debug(logTag + "Interval time changed " + currentIntervalTimeMillis + " -> " + newIntervalTimeMillis + "(ms)");
			}
		}
	}

	private void processWatchdog() {
		if(
			isAutoWatchdog() &&
			watchdogTimekeeper.isTimeout(60, TimeUnit.SECONDS)
		) {
			watchdogTimekeeper.updateTimestamp();

			final StringBuffer sb = new StringBuffer();
			sb.append(this.getClass().getSimpleName().toLowerCase(Locale.ENGLISH));
			if(!getRepeaterModems().isEmpty()) {
				sb.append("_");

				for(final Iterator<RepeaterModem> it = getRepeaterModems().iterator(); it.hasNext();) {
					final RepeaterModem modem = it.next();

					sb.append(modem.getModemType().getTypeName().toLowerCase(Locale.ENGLISH));
					if(it.hasNext()) {sb.append("-");}
				}
			}

			getGateway().kickWatchdogFromRepeater(getRepeaterCallsign(), sb.toString());
		}
	}

	private void setGateway(DSTARGateway gateway) {
		assert gateway != null;
		if(gateway == null)
			throw new IllegalArgumentException();

		this.gateway = gateway;
	}

	private void setRepeaterCallsign(String repeaterCallsign) {
		assert DSTARUtils.isValidCallsignFullLength(repeaterCallsign);
		if(!DSTARUtils.isValidCallsignFullLength(repeaterCallsign))
			throw new IllegalArgumentException();

		this.repeaterCallsign = repeaterCallsign;
	}
}
