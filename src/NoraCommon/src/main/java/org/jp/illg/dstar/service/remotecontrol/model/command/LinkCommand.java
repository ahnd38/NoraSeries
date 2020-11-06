package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.ReconnectType;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class LinkCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	private String repeaterCallsign;

	@Getter
	private ReconnectType reconnectType;

	@Getter
	private String reflectorCallsign;

	public LinkCommand() {
		super(RemoteControlCommandType.LINK);
	}

	@Override
	public LinkCommand clone() {
		LinkCommand copy = null;
		copy = (LinkCommand)super.clone();

		copy.repeaterCallsign = repeaterCallsign;

		copy.reconnectType = reconnectType;

		copy.reflectorCallsign = reflectorCallsign;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "LNK";
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

		int reconnectTypeValue = 0;
		for(int i = 0; i < 4; i++) {
			reconnectTypeValue <<= 8;
			reconnectTypeValue = (reconnectTypeValue & ~0xFF) | srcBuffer.get();
		}

		this.reconnectType = ReconnectType.getReconnectTypeByVallue(reconnectTypeValue);

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
