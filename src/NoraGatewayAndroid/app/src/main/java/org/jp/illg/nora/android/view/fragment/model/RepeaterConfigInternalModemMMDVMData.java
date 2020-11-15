package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.ModemMMDVMConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigInternalModemMMDVMData {

	private boolean serviceRunning;
	
	private ModemMMDVMConfig modemMMDVMConfig;
	
	public RepeaterConfigInternalModemMMDVMData(){
		super();
	}
	
	public RepeaterConfigInternalModemMMDVMData(
			boolean serviceRunning,
			ModemMMDVMConfig modemMMDVMConfig
	){
		this();
		
		setServiceRunning(serviceRunning);
		setModemMMDVMConfig(modemMMDVMConfig);
	}
}
