package org.jp.illg.dstar.reporter.model;

import lombok.Data;

@Data
public class ThreadCPUUsageReport {
	
	private long threadId;

	private String threadName;

	private double cpuUsageCurrent;

	private double cpuUsageMax;

	private double cpuUsageMin;

	private double cpuUsageAverage;
	
	public ThreadCPUUsageReport() {
		super();
	}
	
}
