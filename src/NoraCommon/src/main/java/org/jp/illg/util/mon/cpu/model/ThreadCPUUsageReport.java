package org.jp.illg.util.mon.cpu.model;

import lombok.Getter;
import lombok.Setter;

public class ThreadCPUUsageReport implements Cloneable {
	
	@Getter
	private long threadId;
	
	@Getter
	private String threadName;
	
	@Getter
	@Setter
	private double cpuUsageCurrent;
	
	@Getter
	@Setter
	private double cpuUsageMax;
	
	@Getter
	@Setter
	private double cpuUsageMin;
	
	@Getter
	@Setter
	private double cpuUsageAverage;
	
	
	public ThreadCPUUsageReport(
			final long threadId,
			final String threadName,
			final double cpuUsageCurrent
	) {
		super();
		
		this.threadId = threadId;
		this.threadName = threadName;
		this.cpuUsageCurrent = cpuUsageCurrent;
		
		this.cpuUsageMax = 0.0d;
		this.cpuUsageMin = 0.0d;
		this.cpuUsageAverage = 0.0d;
	}
	
	@Override
	public ThreadCPUUsageReport clone() {
		ThreadCPUUsageReport copy = null;
		try {
			copy = (ThreadCPUUsageReport)super.clone();
			
			copy.threadId = threadId;
			copy.threadName = threadName;
			copy.cpuUsageCurrent = cpuUsageCurrent;
			copy.cpuUsageMax = cpuUsageMax;
			copy.cpuUsageMin = cpuUsageMin;
			
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
		
		return copy;
	}
}

