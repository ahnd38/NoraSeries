package org.jp.illg.dstar.service.web.model;

import java.util.List;

import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRConfig;
import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRLoginClient;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoraVRStatusData extends ModemStatusData {
	
	private List<NoraVRLoginClient> loginClients;
	
	private NoraVRConfig serverConfig;
	
	public NoraVRStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
