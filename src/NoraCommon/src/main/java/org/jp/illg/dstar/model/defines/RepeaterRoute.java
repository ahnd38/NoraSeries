package org.jp.illg.dstar.model.defines;

import lombok.Getter;

public enum RepeaterRoute{
	Unknown((byte)0xFF),
	TO_REPEATER((byte)0x40),
	TO_TERMINAL((byte)0x00),
	;

	private final byte val;

	@Getter
	private static final byte mask = 0x40;

	RepeaterRoute(final byte val) {
		this.val = val;
	}

	public byte getValue() {
		return this.val;
	}

	public static RepeaterRoute getTypeByValue(byte value) {
		for(RepeaterRoute v : values()) {
			if(v.getValue() == (value & mask)) {return v;}
		}
		return RepeaterRoute.Unknown;
	}
}
