package org.jp.illg.nora.gateway.configurators;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.ReflectorProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorCommunicationServiceConfigurator {

	private static final String logHeader =
		ReflectorCommunicationServiceConfigurator.class.getSimpleName() + " : ";


	private ReflectorCommunicationServiceConfigurator() {}

	public static boolean readReflectors(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> gatewayConfig,
		final GatewayProperties gatewayProperties
	) {
		final String key = "Reflectors";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try{
			final HierarchicalConfiguration<ImmutableNode> reflectorConfig =
				gatewayConfig.configurationAt(key);

			attr = "[@hostsFile]";
			String hostsFile = reflectorConfig.getString(attr, "./config/hosts.txt");
			gatewayProperties.setHostsFile(hostsFile);

			if(!readReflector(allKey, reflectorConfig, gatewayProperties)) {
				return false;
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readReflector(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> reflectorConfig,
		final GatewayProperties gatewayProperties
	) {
		final String key = "Reflector";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		final List<HierarchicalConfiguration<ImmutableNode>> reflectors =
			reflectorConfig.configurationsAt(key);

		for(final HierarchicalConfiguration<ImmutableNode> reflectorNode : reflectors) {
			final ReflectorProperties reflector = new ReflectorProperties();

			attr = "[@enable]";
			boolean enable = false;
			try {
				enable = reflectorNode.getBoolean(attr, true);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
			}
			reflector.setEnable(enable);

			attr = "[@type]";
			String reflectorType = reflectorNode.getString(attr, "");
			if(reflectorType == null || "".equals(reflectorType)) {continue;}
			reflector.setType(reflectorType);

			readConfigurationProperties(
				allKey, reflectorNode, reflector, gatewayProperties
			);

			gatewayProperties.getReflectors().put(reflectorType, reflector);
		}

		return true;
	}

	private static boolean readConfigurationProperties(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> reflectorConfig,
		final ReflectorProperties properties,
		final GatewayProperties gatewayProperties
	) {
		final String key = "ConfigurationProperties";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> configularationProperties =
				reflectorConfig.configurationAt(key);

			for(Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
				final String propertyKey = it.next();
				final String propertyValue = configularationProperties.getString(propertyKey, "");

				properties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled()) {
				log.debug(logHeader + "Could not load " + allKey + ".");
			}
		}

		return false;
	}
}
