package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class LogoffCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	private String callsign;

	@Getter
	private String user;

	public LogoffCommand() {
		super(RemoteControlCommandType.LOGOFF);
	}

	@Override
	public LogoffCommand clone() {
		LogoffCommand copy = null;
		copy = (LogoffCommand)super.clone();

		copy.callsign = callsign;

		copy.user = user;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "LGO";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		int dataLength =
				DSTARDefines.CallsignFullLength +
				DSTARDefines.CallsignFullLength;

		if(srcBuffer == null || srcBuffer.remaining() < dataLength)
			return false;

		char[] call = new char[DSTARDefines.CallsignFullLength];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		callsign = String.valueOf(call);

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		user = String.valueOf(call);

		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}



}
