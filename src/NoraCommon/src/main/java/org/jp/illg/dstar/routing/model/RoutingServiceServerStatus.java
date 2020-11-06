package org.jp.illg.dstar.routing.model;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoutingServiceServerStatus {

	private RoutingServiceTypes serviceType;

	private RoutingServiceStatus serviceStatus;

	private boolean useProxyGateway;

	private String proxyGatewayAddress;

	private int proxyGatewayPort;

	private String serverAddress;

	private int serverPort;
}
