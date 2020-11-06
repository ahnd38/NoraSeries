package org.jp.illg.dstar.reflector.protocol.jarllink.model;

import lombok.Getter;

public enum JARLLinkTransmitType {
	Send        ((byte)'s'),
	Response    ((byte)'r'),
	;

	@Getter
	private final byte value;

	private JARLLinkTransmitType(final byte value) {
		this.value = value;
	}

	public static JARLLinkTransmitType getTypeByValue(final byte value) {
		for(final JARLLinkTransmitType t : values()) {
			if(t.getValue() == value) {return t;}
		}

		return null;
	}
}
