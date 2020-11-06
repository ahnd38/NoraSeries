package org.jp.illg.dstar.model.defines;

import org.jp.illg.dstar.routing.service.gltrust.GlobalTrustClientService;
import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBRoutingService;
import org.jp.illg.dstar.routing.service.jptrust.JpTrustClientService;

public enum RoutingServiceTypes {
	Unknown("", ""),
	JapanTrust(
		JpTrustClientService.class.getName(),
		"GIS"
	),
	GlobalTrust(
		GlobalTrustClientService.class.getName(),
		"GS"
	),
	ircDDB(
		IrcDDBRoutingService.class.getName(),
		"G"
	),
	;

	private final String className;
	private String moduleBlacklist;

	RoutingServiceTypes(final String className, final String moduleBlacklist) {
		this.className = className;
		this.moduleBlacklist = moduleBlacklist;
	}

	public String getClassName() {
		return this.className;
	}

	public String getModuleBlacklist() {
		if(this.moduleBlacklist != null)
			return this.moduleBlacklist;
		else
			return "";
	}

	public String getTypeName() {
		return this.name();
	}

	public static RoutingServiceTypes getTypeByClassName(String className) {
		for(RoutingServiceTypes v : values()) {
			if(v.getClassName().equals(className)) {return v;}
		}
		return RoutingServiceTypes.Unknown;
	}

	public static RoutingServiceTypes getTypeByTypeName(String typeName){
		return getTypeByTypeName(typeName, false);
	}
	
	public static RoutingServiceTypes getTypeByTypeNameIgnoreCase(String typeName){
		return getTypeByTypeName(typeName, true);
	}

	public static String getClassNameByType(RoutingServiceTypes type) {
		for(RoutingServiceTypes v : values()) {
			if(v == type) {return v.getClassName();}
		}
		return RoutingServiceTypes.Unknown.getClassName();
	}

	public static String getClassNameByTypeName(String typeName) {
		for(RoutingServiceTypes v : values()) {
			if(v.getTypeName().equals(typeName)) {return v.getClassName();}
		}
		return RoutingServiceTypes.Unknown.getClassName();
	}
	
	private static RoutingServiceTypes getTypeByTypeName(String typeName, boolean ignoreCase) {
		for(RoutingServiceTypes v : values()) {
			if(
				(!ignoreCase && v.getTypeName().equals(typeName)) ||
				(ignoreCase && v.getTypeName().equalsIgnoreCase(typeName))
			) {return v;}
		}
		return RoutingServiceTypes.Unknown;
	}
}
