package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.HelperServiceProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelperServiceControlServiceConfigurator {

	private static final String logTag =
		HelperServiceControlServiceConfigurator.class.getSimpleName() + " : ";

	private HelperServiceControlServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final HelperServiceProperties properties
	) {
		final String key = "HelperService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			attr = "[@port]";
			properties.setPort(node.getInt(attr, HelperServiceProperties.getPortDefault()));

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
