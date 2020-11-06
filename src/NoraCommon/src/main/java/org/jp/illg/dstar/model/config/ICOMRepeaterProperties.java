package org.jp.illg.dstar.model.config;

import java.util.Properties;

import org.jp.illg.dstar.service.icom.model.ICOMRepeaterType;

import lombok.Getter;
import lombok.Setter;

public class ICOMRepeaterProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter
	private ICOMRepeaterType repeaterType;

	@Getter
	private Properties properties;

	public ICOMRepeaterProperties() {
		super();

		repeaterType = ICOMRepeaterType.Unknown;
		properties = new Properties();
	}

}
