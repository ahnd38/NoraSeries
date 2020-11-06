package org.jp.illg.dstar.model;

import lombok.Getter;

public enum BackBoneHeaderType {
	DD((byte)0x40),
	DV((byte)0x20),
	;

	@Getter
	private static final int mask = (byte)0x60;

	@Getter
	private byte value;

	BackBoneHeaderType(final byte value) {
		this.value = value;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static BackBoneHeaderType getTypeByValue(final byte value) {
		for(final BackBoneHeaderType t : values()) {
			if(t.getValue() == (value & getMask())) {return t;}
		}

		return null;
	}
}
