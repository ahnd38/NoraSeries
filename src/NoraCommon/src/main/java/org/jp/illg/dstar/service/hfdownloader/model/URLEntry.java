package org.jp.illg.dstar.service.hfdownloader.model;

import org.jp.illg.util.Timer;

import lombok.Data;

@Data
public class URLEntry {
	
	private boolean enable;
	
	private int intervalMinutes;
	
	private String url;
	
	private Timer intervalTimekeeper;
	
	public URLEntry() {
		super();
		
		setEnable(false);
		setIntervalMinutes(0);
		setUrl("");
		setIntervalTimekeeper(new Timer());
	}
	
}
