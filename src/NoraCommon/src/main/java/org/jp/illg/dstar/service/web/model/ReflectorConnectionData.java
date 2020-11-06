package org.jp.illg.dstar.service.web.model;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;

import lombok.Data;

@Data
public class ReflectorConnectionData {
	
	private UUID connectionId;
	
	private ReflectorProtocolProcessorTypes reflectorType;
	
	private ConnectionDirectionType connectionDirection;
	
	private String reflectorCallsign;
	
	private String repeaterCallsign;
	
	private int protocolVersion;
	
	
	public ReflectorConnectionData() {
		super();
	}
	
}
