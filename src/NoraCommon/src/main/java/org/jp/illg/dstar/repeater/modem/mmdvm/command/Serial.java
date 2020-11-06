package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class Serial extends MMDVMCommandBase {

	@Getter
	@Setter
	private byte[] serialData;

	public Serial() {
		serialData = null;
	}

	@Override
	public Serial clone() {
		Serial copy = (Serial)super.clone();

		if(serialData != null)
			copy.serialData = Arrays.copyOf(serialData, serialData.length);

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		serialData = Arrays.copyOf(getData(), getData().length);

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		final int length = 3 + (serialData != null ? serialData.length : 0);

		if(length > 255) {return Optional.empty();}

		final byte[] buf = new byte[length];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte) buf.length;
		buf[2] = getCommandType().getTypeCode();

		ArrayUtil.copyOfRange(buf, 3, serialData);

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.SERIAL;
	}

}
