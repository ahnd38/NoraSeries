package org.jp.illg.dstar.service.remotecontrol.model;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class RemoteControlUserEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetAddress remoteAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int remotePort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long lastActivityTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Queue<RemoteControlCommand> receiveCommand;

	@Getter
	@Setter
	private boolean loggedin;

	@Getter
	@Setter
	private int randomValue;

	private RemoteControlUserEntry() {
		super();

		setCreatedTime(System.currentTimeMillis());

		setReceiveCommand(new LinkedList<RemoteControlCommand>());
	}

	public RemoteControlUserEntry(InetAddress remoteAddress, int remotePort) {
		this();

		setRemoteAddress(remoteAddress);
		setRemotePort(remotePort);
	}

	public String getRemoteHostAddress() {
		return getRemoteAddress().getHostAddress() + ":" + String.valueOf(getRemotePort());
	}

	public void updateLastActivityTime() {
		setLastActivityTime(System.currentTimeMillis());
	}

}
