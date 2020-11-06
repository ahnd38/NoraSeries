package org.jp.illg.dstar.routing.service.jptrust.model;

import lombok.Getter;

public enum StatusType {
	Login(0x00),
	Logoff(0x01),
	PTTOn(0x05),
	PTTOff(0x06),
	PTTUpdate(0x07),
	KeepAlive(0x99),
	;

	@Getter
	private final int value;

	StatusType(final int value) {
		this.value = value;
	}

	public StatusType getTypeByValue(final int value) {
		for(final StatusType type : values()) {
			if(type.getValue() == value)
				return type;
		}

		return null;
	}
}
