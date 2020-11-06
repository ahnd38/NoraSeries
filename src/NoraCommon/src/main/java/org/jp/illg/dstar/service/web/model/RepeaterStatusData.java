package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RepeaterStatusData extends StatusData{

	private RepeaterTypes repeaterType;

	private String repeaterCallsign;

	private String gatewayCallsign;

	private String lastheardCallsign;

	private String linkedReflectorCallsign;

	private RoutingServiceTypes routingService;

	private boolean routingServiceFixed;

	private boolean useRoutingService;

	private boolean allowDIRECT;

	private boolean transparentMode;

	private boolean autoDisconnectFromReflectorOnTxToG2Route;

	private AccessScope scope;

	private double latitude;

	private double longitude;

	private double agl;

	private String descriotion1;

	private String description2;

	private String url;

	private String name;

	private String location;

	private double range;

	private double frequency;

	private double frequencyOffset;

	public RepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}

}
