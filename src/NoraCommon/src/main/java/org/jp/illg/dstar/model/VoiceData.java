package org.jp.illg.dstar.model;

import org.jp.illg.dstar.model.defines.VoiceCodecType;

public interface VoiceData {

	public VoiceBase clone();

	public void clear();

	public byte[] getVoiceSegment();
	public void setVoiceSegment(byte[] voiceSegment);

	public byte[] getDataSegment();
	public void setDataSegment(byte[] dataSegment);

	public VoiceCodecType getVoiceCodecType();

	public String toString();
	public String toString(int indent);
}
