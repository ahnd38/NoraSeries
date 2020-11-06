package org.jp.illg.dstar.routing.service.ircDDB.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServerProperties {

	private boolean debug;

	private String serverAddress;

	private int serverPort;

	private String serverPassword;

	private String callsign;

	private String channel;

	private String debugChannel;

	private String debugServerUser;

}
