package org.jp.illg.dstar.routing.model;

import lombok.Getter;
import lombok.Setter;

public class RepeaterRoutingInfo extends RoutingInfo {

	@Getter
	@Setter
	private String repeaterCallsign;

	public RepeaterRoutingInfo() {
		super();
	}
}
