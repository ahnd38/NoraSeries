package org.jp.illg.dstar.routing.service.ircDDB.db.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jp.illg.dstar.routing.service.ircDDB.db.define.IRCDDBTableID;

import lombok.Data;

@Data
public class IRCDDBDatabaseModel {

	private Map<IRCDDBTableID, List<IRCDDBRecord>> database;

	public IRCDDBDatabaseModel() {
		super();

		database = new HashMap<>();
	}

}
