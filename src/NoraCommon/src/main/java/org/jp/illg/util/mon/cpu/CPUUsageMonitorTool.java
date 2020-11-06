package org.jp.illg.util.mon.cpu;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.mon.cpu.model.CPUUsageReport;
import org.jp.illg.util.mon.cpu.model.ThreadCPUUsageReport;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

@Slf4j
public class CPUUsageMonitorTool {

	private static final String logTag;

	private final Lock locker;

	private long prevUpTime;
	private Map<Long, Long> prevThreadCpuTime;

	private final Map<Long, ThreadCPUUsageReport> storeReports;

	private long[] saveTicks;

	static {
		logTag = CPUUsageMonitorTool.class.getSimpleName() + " : ";
	}

	public CPUUsageMonitorTool() {
		super();

		locker = new ReentrantLock();

		prevUpTime = 0L;
		prevThreadCpuTime = null;

		storeReports = new HashMap<>();

		getCPUUsageReport();
	}

	public boolean measure() {
		return createReport(null);
	}

	public boolean createReport(CPUUsageReport result) {
		final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
		if(result != null) {
			result.setArch(osMXBean.getArch());
			result.setAvailableProcessors(osMXBean.getAvailableProcessors());
			result.setOperatingSystemName(osMXBean.getName());
			result.setOperatingSystemVersion(osMXBean.getVersion());
			result.setSystemLoadAverage(getCPULoadAverage(osMXBean));
		}

		final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		if(!threadMXBean.isThreadCpuTimeEnabled()) {
			threadMXBean.setThreadCpuTimeEnabled(true);
			if(!threadMXBean.isThreadCpuTimeEnabled()) {
				return false;
			}
		}
		else if(!threadMXBean.isThreadCpuTimeSupported()) {
			return false;
		}

		locker.lock();
		try {

			final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

			final long upTime = runtimeMXBean.getUptime();

			final long[] threadIds = threadMXBean.getAllThreadIds();

			final Map<Long, Long> threadCpuTime = new HashMap<>();
			for (int i = 0; i < threadIds.length; i++) {
				final long threadId = threadIds[i];
				if (threadId != -1) {
					threadCpuTime.put(threadId, threadMXBean.getThreadCpuTime(threadId));
				} else {
					threadCpuTime.put(threadId, 0L);
				}
			}
			final int nCPUs = Runtime.getRuntime().availableProcessors();
			if (prevUpTime > 0L && upTime > prevUpTime) {

				final long elapsedTime = upTime - prevUpTime;
				for (int i = 0; i < threadIds.length; i++) {
					final long threadId = threadIds[i];

					final Long prevCpuTime =
						prevThreadCpuTime != null ? prevThreadCpuTime.get(threadId) : null;
					if(prevCpuTime == null) {continue;}

					final long elapsedCpu = threadCpuTime.get(threadIds[i]) - prevCpuTime;
					if(elapsedCpu < 0) {continue;}

					final float cpuUsage = Math.min(99F, elapsedCpu / (elapsedTime * 1000000F * nCPUs));

					final ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId);

					ThreadCPUUsageReport report = storeReports.get(threadId);
					if(report != null) {
						report.setCpuUsageCurrent(cpuUsage);

						report.setCpuUsageAverage(
							report.getCpuUsageAverage() != 0.0d ? ((report.getCpuUsageAverage() + cpuUsage) / 2) : cpuUsage
						);

						if(report.getCpuUsageMax() < cpuUsage) {report.setCpuUsageMax(cpuUsage);}
						if(report.getCpuUsageMin() > cpuUsage) {report.setCpuUsageMin(cpuUsage);}
					}
					else {
						report =
							new ThreadCPUUsageReport(
								threadId,
								(threadInfo != null ? threadInfo.getThreadName() : "Unknown"),
								cpuUsage
							);

						storeReports.put(threadId, report);
					}
				}

				for(
					final Iterator<ThreadCPUUsageReport> it = storeReports.values().iterator();
					it.hasNext();
				) {
					final ThreadCPUUsageReport report = it.next();

					if(threadCpuTime.get(report.getThreadId()) == null) {
						it.remove();

						if(log.isTraceEnabled()) {
							log.trace(
								logTag +
								"Remove report entry = " +
								"ThreadID:" + String.format("0x%08X", report.getThreadId()) + "/ThreadName:" + report.getThreadName()
							);
						}
					}
					else if(result != null){
						result.getThreadUsageReport().put(report.getThreadId(), report.clone());
					}
				}
			}

			prevUpTime = upTime;
			prevThreadCpuTime = threadCpuTime;
		}finally {
			locker.unlock();
		}

		return true;
	}

	public @NonNull Map<Long, ThreadCPUUsageReport> getCPUThreadUsageReport() {
		final CPUUsageReport report = new CPUUsageReport();

		createReport(report);

		return report.getThreadUsageReport();
	}

	public @NonNull CPUUsageReport getCPUUsageReport() {
		final CPUUsageReport report = new CPUUsageReport();

		createReport(report);

		return report;
	}

	private double getCPULoadAverage(final OperatingSystemMXBean osMXBean) {
		double loadAverage;
		final CentralProcessor cp = new SystemInfo().getHardware().getProcessor();

		locker.lock();
		try {
			if(saveTicks != null)
				loadAverage = cp.getSystemCpuLoadBetweenTicks(saveTicks);
			else
				loadAverage = 0.0d;

			saveTicks = cp.getSystemCpuLoadTicks();
		}finally {
			locker.unlock();
		}

		return loadAverage;
	}
}
