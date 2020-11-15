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

public class NoraGatewayStatusReporterWinLinux extends NoraGatewayStatusReporterBase {

	private final Timer cpuUsageReportIntervalTimekeeper;
	private final CPUUsageMonitorTool cpuMonitorTool;
	private CPUUsageReport cpuUsageReport;

	public NoraGatewayStatusReporterWinLinux(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		@NonNull final CPUUsageMonitorTool cpuMonitorTool
	){
		super(
			systemID,
			exceptionListener,
			applicationInformation
		);

		cpuUsageReportIntervalTimekeeper = new Timer();
		cpuUsageReportIntervalTimekeeper.updateTimestamp();

		this.cpuMonitorTool = cpuMonitorTool;
	}

	public NoraGatewayStatusReporterWinLinux(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		@NonNull final CPUUsageMonitorTool cpuMonitorTool,
		@NonNull final NoraGatewayStatusReportListener listener
	){
		this(
			systemID,
			exceptionListener,
			applicationInformation,
			cpuMonitorTool
		);

		addListener(listener);
	}

	public NoraGatewayStatusReporterWinLinux(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		@NonNull final CPUUsageMonitorTool cpuMonitorTool,
		final NoraGatewayStatusReportListener listener,
		final long reportIntervalTimeMillis
	){
		this(
			systemID,
			exceptionListener,
			applicationInformation,
			cpuMonitorTool,
			listener
		);

		setReportIntervalTimeMillis(reportIntervalTimeMillis);
	}

	@Override
	protected void processReportInternal(BasicStatusInformation info) {
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
	}
}
