package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

public class StarnetCommand extends RemoteControlCommandBase implements Cloneable {

	public StarnetCommand() {
		super(RemoteControlCommandType.STARNET);
	}

	@Override
	public StarnetCommand clone() {
		StarnetCommand copy = null;
		copy = (StarnetCommand)super.clone();

		return copy;
	}

	@Override
	protected String getHeader() {
		return "SNT";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return false;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.of(new byte[0]);
	}

}
