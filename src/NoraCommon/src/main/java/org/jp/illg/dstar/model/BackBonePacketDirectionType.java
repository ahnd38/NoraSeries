package org.jp.illg.dstar.model;

import lombok.Getter;

public enum BackBonePacketDirectionType {
	Send   (0x73),
	Ack    (0x72),
	;

	@Getter
	private final int value;

	private BackBonePacketDirectionType(final int value) {
		this.value = value;
	}

	public static BackBonePacketDirectionType getTypeByValue(final int value) {
		for(final BackBonePacketDirectionType t : values()) {
			if(t.getValue() == value) {return t;}
		}

		return null;
	}
}
