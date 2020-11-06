package org.jp.illg.dstar.model.config;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

import lombok.Data;

@Data
public class ReflectorBlackListEntry {
	
	private boolean enable;
	
	private String callsign;
	
	private ConnectionDirectionType dir;
	
	public ReflectorBlackListEntry() {
		super();
	}
	
}
