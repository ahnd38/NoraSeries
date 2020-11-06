package org.jp.illg.dstar.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CallsignEntry {

	private boolean enable;

	private String callsign;

	public CallsignEntry() {
		super();
	}

}
