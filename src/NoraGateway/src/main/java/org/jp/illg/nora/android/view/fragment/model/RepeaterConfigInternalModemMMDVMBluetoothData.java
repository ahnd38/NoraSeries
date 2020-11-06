package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.ModemMMDVMBluetoothConfig;
import org.jp.illg.nora.android.view.model.ModemMMDVMConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigInternalModemMMDVMBluetoothData {
	
	private boolean serviceRunning;
	
	private ModemMMDVMBluetoothConfig modemMMDVMBluetoothConfig;
	
	public RepeaterConfigInternalModemMMDVMBluetoothData(){
		super();
	}
	
	public RepeaterConfigInternalModemMMDVMBluetoothData(
			boolean serviceRunning,
			ModemMMDVMBluetoothConfig modemMMDVMBluetoothConfig
	){
		this();
		
		setServiceRunning(serviceRunning);
		setModemMMDVMBluetoothConfig(modemMMDVMBluetoothConfig);
	}
}

