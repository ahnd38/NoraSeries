package org.jp.illg.nora.gateway.configurators;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBRoutingService;
import org.jp.illg.dstar.routing.service.jptrust.JpTrustClientService;
import org.jp.illg.nora.NoraDefines;
import org.jp.illg.util.ApplicationInformation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingServiceConfigurator {

	private static final String logHeader =
		RoutingServiceConfigurator.class.getSimpleName() + " : ";


	private RoutingServiceConfigurator() {}

	public static boolean readRoutingServices(
		final ApplicationInformation<?> applicationInformation,
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> gatewayConfig,
		final GatewayProperties gatewayProperties
	) {
		final String key = "RoutingServices.RoutingService";
		final String allKey = parentKey + "." + key;
		String attr;

		//Routing Services
		try{
			final List<HierarchicalConfiguration<ImmutableNode>> routingServices =
				gatewayConfig.configurationsAt(key);

			for(final HierarchicalConfiguration<ImmutableNode> routingServiceNode : routingServices) {
				final RoutingServiceProperties routingService = new RoutingServiceProperties();

				attr = "[@enable]";
				boolean enable = false;
				try {
					enable = routingServiceNode.getBoolean(attr, true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
				}
				routingService.setEnable(enable);

				attr = "[@type]";
				String routingServiceType = routingServiceNode.getString(attr, "");
				if(routingServiceType == null || "".equals(routingServiceType)) {continue;}
				routingService.setType(routingServiceType);

				readConfigurationProperties(
					applicationInformation,
					allKey, routingServiceNode, routingService, gatewayProperties
				);

				gatewayProperties.getRoutingServices().put(routingServiceType, routingService);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return true;
	}

	private static boolean readConfigurationProperties(
		final ApplicationInformation<?> applicationInformation,
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> routingServiceConfig,
		final RoutingServiceProperties properties,
		final GatewayProperties gatewayProperties
	) {
		final String key = "ConfigurationProperties";
		final String allKey = parentKey + "." + key;

		properties.getConfigurationProperties().setProperty(
			"ApplicationName", applicationInformation.getApplicationName()
		);
		properties.getConfigurationProperties().setProperty(
			"ApplicationVersion", applicationInformation.getApplicationVersion()
		);

		properties.getConfigurationProperties().setProperty(
			"UseProxyGateway", String.valueOf(gatewayProperties.isUseProxyGateway())
		);
		properties.getConfigurationProperties().setProperty(
			"ProxyGatewayAddress", String.valueOf(gatewayProperties.getProxyGatewayAddress())
		);
		properties.getConfigurationProperties().setProperty(
			"ProxyPort", String.valueOf(gatewayProperties.getProxyPort())
		);

		//サービス別特殊処理
		final RoutingServiceTypes type = RoutingServiceTypes.getTypeByTypeName(properties.getType());
		switch(type) {
		case JapanTrust:
			//問い合わせIDをセット
			properties.getConfigurationProperties().setProperty(
				JpTrustClientService.queryIDPropertyName,
				String.valueOf(NoraDefines.NoraGatewayQueryIDForJapanTrust)
			);
			break;

		case ircDDB:
			//コールサインをゲートウェイのコールに強制
			if(properties.getConfigurationProperties().contains("Callsign"))
				properties.getConfigurationProperties().remove("Callsign");

			final String ircDDBCallsign =
				gatewayProperties.getCallsign().substring(0, DSTARDefines.CallsignFullLength - 1).trim();
			for(int i = 0; i < IrcDDBRoutingService.getMaxServers(); i++) {
				properties.getConfigurationProperties().setProperty(
					"Callsign" + (i == 0 ? "" : i),
					ircDDBCallsign
				);
			}

			break;

		default:
			break;
		}

		try {
			final HierarchicalConfiguration<ImmutableNode> configularationProperties =
				routingServiceConfig.configurationAt(key);

			for(final Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
				final String propertyKey = it.next();
				final String propertyValue = configularationProperties.getString(propertyKey, "");

				properties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return false;
	}
}
