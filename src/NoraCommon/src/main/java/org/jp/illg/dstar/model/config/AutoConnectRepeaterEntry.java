package org.jp.illg.dstar.model.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

public class AutoConnectRepeaterEntry{
	@Getter
	private String repeaterCallsign;

	@Getter
	private String mode;

	@Getter
	private Map<String, Map<String, String>> entries;

	public AutoConnectRepeaterEntry(String repeaterCallsign, String mode) {
		super();

		this.repeaterCallsign = repeaterCallsign;
		this.mode = mode;

		this.entries = new HashMap<>();
	}
}
