package org.jp.illg.dstar.routing.model;

import java.util.List;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoutingServiceStatusData {

	private RoutingServiceTypes serviceType;

	private List<RoutingServiceServerStatus> serviceStatus;

}
