package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AnalogModemPiGPIOStatusData extends ModemStatusData{

	private boolean uplinkActive;

	private boolean downlinkActive;

	private AnalogModemPiGPIOHeaderData uplinkHeader;

	private AnalogModemPiGPIOHeaderData downlinkHeader;

	private boolean uplinkConfigUseGateway;

	private String uplinkConfigYourCallsign;

	private String uplinkConfigMyCallsign;

	public AnalogModemPiGPIOStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}

}
