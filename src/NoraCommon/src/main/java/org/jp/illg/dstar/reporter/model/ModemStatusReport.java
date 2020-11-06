package org.jp.illg.dstar.reporter.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class ModemStatusReport {
	
	private int modemId;
	
	private ModemTypes modemType;
	
	@Setter(AccessLevel.PRIVATE)
	private Map<String, String> modemProperties;
	
	private AccessScope scope;
	
	public ModemStatusReport() {
		super();
		
		modemProperties = new ConcurrentHashMap<>();
	}
	
}
