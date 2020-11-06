package org.jp.illg.dstar.model.config;

import lombok.Data;

@Data
public class RemoteControlProperties implements Cloneable{

	private boolean enable;

	private int port;

	private String password;

	public RemoteControlProperties() {
		super();
	}

	@Override
	public RemoteControlProperties clone() {
		RemoteControlProperties copy = null;
		try {
			copy = (RemoteControlProperties)super.clone();

			copy.enable = enable;
			copy.port = port;
			copy.password = password;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex.toString());
		}

		return copy;
	}

}
