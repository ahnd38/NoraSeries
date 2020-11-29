package org.jp.illg.nora.gateway;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.jp.illg.nora.gateway.configurators.GatewayConfigurator;
import org.jp.illg.nora.gateway.configurators.RepeaterConfigurator;
import org.jp.illg.nora.gateway.configurators.ServicesConfigurator;
import org.jp.illg.util.ApplicationInformation;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGatewayConfigurator {

	private static final String logHeader;

	static {
		logHeader = NoraGatewayConfigurator.class.getSimpleName() + " : ";
	}


	public static boolean readConfiguration(
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final NoraGatewayConfiguration dstConfig,
		@NonNull final InputStream configurationFile
	) {
		try {
			final XMLConfiguration config =
				new BasicConfigurationBuilder<>(XMLConfiguration.class)
				.configure(new Parameters().xml()).getConfiguration();

			final FileHandler fh = new FileHandler(config);
			fh.load(configurationFile);

			return readConfiguration(applicationInformation, dstConfig, config);
		}catch(ConfigurationException ex){
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not read Configuration file " + configurationFile);
			}

			return false;
		}
	}

	public static boolean readConfiguration(
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final NoraGatewayConfiguration dstConfig,
		@NonNull final File configurationFile
	) {
		if (!configurationFile.exists()) {
			if(log.isErrorEnabled())
				log.error("Not exist configuration file...[" + configurationFile.toString() + "]");

			return false;
		}

		final Parameters params = new Parameters();

		final FileBasedConfigurationBuilder<XMLConfiguration> builder =
			new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
			.configure(params.fileBased().setFile(configurationFile));

		builder.setAutoSave(false);

		try {
			final XMLConfiguration config = builder.getConfiguration();

			return readConfiguration(applicationInformation, dstConfig, config);
		}catch(ConfigurationException ex) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not read Configuration file = " + configurationFile.getAbsolutePath());
			}

			return false;
		}
	}

	private static boolean readConfiguration(
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final NoraGatewayConfiguration dstConfig,
		@NonNull final XMLConfiguration config
	) {
		final String key = "";
		final String allKey = key;

		if(
			!GatewayConfigurator.readGateway(
				applicationInformation, allKey, config, dstConfig.getGatewayProperties()
			) ||
			!RepeaterConfigurator.readRepeaters(allKey, config, dstConfig) ||
			!ServicesConfigurator.readServices(allKey, config, dstConfig)
		) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Fatal error during configuration read.");

			return false;
		}

		return true;
	}
}
