package org.jp.illg.dstar.model.config;

import lombok.Data;
import lombok.NonNull;

@Data
public class ReflectorHostFileDownloadURLEntry {
	
	private boolean enable;
	
	private int intervalMinutes;
	
	private String url;
	
	public ReflectorHostFileDownloadURLEntry() {
		this(false, 0, "");
	}
	
	public ReflectorHostFileDownloadURLEntry(
		final boolean enable,
		final int intervalMinutes,
		@NonNull final String url
	){
		super();
		
		this.enable = enable;
		this.intervalMinutes = intervalMinutes;
		this.url = url;
	}
}
