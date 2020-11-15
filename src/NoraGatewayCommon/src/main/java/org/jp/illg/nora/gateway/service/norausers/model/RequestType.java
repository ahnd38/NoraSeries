package org.jp.illg.nora.gateway.service.norausers.model;

import lombok.NonNull;

public enum RequestType {
	Unknown,
	CheckVersion,
	RequestID,
	UpdateStatusInformation,
	CrashReport,
	;

	public String getTypeName() {
		return this.toString();
	}

	public static RequestType valueOfIgnoreCase(@NonNull final String typeName) {
		return valueOf(typeName, true);
	}

	public static RequestType getTypeByValue(@NonNull final String typeName) {
		return valueOf(typeName, false);
	}

	public static RequestType valueOf(@NonNull final String value, final boolean ignoreCase) {
		for(RequestType t : values()) {
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
