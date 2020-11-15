package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class EchoAutoReplyRepeaterConfig implements Cloneable{

	private String autoReplyOperatorCallsign;

	{
		autoReplyOperatorCallsign = "";
	}
	
	@Override
	public EchoAutoReplyRepeaterConfig clone(){
		EchoAutoReplyRepeaterConfig copy = null;
		
		try{
			copy = (EchoAutoReplyRepeaterConfig)super.clone();
			
			copy.autoReplyOperatorCallsign = this.autoReplyOperatorCallsign;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
