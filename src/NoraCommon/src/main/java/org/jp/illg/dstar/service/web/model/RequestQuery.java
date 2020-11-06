package org.jp.illg.dstar.service.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RequestQuery {

	private String routingServiceType;

	private String queryCallsign;

	public RequestQuery() {
		super();
	}
}
