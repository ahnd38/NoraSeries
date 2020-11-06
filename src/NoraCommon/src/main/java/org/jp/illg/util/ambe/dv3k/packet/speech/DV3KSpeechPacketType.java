package org.jp.illg.util.ambe.dv3k.packet.speech;

import lombok.Getter;

public enum DV3KSpeechPacketType {
	PacketChannel0    ((byte)0x40, 1, 1),
	SpeechData        ((byte)0x00, -1, -1),
	CMode             ((byte)0x02, 2, 2),
	Tone              ((byte)0x08, 2, 2),
	;

	@Getter
	private final byte value;

	@Getter
	private final int dataLengthRequest;

	@Getter
	private final int dataLengthResponse;

	private DV3KSpeechPacketType(
		final byte value, final int dataLengthRequest, final int dataLengthResponse
	) {
		this.value = value;
		this.dataLengthRequest = dataLengthRequest;
		this.dataLengthResponse = dataLengthResponse;
	}

	public static DV3KSpeechPacketType getTypeByValue(final byte value) {
		for(DV3KSpeechPacketType type : values()) {
			if(type.getValue() == value) {return type;}
		}

		return null;
	}
}
