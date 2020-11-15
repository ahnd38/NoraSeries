package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.ExternalHomebrewRepeaterConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigExternalHomebrewFragmentData {

	private boolean serviceRunning;

	private ExternalHomebrewRepeaterConfig externalHomebrewRepeaterConfig;

	public RepeaterConfigExternalHomebrewFragmentData(){}

	public RepeaterConfigExternalHomebrewFragmentData(
			boolean serviceRunning,
			ExternalHomebrewRepeaterConfig externalHomebrewRepeaterConfig
	){
		setServiceRunning(serviceRunning);
		setExternalHomebrewRepeaterConfig(externalHomebrewRepeaterConfig);
	}
}
