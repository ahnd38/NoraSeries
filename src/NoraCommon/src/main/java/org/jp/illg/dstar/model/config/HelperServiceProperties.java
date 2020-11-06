package org.jp.illg.dstar.model.config;

import lombok.Data;
import lombok.Getter;

@Data
public class HelperServiceProperties {

	private int port;

	@Getter
	private static final int portDefault = 42611;

	public HelperServiceProperties() {
		super();

		port = portDefault;
	}
}
