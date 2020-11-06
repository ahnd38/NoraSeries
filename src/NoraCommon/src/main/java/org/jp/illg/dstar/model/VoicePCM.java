package org.jp.illg.dstar.model;

import org.jp.illg.dstar.model.defines.VoiceCodecType;

public class VoicePCM extends VoiceBase implements VoiceData, Cloneable{

	public VoicePCM() {
		super(320);
	}

	@Override
	public VoicePCM clone() {
		VoicePCM copy = null;

		copy = (VoicePCM)super.clone();

		return copy;
	}

	@Override
	public VoiceCodecType getVoiceCodecType() {
		return VoiceCodecType.DPCM;
	}

}
