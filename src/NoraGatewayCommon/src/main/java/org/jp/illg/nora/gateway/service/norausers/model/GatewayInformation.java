package org.jp.illg.nora.gateway.service.norausers.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.AccessScope;

import lombok.Data;

@Data
public class GatewayInformation {

	private String gatewayCallsign;

	private String lastHeardCallsign;

	private double latitude;

	private double longitude;

	private String description1;

	private String description2;

	private String url;

	private boolean useProxy;

	private String proxyServerAddress;

	private int proxyServerPort;

	private String scope;

	private String name;

	private String location;

	private String dashboardUrl;

	public GatewayInformation() {
		super();

		gatewayCallsign = DSTARDefines.EmptyLongCallsign;
		lastHeardCallsign = DSTARDefines.EmptyLongCallsign;

		latitude = 0.0d;
		longitude = 0.0d;
		description1 = "";
		description2 = "";
		url = "";

		useProxy = false;
		proxyServerAddress = "";
		proxyServerPort = -1;

		scope = AccessScope.Unknown.getTypeName();
		name = "";
		location = "";
		dashboardUrl = "";
	}

}
