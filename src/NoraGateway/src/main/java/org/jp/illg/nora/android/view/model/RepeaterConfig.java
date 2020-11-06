package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;
import org.parceler.ParcelConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfig implements Cloneable {

	private Map<Character, RepeaterModuleConfig> repeaterModules;
	
	private char selectedModule;

	public RepeaterConfig(){
		super();

		repeaterModules = new HashMap<>();
		
		selectedModule = 'A';
	}
	
	@Override
	public RepeaterConfig clone(){
		RepeaterConfig copy = null;
		
		try{
			copy = (RepeaterConfig)super.clone();
			
			copy.repeaterModules = new HashMap<>();
			if(this.repeaterModules != null){
				for(Map.Entry<Character, RepeaterModuleConfig> entry : this.repeaterModules.entrySet()){
					copy.repeaterModules.put(entry.getKey(), entry.getValue().clone());
				}
			}
			
			copy.selectedModule = this.selectedModule;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
