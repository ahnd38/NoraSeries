package org.jp.illg.nora.gateway.reporter;

import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.NonNull;

public abstract class NoraGatewayStatusReporterBase implements NoraGatewayStatusReporter {

	/**
	 * リスナプロセスを呼び出す時間間隔(ms)
	 */
	private static final long processIntervalTimeMillisDefault = 100;  //(ms)

	/**
	 * レポートを生成する時間間隔(ms)
	 */
	private static final long reportIntervalMillisDefault = 500;  //(ms)


	private final UUID systemID;

	private final ApplicationInformation<?> applicationInformation;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final List<NoraGatewayStatusReportListener> listeners;
	private final Lock listenersLock = new ReentrantLock();

	@Getter
	private long reportIntervalTimeMillis;

	@Getter
	private final long processIntervalTimeMillis;

	private ThreadBase thread;

	private final Timer reportIntervalTimekeeper;


	public NoraGatewayStatusReporterBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation
	) {
		this.systemID = systemID;
		this.exceptionListener = exceptionListener;
		this.applicationInformation = applicationInformation;

		processIntervalTimeMillis = processIntervalTimeMillisDefault;

		reportIntervalTimeMillis = reportIntervalMillisDefault;
		reportIntervalTimekeeper = new Timer(reportIntervalTimeMillis);

		listeners = new LinkedList<>();
	}

	public NoraGatewayStatusReporterBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		@NonNull final NoraGatewayStatusReportListener listener
	) {
		this(
			systemID,
			exceptionListener,
			applicationInformation
		);

		addListener(listener);
	}

	public NoraGatewayStatusReporterBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		final NoraGatewayStatusReportListener listener,
		final long reportIntervalTimeMillis
	) {
		this(
			systemID,
			exceptionListener,
			applicationInformation,
			listener
		);

		setReportIntervalTimeMillis(reportIntervalTimeMillis);
	}

	public void setReportIntervalTimeMillis(final long intervalTimeMillis) {
		if (reportIntervalTimeMillis >= processIntervalTimeMillisDefault)
			this.reportIntervalTimeMillis = intervalTimeMillis;
		else
			this.reportIntervalTimeMillis = reportIntervalMillisDefault;
	}

	@Override
	public boolean start() {
		if (thread != null) {
			stop();
		}

		thread = new ThreadBase(
			exceptionListener,
			this.getClass().getSimpleName(),
			processIntervalTimeMillis, TimeUnit.MILLISECONDS
		) {
			@Override
			protected ThreadProcessResult threadInitialize() {
				return ThreadProcessResult.NoErrors;
			}

			@Override
			protected void threadFinalize() {

			}

			@Override
			protected ThreadProcessResult process() {
				return NoraGatewayStatusReporterBase.this.process();
			}
		};

		return thread.start();
	}

	@Override
	public void stop() {
		if(isRunning()) {thread.stop();}

		thread = null;
	}

	@Override
	public boolean isRunning() {
		return thread != null && thread.isRunning();
	}

	@Override
	public void close() {
		stop();
	}

	private ThreadProcessResult process() {
		Queue<NoraGatewayStatusReportListener> listeners = null;
		listenersLock.lock();
		try {
			listeners = new LinkedList<>(this.listeners);
		} finally {
			listenersLock.unlock();
		}

		for (final NoraGatewayStatusReportListener listener : listeners) {
			if (listener != null) {
				listener.listenerProcess();
			}
		}

		if (reportIntervalTimekeeper.isTimeout(getReportIntervalTimeMillis(), TimeUnit.MILLISECONDS)) {
			reportIntervalTimekeeper.updateTimestamp();

			final BasicStatusInformation info = new BasicStatusInformation();
			info.setApplicationName(applicationInformation.getApplicationName());
			info.setApplicationVersion(applicationInformation.getApplicationVersion());
			info.setApplicationRunningOS(applicationInformation.getRunningOperatingSystem());
			info.setApplicationUptime(applicationInformation.getUptimeSeconds());


			final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
			if (gateway != null) {
				info.setGatewayStatusReport(gateway.getGatewayStatusReport());
			}

			final List<DSTARRepeater> repeaters = DSTARRepeaterManager.getRepeaters(systemID);
			if (repeaters != null) {
				for (DSTARRepeater repeater : repeaters)
					info.getRepeaterStatusReports().add(repeater.getRepeaterStatusReport());
			}
			final List<ReflectorStatusReport> reflectors =
				gateway != null ? gateway.getReflectorStatusReport() : new ArrayList<>(0);
			info.setReflectorStatusReports(reflectors);

			final List<RoutingServiceStatusReport> routingServices =
				gateway != null ? gateway.getRoutingStatusReport() : new ArrayList<>(0);
			info.setRoutingStatusReports(routingServices);

			if (gateway != null && repeaters != null && reflectors != null) {
				listenersLock.lock();
				try {
					for (Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext(); ) {
						NoraGatewayStatusReportListener listener = it.next();
						if (listener != null)
							listener.report(info);
						else
							it.remove();
					}

				} finally {
					listenersLock.unlock();
				}
			}

			processReportInternal(info);
		}

		return ThreadProcessResult.NoErrors;
	}


	@Override
	public boolean addListener(NoraGatewayStatusReportListener listener) {
		if (listener == null) {
			return false;
		}

		listenersLock.lock();
		try {
			return listeners.add(listener);
		} finally {
			listenersLock.unlock();
		}
	}

	@Override
	public boolean removeListener(NoraGatewayStatusReportListener listener) {
		if (listener == null) {
			return false;
		}

		listenersLock.lock();
		try {
			boolean found = false;
			for (Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext(); ) {
				NoraGatewayStatusReportListener entry = it.next();

				if (entry == listener) {
					found = true;
					it.remove();
				}
			}
			return found;
		} finally {
			listenersLock.unlock();
		}
	}

	@Override
	public void removeListenerAll() {
		listenersLock.lock();
		try {
			listeners.clear();
		} finally {
			listenersLock.unlock();
		}
	}

	protected abstract void processReportInternal(BasicStatusInformation status);
}
