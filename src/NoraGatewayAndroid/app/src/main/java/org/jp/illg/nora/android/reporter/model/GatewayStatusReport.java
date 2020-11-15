package org.jp.illg.nora.android.reporter.model;

import org.parceler.Parcel;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class GatewayStatusReport {

	private String gatewayCallsign;

	private List<GatewayRouteStatusReport> routeReports;

	public GatewayStatusReport(){
		super();

		setRouteReports(new ArrayList<GatewayRouteStatusReport>());
	}

	public GatewayStatusReport(org.jp.illg.dstar.reporter.model.GatewayStatusReport src){
		super();

		if(src != null){
			setGatewayCallsign(src.getGatewayCallsign());

			setRouteReports(new ArrayList<GatewayRouteStatusReport>());
			if(src.getRouteReports() != null){
				for(org.jp.illg.dstar.reporter.model.GatewayRouteStatusReport srcRouteStatus : src.getRouteReports())
					getRouteReports().add(new GatewayRouteStatusReport(srcRouteStatus));
			}
		}
	}
}
