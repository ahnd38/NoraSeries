package org.jp.illg.dstar.repeater.internal.model;

public enum ProcessMode {
	ModemToGateway,
	ModemToModem,
	ModemToNull,
	GatewayToModemValid,
	GatewayToModemInvalid,
	GatewayToGatewayBusy,
	FlagReply,
	;
}
