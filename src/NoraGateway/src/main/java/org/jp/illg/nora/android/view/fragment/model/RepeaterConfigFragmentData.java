package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.RepeaterConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigFragmentData {

	private boolean serviceRunning;

	private RepeaterConfig repeaterConfig;

	public RepeaterConfigFragmentData(){}

	public RepeaterConfigFragmentData(boolean serviceRunning, RepeaterConfig repeaterConfig){
		setServiceRunning(serviceRunning);
		setRepeaterConfig(repeaterConfig);
	}
}
