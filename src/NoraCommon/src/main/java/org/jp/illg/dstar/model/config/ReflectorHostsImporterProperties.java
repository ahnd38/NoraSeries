package org.jp.illg.dstar.model.config;

import java.util.Properties;

import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;

import lombok.Data;

@Data
public class ReflectorHostsImporterProperties {

	private boolean enable;

	private int intervalMinutes;

	private ReflectorHostsImporterType type;

	private Properties configurationProperties;

	public ReflectorHostsImporterProperties() {
		this.configurationProperties = new Properties();
	}
}
