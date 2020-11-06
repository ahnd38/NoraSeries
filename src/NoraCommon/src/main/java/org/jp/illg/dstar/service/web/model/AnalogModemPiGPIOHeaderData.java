package org.jp.illg.dstar.service.web.model;

import lombok.Data;

@Data
public class AnalogModemPiGPIOHeaderData {
	
	private byte[] flags;

	private String repeater1Callsign;

	private String repeater2Callsign;

	private String yourCallsign;

	private String myCallsignLong;

	private String myCallsignShort;

	public AnalogModemPiGPIOHeaderData() {
		super();
	}

}
