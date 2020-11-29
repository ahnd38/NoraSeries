package org.jp.illg.dstar.model.config;

import java.util.LinkedList;
import java.util.List;

import lombok.Data;

@Data
public class ReflectorNameServiceProperties {

	private boolean enable;

	private List<ReflectorHostsImporterProperties> importers;

	public ReflectorNameServiceProperties() {
		enable = true;
		importers = new LinkedList<>();
	}

}
