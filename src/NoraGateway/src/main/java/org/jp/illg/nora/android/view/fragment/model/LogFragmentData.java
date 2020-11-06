package org.jp.illg.nora.android.view.fragment.model;

import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class LogFragmentData {

	private boolean serviceRunning;

	private String[] logs;

	private String log;

	public LogFragmentData(){}

	public LogFragmentData(boolean serviceRunning, String[] logs){
		setServiceRunning(serviceRunning);
		setLogs(logs);
	}

	public LogFragmentData(boolean serviceRunning, String log){
		setServiceRunning(serviceRunning);
		setLog(log);
	}
}