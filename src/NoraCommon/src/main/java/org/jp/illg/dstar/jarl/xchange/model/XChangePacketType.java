package org.jp.illg.dstar.jarl.xchange.model;

import lombok.Getter;

public enum XChangePacketType {

	Dummy((byte)0x00),
	Data((byte)0x11),
	Voice((byte)0x12),
	LocationUpdate((byte)0x21),
	;

	@Getter
	private final byte value;

	@Getter
	private static final byte mask = 0x33;

	private XChangePacketType(final byte value) {
		this.value = value;
	}

	public static XChangePacketType valueOf(final byte value) {
		for(final XChangePacketType v : values()) {
			if((value & mask) == v.value) {return v;}
		}

		return null;
	}
}
