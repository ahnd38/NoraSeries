package org.jp.illg.dstar.service.reflectorname.define;

public enum ReflectorHostsImporterType {
	URL("org.jp.illg.dstar.service.reflectorname.importers.url.URLImporter"),
	JARLMultiForward("org.jp.illg.dstar.service.reflectorname.importers.multiforward.MultiForwardRepeaterHostsImporter"),
	;

	private final String implementationClassName;

	private ReflectorHostsImporterType(final String implementationClassName) {
		this.implementationClassName = implementationClassName;
	}

	public String getName() {
		return this.toString();
	}

	public String getImplementationClassName(){
		return this.implementationClassName;
	}

	public static ReflectorHostsImporterType getTypeByName(final String name) {
		return getTypeByName(name, false);
	}

	public static ReflectorHostsImporterType getTypeByNameIgnoreCase(final String name) {
		return getTypeByName(name, true);
	}

	private static ReflectorHostsImporterType getTypeByName(final String name, final boolean ignoreCase) {
		for(final ReflectorHostsImporterType t : values()) {
			if(
				(!ignoreCase && t.getName().equals(name)) ||
				(ignoreCase && t.getName().equalsIgnoreCase(name))
			) {return t;}
		}

		return null;
	}
}
