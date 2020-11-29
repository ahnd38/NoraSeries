package org.jp.illg.nora.gateway.configurators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.config.CallsignEntry;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.gateway.NoraGatewayConfiguration;
import org.jp.illg.nora.vr.model.NoraVRLoginUserEntry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigurator {

	private static final String logHeader = RepeaterConfigurator.class.getSimpleName() + " : ";

	private RepeaterConfigurator() {}

	public static boolean readRepeaters(
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
					readConfigurationProperties(allKey, repeater, repeaterProperties);
				}

				if(RepeaterTypes.ExternalHomebrew.getTypeName().equals(repeaterProperties.getType())) {
					readAccessAllowList(allKey, repeater, repeaterProperties);
				}

				//Modem
				if(RepeaterTypes.Internal.getTypeName().equals(repeaterProperties.getType())){
					if(
						(
							!readModem(allKey, repeater, repeaterProperties) &&
							!readModems(allKey, repeater, repeaterProperties)
						) ||
						repeaterProperties.getModemProperties().isEmpty()
					) {
						if(log.isWarnEnabled()) {
							log.warn(
								logHeader +
								"InternalRepeater = " + repeaterProperties.getCallsign() + " must have modem configuration, ignore."
							);
						}

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

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Could not read " + allKey + ".");
		}

		return false;
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

			return true;
		}catch(ConfigurationRuntimeException ex){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static ModemProperties readModem(
		final HierarchicalConfiguration<ImmutableNode> modemNode
	) {
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

	private static boolean readConfigurationProperties(
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

			for(Iterator<String> it = configularationProperties.getKeys(); it.hasNext();) {
				final String propertyKey = it.next();
				final String propertyValue = configularationProperties.getString(propertyKey, "");

				properties.getConfigurationProperties().setProperty(propertyKey, propertyValue);
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Could not read " + allKey + "/Callsign:" + properties.getCallsign() + ".");
		}

		return false;
	}

	private static boolean readAccessAllowList(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {

		return readRepeaterAccessAllowList(
				"AccessAllowList.CallsignEntry",
				parentKey,
				repeaterNode,
				properties
			) ||
			readRepeaterAccessAllowList(	//設定互換性保持用(将来的に削除)
				"AccessWhiteList.CallsignEntry",
				parentKey,
				repeaterNode,
				properties
			);
	}

	private static boolean readRepeaterAccessAllowList(
		final String key,
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> repeaterNode,
		final RepeaterProperties properties
	) {
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

				properties.getAccessAllowList().add(new CallsignEntry(isEnable, callsign));
			}

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Could not read " + allKey + "/Callsign:" + properties.getCallsign() + ".");
		}

		return false;
	}
}
