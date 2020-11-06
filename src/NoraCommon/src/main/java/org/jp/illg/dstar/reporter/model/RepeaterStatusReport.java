package org.jp.illg.dstar.reporter.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import com.annimon.stream.Collectors;
import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class RepeaterStatusReport {

	private String repeaterCallsign;

	private RoutingServiceTypes routingService;

	private RepeaterTypes repeaterType;

	private String linkedReflectorCallsign;

	private String lastHeardCallsign;

	private AccessScope scope;

	private double latitude;

	private double longitude;

	private double agl;

	private String description1;

	private String description2;

	private String url;

	private String name;

	private String location;

	private double range;

	private double frequency;

	private double frequencyOffset;

	private Map<String, String> repeaterProperties;

	@Setter(AccessLevel.PRIVATE)
	private List<RepeaterRouteStatusReport> routeReports;

	@Setter(AccessLevel.PRIVATE)
	private List<ModemStatusReport> modemReports;

	public RepeaterStatusReport(){
		super();

		routeReports = new ArrayList<>();
		modemReports = new ArrayList<>();

		repeaterCallsign = DSTARDefines.EmptyLongCallsign;
		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		lastHeardCallsign = DSTARDefines.EmptyLongCallsign;
		scope = AccessScope.Unknown;
		latitude = 0.0d;
		longitude = 0.0d;
		agl = 0.0d;
		description1 = "";
		description2 = "";
		url = "";
		name = "";
		location = "";
		range = 0.0d;
		frequency = 0.0d;
		frequencyOffset = 0.0d;

		repeaterProperties = new ConcurrentHashMap<>();
	}


	public boolean equalsRepeaterStatusReport(RepeaterStatusReport o) {
		if(o == null) {return false;}

		if(
			(getRepeaterCallsign().equals(o.getRepeaterCallsign())) &&
			(getLinkedReflectorCallsign().equals(o.getLinkedReflectorCallsign())) &&
			(getRoutingService().equals(o.getRoutingService())) &&
			(getRepeaterType().equals(o.getRepeaterType())) &&
			(equalsRepeaterRouteStatusReports(o.getRouteReports()))
		)
			return true;
		else
			return false;
	}

	private boolean equalsRepeaterRouteStatusReports(List<RepeaterRouteStatusReport> o) {
		if(this.routeReports == null && o == null)
			return true;

		if(
			(this.routeReports == null && o != null) ||
			(this.routeReports != null && o == null) ||
			(this.routeReports.size() != o.size())
		) {return false;}


		List<RepeaterRouteStatusReport> a =
			Stream.of(this.routeReports)
			.sorted(ComparatorCompat.comparing(new Function<RepeaterRouteStatusReport, Long>(){
				@Override
				public Long apply(RepeaterRouteStatusReport t) {
					return t.getFrameSequenceStartTime();
				}

			}))
			.collect(Collectors.<RepeaterRouteStatusReport>toList());

		List<RepeaterRouteStatusReport> b =
			Stream.of(o)
			.sorted(ComparatorCompat.comparing(new Function<RepeaterRouteStatusReport, Long>(){
				@Override
				public Long apply(RepeaterRouteStatusReport t) {
					return t.getFrameSequenceStartTime();
				}

			}))
			.collect(Collectors.<RepeaterRouteStatusReport>toList());

		return a.equals(b);
	}
}
