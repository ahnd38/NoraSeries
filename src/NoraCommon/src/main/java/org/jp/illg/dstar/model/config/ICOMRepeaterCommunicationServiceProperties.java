package org.jp.illg.dstar.model.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class ICOMRepeaterCommunicationServiceProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	private List<ICOMRepeaterProperties> repeaters;

	public ICOMRepeaterCommunicationServiceProperties() {
		super();

		enable = false;
		repeaters = new ArrayList<>(8);
	}
}
