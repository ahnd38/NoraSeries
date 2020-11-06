package org.jp.illg.nora.gateway.service.norausers.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import lombok.Data;

@Data
public class RepeaterInformation {

	private String repeaterCallsign;

	private String repeaterType;

	private String linkedReflectorCallsign;

	private String routingService;

	private String lastHeardCallsign;

	private double frequency;

	private double frequencyOffset;

	private double range;

	private double latitude;

	private double longitude;

	private double agl;

	private String description1;

	private String description2;

	private String url;

	private ModemInformation[] modemInformation;

	private String scope;

	private String name;

	private String location;

	private Map<String, String> repeaterProperties;

	public RepeaterInformation() {
		super();

		repeaterCallsign = DSTARDefines.EmptyLongCallsign;
		repeaterType = RepeaterTypes.Unknown.getTypeName();
		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		routingService = RoutingServiceTypes.Unknown.getTypeName();
		lastHeardCallsign = DSTARDefines.EmptyLongCallsign;

		frequency = 0.0d;
		frequencyOffset = 0.0d;
		range = 0.0d;
		latitude = 0.0d;
		longitude = 0.0d;
		agl = 0.0d;
		description1 = "";
		description2 = "";
		url = "";

		modemInformation = null;
		scope = AccessScope.Unknown.getTypeName();

		name = "";
		location = "";

		repeaterProperties = new ConcurrentHashMap<>();
	}

}
