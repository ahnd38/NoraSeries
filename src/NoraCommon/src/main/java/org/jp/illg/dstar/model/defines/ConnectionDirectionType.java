package org.jp.illg.dstar.model.defines;

import lombok.Getter;

public enum ConnectionDirectionType {
	Unknown(-1),
	INCOMING(0),
	OUTGOING(1),
	BIDIRECTIONAL(2),
	;

	@Getter
	private final int value;

	private ConnectionDirectionType(int value) {
		this.value = value;
	}
	
	public String getTypeName() {
		return this.toString();
	}
	
	public static ConnectionDirectionType getDirectionTypeByTypeName(
		final String typeName
	) {
		return getDirectionTypeByTypeName(typeName, false);
	}
	
	public static ConnectionDirectionType getDirectionTypeByTypeNameIgnoreCase(
		final String typeName
	) {
		return getDirectionTypeByTypeName(typeName, true);
	}

	public static ConnectionDirectionType getDirectionTypeByValue(final int value) {
		for(ConnectionDirectionType p : values()) {
			if(p.getValue() == value) {return p;}
		}
		return Unknown;
	}
	
	private static ConnectionDirectionType getDirectionTypeByTypeName(
		final String typeName, final boolean ignoreCase
	) {
		for(ConnectionDirectionType p : values()) {
			if(
				p.getTypeName().equals(typeName) ||
				(ignoreCase && p.getTypeName().equalsIgnoreCase(typeName))
			) {return p;}
		}
		return Unknown;
	}
}
