package org.jp.illg.dstar.repeater.modem.mmdvm.define;

import lombok.Getter;

public enum MMDVMMode {
	Unknown(         (byte) 0xFF),

	MODE_IDLE(       (byte) 0),
	MODE_DSTAR(      (byte) 1),
	MODE_DMR(        (byte) 2),
	MODE_YSF(        (byte) 3),
	MODE_P25(        (byte) 4),
	MODE_NXDN(       (byte) 5),
	MODE_POCSAG(     (byte) 6),
	MODE_CW(         (byte) 98),
	MODE_LOCKOUT(    (byte) 99),
	MODE_ERROR(      (byte) 100),
	MODE_QUIT(       (byte) 110),
	;

	@Getter
	private final byte modeCode;

	private MMDVMMode(final byte modeCode) {
		this.modeCode = modeCode;
	}

	public static MMDVMMode getModeByModeCode(final byte modeCode) {
		for(MMDVMMode t : values()) {
			if(t.getModeCode() == modeCode) {return t;}
		}

		return Unknown;
	}
}
