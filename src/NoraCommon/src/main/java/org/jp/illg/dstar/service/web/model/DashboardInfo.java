package org.jp.illg.dstar.service.web.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class DashboardInfo {

	@Getter
	@Setter
	private String applicationName;

	@Getter
	@Setter
	private String applicationVersion;

	@Getter
	@Setter
	private String applicationRunningOS;

	@Getter
	@Setter
	private GatewayDashboardInfo gatewayInfo;
	
	@Getter
	@Setter
	private String requiredDashboardVersion;

	@Getter
	private final List<RepeaterDashboardInfo> repeaterInfos;
	
	@Getter
	private final List<ReflectorDashboardInfo> reflectorInfos;
	
	@Getter
	private final List<RoutingServiceDashboardInfo> routingInfos;

	public DashboardInfo() {
		super();

		repeaterInfos = new ArrayList<>();
		reflectorInfos = new ArrayList<>();
		routingInfos = new ArrayList<>();
	}

}
