package org.jp.illg.dstar.service.web.model;

import lombok.Data;

@Data
public class ResponseHeardLog {

	private HeardEntry[] heardLog;

	public ResponseHeardLog(HeardEntry[] heardLog) {
		super();

		this.heardLog = heardLog;
	}

}
