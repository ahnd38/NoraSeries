package org.jp.illg.dstar.model.config;

import java.util.Properties;

import lombok.Getter;
import lombok.Setter;

public class G1ProxyTransporterProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	private Properties configurationProperties;

	@Getter
	@Setter
	private String applicationVersion;

	@Getter
	@Setter
	private String applicationName;


	public G1ProxyTransporterProperties() {
		super();

		configurationProperties = new Properties();
	}

}
