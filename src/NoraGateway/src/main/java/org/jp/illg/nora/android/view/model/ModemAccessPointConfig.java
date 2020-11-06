package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class ModemAccessPointConfig implements Cloneable{

	private String portName;
	private boolean terminalMode;

	{
		portName = "";
		terminalMode = true;
	}
	
	@Override
	public ModemAccessPointConfig clone(){
		ModemAccessPointConfig copy = null;
		
		try{
			copy = (ModemAccessPointConfig)super.clone();
			
			copy.portName = this.portName;
			copy.terminalMode = this.terminalMode;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
