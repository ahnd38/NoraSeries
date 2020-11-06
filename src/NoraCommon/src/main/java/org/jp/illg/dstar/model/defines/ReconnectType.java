package org.jp.illg.dstar.model.defines;

import lombok.Getter;

public enum ReconnectType {
	ReconnectUnknown(-1),
	ReconnectNEVER(0),
	ReconnectFIXED(1),
	Reconnect5MINS(2),
	Reconnect10MINS(3),
	Reconnect15MINS(4),
	Reconnect20MINS(5),
	Reconnect25MINS(6),
	Reconnect30MINS(7),
	Reconnect60MINS(8),
	Reconnect90MINS(9),
	Reconnect120MINS(10),
	Reconnect180MINS(11),
	;

	@Getter
	private final int value;

	private ReconnectType(int value) {
		this.value = value;
	}

	public static ReconnectType getReconnectTypeByVallue(final int value) {
		for(ReconnectType p : values()) {
			if(p.getValue() == value) {return p;}
		}
		return ReconnectType.ReconnectUnknown;
	}
}
