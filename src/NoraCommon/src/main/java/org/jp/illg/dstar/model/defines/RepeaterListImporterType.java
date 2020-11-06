package org.jp.illg.dstar.model.defines;

public enum RepeaterListImporterType {
	ICOM(org.jp.illg.dstar.service.repeatername.importers.icom.IcomRepeaterListImporter.class),
	;

	private final Class<?> implementationClass;

	private RepeaterListImporterType(final Class<?> implementationClass) {
		this.implementationClass = implementationClass;
	}

	public String getName() {
		return this.toString();
	}

	public Class<?> getImplementationClass(){
		return this.implementationClass;
	}

	public static RepeaterListImporterType getTypeByName(final String name) {
		return getTypeByName(name, false);
	}

	public static RepeaterListImporterType getTypeByNameIgnoreCase(final String name) {
		return getTypeByName(name, true);
	}

	private static RepeaterListImporterType getTypeByName(final String name, final boolean ignoreCase) {
		for(final RepeaterListImporterType t : values()) {
			if(
				(!ignoreCase && t.getName().equals(name)) ||
				(ignoreCase && t.getName().equalsIgnoreCase(name))
			) {return t;}
		}

		return null;
	}
}
