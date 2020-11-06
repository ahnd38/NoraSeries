package org.jp.illg.nora.gateway.reporter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.mon.cpu.CPUUsageMonitorTool;
import org.jp.illg.util.mon.cpu.model.CPUUsageReport;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class NoraGatewayStatusReporter extends ThreadBase {

	private static final long reportIntervalTimeMillisDefault = 1000L;

	private final UUID systemID;

	private final List<NoraGatewayStatusReportListener> listeners;
	private final Lock listenersLock = new ReentrantLock();

	private final CPUUsageMonitorTool cpuMonitorTool;
	private final ApplicationInformation<?> applicationVersion;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long reportIntervalMillis;

	private final Timer reportIntervalTimekeeper;

	private final Timer cpuUsageReportIntervalTimekeeper;

	private CPUUsageReport cpuUsageReport;

	public NoraGatewayStatusReporter(
		@NonNull final UUID systemID,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull CPUUsageMonitorTool cpuMonitorTool,
		@NonNull ApplicationInformation<?> applicationVersion
	){
		super(
			exceptionListener,
			NoraGatewayStatusReporter.class.getSimpleName(),
			TimeUnit.MILLISECONDS.toMillis(100)
		);

		this.systemID = systemID;

		reportIntervalMillis = reportIntervalTimeMillisDefault;
		reportIntervalTimekeeper = new Timer();
		reportIntervalTimekeeper.updateTimestamp();

		cpuUsageReportIntervalTimekeeper = new Timer();
		cpuUsageReportIntervalTimekeeper.updateTimestamp();

		this.cpuMonitorTool = cpuMonitorTool;
		this.applicationVersion = applicationVersion;

		listeners = new LinkedList<>();

		cpuUsageReport = null;
	}

	public NoraGatewayStatusReporter(
		@NonNull final UUID systemID,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull CPUUsageMonitorTool cpuMonitorTool,
		@NonNull ApplicationInformation<?> applicationVersion,
		@NonNull NoraGatewayStatusReportListener listener
	){
		this(systemID, exceptionListener, cpuMonitorTool, applicationVersion);

		addListener(listener);
	}

	public NoraGatewayStatusReporter(
		@NonNull final UUID systemID,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull CPUUsageMonitorTool cpuMonitorTool,
		@NonNull ApplicationInformation<?> applicationVersion,
		@NonNull NoraGatewayStatusReportListener listener,
		long reportIntervalTimeMillis
	){
		this(systemID, exceptionListener, cpuMonitorTool, applicationVersion, listener);

		if(reportIntervalTimeMillis > reportIntervalTimeMillisDefault)
			setReportIntervalMillis(reportIntervalTimeMillis);
		else
			setReportIntervalMillis(reportIntervalTimeMillisDefault);
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {

		listenersLock.lock();
		try {
			for(NoraGatewayStatusReportListener listener : listeners) {
				if(listener != null) {listener.listenerProcess();}
			}
		}finally {
			listenersLock.unlock();
		}

		if(reportIntervalTimekeeper.isTimeout(getReportIntervalMillis(), TimeUnit.MILLISECONDS)) {
			reportIntervalTimekeeper.updateTimestamp();

			final BasicStatusInformation info = new BasicStatusInformation();
			info.setApplicationName(applicationVersion.getApplicationName());
			info.setApplicationVersion(applicationVersion.getApplicationVersion());
			info.setApplicationRunningOS(applicationVersion.getRunningOperatingSystem());
			info.setApplicationUptime(applicationVersion.getUptimeSeconds());

			if(
				cpuUsageReport == null ||
				cpuUsageReportIntervalTimekeeper.isTimeout(
					SystemUtil.getAvailableProcessors() >= 2 ? 2 : 5, TimeUnit.SECONDS
				)
			) {
				cpuUsageReportIntervalTimekeeper.updateTimestamp();

				cpuUsageReport = cpuMonitorTool.getCPUUsageReport();
			}
			info.setCpuUsageReport(cpuUsageReport);

			final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
			if(gateway != null){info.setGatewayStatusReport(gateway.getGatewayStatusReport());}

			final List<DSTARRepeater> repeaters = DSTARRepeaterManager.getRepeaters(systemID);
			if(repeaters != null){
				for(DSTARRepeater repeater : repeaters)
					info.getRepeaterStatusReports().add(repeater.getRepeaterStatusReport());
			}
			final List<ReflectorStatusReport> reflectors =
				gateway != null ? gateway.getReflectorStatusReport() : new ArrayList<>(0);
			info.setReflectorStatusReports(reflectors);

			final List<RoutingServiceStatusReport> routingServices =
				gateway != null ? gateway.getRoutingStatusReport() : new ArrayList<>(0);
			info.setRoutingStatusReports(routingServices);

			if(gateway != null && repeaters != null && reflectors != null) {
				listenersLock.lock();
				try {
					for(Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext();) {
						NoraGatewayStatusReportListener listener = it.next();
						if(listener != null)
							listener.report(info);
						else
							it.remove();
					}

				}finally {
					listenersLock.unlock();
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		removeListenerAll();
	}


	public boolean addListener(NoraGatewayStatusReportListener listener) {
		if(listener == null) {return false;}

		listenersLock.lock();
		try {
			return listeners.add(listener);
		}finally {
			listenersLock.unlock();
		}
	}

	public boolean removeListener(NoraGatewayStatusReportListener listener) {
		if(listener == null) {return false;}

		listenersLock.lock();
		try {
			boolean found = false;
			for(Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext();) {
				NoraGatewayStatusReportListener entry = it.next();

				if(entry == listener) {
					found = true;
					it.remove();
				}
			}
			return found;
		}finally {
			listenersLock.unlock();
		}
	}

	public void removeListenerAll() {
		listenersLock.lock();
		try {
			listeners.clear();
		}finally {
			listenersLock.unlock();
		}
	}
}
