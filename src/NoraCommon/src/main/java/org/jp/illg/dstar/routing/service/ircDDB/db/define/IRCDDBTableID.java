package org.jp.illg.dstar.routing.service.ircDDB.db.define;

import lombok.Getter;

public enum IRCDDBTableID {
	Unknown(-1),
	UserVSAreaRepeaterTable(0),
	AreaRepeaterVSZoneRepeaterTable(1),
	ZoneRepeaterVSIPAddressTable(9),
	;

	@Getter
	private int tableID;

	private IRCDDBTableID(final int tableID) {
		this.tableID = tableID;
	}

	public IRCDDBTableID getTypeByTableID(final int tableID) {
		for(final IRCDDBTableID v : values()) {
			if(v.getTableID() == tableID) {return v;}
		}

		return Unknown;
	}
}
