package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import org.jp.illg.dstar.model.DSTARRepeater;

import lombok.Getter;
import lombok.Setter;

public class AutoConnectRequestData {

	@Setter
	@Getter
	private String linkReflectorCallsign;

	@Getter
	@Setter
	private DSTARRepeater repeater;

	public AutoConnectRequestData(DSTARRepeater repeater, String linkReflectorCallsign) {
		super();

		setRepeater(repeater);
		setLinkReflectorCallsign(linkReflectorCallsign);
	}

}
