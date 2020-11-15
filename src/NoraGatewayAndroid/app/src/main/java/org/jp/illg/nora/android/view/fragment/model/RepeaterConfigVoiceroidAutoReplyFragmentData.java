package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.VoiceroidAutoReplyRepeaterConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigVoiceroidAutoReplyFragmentData {

	private boolean serviceRunning;

	private VoiceroidAutoReplyRepeaterConfig voiceroidAutoReplyRepeaterConfig;

	{
		voiceroidAutoReplyRepeaterConfig = new VoiceroidAutoReplyRepeaterConfig();
	}

	public RepeaterConfigVoiceroidAutoReplyFragmentData(){}

	public RepeaterConfigVoiceroidAutoReplyFragmentData(
			boolean serviceRunning,
			VoiceroidAutoReplyRepeaterConfig voiceroidAutoReplyRepeaterConfig
	){
		setServiceRunning(serviceRunning);
		setVoiceroidAutoReplyRepeaterConfig(voiceroidAutoReplyRepeaterConfig);
	}
}
