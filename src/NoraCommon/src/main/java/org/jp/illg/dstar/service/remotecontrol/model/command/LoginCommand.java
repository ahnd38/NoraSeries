package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

public class LoginCommand extends RemoteControlCommandBase implements Cloneable{

	public LoginCommand() {
		super(RemoteControlCommandType.LOGIN);
	}

	@Override
	public LoginCommand clone() {
		LoginCommand copy = null;
		copy = (LoginCommand)super.clone();

		return copy;
	}

	@Override
	public String getHeader() {
		return "LIN";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		byte[] data = new byte[0];
		return Optional.of(data);
	}

}
