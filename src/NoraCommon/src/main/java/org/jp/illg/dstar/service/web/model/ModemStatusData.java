package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModemStatusData extends StatusData {
	
	private int modemId;
	
	private ModemTypes modemType;

	private String webSocketRoomId;

	private String gatewayCallsign;

	private String repeaterCallsign;
	
	private boolean allowDIRECT;
	
	private AccessScope scope;
	
	
	public ModemStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
