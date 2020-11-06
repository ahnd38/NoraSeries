package org.jp.illg.dstar.model.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

public class AutoConnectProperties{

	@Getter
	@Setter
	private boolean enable;

	@Getter
	private Map<String, AutoConnectRepeaterEntry> repeaterEntries;

	public AutoConnectProperties() {
		super();

		setEnable(true);
		this.repeaterEntries = new HashMap<>();
	}
}

