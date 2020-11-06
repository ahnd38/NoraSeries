package org.jp.illg.dstar.model.config;

import java.util.Properties;

import lombok.Getter;
import lombok.Setter;

public class RoutingServiceProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter
	private String type;

	private Properties configurationProperties;


	/**
	 * @return configurationProperties
	 */
	public synchronized Properties getConfigurationProperties() {
		if(this.configurationProperties != null)
			return configurationProperties;
		else
			return (this.configurationProperties = new Properties());
	}
}
