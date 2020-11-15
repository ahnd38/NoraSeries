package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class ModemNewAccessPointBluetoothConfig implements Cloneable{
	
	private String portName;
	private boolean allowDVSimplex;
	
	{
		portName = "";
		allowDVSimplex = false;
	}
	
	@Override
	public ModemNewAccessPointBluetoothConfig clone(){
		ModemNewAccessPointBluetoothConfig copy = null;
		
		try{
			copy = (ModemNewAccessPointBluetoothConfig)super.clone();
			
			copy.portName = this.portName;
			copy.allowDVSimplex = this.allowDVSimplex;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
