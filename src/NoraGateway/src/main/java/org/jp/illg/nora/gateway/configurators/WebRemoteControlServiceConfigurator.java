package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.WebRemoteControlServiceProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebRemoteControlServiceConfigurator {

	private static final String logTag =
		WebRemoteControlServiceConfigurator.class.getSimpleName() + " : ";

	private WebRemoteControlServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final WebRemoteControlServiceProperties properties
	) {
		final String key = "WebRemoteControlService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			attr = "[@enable]";
			properties.setEnable(node.getBoolean(attr, false));

			attr = "[@port]";
			properties.setPort(node.getInt(attr, WebRemoteControlServiceProperties.portDefault));

			attr = "[@context]";
			properties.setContext(node.getString(attr, "/socket.io"));

			attr = "[@userListFile]";
			properties.setUserListFile(node.getString(attr, WebRemoteControlServiceProperties.userListFileDefault));

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
