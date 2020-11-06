package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import lombok.Getter;

public class RoutingServiceDashboardInfo {
	
	@Getter
	private final RoutingServiceTypes routingType;
	
	@Getter
	private final String webSocketRoomId;
	
	public RoutingServiceDashboardInfo(
		final RoutingServiceTypes routingType,
		final String webSocketRoomId
	) {
		super();
		
		this.routingType = routingType;
		this.webSocketRoomId = webSocketRoomId;
	}
	
}
