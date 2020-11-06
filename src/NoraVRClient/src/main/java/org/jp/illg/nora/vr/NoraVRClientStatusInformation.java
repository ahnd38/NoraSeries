package org.jp.illg.nora.vr;

import lombok.Data;
import lombok.Getter;

public class NoraVRClientStatusInformation {

	public enum NoraVRClientRouteDirection {
		Uplink,
		Downlink,
		;
	}

	@Data
	public static class NoraVRClientRouteReport {
		private NoraVRClientRouteDirection direction;
		private int frameID;
		private String repeater1Callsign;
		private String repeater2Callsign;
		private String yourCallsign;
		private String myCallsignLong;
		private String myCallsignShort;
	}


	@Getter
	private NoraVRClientRouteReport uplinkReport;

	@Getter
	private NoraVRClientRouteReport downlinkReport;

	public NoraVRClientStatusInformation(
		NoraVRClientRouteReport uplinkReport,
		NoraVRClientRouteReport downlinkReport
	) {
		this.uplinkReport = uplinkReport;
		this.downlinkReport = downlinkReport;
	}

}
