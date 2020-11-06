package org.jp.illg.dstar.service.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReflectorLinkControl {
	
	private String reflectorCallsign;
	
	private String repeaterCallsign;
	
	private boolean success;
	
	public ReflectorLinkControl() {
		super();
	}
	
}
