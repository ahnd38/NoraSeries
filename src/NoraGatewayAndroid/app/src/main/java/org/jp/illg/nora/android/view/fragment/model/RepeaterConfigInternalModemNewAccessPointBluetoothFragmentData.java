package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.ModemNewAccessPointBluetoothConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData {
	
	private boolean serviceRunning;
	
	private ModemNewAccessPointBluetoothConfig modemNewAccessPointBluetoothConfig;
	
	public RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData(){}
	
	public RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData(
			boolean serviceRunning,
			ModemNewAccessPointBluetoothConfig modemAccessPointConfig
	){
		setServiceRunning(serviceRunning);
		setModemNewAccessPointBluetoothConfig(modemAccessPointConfig);
	}
}
