package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class GetRepeaterCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	private String callsign;

	public GetRepeaterCommand() {
		super(RemoteControlCommandType.GETREPEATERS);
	}

	@Override
	public GetRepeaterCommand clone() {
		GetRepeaterCommand copy = null;
		copy = (GetRepeaterCommand)super.clone();

		copy.callsign = callsign;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "GRP";
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
