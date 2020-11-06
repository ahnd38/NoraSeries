package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMMode;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class SetMode extends MMDVMCommandBase {

	@Getter
	@Setter
	MMDVMMode mode;

	public SetMode() {
		super();
	}

	@Override
	public SetMode clone() {
		SetMode copy = (SetMode)super.clone();

		copy.mode = this.mode;

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		if(getDataLength() < 1) {return false;}

		setMode(MMDVMMode.getModeByModeCode(getData()[0]));

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		byte[] buf = new byte[4];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte) buf.length;
		buf[2] = MMDVMFrameType.SET_MODE.getTypeCode();
		buf[3] = getMode().getModeCode();

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.SET_MODE;
	}

}
