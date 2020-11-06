package org.jp.illg.util.mon.cpu.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

public class CPUUsageReport implements Cloneable {
	
	@Getter
	@Setter
	private String arch;
	
	@Getter
	@Setter
	private int availableProcessors;
	
	@Getter
	@Setter
	private String operatingSystemName;
	
	@Getter
	@Setter
	private String operatingSystemVersion;
	
	@Getter
	@Setter
	private double systemLoadAverage;
	
	@Getter
	private Map<Long, ThreadCPUUsageReport> threadUsageReport;
	
	public CPUUsageReport() {
		this(
				"", 1, "", "", -1, new HashMap<>()
		);
	}
	
	public CPUUsageReport(
			final String arch,
			final int availableProcessors,
			final String operatingSystemName,
			final String operatingSystemVersion,
			final double systemLoadAverage,
			final Map<Long, ThreadCPUUsageReport> threadUsageReport
	) {
		super();
		
		this.arch = arch;
		this.availableProcessors = availableProcessors;
		this.operatingSystemName = operatingSystemName;
		this.operatingSystemVersion = operatingSystemVersion;
		this.systemLoadAverage = systemLoadAverage;
		this.threadUsageReport = threadUsageReport;
	}
}
