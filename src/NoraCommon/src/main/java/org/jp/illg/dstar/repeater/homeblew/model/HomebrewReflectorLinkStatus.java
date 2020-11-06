package org.jp.illg.dstar.repeater.homeblew.model;


public enum HomebrewReflectorLinkStatus {
	LS_NONE									((byte)0),
	LS_PENDING_IRCDDB				((byte)1),
	LS_LINKING_LOOPBACK			((byte)2),
	LS_LINKING_DEXTRA				((byte)3),
	LS_LINKING_DPLUS				((byte)4),
	LS_LINKING_DCS					((byte)5),
	LS_LINKING_CCS					((byte)6),
	LS_LINKED_LOOPBACK			((byte)7),
	LS_LINKED_DEXTRA				((byte)8),
	LS_LINKED_DPLUS					((byte)9),
	LS_LINKED_DCS						((byte)10),
	LS_LINKED_CCS						((byte)11),
	;

	private final byte val;

	private HomebrewReflectorLinkStatus(final byte val) {
		this.val = val;
	}

	public byte getValue() {
		return this.val;
	}

	public static HomebrewReflectorLinkStatus getTypeByValue(byte value) {
		for(HomebrewReflectorLinkStatus v : values()) {
			if(v.getValue() == value) {return v;}
		}
		return HomebrewReflectorLinkStatus.LS_NONE;
	}
}
