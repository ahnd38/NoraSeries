package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.StatusConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class StatusFragmentData {

	private boolean serviceRunning;

	private String[] logs;

	private StatusConfig statusConfig;

	public StatusFragmentData(){
		setStatusConfig(new StatusConfig());
	}

	public StatusFragmentData(boolean serviceRunning, String[] logs, StatusConfig statusConfig){
		setServiceRunning(serviceRunning);
		setLogs(logs);
		setStatusConfig(statusConfig);
	}
}
