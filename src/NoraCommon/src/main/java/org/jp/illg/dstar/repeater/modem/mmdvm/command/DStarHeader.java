package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class DStarHeader extends MMDVMCommandBase {

	@Getter
	@Setter
	private Header header;

	public DStarHeader() {
		super();
	}

	@Override
	public DStarHeader clone() {
		DStarHeader copy = (DStarHeader)super.clone();

		if(this.header != null)
			copy.header = this.header.clone();

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		int dataLength = getDataLength();
		if(dataLength < 41) {return false;}

		byte[] data = getData();

		Header header = new Header();
		ArrayUtil.copyOfRange(header.getFlags(), data, 0, 2);
		ArrayUtil.copyOfRange(header.getRepeater2Callsign(), data, 3, 3 + DSTARDefines.CallsignFullLength);
		ArrayUtil.copyOfRange(header.getRepeater1Callsign(), data, 11, 11 + DSTARDefines.CallsignFullLength);
		ArrayUtil.copyOfRange(header.getYourCallsign(), data, 19, 19 + DSTARDefines.CallsignFullLength);
		ArrayUtil.copyOfRange(header.getMyCallsign(), data, 27, 27 + DSTARDefines.CallsignFullLength);
		ArrayUtil.copyOfRange(header.getMyCallsignAdd(), data, 35, 35 + DSTARDefines.CallsignShortLength);
		ArrayUtil.copyOfRange(header.getCrc(), data, 39, 40);

		setHeader(header);

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		if(getHeader() == null) {return Optional.empty();}

		byte[] buf = new byte[44];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte)buf.length;
		buf[2] = getCommandType().getTypeCode();

		ArrayUtil.copyOfRange(buf, 3, getHeader().getFlags());
		ArrayUtil.copyOfRange(buf, 6, getHeader().getRepeater2Callsign());
		ArrayUtil.copyOfRange(buf, 14, getHeader().getRepeater1Callsign());
		ArrayUtil.copyOfRange(buf, 22, getHeader().getYourCallsign());
		ArrayUtil.copyOfRange(buf, 30, getHeader().getMyCallsign());
		ArrayUtil.copyOfRange(buf, 38, getHeader().getMyCallsignAdd());

		int crc = DSTARCRCCalculator.calcCRCRange(buf, 3, 41);
		buf[42] = (byte)(crc & 0xff);
		buf[43] = (byte)((crc >> 8) & 0xff);

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.DSTAR_HEADER;
	}

}
