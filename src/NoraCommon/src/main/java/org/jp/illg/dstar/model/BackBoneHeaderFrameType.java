package org.jp.illg.dstar.model;

import lombok.Getter;

public enum BackBoneHeaderFrameType {
	VoiceData((byte)0x00),
	VoiceDataLastFrame((byte)0x40),
	VoiceDataHeader((byte)0x80),
	Reserved((byte)0xC0),
	;

	@Getter
	private final byte value;

	@Getter
	private static final byte mask = (byte)0xC0;

	private BackBoneHeaderFrameType(final byte value) {
		this.value = value;
	}

	public static BackBoneHeaderFrameType valueOf(final byte value) {
		for(final BackBoneHeaderFrameType v : values()) {
			if(v.getValue() == (value & getMask())) {return v;}
		}

		return null;
	}
}
