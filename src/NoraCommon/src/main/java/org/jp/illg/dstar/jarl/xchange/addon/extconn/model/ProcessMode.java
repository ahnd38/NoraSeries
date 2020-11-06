package org.jp.illg.dstar.jarl.xchange.addon.extconn.model;

public enum ProcessMode {
	Unknown,
	G2ToRepeater,
	RepeaterToG2,
	ReflectorToRepeater,		// Reflector -> Repeater
	RepeaterToReflector,	 	// Reflector <- Repeater
	RepeaterToNull,				// routing to null
	LocalCQ,
	Control,					// Control Command
	;
}
