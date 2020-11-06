package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

public class DStarVoiceEOT extends DStarVoice {

	public DStarVoiceEOT() {
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
		byte[] buf = new byte[3];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte) buf.length;
		buf[2] = getCommandType().getTypeCode();

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.DSTAR_EOT;
	}
}
