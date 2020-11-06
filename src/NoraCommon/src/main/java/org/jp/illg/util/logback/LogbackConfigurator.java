package org.jp.illg.util.logback;

import java.io.InputStream;

import org.jp.illg.util.io.fum.FileUpdateMonitoringFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogbackConfigurator {

	private final String logTag = LogbackConfigurator.class.getSimpleName() + " : ";

	private final String configurationFilePath;

	@Getter
	@Setter
	private boolean resetConfigOnReload;

	@Getter
	private final FileUpdateMonitoringFunction fileUpdateMonitoringFunction =
		new FileUpdateMonitoringFunction() {
			@Override
			public String getTargetFilePath() {
				return configurationFilePath;
			}

			@Override
			public int getMonitoringIntervalTimeSeconds() {
				return 30;
			}

			@Override
			public boolean initialize(InputStream targetFile) {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Initializing logback configuration file = " +
						configurationFilePath
					);
				}

				return initializeLogger(targetFile);
			}

			@Override
			public boolean fileUpdate(InputStream targetFile) {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Detected logback configuration file update, going to execute reconfigure process..." +
						configurationFilePath
					);
				}

				return initializeLogger(targetFile);
			}

			@Override
			public boolean rollback(InputStream targetFile) {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Rollback to last success logback configuration file, Could not reconfigure " +
						configurationFilePath
					);
				}

				return initializeLogger(targetFile);
			}
		};

	public LogbackConfigurator(
		final boolean resetConfigOnReload,
		@NonNull final String configurationFilePath
	) {
		super();

		this.resetConfigOnReload = resetConfigOnReload;
		this.configurationFilePath = configurationFilePath;
	}

	public LogbackConfigurator(
		@NonNull final String configurationFilePath
	) {
		this(false, configurationFilePath);
	}

	private boolean initializeLogger(final InputStream configurationFile) {
		return LogbackUtil.initializeLogger(configurationFile, resetConfigOnReload);
	}
}
