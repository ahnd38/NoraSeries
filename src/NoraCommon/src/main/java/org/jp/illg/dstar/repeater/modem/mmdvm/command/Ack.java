package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

public class Ack extends MMDVMCommandBase {

	public Ack() {
		super();
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		throw new UnsupportedOperationException();
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.ACK;
	}

}
