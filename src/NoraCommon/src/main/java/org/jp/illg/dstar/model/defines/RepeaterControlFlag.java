package org.jp.illg.dstar.model.defines;

import lombok.Getter;

public enum RepeaterControlFlag{
	Unknown((byte)0xFF),
	NOTHING_NULL((byte)0x0),
	CANT_REPEAT((byte)0x1),
	NO_REPLY((byte)0x2),
	ACK((byte)0x3),
	REQUEST_RETRY((byte)0x4),
	AUTO_REPLY((byte)0x6),
	REPEATER_CONTROL((byte)0x7);

	private final byte val;

	@Getter
	private static final byte mask = 0x7;

	private RepeaterControlFlag(final byte val) {
		this.val = val;
	}

	public byte getValue() {
		return this.val;
	}

	public static RepeaterControlFlag getTypeByValue(byte value) {
		for(RepeaterControlFlag v : values()) {
			if(v.getValue() == (value & mask)) {return v;}
		}
		return RepeaterControlFlag.Unknown;
	}
}
