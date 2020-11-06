package org.jp.illg.dstar.service.repeatername.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RepeaterData {

	private String name;

	private String repeaterCallsign;

	private String gatewayCallsign;

	private double frequencyMHz;

	private double frequencyOffsetMHz;

	private double latitude;

	private double longitude;

	private int utcOffset;
}
