package org.jp.illg.nora.gateway.service.norausers.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;

import lombok.Data;

@Data
public class ModemInformation {
	
	private int modemId;
	
	private String modemType;
	
	private Map<String, String> modemProperties;
	
	private String scope;
	
	public ModemInformation() {
		super();
		
		modemId = 0x0;
		modemType = ModemTypes.Unknown.getTypeName();
		modemProperties = new ConcurrentHashMap<>();
		scope = AccessScope.Unknown.getTypeName();
	}
	
}
