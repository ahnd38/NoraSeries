package org.jp.illg.nora.gateway;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.config.AutoConnectRepeaterEntry;
import org.jp.illg.dstar.model.config.CallsignEntry;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.config.ReflectorBlackListEntry;
import org.jp.illg.dstar.model.config.ReflectorLinkManagerProperties;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.config.RemoteControlProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBRoutingService;
import org.jp.illg.dstar.routing.service.jptrust.JpTrustClientService;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.NoraDefines;
import org.jp.illg.nora.gateway.configurators.CrashReportServiceControlServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.HelperServiceControlServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.ICOMRepeaterCommunicationServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.ReflectorHostFileDownloadServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.RepeaterNameServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.StatusInformationFileOutputServiceConfigurator;
import org.jp.illg.nora.gateway.configurators.WebRemoteControlServiceConfigurator;
import org.jp.illg.nora.vr.model.NoraVRLoginUserEntry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGatewayConfigurator {

	private static final String applicationName =
			NoraGatewayUtil.getApplicationName() + "@" + NoraGatewayUtil.getRunningOperatingSystem();
	private static final String applicationVersion = NoraGatewayUtil.getApplicationVersion();

	private static final String logHeader;

	static {
		logHeader = NoraGatewayConfigurator.class.getSimpleName() + " : ";
	}


	public static boolean readConfiguration(NoraGatewayConfiguration dstConfig, InputStream configurationFile) {
		if(configurationFile == null || dstConfig == null){return false;}

		try {
			XMLConfiguration config =
					new BasicConfigurationBuilder<>(XMLConfiguration.class)
							.configure(new Parameters().xml()).getConfiguration();
			FileHandler fh = new FileHandler(config);
			fh.load(configurationFile);

			return readConfiguration(dstConfig, config);
		}catch(ConfigurationException ex){
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not read Configuration file " + configurationFile);
			}

			return false;
		}
	}

	public static boolean readConfiguration(NoraGatewayConfiguration dstConfig, File configurationFile) {
		if (configurationFile == null || dstConfig == null)
			return false;

		if (!configurationFile.exists()) {
			if(log.isErrorEnabled())
				log.error("Not exist configuration file...[" + configurationFile.toString() + "]");

			return false;
		}

		Parameters params = new Parameters();

		FileBasedConfigurationBuilder<XMLConfiguration> builder =
				new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
						.configure(params.fileBased().setFile(configurationFile));

		builder.setAutoSave(false);

		try {
			XMLConfiguration config = builder.getConfiguration();

			return readConfiguration(dstConfig, config);
		}catch(ConfigurationException ex) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Could not read Configuration file = " + configurationFile.getAbsolutePath());
			}

			return false;
		}
	}


	public static boolean readConfiguration(NoraGatewayConfiguration dstConfig, XMLConfiguration config) {
		if(config == null || dstConfig == null)
			return false;

		final String key = "";
		final String allKey = key;

		if(
			!readGateway(allKey, config, dstConfig.getGatewayProperties()) ||
			!readRepeaters(allKey, config, dstConfig)
		) {
			if(log.isErrorEnabled()) {
				log.error(logHeader + "Fatal error during configuration read.");
			}

			return false;
		}

		readServices(allKey, config, dstConfig);

		return true;
	}

	private static boolean readModems(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {
		final String key = "Modems.Modem";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		ModemProperties modemProperties = null;

		try{	//複数のモデムを使用する場合
			final List<HierarchicalConfiguration<ImmutableNode>> modemNodes =
				repeaterNode.configurationsAt(key);

			if(modemNodes != null) {
				for(HierarchicalConfiguration<ImmutableNode> modemNode : modemNodes) {
					if((modemProperties = readModem(modemNode)) != null) {
						properties.addModemProperties(modemProperties);
					}
					else {
						if(log.isWarnEnabled()) {
							log.warn(
								logHeader + "Could not read Modem Properties at " + allKey +
								"/Callsign:" + properties.getCallsign() + "."
							);
						}
					}
				}
			}
		}catch(ConfigurationRuntimeException ex) {}

		return true;
	}

	private static boolean readModem(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {
		final String key = "Modem";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		ModemProperties modemProperties = null;

		try{	//単独モデムにて使用
			final HierarchicalConfiguration<ImmutableNode> modemNode = repeaterNode.configurationAt(key);

			if(modemNode != null) {
				if((modemProperties = readModem(modemNode)) != null)
					properties.addModemProperties(modemProperties);
				else {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader + "Could not read Modem Properties at " + allKey +
							"/Callsign:" + properties.getCallsign() + "."
						);
					}
				}
			}
		}catch(ConfigurationRuntimeException ex){}

		return true;
	}

	private static ModemProperties readModem(HierarchicalConfiguration<ImmutableNode> modemNode) {

		String key = "", attr = "";

		ModemProperties modemProperties = new ModemProperties();

		attr = "[@enable]";
		boolean isEnable = modemNode.getBoolean(attr, true);
		modemProperties.setEnable(isEnable);

		attr = "[@type]";
		String modemType = modemNode.getString(attr, "");
		if(modemType ==  null || "".equals(modemType)) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read Modem" + attr + ".");

			return null;
		}
		modemProperties.setType(modemType);

		attr = "[@allowDIRECT]";
		boolean allowDIRECT = false;
		try {
			allowDIRECT = modemNode.getBoolean(attr, false);
		}catch(ConversionException ex) {
			if(log.isWarnEnabled())
				log.warn("Could not convert property " + key + attr + " to numeric value.", ex);
		}
		modemProperties.setAllowDIRECT(allowDIRECT);

		attr = "[@scope]";
		final String scope = modemNode.getString(attr, AccessScope.Unknown.getTypeName());
		modemProperties.setScope(scope);

		attr = "[@transceiverMode]";
		final ModemTransceiverMode transceiverMode =
			ModemTransceiverMode.getTypeByTypeNameIgnoreCase(modemNode.getString(attr, ""));
		modemProperties.setTransceiverMode(transceiverMode != null ? transceiverMode : ModemTransceiverMode.Unknown);

		key = "ConfigurationProperties";

		final HierarchicalConfiguration<ImmutableNode> configularationProperties =
				modemNode.configurationAt(key);
		if(configularationProperties == null) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read Modem." + key + ".");

			return null;
		}

		for(Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
			String propertyKey = it.next();
			String propertyValue = configularationProperties.getString(propertyKey, "");
			modemProperties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
		}

		if(ModemTypes.getTypeByTypeName(modemType) == ModemTypes.NoraVR) {
			final List<NoraVRLoginUserEntry> userEntries = new ArrayList<>();

			final List<HierarchicalConfiguration<ImmutableNode>> userNodes =
				configularationProperties.configurationsAt("LoginUserList.LoginUser");

			for(final HierarchicalConfiguration<ImmutableNode> userNode : userNodes) {

				final String loginCallsign = userNode.getString("[@loginCallsign]", "");
				final String loginPassword = userNode.getString("[@loginPassword]", "");
				final boolean allowRFNode = userNode.getBoolean("[@allowRFNode]", false);
				if(CallSignValidator.isValidUserCallsign(DSTARUtils.formatFullCallsign(loginCallsign))) {
					final NoraVRLoginUserEntry userEntry =
						new NoraVRLoginUserEntry(loginCallsign, loginPassword, allowRFNode);

					userEntries.add(userEntry);
				}
				else {
					if(log.isWarnEnabled())
						log.warn("Illegal NoraVR login user entry, loginCallsign=" + loginCallsign + ".");
				}
			}

			modemProperties.getConfigurationProperties().put("LoginUserList", userEntries);
		}

		return modemProperties;
	}

	private static boolean readRepeaterConfigurationProperties(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {
		final String key = "ConfigurationProperties";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> configularationProperties =
				repeaterNode.configurationAt(key);

			if(configularationProperties != null) {
				for(Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
					final String propertyKey = it.next();
					final String propertyValue = configularationProperties.getString(propertyKey, "");

					properties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
				}
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + "/Callsign:" + properties.getCallsign() + ".");
		}

		return true;
	}

	private static boolean readRepeaterAccessWhiteList(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {
		final String key = "AccessWhiteList.CallsignEntry";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final List<HierarchicalConfiguration<ImmutableNode>> callsignEntries =
				repeaterNode.configurationsAt(key);

			for(final HierarchicalConfiguration<ImmutableNode> callsignEntry : callsignEntries) {
				String attr = "[@enable]";
				final boolean isEnable = callsignEntry.getBoolean(attr, false);

				attr = "[@callsign]";
				final String callsign =
					DSTARUtils.formatFullLengthCallsign(callsignEntry.getString(attr, DSTARDefines.EmptyLongCallsign));

				properties.getAccessWhiteList().add(new CallsignEntry(isEnable, callsign));
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + "/Callsign:" + properties.getCallsign() + ".");
		}

		return true;
	}

	private static boolean readRepeaters(
		final String parentKey,
		final XMLConfiguration config,
		final NoraGatewayConfiguration properties
	) {
		final String key = "Repeaters.Repeater";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try{
			final List<HierarchicalConfiguration<ImmutableNode>> repeaters =
					config.configurationsAt(key);
			if(repeaters.size() <= 0) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not read " + allKey + ".");

				return false;
			}

			for(HierarchicalConfiguration<ImmutableNode> repeater : repeaters) {
				final RepeaterProperties repeaterProperties = new RepeaterProperties();

				attr = "[@enable]";
				boolean enable = false;
				try {
					enable = repeater.getBoolean(attr, true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
				}
				repeaterProperties.setEnable(enable);

				attr = "[@type]";
				String repeaterType = repeater.getString(attr, "");
				repeaterProperties.setType(repeaterType);

				attr = "[@module] or [@callsign]";
				final String repeaterModuleSource = repeater.getString("[@module]", "");
				final String repeaterCallsignSource = repeater.getString("[@callsign]", "");
				final String repeaterCallsignModule =
					repeaterCallsignSource.length() >= 4 ? repeaterCallsignSource.substring(repeaterCallsignSource.length() - 1) : "";
				final String repeaterCallsign =
					DSTARUtils.formatFullCallsign(
						properties.getGatewayProperties().getCallsign().substring(0, DSTARDefines.CallsignFullLength - 1),
						repeaterModuleSource.length() > 0 ?
							repeaterModuleSource.charAt(repeaterModuleSource.length() - 1) :
							(repeaterCallsignModule.length() > 0 ? repeaterCallsignModule.charAt(repeaterCallsignModule.length() - 1) : ' ')
					);
				if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Cound not set to configuration parameter " + repeater.getRootElementName() + attr +
							", because illegal callsign format " + repeaterCallsign + "."
						);
					}
					continue;
				}
				else if(repeaterCallsign.charAt(DSTARDefines.CallsignFullLength - 1) == 'G') {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Cound not set to configuration parameter " + repeater.getRootElementName() + attr +
							", because illegal module " + repeaterCallsign + " is set. do not use callsign module 'G' for repeater callsign."
						);
					}
					continue;
				}
				else {
					repeaterProperties.setCallsign(repeaterCallsign);
				}

				if(!enable) {
					if(log.isInfoEnabled())
						log.info("Repeater " + repeaterCallsign + " is disable(enable=false)");

					continue;
				}

				attr = "[@defaultRoutingService]";
				String defaultRoutingService = repeater.getString(attr, "");
				repeaterProperties.setDefaultRoutingService(defaultRoutingService);

				attr = "[@routingServiceFixed]";
				String routingServiceFixed = repeater.getString(attr, "");
				repeaterProperties.setRoutingServiceFixed(routingServiceFixed);

				attr = "[@allowDIRECT]";
				boolean allowDIRECT = false;
				try {
					allowDIRECT = repeater.getBoolean(attr, false);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
				}
				repeaterProperties.setAllowDIRECT(allowDIRECT);

				attr = "[@directMyCallsign]";
				String directMyCallsign =
					DSTARUtils.formatFullCallsign(
						repeater.getString(attr, DSTARDefines.EmptyLongCallsign).trim().toUpperCase(Locale.ENGLISH),
						' '
					);
				repeaterProperties.getDirectMyCallsigns().add(directMyCallsign);

				attr = "[@directMyCallsigns]";
				final String directMyCallsigns = repeater.getString(attr, "");
				if(directMyCallsigns.length() > 0) {
					for(final String dmc : directMyCallsigns.split(",")) {
						final String dMyCallsign =
							DSTARUtils.formatFullCallsign(dmc.trim().toUpperCase(Locale.ENGLISH), ' ');

						if(!CallSignValidator.isValidUserCallsign(dMyCallsign)) {
							if(log.isWarnEnabled())
								log.warn(logHeader + "Illegal callsign = " + dMyCallsign + "@" + allKey + attr);

							continue;
						}

						repeaterProperties.getDirectMyCallsigns().add(dMyCallsign);
					}
				}

				attr = "[@useRoutingService]";
				// ExternalHomebrewでUseRoutingServiceの明示的な設定が無ければ、無効に設定する
				if(
					repeaterType.equals(RepeaterTypes.ExternalHomebrew.toString()) &&
					!repeater.containsKey(attr)
				) {
					repeaterProperties.setUseRoutingService(false);
				}
				else {
					boolean useRoutingService = false;
					try {
						useRoutingService = repeater.getBoolean(attr, true);
					}catch(ConversionException ex) {
						if(log.isWarnEnabled())
							log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
					}
					repeaterProperties.setUseRoutingService(useRoutingService);
				}

				attr = "[@autoDisconnectFromReflectorOnTxToG2Route]";
				boolean autoDisconnectFromReflectorOnTxToG2Route = true;
				try {
					autoDisconnectFromReflectorOnTxToG2Route = repeater.getBoolean(attr, true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
				}
				repeaterProperties.setAutoDisconnectFromReflectorOnTxToG2Route(
					autoDisconnectFromReflectorOnTxToG2Route
				);

				attr = "[@autoDisconnectFromReflectorOutgoingUnusedMinutes]";
				int autoDisconnectFromReflectorOutgoingUnusedMinutes = 0;
				try {
					autoDisconnectFromReflectorOutgoingUnusedMinutes = repeater.getInt(attr, 0);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setAutoDisconnectFromReflectorOutgoingUnusedMinutes(
					autoDisconnectFromReflectorOutgoingUnusedMinutes
				);

				attr = "[@allowIncomingConnection]";
				boolean allowIncomingConnection = true;
				try {
					allowIncomingConnection = repeater.getBoolean(attr, true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
				}
				repeaterProperties.setAllowIncomingConnection(allowIncomingConnection);

				attr = "[@allowOutgoingConnection]";
				boolean allowOutgoingConnection = true;
				try {
					allowOutgoingConnection = repeater.getBoolean(attr, true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
				}
				repeaterProperties.setAllowOutgoingConnection(allowOutgoingConnection);

				attr = "[@scope]";
				final String scope = repeater.getString(attr, AccessScope.Unknown.getTypeName());
				repeaterProperties.setScope(scope);

				attr = "[@latitude]";
				double latitude = 0.0d;
				try {
					latitude = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setLatitude(latitude);

				attr = "[@longitude]";
				double longitude = 0.0d;
				try {
					longitude = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setLongitude(longitude);

				attr = "[@agl]";
				double agl = 0.0d;
				try {
					agl = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setAgl(agl);

				attr = "[@description1]";
				final String description1 = repeater.getString(attr, "");
				repeaterProperties.setDescription1(description1);

				attr = "[@description2]";
				final String description2 = repeater.getString(attr, "");
				repeaterProperties.setDescription2(description2);

				attr = "[@url]";
				final String url = repeater.getString(attr, "");
				repeaterProperties.setUrl(url);

				attr = "[@range]";
				double range = 0.0d;
				try {
					range = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setRange(range);

				attr = "[@frequency]";
				double frequency = 0.0d;
				try {
					frequency = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setFrequency(frequency);

				attr = "[@frequencyOffset]";
				double frequencyOffset = 0.0d;
				try {
					frequencyOffset = repeater.getDouble(attr, 0.0d);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
				}
				repeaterProperties.setFrequencyOffset(frequencyOffset);

				attr = "[@name]";
				final String name = repeater.getString(attr, "");
				repeaterProperties.setName(name);

				attr = "[@location]";
				final String location = repeater.getString(attr, "");
				repeaterProperties.setLocation(location);

				if(
					RepeaterTypes.ExternalHomebrew.getTypeName().equals(repeaterProperties.getType()) ||
					RepeaterTypes.VoiceroidAutoReply.getTypeName().equals(repeaterProperties.getType()) ||
					RepeaterTypes.EchoAutoReply.getTypeName().equals(repeaterProperties.getType()) ||
					RepeaterTypes.ReflectorEchoAutoReply.getTypeName().equals(repeaterProperties.getType())
				){
					readRepeaterConfigurationProperties(allKey, repeater, repeaterProperties);
				}

				if(RepeaterTypes.ExternalHomebrew.getTypeName().equals(repeaterProperties.getType())) {
					readRepeaterAccessWhiteList(allKey, repeater, repeaterProperties);
				}

				//Modem
				if(RepeaterTypes.Internal.getTypeName().equals(repeaterProperties.getType())){

					if(
						!readModem(allKey, repeater, repeaterProperties) ||
						!readModems(allKey, repeater, repeaterProperties)
					) {continue;}

					if(repeaterProperties.getModemProperties().isEmpty()) {
						if(log.isWarnEnabled())
							log.warn(logHeader + "Repeater " + repeaterCallsign + " does not have modem, ignore.");

						continue;
					}
				}

				if(properties.getRepeaterProperties().containsKey(repeaterCallsign)) {
					final RepeaterProperties duplicateRepeater =
						properties.getRepeaterProperties().get(repeaterCallsign);

					if(
						(!duplicateRepeater.isEnable() && repeaterProperties.isEnable()) ||
						(duplicateRepeater.isEnable() && repeaterProperties.isEnable()) ||
						(!duplicateRepeater.isEnable() && !repeaterProperties.isEnable())
					) {
						properties.getRepeaterProperties().put(repeaterCallsign, repeaterProperties);

						if(log.isWarnEnabled())
							log.warn(logHeader + "Duplicate repeater entry " + repeaterCallsign + ".");
					}
				}
				else {
					properties.getRepeaterProperties().put(repeaterCallsign, repeaterProperties);
				}
			}

			return true;

		}catch(ConfigurationRuntimeException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not read " + allKey + ".");

			return false;
		}
	}

	private static boolean readGateway(
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
				!readRoutingServices(allKey, gatewayConfig, gatewayProperties) ||
				!readReflectors(allKey, gatewayConfig, gatewayProperties) ||
				!readReflectorLinkManager(
					allKey, gatewayConfig, gatewayProperties.getReflectorLinkManager()
				) ||
				!readRemoteControlService(allKey, gatewayConfig, gatewayProperties)
			) {
				return false;
			}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not read Gateway property.");

			return false;
		}

		return true;
	}

	private static boolean readRemoteControlService(
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

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return true;
	}

	private static boolean readReflectors(
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

			if(
				!readReflectorsDetail(allKey, reflectorConfig, gatewayProperties)
			) {return false;}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return true;
	}

	private static boolean readReflectorsDetail(
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
		for(HierarchicalConfiguration<ImmutableNode> reflectorNode : reflectors) {
			final ReflectorProperties reflector = new ReflectorProperties();

			reflector.setApplicationName(applicationName);
			reflector.setApplicationVersion(applicationVersion);

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

			if(
				!readReflectorConfigurationProperties(
					allKey, reflectorNode, reflector, gatewayProperties
				)
			) {return false;}

			gatewayProperties.getReflectors().put(reflectorType, reflector);
		}

		return true;
	}

	private static boolean readReflectorConfigurationProperties(
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
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not load " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readRoutingServices(
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

			for(HierarchicalConfiguration<ImmutableNode> routingServiceNode : routingServices) {
				RoutingServiceProperties routingService = new RoutingServiceProperties();

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

				if(
					!readRoutingServiceConfigurationProperties(
						allKey, routingServiceNode, routingService, gatewayProperties)
				) {return false;}

				gatewayProperties.getRoutingServices().put(routingServiceType, routingService);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return true;
	}

	private static boolean readRoutingServiceConfigurationProperties(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> routingServiceConfig,
		final RoutingServiceProperties properties,
		final GatewayProperties gatewayProperties
	) {
		final String key = "ConfigurationProperties";
		final String allKey = parentKey + "." + key;

		try {
			final HierarchicalConfiguration<ImmutableNode> configularationProperties =
				routingServiceConfig.configurationAt(key);

			properties.getConfigurationProperties().setProperty(
					"ApplicationName", NoraGatewayUtil.getApplicationName()
			);
			properties.getConfigurationProperties().setProperty(
					"ApplicationVersion", NoraGatewayUtil.getApplicationVersion()
			);

			for(Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
				final String propertyKey = it.next();
				final String propertyValue = configularationProperties.getString(propertyKey, "");

				properties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
			}

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
					properties.getConfigurationProperties().put(
						"Callsign" + (i == 0 ? "" : i),
						ircDDBCallsign
					);
				}

				break;

			default:
				break;
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManager(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> gatewayConfig,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "ReflectorLinkManager";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> reflectorLinkManagerNode =
				gatewayConfig.configurationAt(key);

			attr = "[@enable]";
			boolean enable = false;
			try {
				enable = reflectorLinkManagerNode.getBoolean(attr, true);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + key + attr + " to boolean value.", ex);
			}
			reflectorLinkManagerProperties.setEnable(enable);


			if(
				!readReflectorLinkManagerAutoConnect(
					allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
				) ||
				!readReflectorLinkManagerReflectorBlackList(
					allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
				) ||
				!readReflectorLinkManagerDefaultReflectorPreferredProtocols(
					allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
				) ||
				!readReflectorLinkManagerReflectorPreferredProtocols(
					allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
				)
			) {
				return false;
			}

			return true;

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");

			return true;
		}
	}

	private static boolean readReflectorLinkManagerAutoConnect(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "AutoConnect";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> autoConnectNode =
				node.configurationAt(key);

			attr = "[@enable]";
			boolean autoConnectEnable = false;
			try {
				autoConnectEnable = autoConnectNode.getBoolean(attr, false);
			}catch(ConversionException ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not convert property " + key + attr + " to numeric value.", ex);
			}
			reflectorLinkManagerProperties.getAutoConnectProperties().setEnable(
				autoConnectEnable
			);

			if(
				!readReflectorLinkManagerAutoConnectRepeater(allKey, autoConnectNode, reflectorLinkManagerProperties)
			) {
				return false;
			}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerAutoConnectRepeater(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "Repeater";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final List<HierarchicalConfiguration<ImmutableNode>> repeatersNode =
				node.configurationsAt(key);

			for(HierarchicalConfiguration<ImmutableNode> repeaterNode : repeatersNode) {
				attr = "[@callsign]";
				final String repeaterCallsign = repeaterNode.getString(attr, DSTARDefines.EmptyLongCallsign);
				if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Illegal callsign format = " + repeaterCallsign +
							", ignore ReflectorLinkManager.AutoConnect.Repeater."
						);
					}
					continue;
				}

				attr = "[@mode]";
				final String mode = repeaterNode.getString(attr, "");

				final AutoConnectRepeaterEntry entry =
						new AutoConnectRepeaterEntry(repeaterCallsign, mode);

				final Pattern attrPattern = Pattern.compile("^[\\[][@].*[\\]]$");

				try{
					final List<HierarchicalConfiguration<ImmutableNode>> timebasedEntriesNode =
							repeaterNode.configurationsAt("TimeBasedEntry");
					for(HierarchicalConfiguration<ImmutableNode> timebasedEntryNode : timebasedEntriesNode) {
						final Map<String,String> timebasedConf = new HashMap<>();
						entry.getEntries().put("TimeBasedEntry" + "@" + UUID.randomUUID().toString(), timebasedConf);

						for(Iterator<String> it = timebasedEntryNode.getKeys();it.hasNext();) {
							String k = it.next();
							String v = timebasedEntryNode.getString(k, "");

							if(attrPattern.matcher(k).matches()) {
								k = k.replaceAll("\\[", "");
								k = k.replaceAll("@", "");
								k = k.replaceAll("\\]", "");
							}

							timebasedConf.put(k, v);
						}
					}
				}catch(ConfigurationRuntimeException ex){
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Coult not read AutoConnect.Repeater = " + repeaterCallsign + ".TimeBasedEntry.",
							ex
						);
					}
				}

				try{
					final List<HierarchicalConfiguration<ImmutableNode>> fixedEntriesNode =
							repeaterNode.configurationsAt("FixedEntry");
					for(HierarchicalConfiguration<ImmutableNode> fixedEntryNode : fixedEntriesNode) {
						final Map<String,String> fixedConf = new HashMap<>();
						entry.getEntries().put("FixedEntry" + "@" + UUID.randomUUID().toString(), fixedConf);

						for(Iterator<String> it = fixedEntryNode.getKeys();it.hasNext();) {
							String k = it.next();
							String v = fixedEntryNode.getString(k, "");

							if(attrPattern.matcher(k).matches()) {
								k = k.replaceAll("\\[", "");
								k = k.replaceAll("@", "");
								k = k.replaceAll("\\]", "");
							}

							fixedConf.put(k, v);
						}
					}
				}catch(ConfigurationRuntimeException ex){
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Coult not read AutoConnect.Repeater = " + repeaterCallsign + ".FixedEntry.",
							ex
						);
					}
				}

				final Map<String, AutoConnectRepeaterEntry> repeaterEntries =
						reflectorLinkManagerProperties
						.getAutoConnectProperties()
						.getRepeaterEntries();

				if(repeaterEntries.containsKey(repeaterCallsign)) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"AutoConnect.Repeater callsign = " + repeaterCallsign + " is overlapped."
						);
					}

					repeaterEntries.remove(repeaterCallsign);
				}
				repeaterEntries.put(repeaterCallsign, entry);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerReflectorBlackList(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "ReflectorBlackList";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> reflectorBlackListNode =
				node.configurationAt(key);

			if(
				!readReflectorLinkManagerReflectorBlackListCallsignEntry(
					allKey, reflectorBlackListNode, reflectorLinkManagerProperties
				)
			) {return false;}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerReflectorBlackListCallsignEntry(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "CallsignEntry";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			for(
				final HierarchicalConfiguration<ImmutableNode> callsignEntryNode :
				node.configurationsAt(key)
			) {
				String callsign = "";;
				try {
					callsign = callsignEntryNode.getString("", "");
				}catch(ConversionException ex) {
					continue;
				}
				callsign = DSTARUtils.formatFullLengthCallsign(callsign);

				boolean entryEnable = true;
				try {
					entryEnable = callsignEntryNode.getBoolean("[@enable]", true);
				}catch(ConversionException ex) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Reflector black list callsign = " + callsign + ",Unknown enable = " + entryEnable
						);
					}

					entryEnable = true;
				}

				ConnectionDirectionType dir = ConnectionDirectionType.Unknown;
				String dirString = "";
				try {
					dirString = callsignEntryNode.getString("[@dir]", "");

					dir = ConnectionDirectionType.getDirectionTypeByTypeNameIgnoreCase(dirString);
					if(dir == null || dir == ConnectionDirectionType.Unknown) {
						if(log.isWarnEnabled()) {
							log.warn(
								logHeader +
								"Reflector black list callsign = " + callsign + ",Unknown dir = " + dirString
							);
						}

						continue;
					}
				}catch(ConversionException ex) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Reflector black list callsign = " + callsign + ",Unknown dir = " + dirString
						);
					}

					continue;
				}

				final ReflectorBlackListEntry ce = new ReflectorBlackListEntry();
				ce.setEnable(entryEnable);
				ce.setCallsign(callsign);
				ce.setDir(dir);

				reflectorLinkManagerProperties.getReflectorBlackList().put(callsign, ce);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerDefaultReflectorPreferredProtocols(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "DefaultReflectorPreferredProtocols";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> defaultReflectorPreferredProtocolsNode =
				node.configurationAt(key);

			if(
				!readReflectorLinkManagerDefaultReflectorPreferredProtocolsProtocolEntry(
					allKey, defaultReflectorPreferredProtocolsNode, reflectorLinkManagerProperties
				)
			) {return false;}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerDefaultReflectorPreferredProtocolsProtocolEntry(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "ProtocolEntry";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			for(
				HierarchicalConfiguration<ImmutableNode> protocolEntryNode :
				node.configurationsAt(key)
			) {
				attr = "[@enable]";
				final boolean isEnable = protocolEntryNode.getBoolean(attr, true);
				if(!isEnable) {continue;}

				attr = "[@protocol]";
				final String protocolString =
					protocolEntryNode.getString(attr, DSTARProtocol.Unknown.getName());
				final DSTARProtocol protocol =
					DSTARProtocol.getProtocolByNameIgnoreCase(protocolString);
				if(protocol == null || DSTARProtocol.Unknown == protocol) {continue;}

				reflectorLinkManagerProperties.getDefaultReflectorPreferredProtocols().add(protocol);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerReflectorPreferredProtocols(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "ReflectorPreferredProtocols";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {
			final HierarchicalConfiguration<ImmutableNode> reflectorPreferredProtocolsNode =
				node.configurationAt(key);

			if(
				!readReflectorLinkManagerReflectorPreferredProtocolsProtocolEntry(
					allKey, reflectorPreferredProtocolsNode, reflectorLinkManagerProperties
				)
			) {return false;}

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readReflectorLinkManagerReflectorPreferredProtocolsProtocolEntry(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		final String key = "ProtocolEntry";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			for(
				HierarchicalConfiguration<ImmutableNode> protocolEntryNode :
					node.configurationsAt(key)
			) {
				attr = "[@enable]";
				final boolean isEnable = protocolEntryNode.getBoolean(attr, true);
				if(!isEnable) {continue;}

				attr = "[@callsign]";
				final String callsign =
					DSTARUtils.formatFullLengthCallsign(protocolEntryNode.getString(attr, ""));
				if(callsign == null || DSTARDefines.EmptyLongCallsign.equals(callsign)) {continue;}

				attr = "[@protocol]";
				final String protocolString =
					protocolEntryNode.getString(attr, DSTARProtocol.Unknown.getName());
				final DSTARProtocol protocol =
					DSTARProtocol.getProtocolByNameIgnoreCase(protocolString);
				if(protocol == null || DSTARProtocol.Unknown == protocol) {continue;}

				reflectorLinkManagerProperties.getReflectorPreferredProtocols().put(callsign, protocol);
			}
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return true;
	}

	private static boolean readServices(
		final String parentKey,
		final XMLConfiguration config,
		final NoraGatewayConfiguration dstConfig
	) {
		final String key = "Services";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		try {

			boolean isNewConfiguraion = true;
			try {
				config.configurationAt(allKey);
			}catch(ConfigurationRuntimeException ex) {
				isNewConfiguraion = false;
			}

			final HierarchicalConfiguration<ImmutableNode> node =
				isNewConfiguraion ? config.configurationAt(allKey) : config;

			StatusInformationFileOutputServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getStatusInformationFileOutputServiceProperties()
			);

			WebRemoteControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getWebRemoteControlServiceProperties()
			);

			ReflectorHostFileDownloadServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getReflectorHostFileDownloadServiceProperties()
			);

			HelperServiceControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getHelperServiceProperties()
			);

			CrashReportServiceControlServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getCrashReportServiceProperties()
			);

			ICOMRepeaterCommunicationServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getIcomRepeaterCommunicationServiceProperties()
			);

			RepeaterNameServiceConfigurator.readConfig(
				isNewConfiguraion ? allKey : parentKey,
				node, dstConfig.getServiceProperties().getRepeaterNameServiceProperties()
			);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}
}
