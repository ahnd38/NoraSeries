package org.jp.illg.dstar.routing.model;

import lombok.Getter;
import lombok.Setter;

public class UserRoutingInfo extends RoutingInfo {

	@Getter
	@Setter
	private String yourCallsign;

	@Getter
	@Setter
	private String repeaterCallsign;

	public UserRoutingInfo() {
		super();
	}
}
