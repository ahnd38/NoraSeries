package org.jp.illg.nora.android.view.model;

import org.jp.illg.dstar.model.defines.ModemTypes;
import org.parceler.Parcel;

import java.nio.channels.ClosedChannelException;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class InternalRepeaterConfig implements Cloneable {

	private String modemType;

	private String directMyCallsigns;

	private ModemAccessPointConfig modemAccessPointConfig;
	
	private ModemMMDVMConfig modemMMDVMConfig;
	
	private ModemMMDVMBluetoothConfig modemMMDVMBluetoothConfig;
	
	private ModemNewAccessPointBluetoothConfig modemNewAccessPointBluetoothConfig;

	{
		modemType = ModemTypes.AccessPoint.getTypeName();
		modemAccessPointConfig = new ModemAccessPointConfig();
		modemMMDVMConfig = new ModemMMDVMConfig();
		modemMMDVMBluetoothConfig = new ModemMMDVMBluetoothConfig();
		modemNewAccessPointBluetoothConfig = new ModemNewAccessPointBluetoothConfig();
	}

	public InternalRepeaterConfig(){
		super();
	}
	
	@Override
	public InternalRepeaterConfig clone(){
		InternalRepeaterConfig copy = null;
		
		try{
			copy = (InternalRepeaterConfig)super.clone();
			
			copy.modemType = this.modemType;
			copy.directMyCallsigns = this.directMyCallsigns;
			copy.modemAccessPointConfig = this.modemAccessPointConfig.clone();
			copy.modemMMDVMConfig = this.modemMMDVMConfig.clone();
			copy.modemMMDVMBluetoothConfig = this.modemMMDVMBluetoothConfig.clone();
			copy.modemNewAccessPointBluetoothConfig = this.modemNewAccessPointBluetoothConfig.clone();
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
