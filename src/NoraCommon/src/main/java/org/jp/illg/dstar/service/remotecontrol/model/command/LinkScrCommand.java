package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.ReconnectType;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class LinkScrCommand extends RemoteControlCommandBase implements Cloneable {

	@Getter
	private String repeaterCallsign;

	@Getter
	private String reflectorCallsign;

	@Getter
	private ReconnectType reconnectType;


	public LinkScrCommand() {
		super(RemoteControlCommandType.LINKSCR);
	}

	@Override
	public LinkScrCommand clone() {
		LinkScrCommand copy = null;
		copy = (LinkScrCommand)super.clone();

		copy.repeaterCallsign = repeaterCallsign;

		copy.reflectorCallsign = reflectorCallsign;

		copy.reconnectType = reconnectType;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "LKS";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		int dataLength =
				DSTARDefines.CallsignFullLength +	// repeater callsign
				DSTARDefines.CallsignFullLength +	// reflector callsign
				1;													// reconnect

		if(srcBuffer == null || srcBuffer.remaining() < dataLength)
			return false;

		char[] call = new char[DSTARDefines.CallsignFullLength];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		repeaterCallsign = String.valueOf(call);

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			call[i] = (char)srcBuffer.get();

		reflectorCallsign = String.valueOf(call);

		reconnectType = ReconnectType.getReconnectTypeByVallue(srcBuffer.get());

		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}

}
