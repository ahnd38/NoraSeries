package org.jp.illg.dstar.repeater.modem;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlModemHandler;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class DStarRepeaterModemBase extends ThreadBase
implements RepeaterModem, WebRemoteControlModemHandler {

	/**
	 * 通常時時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisNormalDefault = 100L;

	/**
	 * スリープ時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisSleepDefault = 1000L;

	/**
	 * 音声送信時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisVoiceTransferDefault = 10L;

	/**
	 * レピータ向けパケットキューのパケット数制限
	 */
	private static final int readPacketLimit = 100;

	protected static enum ProcessIntervalMode {
		Normal,
		Sleep,
		VoiceTransfer,
		;
	}

	private static class EventEntry{
		@Getter
		private final DStarRepeaterModemEvent event;
		@Getter
		private final Object attachment;

		public EventEntry(final DStarRepeaterModemEvent event, final Object attachment) {
			this.event = event;
			this.attachment = attachment;
		}
	}

	private String logTag;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int modemId;

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter
	private boolean allowDIRECT;

	@Getter
	@Setter
	private AccessScope scope;

	@Getter
	private final ExecutorService workerExecutor;

	@Getter
	private final DSTARGateway gateway;

	@Getter
	private final DSTARRepeater repeater;

	@Getter
	private final SocketIO socketIO;

	@Getter
	private ModemTypes modemType;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private WebRemoteControlService webRemoteControlService;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String webSocketRoomId;

	@Getter
	@Setter(AccessLevel.PROTECTED)
	private ModemTransceiverMode transceiverMode;

	private boolean statusChanged;

	private long requestProcessLoopIntervalTimeMillis;
	private final Timer processLoopIntervalTimeDelayTimer;

	private static final Random modemIdRandom;

	private Queue<DSTARPacket> readPackets;
	private final Lock readPacketsLocker;

	@Getter(AccessLevel.PROTECTED)
	private final EventListener<DStarRepeaterModemEvent> eventListener;

	private final TaskQueue<EventEntry, Boolean> eventQueue;

	private final Function<EventEntry, Boolean> eventDispacher = new Function<EventEntry, Boolean>() {
		@Override
		public Boolean apply(EventEntry e) {
			eventListener.event(e.getEvent(), e.getAttachment());

			return true;
		}
	};

	public abstract boolean initializeWebRemoteControlInt(final WebRemoteControlService webRemoteControlService);

	static {
		modemIdRandom = new Random(System.currentTimeMillis() ^ 0x5243fdad);
	}

	protected DStarRepeaterModemBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		@NonNull ExecutorService workerExecutor,
		@NonNull final ModemTypes modemType,
		@NonNull final DSTARGateway gateway,
		@NonNull final DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		final SocketIO socketIO
	) {
		super(exceptionListener, workerThreadName);

		setModemId(generateModemId());

		logTag = modemType + "(" + getModemId() + ") : ";

		this.workerExecutor = workerExecutor;
		this.modemType = modemType;
		this.gateway = gateway;
		this.repeater = repeater;
		this.eventListener = eventListener;
		this.socketIO = socketIO;

		readPackets = null;
		readPacketsLocker = new ReentrantLock();

		eventQueue = new TaskQueue<>(getWorkerExecutor());

		statusChanged = false;

		requestProcessLoopIntervalTimeMillis = -1;
		processLoopIntervalTimeDelayTimer = new Timer();

		setRepeaterCallsign(DSTARDefines.EmptyLongCallsign);
		setGatewayCallsign(DSTARDefines.EmptyLongCallsign);
		setAllowDIRECT(false);
		setScope(AccessScope.Unknown);

		setWebRemoteControlService(null);
		setWebSocketRoomId("");
		setTransceiverMode(ModemTransceiverMode.HalfDuplex);
	}


	protected abstract ThreadProcessResult processModem();
	protected abstract boolean writePacketInternal(DSTARPacket packet);
	protected abstract boolean setPropertiesInternal(ModemProperties properties);

	protected abstract ModemStatusData createStatusDataInternal();
	protected abstract Class<? extends ModemStatusData> getStatusDataTypeInternal();

	protected abstract ModemStatusReport getStatusReportInternal(ModemStatusReport report);

	protected abstract ProcessIntervalMode getProcessLoopIntervalMode();

	@Override
	public final boolean setProperties(ModemProperties properties) {
		if(properties == null) {return false;}

		ModemTransceiverMode transceiverMode = ModemTransceiverMode.Unknown;
		if(
			properties.getTransceiverMode() != null &&
			isSupportTransceiverMode(properties.getTransceiverMode())
		) {
			transceiverMode = properties.getTransceiverMode();
		}
		else {
			transceiverMode = getDefaultTransceiverMode();

			if(
				(
					properties.getTransceiverMode() != null &&
					properties.getTransceiverMode() != ModemTransceiverMode.Unknown
				) &&
				log.isInfoEnabled()
			) {
				log.info(
					logTag +
					"This modem is not support transceiver mode = " + properties.getTransceiverMode() +
					", default transceiver mode " + getDefaultTransceiverMode() + " is set."
				);
			}
		}

		setTransceiverMode(transceiverMode);

		return setPropertiesInternal(properties);
	}

	@Override
	protected final ThreadProcessResult process() {
		final ThreadProcessResult result = processModem();

		if(statusChanged) {
			statusChanged = false;

			final WebRemoteControlService service = getWebRemoteControlService();
			if(isEnableWebRemoteControlService() && service != null)
				service.notifyModemStatusChanged(getWebRemoteControlHandler());
		}

		final long requestIntervalTimeMillis = getRequestProcessLoopIntervalTimeMillis();
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

		return result;
	}

	protected long getProcessLoopIntervalTimeMillisNormal() {
		return processLoopIntervalTimeMillisNormalDefault;
	}

	protected long getProcessLoopIntervalTimeMillisSleep() {
		return processLoopIntervalTimeMillisSleepDefault;
	}

	protected long getProcessLoopIntervalTimeMillisVoiceTransfer() {
		return processLoopIntervalTimeMillisVoiceTransferDefault;
	}

	@Override
	public final boolean writePacket(@NonNull DSTARPacket packet) {
		final boolean success = writePacketInternal(packet);

		wakeupProcessThread();

		return success;
	}

	@Override
	public final String initializeWebRemoteControl(@NonNull final WebRemoteControlService webRemoteControlService) {

		setWebRemoteControlService(webRemoteControlService);
		setWebSocketRoomId(
			WebSocketTool.formatRoomId(
				getGatewayCallsign(),
				getRepeaterCallsign(),
				getModemType().getTypeName(),
				String.valueOf(getModemId())
			)
		);

		return initializeWebRemoteControlInt(webRemoteControlService) ? getWebSocketRoomId() : null;
	}

	@Override
	public final ModemStatusReport getStatusReport() {
		final ModemStatusReport report = new ModemStatusReport();

		report.setModemId(getModemId());
		report.setModemType(getModemType());
		report.setScope(getScope());

		return getStatusReportInternal(report);
	}

	@Override
	public final ModemStatusData createStatusData() {
		final ModemStatusData status = createStatusDataInternal();
		if(status == null)
			throw new RuntimeException("Status data must not null.");

		status.setModemId(getModemId());
		status.setModemType(getModemType());
		status.setWebSocketRoomId(getWebSocketRoomId());
		status.setGatewayCallsign(getGatewayCallsign());
		status.setRepeaterCallsign(getRepeaterCallsign());
		status.setAllowDIRECT(isAllowDIRECT());
		status.setScope(getScope());

		return status;
	}

	@Override
	public final Class<? extends ModemStatusData> getStatusDataType(){
		return getStatusDataTypeInternal();
	}

	@Override
	public WebRemoteControlModemHandler getWebRemoteControlHandler() {
		return this;
	}

	@Override
	public boolean hasReadPacket() {
		boolean hasReadPacket = false;

		readPacketsLocker.lock();
		try {
			hasReadPacket = readPackets != null ? !readPackets.isEmpty() : false;
		}finally {
			readPacketsLocker.unlock();
		}

		return hasReadPacket;
	}

	@Override
	public DSTARPacket readPacket() {
		DSTARPacket packet = null;

		readPacketsLocker.lock();
		try {
			packet = readPackets != null ? readPackets.poll() : null;
		}finally {
			readPacketsLocker.unlock();
		}

		if(packet != null) {onReadPacket(packet);}

		return packet;
	}

	protected void onReadPacket(final DSTARPacket packet) {}

	protected boolean addReadPacket(final DSTARPacket packet) {
		if(packet == null) {return false;}

		boolean success = false;

		readPacketsLocker.lock();
		try {
			if(readPackets == null) {readPackets = new LinkedList<>();}
			while(readPackets.size() >= readPacketLimit) {readPackets.poll();}

			success = readPackets.add(packet.clone());
		}finally {
			readPacketsLocker.unlock();
		}

		if(success && eventListener != null) {
			eventQueue.addEventQueue(
				eventDispacher,
				new EventEntry(DStarRepeaterModemEvent.ReceivePacket, null),
				getExceptionListener()
			);
		}

		return success;
	}

	protected void notifyStatusChanged() {
		statusChanged = true;
	}

	protected boolean isEnableWebRemoteControlService() {
		return getWebRemoteControlService() != null;
	}

	protected int generateFrameID() {
		return DSTARUtils.generateFrameID();
	}

	private static int generateModemId() {
		return modemIdRandom.nextInt(0xFFFF) + 0x1;
	}

	private boolean isSupportTransceiverMode(ModemTransceiverMode targetMode) {
		boolean isSupport = false;

		for(final ModemTransceiverMode mode : getSupportedTransceiverModes()) {
			if(mode == targetMode) {
				isSupport = true;
				break;
			}
		}

		return isSupport;
	}

	private long getRequestProcessLoopIntervalTimeMillis() {
		final ProcessIntervalMode intervalMode = getProcessLoopIntervalMode();

		switch(intervalMode) {
		case Normal:
			return getProcessLoopIntervalTimeMillisNormal();

		case Sleep:
			return getProcessLoopIntervalTimeMillisSleep();

		case VoiceTransfer:
			return getProcessLoopIntervalTimeMillisVoiceTransfer();

		default:
			return -1;
		}
	}
}
