package org.jp.illg.dstar.routing.model;

import org.jp.illg.dstar.routing.define.RoutingServiceResult;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PositionUpdateInfo {

	private String userCallsign;

	private RoutingServiceResult routingResult;

	public PositionUpdateInfo() {
		super();
	}

}
