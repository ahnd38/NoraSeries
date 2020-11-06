package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

public enum AutoConnectMode {
	Unknown,
	TimeBased,
	Fixed,
	;

	public static AutoConnectMode getModeByModeString(String mode) {
		for(AutoConnectMode v : values()) {
			if(v.getModeString().equals(mode)) {return v;}
		}

		return AutoConnectMode.Unknown;
	}

	public String getModeString() {
		return this.toString();
	}
}
