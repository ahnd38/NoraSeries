package org.jp.illg.dstar.routing.service.ircDDB.model;


import org.jp.illg.dstar.routing.define.RoutingServiceTasks;

import lombok.Getter;

public enum IRCDDBQueryType {
	Unknown				(null),
	FindUser			(RoutingServiceTasks.FindUser),
	FindRepeater		(RoutingServiceTasks.FindRepeater),
	SendHeard			(RoutingServiceTasks.PositionUpdate),
	;

	@Getter
	private final RoutingServiceTasks routingServiceType;

	private IRCDDBQueryType(RoutingServiceTasks routingServiceType) {
		this.routingServiceType = routingServiceType;
	}
}
