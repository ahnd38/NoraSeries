package org.jp.illg.dstar.routing.model;

import java.net.InetAddress;

import org.jp.illg.dstar.routing.define.RoutingServiceResult;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class RoutingInfo {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter
	private RoutingServiceResult routingResult;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter
	private InetAddress gatewayAddress;

	@Getter
	@Setter
	private long timestamp;

	public RoutingInfo() {
		super();
		setCreatedTimestamp(System.currentTimeMillis());
		setRoutingResult(RoutingServiceResult.Failed);
	}
}
