package org.jp.illg.nora.vr.model;

public enum NoraVRCodecType {
	PCM,
	Opus8k,
	Opus24k,
	Opus64k,
	Opus,
	AMBE,
	;

	public String getTypeName() {
		return toString();
	}

	public static NoraVRCodecType getTypeByTypeName(final String typeName) {
		for(final NoraVRCodecType type : values()) {
			if(type.getTypeName().equals(typeName)) {return type;}
		}

		return null;
	}
}
