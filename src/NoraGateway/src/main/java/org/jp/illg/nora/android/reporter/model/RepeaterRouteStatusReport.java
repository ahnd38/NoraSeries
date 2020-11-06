package org.jp.illg.nora.android.reporter.model;

import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterRouteStatusReport {

	private String routeMode = "";

	private int frameID = 0x0;

	private long frameSequenceStartTime = 0;

	private String yourCallsign = "";

	private String repeater1Callsign = "";

	private String repeater2Callsign = "";

	private String myCallsign = "";

	private String myCallsignAdd = "";

	public RepeaterRouteStatusReport(){super();}

	public RepeaterRouteStatusReport(org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport src){
		super();

		if(src != null){
			setRouteMode(new String(src.getRouteMode()));
			setFrameID(src.getFrameID());
			setFrameSequenceStartTime(src.getFrameSequenceStartTime());
			setYourCallsign(new String(src.getYourCallsign()));
			setRepeater1Callsign(new String(src.getRepeater1Callsign()));
			setRepeater2Callsign(new String(src.getRepeater2Callsign()));
			setMyCallsign(new String(src.getMyCallsign()));
			setMyCallsignAdd(new String(src.getMyCallsignAdd()));
		}
	}
}
