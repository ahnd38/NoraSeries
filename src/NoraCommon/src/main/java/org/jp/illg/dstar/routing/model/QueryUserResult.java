package org.jp.illg.dstar.routing.model;

import java.net.InetAddress;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QueryUserResult {
	
	private String userCallsign;
	
	private String areaRepeaterCallsign;
	
	private String zoneRepeaterCallsign;
	
	private InetAddress gatewayAddress;
	
}
