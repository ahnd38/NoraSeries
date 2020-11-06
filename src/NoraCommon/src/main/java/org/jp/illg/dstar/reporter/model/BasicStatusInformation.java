package org.jp.illg.dstar.reporter.model;

import java.util.ArrayList;
import java.util.List;

import com.annimon.stream.Collectors;
import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class BasicStatusInformation {

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
	private long applicationUptime;

	@Getter
	@Setter
	private int currentViewers;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private List<String> applicationLogs;

	private org.jp.illg.util.mon.cpu.model.CPUUsageReport cpuUsageReport;

	private GatewayStatusReport gatewayStatusReport;

	private List<RepeaterStatusReport> repeaterStatusReports;

	private List<ReflectorStatusReport> reflectorStatusReports;

	private List<RoutingServiceStatusReport> routingStatusReports;


	public BasicStatusInformation(){
		super();

		applicationName = "";
		applicationVersion = "";
		applicationRunningOS = "";
		applicationUptime = 0;
		currentViewers = 0;

		repeaterStatusReports = new ArrayList<>();
		reflectorStatusReports = new ArrayList<>();
		routingStatusReports = new ArrayList<>();
	}

	public boolean equalsNoraGatewayStatusInformation(BasicStatusInformation o) {
		if(o == null) {return false;}

		if(
			(getGatewayStatusReport().equals(o.getGatewayStatusReport())) &&
			(equalsRepeaterStatusReports(o.getRepeaterStatusReports()))
		)
			return true;
		else
			return false;
	}

	private boolean equalsRepeaterStatusReports(List<RepeaterStatusReport> o) {
		if(this.repeaterStatusReports == null && o == null)
			return true;

		if(
			(this.repeaterStatusReports == null && o != null) ||
			(this.repeaterStatusReports != null && o == null) ||
			(this.repeaterStatusReports.size() != o.size())
		) {return false;}


		List<RepeaterStatusReport> a =
			Stream.of(this.repeaterStatusReports)
			.sorted(ComparatorCompat.comparing(new Function<RepeaterStatusReport, String>(){
				@Override
				public String apply(RepeaterStatusReport t) {
					return t.getRepeaterCallsign();
				}

			}))
			.collect(Collectors.<RepeaterStatusReport>toList());

		List<RepeaterStatusReport> b =
				Stream.of(o)
				.sorted(ComparatorCompat.comparing(new Function<RepeaterStatusReport, String>(){
					@Override
					public String apply(RepeaterStatusReport t) {
						return t.getRepeaterCallsign();
					}

				}))
				.collect(Collectors.<RepeaterStatusReport>toList());

		for(int i = 0; i < a.size(); i++) {
			RepeaterStatusReport aa = a.get(i);
			RepeaterStatusReport bb = b.get(i);

			if(!aa.equalsRepeaterStatusReport(bb)) {return false;}
		}

		return true;
	}
}
