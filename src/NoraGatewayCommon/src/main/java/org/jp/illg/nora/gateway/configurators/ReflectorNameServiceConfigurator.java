package org.jp.illg.nora.gateway.configurators;

import java.util.Iterator;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.ReflectorHostsImporterProperties;
import org.jp.illg.dstar.model.config.ReflectorNameServiceProperties;
import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorNameServiceConfigurator {

	private static final String logTag =
		ReflectorNameServiceConfigurator.class.getSimpleName() + " : ";

	private ReflectorNameServiceConfigurator() {}


	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorNameServiceProperties properties
	) {
		final String key = "ReflectorNameService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		//旧ReflectorHostFileDownloadService設定読み込み(設定互換性保持用)
		readReflectorHostFileDownloadServiceConfig(parentKey, config, properties);

		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				config.configurationAt(key);

			properties.setEnable(
				node.getBoolean("[@enable]", false)
			);

			return readImporters(allKey, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readImporters(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorNameServiceProperties properties
	) {
		final String key = "Importers";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				config.configurationAt(key);

			return readImporter(allKey, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readImporter(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorNameServiceProperties properties
	) {
		final String key = "Importer";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			for(final HierarchicalConfiguration<ImmutableNode> importerNode : config.configurationsAt(key)) {
				final ReflectorHostsImporterProperties importer = new ReflectorHostsImporterProperties();

				importer.setEnable(importerNode.getBoolean("[@enable]", false));

				final ReflectorHostsImporterType type =
					ReflectorHostsImporterType.getTypeByNameIgnoreCase(importerNode.getString("[@type]", ""));
				if(type == null) {
					continue;
				}
				importer.setType(type);

				int intervalMinutes = 0;
				try {
					intervalMinutes = importerNode.getInt("[@intervalMinutes]");
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn("Could not convert intevalMinutes to number " + ", importer type = " + type);

					intervalMinutes = 360;
				}
				importer.setIntervalMinutes(intervalMinutes);

				readImporterConfigurationProperties(allKey, importerNode, importer);

				properties.getImporters().add(importer);
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
		final ReflectorHostsImporterProperties properties
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

				properties.getConfigurationProperties().setProperty(keyEntry, valueEntry);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not read " + allKey + ".");
		}

		return true;
	}

	private static boolean readReflectorHostFileDownloadServiceConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorNameServiceProperties properties
	) {
		final String key = "ReflectorHostFileDownloadService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				config.configurationAt(key);

			final boolean enable = node.getBoolean("[@enable]", false);
			if(!enable) {return true;}

			return readReflectorHostFileDownloadServiceURLEntry(allKey, node, properties);
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readReflectorHostFileDownloadServiceURLEntry(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorNameServiceProperties properties
	) {
		final String key = "URLEntry";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			for(final HierarchicalConfiguration<ImmutableNode> urlEntryNode : config.configurationsAt(key)) {
				final ReflectorHostsImporterProperties importer = new ReflectorHostsImporterProperties();

				importer.setEnable(urlEntryNode.getBoolean("[@enable]", false));

				importer.setType(ReflectorHostsImporterType.URL);

				final String url = urlEntryNode.getString("[@url]", "");
				importer.getConfigurationProperties().setProperty("URL", url);

				int intervalMinutes = 0;
				try {
					intervalMinutes = urlEntryNode.getInt("[@intervalMinutes]");
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn("URLEntry intervalMinutes = " + intervalMinutes + ", Could not convert to number, url = " + url);

					intervalMinutes = 360;
				}
				importer.setIntervalMinutes(intervalMinutes);

				properties.getImporters().add(importer);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
