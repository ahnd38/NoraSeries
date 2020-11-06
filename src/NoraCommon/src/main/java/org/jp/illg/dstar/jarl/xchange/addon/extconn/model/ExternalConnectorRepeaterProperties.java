package org.jp.illg.dstar.jarl.xchange.addon.extconn.model;

import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AuthType;

import lombok.Getter;
import lombok.Setter;

public class ExternalConnectorRepeaterProperties extends RepeaterProperties {

	@Getter
	@Setter
	private AuthType authMode;
	public static final AuthType authModeDefault = AuthType.INCOMING;

	@Getter
	@Setter
	private boolean useXChange;
	public static final boolean useXChangeDefault = true;


	public ExternalConnectorRepeaterProperties() {
		super();

		setAuthMode(AuthType.UNKNOWN);
		setUseXChange(false);
	}

}
