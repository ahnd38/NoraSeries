package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.InternalRepeaterConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigInternalFragmentData {

	private boolean serviceRunning;

	private InternalRepeaterConfig internalRepeaterConfig;

	public RepeaterConfigInternalFragmentData(){}

	public RepeaterConfigInternalFragmentData(boolean serviceRunning, InternalRepeaterConfig internalRepeaterConfig){
		setServiceRunning(serviceRunning);
		setInternalRepeaterConfig(internalRepeaterConfig);
	}
}
