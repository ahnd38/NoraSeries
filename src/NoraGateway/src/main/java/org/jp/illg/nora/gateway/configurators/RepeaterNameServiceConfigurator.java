package org.jp.illg.nora.gateway.configurators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.RepeaterListImporterProperties;
import org.jp.illg.dstar.model.config.RepeaterNameServiceProperties;
import org.jp.illg.dstar.model.defines.RepeaterListImporterType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterNameServiceConfigurator {

	private static final String logTag =
		RepeaterNameServiceConfigurator.class.getSimpleName() + " : ";

	private RepeaterNameServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final RepeaterNameServiceProperties properties
	) {
		final String key = "RepeaterNameService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			return readImporters(key, config, node, properties);

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readImporters(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final HierarchicalConfiguration<ImmutableNode> parentNode,
		final RepeaterNameServiceProperties properties
	) {
		final String key = "Importers";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		properties.setImporters(new ArrayList<>(4));
		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				parentNode.configurationAt(key);

			return readImporter(allKey, config, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readImporter(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final HierarchicalConfiguration<ImmutableNode> parentNode,
		final RepeaterNameServiceProperties properties
	) {
		final String key = "Importer";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			for(HierarchicalConfiguration<ImmutableNode> node : parentNode.configurationsAt(key)) {
				final RepeaterListImporterProperties importerProperties =
					new RepeaterListImporterProperties();

				importerProperties.setEnable(
					node.getBoolean("[@enable]", true)
				);

				final String typeString = node.getString("[@type]", "");
				final RepeaterListImporterType importerType =
					RepeaterListImporterType.getTypeByNameIgnoreCase(typeString);
				if(importerType == null) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Unknown importer type = " + typeString + "@" + allKey);

					continue;
				}
				importerProperties.setImporterType(importerType);

				if(readImporterConfigurationProperties(allKey, config, node, importerProperties))
					properties.getImporters().add(importerProperties);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readImporterConfigurationProperties(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final HierarchicalConfiguration<ImmutableNode> parentNode,
		final RepeaterListImporterProperties properties
	) {
		final String key = "ConfigurationProperties";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				parentNode.configurationAt(key);
			if(node == null) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not read ConfigurationProperties@" + allKey);

				return false;
			}

			final Properties prop = new Properties();
			for(Iterator<String> it = node.getKeys(); it.hasNext();) {
				String propertyKey = it.next();
				String propertyValue = node.getString(propertyKey, "");

				prop.setProperty(propertyKey, propertyValue);
			}
			properties.setConfigurationProperties(prop);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
