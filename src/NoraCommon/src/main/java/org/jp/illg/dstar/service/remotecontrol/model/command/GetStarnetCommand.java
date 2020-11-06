package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class GetStarnetCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	private String callsign;

	public GetStarnetCommand() {
		super(RemoteControlCommandType.GETSTARNET);
	}

	@Override
	protected String getHeader() {
		return "GSN";
	}

	@Override
	public GetStarnetCommand clone() {
		GetStarnetCommand copy = null;
		copy = (GetStarnetCommand)super.clone();

		copy.callsign = callsign;

		return copy;
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		int dataLength =
				DSTARDefines.CallsignFullLength;

		if(srcBuffer == null || srcBuffer.remaining() < dataLength)
			return false;

		char[] call = new char[DSTARDefines.CallsignFullLength];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		callsign = String.valueOf(call);

		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}

}
