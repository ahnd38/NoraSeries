package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalTrustClientServiceStatusData extends RoutingServiceStatusData {
	
	public GlobalTrustClientServiceStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
