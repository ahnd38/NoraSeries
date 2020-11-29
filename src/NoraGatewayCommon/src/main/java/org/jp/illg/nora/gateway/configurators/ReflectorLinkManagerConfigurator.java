package org.jp.illg.nora.gateway.configurators;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.config.AutoConnectRepeaterEntry;
import org.jp.illg.dstar.model.config.ReflectorBlackListEntry;
import org.jp.illg.dstar.model.config.ReflectorLinkManagerProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorLinkManagerConfigurator {

	private static final String logHeader = ReflectorLinkManagerConfigurator.class.getSimpleName() + " : ";

	private ReflectorLinkManagerConfigurator() {}

	public static boolean readReflectorLinkManager(
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

			readAutoConnect(
				allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
			);

			readReflectorBlockList(
				allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
			);

			readDefaultReflectorPreferredProtocols(
				allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
			);

			readReflectorPreferredProtocols(
				allKey, reflectorLinkManagerNode, reflectorLinkManagerProperties
			);

			return true;

		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");

			return true;
		}
	}

	private static boolean readAutoConnect(
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

			readAutoConnectRepeater(allKey, autoConnectNode, reflectorLinkManagerProperties);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logHeader + "Could not read " + allKey + ".");
			}
		}

		return false;
	}

	private static boolean readAutoConnectRepeater(
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

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readReflectorBlockList(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
		return readReflectorLinkManagerReflectorBlockList(
				"ReflectorBlackList",
				parentKey,
				node,
				reflectorLinkManagerProperties
			) &&
			readReflectorLinkManagerReflectorBlockList(
				"ReflectorBlockList",
				parentKey,
				node,
				reflectorLinkManagerProperties
			);
	}

	private static boolean readReflectorLinkManagerReflectorBlockList(
		final String key,
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final ReflectorLinkManagerProperties reflectorLinkManagerProperties
	) {
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

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readDefaultReflectorPreferredProtocols(
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

			readDefaultReflectorPreferredProtocolsProtocolEntry(
				allKey, defaultReflectorPreferredProtocolsNode, reflectorLinkManagerProperties
			);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readDefaultReflectorPreferredProtocolsProtocolEntry(
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
				final HierarchicalConfiguration<ImmutableNode> protocolEntryNode :
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

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readReflectorPreferredProtocols(
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

			readReflectorPreferredProtocolsProtocolEntry(
				allKey, reflectorPreferredProtocolsNode, reflectorLinkManagerProperties
			);

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}

	private static boolean readReflectorPreferredProtocolsProtocolEntry(
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
				final HierarchicalConfiguration<ImmutableNode> protocolEntryNode : node.configurationsAt(key)
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

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not read " + allKey + ".");
		}

		return false;
	}
}
