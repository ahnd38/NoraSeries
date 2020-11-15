package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.ModemAccessPointConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigInternalModemAccessPointFragmentData {

	private boolean serviceRunning;

	private ModemAccessPointConfig modemAccessPointConfig;

	public RepeaterConfigInternalModemAccessPointFragmentData(){}

	public RepeaterConfigInternalModemAccessPointFragmentData(
			boolean serviceRunning,
			ModemAccessPointConfig modemAccessPointConfig
	){
		setServiceRunning(serviceRunning);
		setModemAccessPointConfig(modemAccessPointConfig);
	}
}
