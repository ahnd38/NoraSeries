package org.jp.illg.dstar.reporter.model;

import java.util.ArrayList;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.HeardEntry;
import org.jp.illg.dstar.model.defines.AccessScope;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class GatewayStatusReport {

	private String gatewayCallsign;

	private String lastHeardCallsign;

	private boolean useProxy;

	private String proxyServerAddress;

	private int proxyServerPort;

	private AccessScope scope;

	private double latitude;

	private double longitude;

	private String description1;

	private String description2;

	private String url;

	private String name;

	private String location;

	private String dashboardUrl;

	@Setter(AccessLevel.PRIVATE)
	private List<GatewayRouteStatusReport> routeReports;

	@Setter(AccessLevel.PRIVATE)
	private List<RoutingServiceStatusReport> routingServiceReports;

	@Setter(AccessLevel.PRIVATE)
	private List<HeardEntry> heardReports;

	public GatewayStatusReport(){
		super();

		setRouteReports(new ArrayList<GatewayRouteStatusReport>());
		setRoutingServiceReports(new ArrayList<RoutingServiceStatusReport>());
		setHeardReports(new ArrayList<HeardEntry>());

		setGatewayCallsign(DSTARDefines.EmptyLongCallsign);

		setLastHeardCallsign(DSTARDefines.EmptyLongCallsign);
		setUseProxy(false);
		setProxyServerAddress("");
		setProxyServerPort(0);
		setScope(AccessScope.Unknown);
		setLatitude(0.0d);
		setLongitude(0.0d);
		setDescription1("");
		setDescription2("");
		setUrl("");
		setName("");
		setLocation("");
		setDashboardUrl("");
	}


	public boolean equalsGatewayStatusReport(GatewayStatusReport o) {
		if(o == null) {return false;}

		if(
			(getGatewayCallsign().equals(o.getGatewayCallsign())) &&
			(equalRouteReports(o.getRouteReports())) &&
			(equalRoutingServiceReports(o.getRoutingServiceReports())) &&
			(equalHeardReports(o.getHeardReports()))
		)
			return true;
		else
			return false;
	}

	private boolean equalRoutingServiceReports(List<RoutingServiceStatusReport> o) {
		if(
			routingServiceReports == null && o == null
		) {
			return true;
		}
		else if(
			(routingServiceReports == null && o != null) ||
			(routingServiceReports != null && o == null) ||
			(routingServiceReports.size() != o.size())
		) {
			return false;
		}

		return routingServiceReports.containsAll(o);
	}

	private boolean equalRouteReports(List<GatewayRouteStatusReport> o) {
		if(
			this.routeReports == null && o == null
		) {
			return true;
		}
		else if(
			(this.routeReports == null && o != null) ||
			(this.routeReports != null && o == null) ||
			(this.routeReports.size() != o.size())
		) {return false;}


		return routeReports.containsAll(o);
	}

	private boolean equalHeardReports(List<HeardEntry> o) {
		if(heardReports == null && o == null) {
			return true;
		}
		else if(
			(heardReports == null && o != null) ||
			(heardReports != null && o == null) ||
			(heardReports.size() != o.size())
		) {
			return false;
		}

		return heardReports.containsAll(o);
	}
}
