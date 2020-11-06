package org.jp.illg.dstar.model.config;

import java.util.Properties;

import org.jp.illg.dstar.model.defines.RepeaterListImporterType;

import lombok.Data;

@Data
public class RepeaterListImporterProperties {

	private boolean enable;

	private RepeaterListImporterType importerType;

	private Properties configurationProperties;
}
