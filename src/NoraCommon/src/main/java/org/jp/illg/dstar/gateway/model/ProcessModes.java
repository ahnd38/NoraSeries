package org.jp.illg.dstar.gateway.model;

public enum ProcessModes {
	Unknown,
	RepeaterToReflector,	 	// Reflector <- Repeater
	ReflectorToRepeater,		// Reflector -> Repeater
	G2ToRepeater, 				// G2 <- Repeater
	RepeaterToG2, 				// G2 -> Repeater
	RepeaterToNull,				// routing to null
	RepeaterToCrossband,		// cross band
	HeardOnly,					// heard only
	Control						// Control Command
	;
}
