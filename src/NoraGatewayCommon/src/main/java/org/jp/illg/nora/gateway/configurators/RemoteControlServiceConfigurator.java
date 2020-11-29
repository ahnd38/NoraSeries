package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.RemoteControlProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteControlServiceConfigurator {

	private static final String logHeader =
		RemoteControlServiceConfigurator.class.getSimpleName() + " : ";

	private RemoteControlServiceConfigurator() {}

	public static boolean readRemoteControlService(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> gatewayConfig,
		final GatewayProperties gatewayProperties
	) {
		final String key = "RemoteControlService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try{
			final RemoteControlProperties remoteControlProperties =
				gatewayProperties.getRemoteControlService();

			final HierarchicalConfiguration<ImmutableNode> remoteService =
				gatewayConfig.configurationAt(key);

			remoteControlProperties.setEnable(remoteService.getBoolean("[@enable]", true));
			remoteControlProperties.setPort(remoteService.getInt("[@port]", 0));
			remoteControlProperties.setPassword(remoteService.getString("[@password]", ""));

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}
}
