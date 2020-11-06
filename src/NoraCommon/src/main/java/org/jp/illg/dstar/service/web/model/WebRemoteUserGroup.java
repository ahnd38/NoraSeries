package org.jp.illg.dstar.service.web.model;

import lombok.Getter;

public enum WebRemoteUserGroup {
	Administrators       (1),
	Users                (100),
	Guests               (1000),
	;

	@Getter
	private final int level;

	private WebRemoteUserGroup(final int level) {
		this.level = level;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static WebRemoteUserGroup getTypeByName(final String typeName) {
		return getTypeByName(typeName, false);
	}

	public static WebRemoteUserGroup getTypeByNameIgnoreCase(final String typeName) {
		return getTypeByName(typeName, true);
	}

	private static WebRemoteUserGroup getTypeByName(final String typeName, final boolean ignoreCase) {
		for(final WebRemoteUserGroup v : values()) {
			if(
				(ignoreCase && v.getTypeName().equalsIgnoreCase(typeName)) ||
				(!ignoreCase && v.getTypeName().equals(typeName))
			) {return v;}
		}

		return null;
	}
}
