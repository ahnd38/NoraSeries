package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class Transparent extends MMDVMCommandBase {

	@Getter
	@Setter
	private byte[] serialData;

	@Getter
	@Setter
	private byte customFrameType;

	public Transparent() {
		serialData = null;

		customFrameType = 0x0;
	}

	@Override
	public Transparent clone() {
		Transparent copy = (Transparent)super.clone();

		if(serialData != null)
			copy.serialData = Arrays.copyOf(serialData, serialData.length);

		copy.customFrameType = customFrameType;

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

		if(getCustomFrameType() != 0x0)
			buf[2] = getCustomFrameType();
		else
			buf[2] = getCommandType().getTypeCode();

		ArrayUtil.copyOfRange(buf, 3, serialData);

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.TRANSPARENT;
	}

}

