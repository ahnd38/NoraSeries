package org.jp.illg.util.ambe.dv3k.model;

public enum DV3KProcessState {
	Initialize,
	SoftReset,
	ReadProductionID,
	ReadVerstionString,
	SetRATEP,
	MainState,
	Wait,
	;
}
