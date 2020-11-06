package org.jp.illg.dstar.jarl.xchange.model;

import lombok.Getter;

public enum XChangeRouteFlag {

	Gateway((byte)0x80),
	ZoneRepeater((byte)0x40),
	XChange((byte)0x08),
	Forward((byte)0x04),
	;

	@Getter
	private final byte value;

	@Getter
	private static final byte mask = (byte)0xCC;

	private XChangeRouteFlag(final byte value) {
		this.value = value;
	}

	public static XChangeRouteFlagData valueOf(final byte value) {
		final XChangeRouteFlagData result = new XChangeRouteFlagData();

		for(final XChangeRouteFlag flag : values()) {
			if((value & getMask() & flag.getValue()) != 0x0) {result.addRouteFlag(flag);}
		}

		return result;
	}
}
