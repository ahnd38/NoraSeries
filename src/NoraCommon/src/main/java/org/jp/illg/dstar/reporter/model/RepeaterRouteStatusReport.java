package org.jp.illg.dstar.reporter.model;

import lombok.Data;

@Data
public class RepeaterRouteStatusReport {

	private String routeMode = "";

	private int frameID = 0x0;

	private long frameSequenceStartTime = 0;

	private String yourCallsign = "";

	private String repeater1Callsign = "";

	private String repeater2Callsign = "";

	private String myCallsign = "";

	private String myCallsignAdd = "";
}
