package org.jp.illg.dstar.model.config;

import lombok.Data;
import lombok.Getter;

@Data
public class CrashReportServiceProperties {

	private boolean enable;

	@Getter
	private static final boolean enableDefault = true;
}
