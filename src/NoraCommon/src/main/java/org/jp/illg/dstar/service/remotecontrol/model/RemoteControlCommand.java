package org.jp.illg.dstar.service.remotecontrol.model;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import com.annimon.stream.Optional;

public interface RemoteControlCommand {

	public RemoteControlCommandType getType();

	public RemoteControlCommand clone();

	public InetAddress getRemoteAddress();
	public void setRemoteAddress(InetAddress remoteAddress);
	public int getRemotePort();
	public void setRemotePort(int remotePort);

	public Optional<RemoteControlCommand> isValidCommand(ByteBuffer buffer);

	public Optional<byte[]> assembleCommand();
}
