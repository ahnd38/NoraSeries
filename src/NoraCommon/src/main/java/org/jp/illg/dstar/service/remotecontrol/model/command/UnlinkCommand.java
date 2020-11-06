package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class UnlinkCommand extends RemoteControlCommandBase implements Cloneable {

	@Getter
	private String repeaterCallsign;

	@Getter
	private DSTARProtocol protocol;

	@Getter
	private String reflectorCallsign;

	public UnlinkCommand() {
		super(RemoteControlCommandType.UNLINK);
	}

	@Override
	public UnlinkCommand clone() {
		UnlinkCommand copy = null;
		copy = (UnlinkCommand)super.clone();

		copy.repeaterCallsign = repeaterCallsign;

		copy.protocol = protocol;

		copy.reflectorCallsign = reflectorCallsign;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "UNL";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		int dataLength =
				DSTARDefines.CallsignFullLength +
				4 +
				DSTARDefines.CallsignFullLength;

		if(srcBuffer == null || srcBuffer.remaining() < dataLength)
			return false;

		char[] call = new char[DSTARDefines.CallsignFullLength];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		repeaterCallsign = String.valueOf(call);

		protocol = DSTARProtocol.getProtocolByValue(srcBuffer.get());

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		reflectorCallsign = String.valueOf(call);

		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}

}
