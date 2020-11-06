package org.jp.illg.nora.vr;

import lombok.Getter;

public class NoraVRUser {

	@Getter
	private boolean remoteUser;

	@Getter
	private String callsignLong;

	@Getter
	private String callsignShort;


	public NoraVRUser(
		final boolean remoteUser,
		final String callsignLong,
		final String callsignShort
	) {
		super();

		this.remoteUser = remoteUser;
		this.callsignLong = callsignLong;
		this.callsignShort = callsignShort;
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indentLevel; i++) {sb.append(' ');}

		sb.append("Callsign:");
		sb.append(callsignLong);
		sb.append('_');
		sb.append(callsignShort);

		sb.append('/');

		sb.append("isRemoteUser:");
		sb.append(remoteUser);

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}
}
