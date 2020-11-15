package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class ExternalHomebrewRepeaterConfig implements Cloneable {

	private String remoteRepeaterAddress;

	private int remoteRepeaterPort;

	private int localPort;

	{
		remoteRepeaterAddress = "";
		remoteRepeaterPort = 0;
		localPort = 0;
	}
	
	@Override
	public ExternalHomebrewRepeaterConfig clone(){
		ExternalHomebrewRepeaterConfig copy = null;
		
		try{
			copy = (ExternalHomebrewRepeaterConfig)super.clone();
			
			copy.remoteRepeaterAddress = this.remoteRepeaterAddress;
			copy.remoteRepeaterPort = this.remoteRepeaterPort;
			copy.localPort = this.localPort;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
