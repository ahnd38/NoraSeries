package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

public class LogoutCommand extends RemoteControlCommandBase implements Cloneable{

	public LogoutCommand() {
		super(RemoteControlCommandType.LOGOUT);
	}

	@Override
	public LogoutCommand clone() {
		LogoutCommand copy = null;
		copy = (LogoutCommand)super.clone();

		return copy;
	}

	@Override
	protected String getHeader() {
		return "LOG";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}

}
