package org.jp.illg.dstar.service.web.handler;

import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.service.web.model.ModemStatusData;

public interface WebRemoteControlModemHandler extends WebRemoteControlHandler{
	
	public int getModemId();
	
	public ModemTypes getModemType();
	
	public DSTARRepeater getRepeater();
	
	public ModemStatusData createStatusData();
	
	public Class<? extends ModemStatusData> getStatusDataType();
}
