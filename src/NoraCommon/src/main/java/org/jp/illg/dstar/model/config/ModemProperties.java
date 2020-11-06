package org.jp.illg.dstar.model.config;

import java.util.Properties;

import org.jp.illg.dstar.model.ModemTransceiverMode;

import lombok.Getter;
import lombok.Setter;

public class ModemProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter
	private String type;

	@Getter
	@Setter
	private boolean allowDIRECT;

	@Getter
	@Setter
	private String scope;

	@Getter
	@Setter
	private ModemTransceiverMode transceiverMode;

	private Properties configurationProperties;


	/**
	 * @return configulationProperties
	 */
	public synchronized Properties getConfigurationProperties() {
		if(this.configurationProperties != null)
			return configurationProperties;
		else
			return (this.configurationProperties = new Properties());
	}
}
