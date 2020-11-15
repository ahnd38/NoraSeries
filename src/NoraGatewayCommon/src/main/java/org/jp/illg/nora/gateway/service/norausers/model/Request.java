package org.jp.illg.nora.gateway.service.norausers.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class Request {

	private String callsign;

	private String id;

	private String requestType;

	private StatusInformation statusInformation;

	private String applicationName;

	private String applicationVersion;

	private String runningOsName;

	private String crashReport;

	public Request(@NonNull final String callsign, final String id) {
		super();

		this.callsign = callsign;
		this.id = id != null ? id : "";
		requestType = RequestType.Unknown.getTypeName();
		statusInformation = null;
		applicationName = "";
		applicationVersion = "";
		runningOsName = "";
		crashReport = "";
	}

	public Request(@NonNull final String callsign) {
		this(callsign, null);
	}

}
