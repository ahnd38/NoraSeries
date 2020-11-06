package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

public interface MMDVMCommand {

	public MMDVMFrameType getCommandType();

	public boolean isValidCommand(ByteBuffer buffer);

	public Optional<MMDVMCommand> parseCommand(ByteBuffer buffer);

	public Optional<ByteBuffer> assembleCommand();

	public MMDVMCommand clone();
}
