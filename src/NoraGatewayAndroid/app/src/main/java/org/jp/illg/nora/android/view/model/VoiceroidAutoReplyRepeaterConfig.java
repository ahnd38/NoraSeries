package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class VoiceroidAutoReplyRepeaterConfig implements Cloneable{

	private String autoReplyOperatorCallsign;

	private String autoReplyCharacterName;

	{
		autoReplyOperatorCallsign = "";
		autoReplyCharacterName = "";
	}
	
	@Override
	public VoiceroidAutoReplyRepeaterConfig clone(){
		VoiceroidAutoReplyRepeaterConfig copy = null;
		
		try{
			copy = (VoiceroidAutoReplyRepeaterConfig)super.clone();
			
			copy.autoReplyOperatorCallsign = this.autoReplyOperatorCallsign;
			copy.autoReplyCharacterName = this.autoReplyCharacterName;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
