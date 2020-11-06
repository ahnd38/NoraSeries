package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

public class AckCommand extends RemoteControlCommandBase implements Cloneable {

	public AckCommand() {
		super(RemoteControlCommandType.ACK);
	}

	@Override
	public NakCommand clone() {
		NakCommand copy = null;
		copy = (NakCommand)super.clone();

		return copy;
	}

	@Override
	protected String getHeader() {
		return "ACK";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.of(new byte[0]);
	}

}
