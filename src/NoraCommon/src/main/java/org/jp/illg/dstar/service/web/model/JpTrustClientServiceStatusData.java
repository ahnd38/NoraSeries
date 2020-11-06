package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JpTrustClientServiceStatusData extends RoutingServiceStatusData {
	
	public JpTrustClientServiceStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
