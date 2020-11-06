package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

public class GetCallsignCommand extends RemoteControlCommandBase implements Cloneable{

	public GetCallsignCommand() {
		super(RemoteControlCommandType.GETCALLSIGNS);
	}

	@Override
	public GetCallsignCommand clone() {
		GetCallsignCommand copy = null;
		copy = (GetCallsignCommand)super.clone();

		return copy;
	}

	@Override
	protected String getHeader() {
		return "GCS";
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
