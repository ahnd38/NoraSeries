package org.jp.illg.dstar.model.defines;

public enum AuthType {
	UNKNOWN,
	INCOMING,
	OUTGOING,
	BIDIRECTIONAL,
	OFF,
	;

	public String getTypeName() {
		return this.toString();
	}

	public static AuthType getTypeByTypeNameIgnoreCase(final String typeName) {
		for(final AuthType v : values()) {
			if(v.getTypeName().equalsIgnoreCase(typeName))
				return v;
		}

		return null;
	}
}
