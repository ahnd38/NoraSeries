package org.jp.illg.dstar.model.config;

import lombok.Data;

@Data
public class StatusInformationFileOutputServiceProperties {

	private boolean enable = false;

	private String outputPath = "";

}
