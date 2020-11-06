package org.jp.illg.dstar.service.web.handler;

import java.util.List;

import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;

public interface WebRemoteControlReflectorHandler
extends WebRemoteControlHandler {
	
	public ReflectorProtocolProcessorTypes getReflectorType();
	
	public List<ReflectorConnectionData> getReflectorConnections();
	
	public ReflectorStatusData createStatusData();
	
	public Class<? extends ReflectorStatusData> getStatusDataType();
}
