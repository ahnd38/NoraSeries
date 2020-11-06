package org.jp.illg.util.io.fum.model;

import org.jp.illg.util.Timer;
import org.jp.illg.util.io.fum.FileUpdateMonitoringFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class MonitoringEntry {

	@Getter
	private final Timer intervalTimekeeper;

	@Getter
	private final FileUpdateMonitoringFunction function;

	@Getter
	private final String targetFilePath;

	@Getter
	@Setter
	private long lastUpdateTimestamp;

	@Getter
	@Setter
	private byte[] lastSuccessFile;

	@Getter
	@Setter
	private boolean error;

	@Getter
	@Setter
	private boolean processing;

	@Getter
	@Setter
	private boolean initialized;


	public MonitoringEntry(
		@NonNull final FileUpdateMonitoringFunction function
	) {
		this.function = function;
		targetFilePath = function.getTargetFilePath();

		intervalTimekeeper = new Timer();
		intervalTimekeeper.updateTimestamp();
		lastUpdateTimestamp = 0;
		lastSuccessFile = null;

		error = false;
		processing = false;
		initialized = false;
	}

}
