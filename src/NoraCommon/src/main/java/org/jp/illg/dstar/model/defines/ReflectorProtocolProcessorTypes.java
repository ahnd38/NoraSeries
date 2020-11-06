package org.jp.illg.dstar.model.defines;

import org.jp.illg.dstar.reflector.protocol.dcs.DCSCommunicationService;
import org.jp.illg.dstar.reflector.protocol.dextra.DExtraCommunicationService;
import org.jp.illg.dstar.reflector.protocol.dplus.DPlusCommunicationService;
import org.jp.illg.dstar.reflector.protocol.jarllink.JARLLinkCommunicationService;

public enum ReflectorProtocolProcessorTypes {
	Unknown(DSTARProtocol.Unknown , ""),
	DExtra(DSTARProtocol.DExtra, DExtraCommunicationService.class.getName()),
	DCS(DSTARProtocol.DCS, DCSCommunicationService.class.getName()),
	DPlus(DSTARProtocol.DPlus, DPlusCommunicationService.class.getName()),
	JARLLink(DSTARProtocol.JARLLink, JARLLinkCommunicationService.class.getName()),
	;

	private final DSTARProtocol protocol;
	private final String className;


	ReflectorProtocolProcessorTypes(final DSTARProtocol protocol, final String className) {
		this.className = className;
		this.protocol = protocol;
	}

	public String getClassName() {
		return this.className;
	}

	public String getTypeName() {
		return this.toString();
	}

	public DSTARProtocol getProtocol() {
		return this.protocol;
	}

	public static ReflectorProtocolProcessorTypes getTypeByClassName(String className) {
		for(ReflectorProtocolProcessorTypes v : values()) {
			if(v.getClassName() == className) {return v;}
		}
		return ReflectorProtocolProcessorTypes.Unknown;
	}

	public static ReflectorProtocolProcessorTypes getTypeByTypeName(String typeName) {
		return getTypeByTypeName(typeName, false);
	}
	
	public static ReflectorProtocolProcessorTypes getTypeByTypeNameIgnoreCase(String typeName) {
		return getTypeByTypeName(typeName, true);
	}

	public static String getClassNameByType(ReflectorProtocolProcessorTypes type) {
		for(ReflectorProtocolProcessorTypes v : values()) {
			if(v == type) {return v.getClassName();}
		}
		return ReflectorProtocolProcessorTypes.Unknown.getClassName();
	}

	public static String getClassNameByTypeName(String typeName) {
		for(ReflectorProtocolProcessorTypes v : values()) {
			if(v.getTypeName().equals(typeName)) {return v.getClassName();}
		}
		return ReflectorProtocolProcessorTypes.Unknown.getClassName();
	}
	
	private static ReflectorProtocolProcessorTypes getTypeByTypeName(final String typeName, final boolean ignoreCase) {
		for(final ReflectorProtocolProcessorTypes t : values()) {
			if(
				(ignoreCase && t.getTypeName().equalsIgnoreCase(typeName)) ||
				(!ignoreCase && t.getTypeName().equals(typeName))
			) {return t;}
		}
		
		return Unknown;
	}
}
