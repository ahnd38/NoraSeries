package org.jp.illg.nora.gateway.configurators;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.ICOMRepeaterCommunicationServiceProperties;
import org.jp.illg.dstar.model.config.ICOMRepeaterProperties;
import org.jp.illg.dstar.service.icom.model.ICOMRepeaterType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ICOMRepeaterCommunicationServiceConfigurator {

	private static final String logTag =
		ICOMRepeaterCommunicationServiceConfigurator.class.getSimpleName() + " : ";

	private ICOMRepeaterCommunicationServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ICOMRepeaterCommunicationServiceProperties properties
	) {
		final String key = "ICOMRepeaterCommunicationService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			attr = "[@enable]";
			properties.setEnable(node.getBoolean(attr, true));

			return readICOMRepeaters(allKey, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readICOMRepeaters(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ICOMRepeaterCommunicationServiceProperties properties
	) {
		final String key = "ICOMRepeaters";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		//String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			return readICOMRepeater(allKey, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readICOMRepeater(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ICOMRepeaterCommunicationServiceProperties properties
	) {
		boolean result = true;

		final String key = "ICOMRepeater";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final List<HierarchicalConfiguration<ImmutableNode>> nodes = config.configurationsAt(key);

			for(final HierarchicalConfiguration<ImmutableNode> node : nodes) {
				final ICOMRepeaterProperties repeaterProperties = new ICOMRepeaterProperties();

				attr = "[@enable]";
				repeaterProperties.setEnable(node.getBoolean(attr, true));

				attr = "[@type]";
				final String typeString = node.getString(attr, ICOMRepeaterType.Unknown.getTypeName());
				final ICOMRepeaterType repeaterType = ICOMRepeaterType.getTypeByTypeNameIgnoreCase(typeString);
				if(repeaterType == null || repeaterType == ICOMRepeaterType.Unknown) {
					continue;
				}
				repeaterProperties.setRepeaterType(repeaterType);

				if(result &= readICOMRepeaterConfigurationProperties(
					allKey, node, repeaterProperties
				)) {
					properties.getRepeaters().add(repeaterProperties);
				}
			}

			return result;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readICOMRepeaterConfigurationProperties(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ICOMRepeaterProperties properties
	) {
		final String key = "ConfigurationProperties";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		//String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			for(final Iterator<String> it = node.getKeys() ; it.hasNext();) {
				final String keyEntry = it.next();
				final String valueEntry = node.getString(keyEntry, "");

				properties.getProperties().setProperty(keyEntry, valueEntry);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

}
