package org.jp.illg.dstar.model.config;

import java.util.List;

import lombok.Data;

@Data
public class RepeaterNameServiceProperties {

	private boolean enable;

	private List<RepeaterListImporterProperties> importers;

}
