/**
 *
 */
package org.jp.illg.dstar.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.VoiceCodecType;

import lombok.NonNull;

public class VoiceAMBE extends VoiceBase implements VoiceData, Cloneable {

	public VoiceAMBE() {
		super(DSTARDefines.VoiceSegmentLength);
	}

	public VoiceAMBE(
		@NonNull final byte[] voiceSegmentData, @NonNull final byte[] dataSegmentData
	) {
		super(voiceSegmentData, dataSegmentData);
	}

	@Override
	public VoiceAMBE clone() {
		VoiceAMBE copy = null;

		copy = (VoiceAMBE)super.clone();

		return copy;
	}

	@Override
	public VoiceCodecType getVoiceCodecType() {
		return VoiceCodecType.AMBE;
	}
}
