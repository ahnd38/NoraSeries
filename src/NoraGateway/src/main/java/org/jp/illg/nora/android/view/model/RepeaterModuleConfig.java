package org.jp.illg.nora.android.view.model;

import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.parceler.Parcel;
import org.parceler.ParcelConstructor;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterModuleConfig implements Cloneable {

	private char repeaterModule;

	private boolean repeaterEnabled;

	private String repeaterType;

	private InternalRepeaterConfig internalRepeaterConfig;

	private ExternalHomebrewRepeaterConfig externalHomebrewRepeaterConfig;

	private VoiceroidAutoReplyRepeaterConfig voiceroidAutoReplyRepeaterConfig;

	private EchoAutoReplyRepeaterConfig echoAutoReplyRepeaterConfig;

	{
		repeaterModule = 'A';
		repeaterEnabled = false;
		repeaterType = RepeaterTypes.Internal.getTypeName();
		internalRepeaterConfig = new InternalRepeaterConfig();
		externalHomebrewRepeaterConfig = new ExternalHomebrewRepeaterConfig();
		voiceroidAutoReplyRepeaterConfig = new VoiceroidAutoReplyRepeaterConfig();
		echoAutoReplyRepeaterConfig = new EchoAutoReplyRepeaterConfig();
	}

	public RepeaterModuleConfig(){
		super();
	}

	public RepeaterModuleConfig(char module){
		this();

		setRepeaterModule(module);
	}
	
	@Override
	public RepeaterModuleConfig clone(){
		RepeaterModuleConfig copy = null;
		
		try{
			copy = (RepeaterModuleConfig)super.clone();
			
			copy.repeaterModule = this.repeaterModule;
			copy.repeaterEnabled = this.repeaterEnabled;
			copy.repeaterType = this.repeaterType;
			copy.internalRepeaterConfig = this.internalRepeaterConfig.clone();
			copy.externalHomebrewRepeaterConfig = this.externalHomebrewRepeaterConfig.clone();
			copy.voiceroidAutoReplyRepeaterConfig = this.voiceroidAutoReplyRepeaterConfig.clone();
			copy.echoAutoReplyRepeaterConfig = this.echoAutoReplyRepeaterConfig.clone();
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
