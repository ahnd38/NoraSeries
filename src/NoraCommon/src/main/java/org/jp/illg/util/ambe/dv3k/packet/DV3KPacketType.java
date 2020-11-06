package org.jp.illg.util.ambe.dv3k.packet;

import lombok.Getter;

public enum DV3KPacketType {
	ControlPacket(0x00),
	SpeechPacket(0x02),
	ChannelPacket(0x01),
	;

	@Getter
	private final int value;

	private DV3KPacketType(final int value) {
		this.value = value;
	}

	public static DV3KPacketType getTypeByValue(final int value) {
		for(DV3KPacketType v : values()) {
			if(v.getValue() == value) {return v;}
		}

		return null;
	}
}
