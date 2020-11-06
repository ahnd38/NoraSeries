package org.jp.illg.dstar.model.config;

import java.util.Properties;

import lombok.Getter;
import lombok.Setter;

public class ReflectorProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter
	private String type;

	private Properties configurationProperties;

	@Getter
	@Setter
	private String applicationVersion;

	@Getter
	@Setter
	private String applicationName;

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
