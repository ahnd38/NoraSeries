package org.jp.illg.nora.gateway.service.norausers.model;

import lombok.NonNull;

public enum ResultType {
	Unknown,
	ACK,
	NAK,
	;
	
	public String getTypeName() {
		return this.toString();
	}
	
	public static ResultType getTypeByValueIgnoreCase(@NonNull final String typeName) {
		return valueOf(typeName, true);
	}
	
	public static ResultType getTypeByValue(@NonNull final String typeName) {
		return valueOf(typeName, false);
	}
	
	public static ResultType valueOf(@NonNull final String value, final boolean ignoreCase) {
		for(ResultType t : values()) {
			if(
				(ignoreCase && t.getTypeName().equalsIgnoreCase(value)) ||
				(!ignoreCase && t.getTypeName().equals(value))
			) {
				return t;
			}
		}
		
		return null;
	}
}
