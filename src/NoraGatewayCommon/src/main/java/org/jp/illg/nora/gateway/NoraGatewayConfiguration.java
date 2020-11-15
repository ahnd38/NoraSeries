package org.jp.illg.nora.gateway;

import java.util.HashMap;
import java.util.Map;

import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.config.ServiceProperties;

import lombok.Data;

@Data
public class NoraGatewayConfiguration {

	private GatewayProperties gatewayProperties;

	private Map<String, RepeaterProperties> repeaterProperties;

	private ServiceProperties serviceProperties;

	public NoraGatewayConfiguration() {
		gatewayProperties = new GatewayProperties();

		repeaterProperties = new HashMap<>();

		serviceProperties = new ServiceProperties();
	}
}
