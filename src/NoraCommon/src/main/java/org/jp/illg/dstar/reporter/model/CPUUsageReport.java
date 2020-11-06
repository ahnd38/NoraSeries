package org.jp.illg.dstar.reporter.model;

import java.util.Map;

import lombok.Data;

@Data
public class CPUUsageReport {
	
	private String arch;
	
	private int availableProcessors;
	
	private String operatingSystemName;
	
	private String operatingSystemVersion;
	
	private double systemLoadAverage;
	
	private Map<Long, ThreadCPUUsageReport> threadUsageReport;
	
	public CPUUsageReport() {
		super();
	}
	
}
