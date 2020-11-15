package org.jp.illg.nora.gateway.configurators;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.model.config.CrashReportServiceProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrashReportServiceControlServiceConfigurator {

	private static final String logTag =
		CrashReportServiceControlServiceConfigurator.class.getSimpleName() + " : ";

	private CrashReportServiceControlServiceConfigurator() {}

	public static boolean readConfig(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> config,
		final CrashReportServiceProperties properties
	) {
		final String key = "CrashReportService";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;
		String attr;

		try {
			final HierarchicalConfiguration<ImmutableNode> node = config.configurationAt(key);

			attr = "[@enable]";
			properties.setEnable(node.getBoolean(attr, CrashReportServiceProperties.isEnableDefault()));

			return true;
		}catch(ConfigurationRuntimeException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read " + allKey + ".");
		}

		return false;
	}
}
