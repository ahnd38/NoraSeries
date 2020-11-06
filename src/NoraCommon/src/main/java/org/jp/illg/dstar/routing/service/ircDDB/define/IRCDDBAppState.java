package org.jp.illg.dstar.routing.service.ircDDB.define;

import lombok.Getter;

public enum IRCDDBAppState {
	WaitForNetworkStart(0),
	ConnectToDB(1),
	ChooseServer(2),
	CheckSendList(3),
	RequestSendList(4),
	WaitSendList(5),
	EndOfSendList(6),
	Standby(7),
	DisconnectFromDB(10),
	;

	@Getter
	private final int stateNumber;

	private IRCDDBAppState(final int stateNumber) {
		this.stateNumber = stateNumber;
	}

	public String getStateName() {
		return this.toString();
	}
}
