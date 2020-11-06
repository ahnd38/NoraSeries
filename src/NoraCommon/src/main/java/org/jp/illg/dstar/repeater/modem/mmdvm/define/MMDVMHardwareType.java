package org.jp.illg.dstar.repeater.modem.mmdvm.define;

public enum MMDVMHardwareType {

	Unknown("Unknown"),
	MMDVM("MMDVM "),
	DVMEGA("DVMEGA"),
	ZUMspot("ZUMspot"),
	MMDVM_HS_HAT("MMDVM_HS_Hat"),
	MMDVM_HS_DUAL_HAT("MMDVM_HS_Dual_Hat"),
	NANO_HOTSPOT("Nano_hotSPOT"),
	NANO_DV("Nano_DV"),
	MMDVM_HS("MMDVM_HS-"),
	;

	private final String typeName;

	private MMDVMHardwareType(final String typeName) {
		this.typeName = typeName;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static MMDVMHardwareType getTypeByTypeName(final String typeName) {
		for(MMDVMHardwareType t : values()) {
			if(t.typeName.equals(typeName)) {return t;}
		}

		return Unknown;
	}

	public static MMDVMHardwareType getTypeByTypeNameStartWith(final String typeName) {
		if(typeName == null) {return Unknown;}

		for(MMDVMHardwareType t : values()) {
			if(typeName.startsWith(t.typeName)) {return t;}
		}

		return Unknown;
	}
}
