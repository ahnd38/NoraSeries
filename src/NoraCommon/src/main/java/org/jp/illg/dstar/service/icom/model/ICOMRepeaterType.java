package org.jp.illg.dstar.service.icom.model;

import org.jp.illg.dstar.service.icom.repeaters.idrp2c.IDRP2CCommunicationService;

import lombok.Getter;
import lombok.NonNull;

public enum ICOMRepeaterType {
	ID_RP2C(IDRP2CCommunicationService.class.getName()),
	Unknown(""),
	;

	@Getter
	private final String className;

	private ICOMRepeaterType(final String className) {
		this.className = className;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static ICOMRepeaterType getTypeByTypeName(
		@NonNull final String typeName
	) {
		return getTypeByTypeName(typeName, false);
	}

	public static ICOMRepeaterType getTypeByTypeNameIgnoreCase(
		@NonNull final String typeName
	) {
		return getTypeByTypeName(typeName, true);
	}

	private static ICOMRepeaterType getTypeByTypeName(String typeName, boolean ignoreCase) {
		final String targetTypeName = typeName.replaceAll("-", "_");

		for(ICOMRepeaterType v : values()) {
			if(
				(!ignoreCase && v.getTypeName().equals(targetTypeName)) ||
				(ignoreCase && v.getTypeName().equalsIgnoreCase(targetTypeName))
			) {return v;}
		}

		return null;
	}
}
