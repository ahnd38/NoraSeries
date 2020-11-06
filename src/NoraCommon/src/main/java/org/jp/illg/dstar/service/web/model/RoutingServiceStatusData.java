package org.jp.illg.dstar.service.web.model;

import java.util.List;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoutingServiceStatusData extends StatusData {

	private RoutingServiceTypes routingServiceType;

	private List<RoutingServiceServerStatus> routingServiceStatus;

	public RoutingServiceStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}

}
