package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadServiceProperties;
import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadURLEntry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorHostFileDownloadServiceConfigurator {

	private static final String logTag =
		ReflectorHostFileDownloadServiceConfigurator.class.getSimpleName() + " : ";

	private ReflectorHostFileDownloadServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final ReflectorHostFileDownloadServiceProperties properties
	) {
		final String key = "ReflectorHostFileDownloadService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> node =
				config.configurationAt(key);

			properties.setEnable(
				node.getBoolean("[@enable]", false)
			);

			for(HierarchicalConfiguration<ImmutableNode> urlEntryNode : node.configurationsAt("URLEntry")) {
				final ReflectorHostFileDownloadURLEntry urlEntry =
					new ReflectorHostFileDownloadURLEntry();

				urlEntry.setEnable(urlEntryNode.getBoolean("[@enable]", false));

				final String url = urlEntryNode.getString("[@url]", "");
				urlEntry.setUrl(url);

				int intervalMinutes = 0;
				try {
					intervalMinutes = urlEntryNode.getInt("[@intervalMinutes]");
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn("URLEntry intevalMinutes = " + intervalMinutes + ", Could not convert to number, url = " + url);

					intervalMinutes = 360;
				}
				urlEntry.setIntervalMinutes(intervalMinutes);

				properties.getUrlEntries().add(urlEntry);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
