package org.jp.illg.dstar.routing.service.jptrust.model;

import lombok.Getter;

public enum JpTrustResult {
	Success(0x0),
	NoDATA(0x1),
	NowDisable(0x2),
	GWRegistRequest(0x3),
	GWRegistFailed(0x4),
	;

	@Getter
	private final int value;

	private JpTrustResult(final int value) {
		this.value = value;
	}

	public static JpTrustResult getResultByValue(final int value) {
		for(JpTrustResult v : values()) {
			if(v.getValue() == value) {return v;}
		}

		return JpTrustResult.Success;
	}
}
