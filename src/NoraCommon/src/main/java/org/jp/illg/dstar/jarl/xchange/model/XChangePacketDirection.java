package org.jp.illg.dstar.jarl.xchange.model;

import lombok.Getter;

public enum XChangePacketDirection {

	FromGateway('s'),
	ToGateway('r'),
	;

	@Getter
	private final char value;

	private XChangePacketDirection(final char value) {
		this.value = value;
	}

	public static XChangePacketDirection valueOf(final char value) {
		for(final XChangePacketDirection v : values()) {
			if(v.value == value) {return v;}
		}

		return null;
	}
}
