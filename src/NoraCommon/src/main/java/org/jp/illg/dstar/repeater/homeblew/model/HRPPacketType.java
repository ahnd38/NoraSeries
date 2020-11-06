package org.jp.illg.dstar.repeater.homeblew.model;


public enum HRPPacketType {
	Unknown			((byte)0xFF),
	Text			((byte)0x00),
	TempText		((byte)0x01),
	Status			((byte)0x04),
	Poll			((byte)0x0A),
	Register		((byte)0x0B),
	Header			((byte)0x20),
	BusyHeader		((byte)0x22),
	AMBE			((byte)0x21),
	BusyAMBE		((byte)0x23),
	;

	private final byte val;

	HRPPacketType(final byte val) {
		this.val = val;
	}

	public byte getValue() {
		return this.val;
	}

	public static HRPPacketType getTypeByValue(byte val) {
		for(HRPPacketType v : values()) {
			if(v.getValue() == val) {return v;}
		}
		return HRPPacketType.Unknown;
	}
}
