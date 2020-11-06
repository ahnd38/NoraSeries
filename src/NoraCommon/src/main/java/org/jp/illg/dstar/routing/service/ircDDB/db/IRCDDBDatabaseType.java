package org.jp.illg.dstar.routing.service.ircDDB.db;

import lombok.Getter;

public enum IRCDDBDatabaseType {
	Unknown(""),
	InMemory("org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.IRCDDBInMemoryDB"),
	;

	@Getter
	private String className;

	private IRCDDBDatabaseType(final String className) {
		this.className = className;
	}
}
