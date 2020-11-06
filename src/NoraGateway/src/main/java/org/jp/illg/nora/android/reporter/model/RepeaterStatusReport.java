package org.jp.illg.nora.android.reporter.model;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.parceler.Parcel;

import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterStatusReport {

	private String repeaterCallsign = "";

	private String linkedReflectorCallsign = "";

	private RoutingServiceTypes routingService = RoutingServiceTypes.Unknown;

	@Setter(AccessLevel.PRIVATE)
	private List<RepeaterRouteStatusReport> routeReports;

	public RepeaterStatusReport(){
		super();

		setRouteReports(new ArrayList<RepeaterRouteStatusReport>());
	}

	public RepeaterStatusReport(org.jp.illg.dstar.reporter.model.RepeaterStatusReport src){
		super();

		if(src != null){
			setRepeaterCallsign(src.getRepeaterCallsign());
			setLinkedReflectorCallsign(src.getLinkedReflectorCallsign());
			setRoutingService(src.getRoutingService());

			setRouteReports(new ArrayList<RepeaterRouteStatusReport>());
			if(src.getRouteReports() != null){
				for(org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport srcRouteStatus : src.getRouteReports())
					getRouteReports().add(new RepeaterRouteStatusReport(srcRouteStatus));
			}
		}
	}
}
