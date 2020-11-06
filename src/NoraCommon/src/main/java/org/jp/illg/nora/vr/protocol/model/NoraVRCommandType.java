package org.jp.illg.nora.vr.protocol.model;

import lombok.Getter;

public enum NoraVRCommandType {
	ACK(1),
	NAK(1),
	LOGINUSR(1),
	LGINUSR2(2),
	LOGIN_CC(1),
	LOGIN_HS(1),
	LOGINACK(1),
	CONFSET(1),
	LOGOUT(1),
	PING(1),
	PONG(1),
	VTPCM(1),
	VTOPUS(1),
	VTAMBE(1),
	RLINKGET(1),
	RLINK(1),
	RSRVGET(2),
	RSRV(2),
	ACLOGGET(2),
	ACLOG(2),
	USLSTGET(2),
	USLST(2),
	RINFOGET(2),
	RINFO(2),
	;

	@Getter
	private final String commandString;

	@Getter
	private final int protocolVersion;

	private NoraVRCommandType(final int protocolVersion) {
		String cmdString = String.format("%-8s", this.toString());
		this.commandString = cmdString.replace(' ', '_');

		this.protocolVersion = protocolVersion;
	}

	public static NoraVRCommandType getTypeByCommandString(final String commandString) {
		for(final NoraVRCommandType type : values()) {
			if(type.getCommandString().equals(commandString))
				return type;
		}

		return null;
	}
}
