package org.jp.illg.dstar.model;

public enum ModemTransceiverMode {

	Unknown,
	HalfDuplex,
	FullDuplex,
	TxOnly,
	RxOnly,
	;

	public String getTypeName() {
		return toString();
	}

	public static ModemTransceiverMode getTypeByTypeNameIgnoreCase(final String typeName) {
		return getTypeByTypeNameIgnoreCase(typeName, true);
	}

	private static ModemTransceiverMode getTypeByTypeNameIgnoreCase(final String typeName, boolean ignoreCase) {
		for(ModemTransceiverMode v : values()) {
			if(
				(!ignoreCase && v.getTypeName().equals(typeName)) ||
				(ignoreCase && v.getTypeName().equalsIgnoreCase(typeName))
			) {return v;}
		}
		return Unknown;
	}
}
