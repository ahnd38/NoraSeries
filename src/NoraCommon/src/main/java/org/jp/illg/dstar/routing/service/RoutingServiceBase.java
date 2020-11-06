package org.jp.illg.dstar.routing.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.GlobalIPInfo;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.reflector.protocol.ReflectorCommunicationServiceBase;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRoutingServiceHandler;
import org.jp.illg.dstar.service.web.model.RoutingServiceStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class RoutingServiceBase<T extends BufferEntry>
extends SocketIOHandlerWithThread<T>
implements RoutingService, WebRemoteControlRoutingServiceHandler {

	/**
	 * 通常時時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisNormalDefault = 100L;

	/**
	 * スリープ時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisSleepDefault = 1000L;


	@SuppressWarnings("unused")
	private final String logTag;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PROTECTED)
	private GlobalIPInfo globalIP;

	@Getter(AccessLevel.PROTECTED)
	private final UUID systemID;

	@Getter(AccessLevel.PROTECTED)
	private final DSTARGateway gateway;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private WebRemoteControlService webRemoteControlService;

	@Getter(AccessLevel.PROTECTED)
	private ExecutorService workerExecutor;

	private boolean statusChanged;

	private final ApplicationInformation<?> applicationVersion;

	@Getter(AccessLevel.PROTECTED)
	private final EventListener<RoutingServiceEvent> eventListener;

	public RoutingServiceBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		@NonNull final Class<?> processorClass,
		final SocketIO socketIO,
		@NonNull final Class<T> bufferEntryClass,
		@NonNull final HostIdentType hostIdentType
	) {
		super(exceptionListener, processorClass, socketIO, bufferEntryClass, hostIdentType);

		setProcessLoopIntervalTimeMillis(processLoopIntervalTimeMillisSleepDefault);

		logTag =
			this.getClass().getSimpleName() +
			"(" + ReflectorCommunicationServiceBase.class.getSimpleName() + ") : ";

		this.systemID = systemID;
		this.gateway = gateway;

		this.applicationVersion = applicationVersion;
		this.eventListener = eventListener;

		statusChanged = false;

		this.workerExecutor = workerExecutor;

		setGlobalIP(null);
		setWebRemoteControlService(null);
	}

	public RoutingServiceBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		@NonNull final Class<?> processorClass,
		@NonNull final Class<T> bufferEntryClass,
		@NonNull final HostIdentType hostIdentType
	) {
		this(
			systemID,
			exceptionListener,
			gateway,
			workerExecutor,
			applicationVersion,
			eventListener,
			processorClass, null, bufferEntryClass, hostIdentType
		);
	}

	@Override
	public final ThreadProcessResult processThread() {
		final ThreadProcessResult processResult = processRoutingService();

		if(statusChanged) {
			statusChanged = false;

			final WebRemoteControlService service = getWebRemoteControlService();
			if(isEnableWebRemoteControl() && service != null)
				service.notifyRoutingServiceStatusChanged(getWebRemoteControlHandler());
		}

		if(isCanSleep())
			setProcessLoopIntervalTimeMillis(processLoopIntervalTimeMillisSleepDefault);
		else
			setProcessLoopIntervalTimeMillis(processLoopIntervalTimeMillisNormalDefault);

		return processResult;
	}

	protected abstract boolean isCanSleep();

	@Override
	public boolean initializeWebRemoteControl(
		@NonNull WebRemoteControlService webRemoteControlService
	) {
		setWebRemoteControlService(webRemoteControlService);

		return initializeWebRemoteControlInternal(webRemoteControlService);
	}

	@Override
	public Optional<GlobalIPInfo> getGlobalIPAddress() {
		return Optional.ofNullable(getGlobalIP());
	}

	@Override
	public RoutingServiceStatusReport getRoutingServiceStatusReport() {
		final RoutingServiceStatusReport report =
			new RoutingServiceStatusReport(getServiceType(), getServerStatus());

		return report;
	}

	@Override
	public final String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGatewayCallsign(), getServiceType().getTypeName()
		);
	}

	@Override
	public final WebRemoteControlRoutingServiceHandler getWebRemoteControlHandler() {
		return this;
	}

	@Override
	public final RoutingServiceStatusData createStatusData() {
		final RoutingServiceStatusData status = createStatusDataInternal();

		status.setRoutingServiceType(getServiceType());
		status.setRoutingServiceStatus(getServerStatus());

		return status;
	}

	@Override
	public final org.jp.illg.dstar.routing.model.RoutingServiceStatusData getServiceStatus() {
		return new org.jp.illg.dstar.routing.model.RoutingServiceStatusData(
			getServiceType(), getServerStatus()
		);
	}

	protected boolean isEnableWebRemoteControl() {
		return getWebRemoteControlService() != null;
	}

	@Override
	public String getApplicationName() {
		return applicationVersion.getApplicationName();
	}

	@Override
	public String getApplicationVersion() {
		return applicationVersion.getApplicationVersion();
	}

	protected void notifyStatusChanged() {
		statusChanged = true;
	}

	protected void dispatchEvent(final RoutingServiceEvent eventType, final UUID queryId) {
		workerExecutor.submit(new RunnableTask(getExceptionListener()) {
			@Override
			public void task() {
				eventListener.event(eventType, queryId);
			}
		});
	}

	protected abstract ThreadProcessResult processRoutingService();

	protected abstract boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService);
	protected abstract RoutingServiceStatusData createStatusDataInternal();

	protected abstract List<RoutingServiceServerStatus> getServerStatus();
}
