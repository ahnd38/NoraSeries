package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayConfigurator {

	private static final String logHeader = GatewayConfigurator.class.getSimpleName() + " : ";

	private GatewayConfigurator() {}

	public static boolean readGateway(
		final ApplicationInformation<?> applicationInformation,
		final String parentKey,
		final XMLConfiguration config,
		final GatewayProperties gatewayProperties
	) {
		final String key = "Gateway";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> gatewayConfig =
				config.configurationAt(key);

			attr = "[@callsign]";
			final String gatewayCallsign =
				DSTARUtils.formatFullCallsign(gatewayConfig.getString(attr, ""), 'G');
			if(
				CallSignValidator.isValidGatewayCallsign(gatewayCallsign) &&
				!CallSignValidator.isValidJARLRepeaterCallsign(gatewayCallsign)
			) {
				gatewayProperties.setCallsign(gatewayCallsign);
			}
			else {
				if(log.isErrorEnabled()) {
					log.error(
						logHeader +
						"Could not set to configuration parameter " + allKey + attr +
						", because illegal callsign " + gatewayCallsign + " is set."
					);
				}
				return false;
			}

			attr = "[@port]";
			int gatewayPort = 40000;
			try {
				gatewayPort = gatewayConfig.getInt(attr, 40000);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn("Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			if((gatewayPort >= 1024 && gatewayPort < 65535) || gatewayPort == 0)
				gatewayProperties.setPort(gatewayPort);
			else {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Out of range parameter " + allKey + attr + " default port 40000 is set at gateway " + gatewayCallsign
					);
				}
				gatewayProperties.setPort(40000);
			}

			attr = "[@g2ProtocolVersion]";
			int g2ProtocolVersion = GatewayProperties.g2ProtocolVersionDefault;
			try {
				g2ProtocolVersion =
					gatewayConfig.getInt(attr, GatewayProperties.g2ProtocolVersionDefault);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			if(
				g2ProtocolVersion >= GatewayProperties.g2ProtocolVersionMin &&
				g2ProtocolVersion <= GatewayProperties.g2ProtocolVersionMax
			)
				gatewayProperties.setG2protocolVersion(g2ProtocolVersion);
			else {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Out of range parameter " + allKey + attr +
						" default port " + GatewayProperties.g2ProtocolVersionDefault  +
						" is set at gateway " + gatewayCallsign
					);
				}
				gatewayProperties.setG2protocolVersion(GatewayProperties.g2ProtocolVersionDefault);
			}

			attr = "[@useProxyGateway]";
			boolean useProxyGateway = false;
			try {
				useProxyGateway = gatewayConfig.getBoolean(attr, false);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			gatewayProperties.setUseProxyGateway(useProxyGateway);

			attr = "[@proxyGatewayAddress]";
			String proxyGatewayAddress = gatewayConfig.getString(attr, "");
			gatewayProperties.setProxyGatewayAddress(proxyGatewayAddress);

			attr = "[@proxyPort]";
			int proxyPort = 52161;
			try{
				proxyPort = gatewayConfig.getInt(attr, 52161);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to numeric value.", ex);
			}
			gatewayProperties.setProxyPort(proxyPort);

			attr = "[@disableHeardAtReflector]";
			boolean disableHeardAtReflector = true;
			try {
				disableHeardAtReflector = gatewayConfig.getBoolean(attr, true);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			gatewayProperties.setDisableHeardAtReflector(disableHeardAtReflector);

			attr = "[@announceVoice]";
			String announceVoice = gatewayConfig.getString(attr, VoiceCharactors.KizunaAkari.getCharactorName());
			gatewayProperties.setAnnounceVoice(announceVoice);

			attr = "[@disableWakeupAnnounce]";
			boolean disableWakeupAnnounce = false;
			try {
				disableWakeupAnnounce = gatewayConfig.getBoolean(attr, false);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			gatewayProperties.setDisableWakeupAnnounce(disableWakeupAnnounce);

			attr = "[@autoReplaceCQFromReflectorLinkCommand]";
			boolean autoReplaceCQFromReflectorLinkCommand = false;
			try {
				autoReplaceCQFromReflectorLinkCommand = gatewayConfig.getBoolean(attr, false);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to boolean value.", ex);
			}
			gatewayProperties.setAutoReplaceCQFromReflectorLinkCommand(autoReplaceCQFromReflectorLinkCommand);

			attr = "[@scope]";
			final String scope = gatewayConfig.getString(attr, AccessScope.Unknown.getTypeName());
			gatewayProperties.setScope(scope);

			attr = "[@latitude]";
			double latitude = 0.0d;
			try {
				latitude = gatewayConfig.getDouble(attr, 0.0d);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to numeric value.", ex);
			}
			gatewayProperties.setLatitude(latitude);

			attr = "[@longitude]";
			double longitude = 0.0d;
			try {
				longitude = gatewayConfig.getDouble(attr, 0.0d);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to numeric value.", ex);
			}
			gatewayProperties.setLongitude(longitude);

			attr = "[@agl]";
			double agl = 0.0d;
			try {
				agl = gatewayConfig.getDouble(attr, 0.0d);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + allKey + attr + " to numeric value.", ex);
			}
			gatewayProperties.setAgl(agl);

			attr = "[@description1]";
			final String description1 = gatewayConfig.getString(attr, "");
			gatewayProperties.setDescription1(description1);

			attr = "[@description2]";
			final String description2 = gatewayConfig.getString(attr, "");
			gatewayProperties.setDescription2(description2);

			attr = "[@url]";
			final String url = gatewayConfig.getString(attr, "");
			gatewayProperties.setUrl(url);

			attr = "[@name]";
			final String name = gatewayConfig.getString(attr, "");
			gatewayProperties.setName(name);

			attr = "[@location]";
			final String location = gatewayConfig.getString(attr, "");
			gatewayProperties.setLocation(location);

			attr = "[@dashboardUrl]";
			final String dashboardUrl = gatewayConfig.getString(attr, "");
			gatewayProperties.setDashboardUrl(dashboardUrl);

			if(
				!RoutingServiceConfigurator.readRoutingServices(
					applicationInformation, allKey, gatewayConfig, gatewayProperties
				) ||
				!ReflectorCommunicationServiceConfigurator.readReflectors(
					allKey, gatewayConfig, gatewayProperties
				) ||
				!ReflectorLinkManagerConfigurator.readReflectorLinkManager(
					allKey, gatewayConfig, gatewayProperties.getReflectorLinkManager()
				) ||
				!RemoteControlServiceConfigurator.readRemoteControlService(
					allKey, gatewayConfig, gatewayProperties
				)
			) {
				return false;
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not read Gateway property.");
		}

		return false;
	}
}
