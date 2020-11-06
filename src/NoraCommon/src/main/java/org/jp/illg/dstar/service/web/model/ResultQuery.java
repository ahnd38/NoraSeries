package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;

import lombok.Data;

@Data
public class ResultQuery {

	private String result;

	private String routingServiceType;

	private String queryCallsign;

	private String message;

	private String areaRepeaterCallsign;

	private String zoneRepeaterCallsign;

	private String gatewayHostName;
	private String gatewayIpAddress;

	private String repeaterName;

	private long timestamp;


	public ResultQuery() {
		super();

		result = RoutingServiceResult.Failed.toString();
		routingServiceType = RoutingServiceTypes.Unknown.toString();
		queryCallsign = DSTARDefines.EmptyLongCallsign;
		message = "";

		areaRepeaterCallsign = DSTARDefines.EmptyLongCallsign;
		zoneRepeaterCallsign = DSTARDefines.EmptyLongCallsign;
		gatewayHostName = "";
		gatewayIpAddress = "";

		repeaterName = "";

		timestamp = 0;
	}
}
