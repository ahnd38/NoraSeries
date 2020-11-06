package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.AccessScope;

import lombok.Data;

@Data
public class GatewayStatusData {

	private String webSocketRoomId;

	private String gatewayCallsign;

	private AccessScope scope;

	private double latitude;

	private double longitude;

	private String description1;

	private String description2;

	private String url;

	private String name;

	private String location;

	private String dashboardUrl;

	private String gatewayGlobalIpAddress;

	private String lastheardCallsign;

	private boolean useProxy;

	private String proxyServerAddress;

	private int proxyServerPort;

	public GatewayStatusData() {
		super();
	}

}
