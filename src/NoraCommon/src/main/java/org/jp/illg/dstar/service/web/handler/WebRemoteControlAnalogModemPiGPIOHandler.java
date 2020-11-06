package org.jp.illg.dstar.service.web.handler;

import org.jp.illg.dstar.service.web.model.AnalogModemPiGPIOHeaderData;

public interface WebRemoteControlAnalogModemPiGPIOHandler extends WebRemoteControlModemHandler {
	
	public void updateHeaderFromWebRemoteControl(final AnalogModemPiGPIOHeaderData data);
}
