package org.jp.illg.dstar.service.web.model;

import lombok.Getter;
import lombok.NonNull;

public class GatewayDashboardInfo {

	@Getter
	private final String gatewayCallsign;

	@Getter
	private final String webSocketRoomId;

	public GatewayDashboardInfo(
		@NonNull final String gatewayCallsign, @NonNull final String webSocketRoomId
	) {
		super();

		this.gatewayCallsign = gatewayCallsign;
		this.webSocketRoomId = webSocketRoomId;
	}

}
