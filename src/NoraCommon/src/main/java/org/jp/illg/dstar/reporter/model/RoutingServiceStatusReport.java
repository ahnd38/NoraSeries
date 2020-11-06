package org.jp.illg.dstar.reporter.model;

import java.util.List;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class RoutingServiceStatusReport {

	@Getter
	private RoutingServiceTypes serviceType;

	@Getter
	private List<RoutingServiceServerStatus> serviceStatus;


	public RoutingServiceStatusReport(
		final RoutingServiceTypes serviceType,
		final List<RoutingServiceServerStatus> serviceStatus
	) {
		super();

		this.serviceType = serviceType;
		this.serviceStatus = serviceStatus;
	}
}
