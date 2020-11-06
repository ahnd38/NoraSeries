package org.jp.illg.dstar.model.defines;

public enum AccessScope {
	Private,
	Public,
	Unknown,
	;

	public String getTypeName() {
		return this.toString();
	}

	public static AccessScope getTypeByTypeNameIgnoreCase(final String typeName) {
		for(final AccessScope v : values()) {
			if(v.getTypeName().equalsIgnoreCase(typeName))
				return v;
		}

		return null;
	}
}
