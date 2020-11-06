package org.jp.illg.dstar.service.web.handler;

import java.net.InetSocketAddress;

public interface WebRemoteControlIrcDDBRoutingHandler extends WebRemoteControlRoutingServiceHandler {

	public InetSocketAddress[] getServerAddress();
	public String getServerConnectionStatus();
}
