package org.jp.illg.dstar.service.web.handler;

import org.jp.illg.dstar.service.web.model.RepeaterStatusData;

public interface WebRemoteControlRepeaterHandler extends WebRemoteControlHandler {
	
	public String getRepeaterCallsign();
	
	public RepeaterStatusData createStatusData();
	
	public Class<? extends RepeaterStatusData> getStatusDataType();
}
