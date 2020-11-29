package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.nora.gateway.NoraGatewayConfiguration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServicesConfigurator {

	private static final String logHeader = ServicesConfigurator.class.getSimpleName() + " : ";

	private ServicesConfigurator() {}

	public static boolean readServices(
		final String parentKey,
		final XMLConfiguration config,
		final NoraGatewayConfiguration dstConfig
	) {
		final String key = "Services";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {

			boolean isNewConfiguraion = true;
			try {
				config.configurationAt(allKey);
			}catch(ConfigurationRuntimeException ex) {
				isNewConfiguraion = false;
			}

			final HierarchicalConfiguration<ImmutableNode> node =
				isNewConfiguraion ? config.configurationAt(allKey) : config;

			StatusInformationFileOutputServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getStatusInformationFileOutputServiceProperties()
			);

			WebRemoteControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getWebRemoteControlServiceProperties()
			);

			HelperServiceControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getHelperServiceProperties()
			);

			CrashReportServiceControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getCrashReportServiceProperties()
			);

			ICOMRepeaterCommunicationServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getIcomRepeaterCommunicationServiceProperties()
			);

			RepeaterNameServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getRepeaterNameServiceProperties()
			);

			ReflectorNameServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getReflectorNameServiceProperties()
			);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}
}
