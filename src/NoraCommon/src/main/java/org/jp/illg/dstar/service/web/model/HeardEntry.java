package org.jp.illg.dstar.service.web.model;

import lombok.Data;

@Data
public class HeardEntry {

	private String protocol;

	private String direction;

	private String state;

	private long heardTime;

	private String repeater1Callsign;

	private String repeater2Callsign;

	private String yourCallsign;

	private String myCallsignLong;

	private String myCallsignShort;

	private String shortMessage;

	private boolean locationAvailable;

	private double latitude;

	private double longitude;

	private String destination;

	private String from;

	private int packetCount;

	private double packetDropRate;

	private double bitErrorRate;

	public HeardEntry() {
		super();
	}

}
