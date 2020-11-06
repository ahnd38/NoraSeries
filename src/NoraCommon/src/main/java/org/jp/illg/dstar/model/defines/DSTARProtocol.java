package org.jp.illg.dstar.model.defines;

import lombok.Getter;

public enum DSTARProtocol {
	Unknown(-1, -1),
	DExtra(0, 30001),
	DPlus(1, 20001),
	DCS(2, 30051),
	JARLLink(8, 51000),
	G123(10, 40000),
	Homeblew(20, 20010),
	Internal(30, 0),
	XChange(1000, 0),
	ICOM(10000, 0),
	;

	@Getter
	private final int value;

	@Getter
	private final int portNumber;

	private DSTARProtocol(final int value, int portNumber) {
		this.value = value;
		this.portNumber = portNumber;
	}

	public String getName() {
		return this.toString();
	}

	public static DSTARProtocol getProtocolByValue(final int value) {
		for(DSTARProtocol p : values()) {
			if(p.getValue() == value) {return p;}
		}
		return Unknown;
	}

	public static DSTARProtocol getProtocolByName(final String name) {
		return getProtocolByName(name, false);
	}

	public static DSTARProtocol getProtocolByNameIgnoreCase(final String name) {
		return getProtocolByName(name, true);
	}

	private static DSTARProtocol getProtocolByName(final String name, final boolean ignoreCase) {
		for(DSTARProtocol p : values()) {
			if(
				p.getName().equals(name) ||
				(ignoreCase && p.getName().equalsIgnoreCase(name))
			) {return p;}
		}

		return Unknown;
	}
}
