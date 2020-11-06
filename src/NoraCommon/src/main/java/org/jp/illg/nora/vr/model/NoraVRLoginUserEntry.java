package org.jp.illg.nora.vr.model;

import lombok.Getter;

public class NoraVRLoginUserEntry implements Cloneable {

	@Getter
	private String loginCallsign;

	@Getter
	private String loginPassword;

	@Getter
	private boolean allowRFNode;

	private NoraVRLoginUserEntry() {
		super();
	}

	public NoraVRLoginUserEntry(
		final String loginCallsign,
		final String loginPassword,
		final boolean allowRFNode
	) {
		this();

		this.loginCallsign = loginCallsign;
		this.loginPassword = loginPassword;
		this.allowRFNode = allowRFNode;
	}

	@Override
	public NoraVRLoginUserEntry clone() {
		NoraVRLoginUserEntry copy = null;
		try {
			copy = (NoraVRLoginUserEntry)super.clone();

			copy.loginCallsign = loginCallsign;
			copy.loginPassword = loginPassword;
			copy.allowRFNode = allowRFNode;

			return copy;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}
