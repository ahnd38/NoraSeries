package org.jp.illg.dstar.model;

import lombok.Getter;

public enum BackBonePacketType {
	Check          (0x00),
	Error          (0x01),
	DDPacket       (0x11),
	DVPacket       (0x12),
	PositionUpdate (0x21),
	;

	@Getter
	private final int value;

	private BackBonePacketType(final int value) {
		this.value = value;
	}

	public static BackBonePacketType getTypeByValue(final int value) {
		for(final BackBonePacketType t : values()) {
			if(t.getValue() == value) {return t;}
		}

		return null;
	}
}
