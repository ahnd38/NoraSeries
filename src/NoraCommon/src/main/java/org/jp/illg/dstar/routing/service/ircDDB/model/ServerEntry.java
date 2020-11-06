package org.jp.illg.dstar.routing.service.ircDDB.model;

import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBClient;
import org.jp.illg.util.irc.IRCClient;

import lombok.Getter;
import lombok.NonNull;

public class ServerEntry {

	@Getter
	private final IrcDDBClient ddbClient;

	@Getter
	private final IRCClient ircClient;

	@Getter
	private final ServerProperties properties;

	public ServerEntry(
		@NonNull final IrcDDBClient ddbClient,
		@NonNull final IRCClient ircClient,
		@NonNull final ServerProperties properties
	) {
		super();

		this.ddbClient = ddbClient;
		this.ircClient = ircClient;
		this.properties = properties;
	}

}
