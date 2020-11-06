package org.jp.illg.dstar.service.web.handler;

import java.util.List;

import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRConfig;
import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRLoginClient;

public interface WebRemoteControlNoraVRHandler extends WebRemoteControlModemHandler {
	
	public List<NoraVRLoginClient> getLoginClients();
	
	public NoraVRConfig getConfig();
}
