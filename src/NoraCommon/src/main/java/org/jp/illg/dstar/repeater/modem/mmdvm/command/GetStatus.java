package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class GetStatus extends MMDVMCommandBase {

	@Getter
	@Setter
	private boolean tx;

	@Getter
	@Setter
	private boolean adcOverflow;

	@Getter
	@Setter
	private boolean rxOverflow;

	@Getter
	@Setter
	private boolean txOverflow;

	@Getter
	@Setter
	private boolean lockout;

	@Getter
	@Setter
	private boolean dacOverflow;

	@Getter
	@Setter
	private boolean cd;

	@Getter
	@Setter
	private int dstarSpace;


	public GetStatus() {
		super();
	}

	@Override
	public GetStatus clone() {
		GetStatus copy = (GetStatus)super.clone();

		copy.tx = this.tx;
		copy.adcOverflow = this.adcOverflow;
		copy.rxOverflow = this.rxOverflow;
		copy.txOverflow = this.txOverflow;
		copy.lockout = this.lockout;
		copy.dacOverflow = this.dacOverflow;
		copy.cd = this.cd;

		copy.dstarSpace = this.dstarSpace;

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		int dataLength = getDataLength();
		byte[] data = getData();

		if(dataLength < 5) {return false;}

		setTx((data[2] & 0x02) == 0x02);
		setAdcOverflow((data[2] & 0x02) == 0x02);
		setRxOverflow((data[2] & 0x04) == 0x04);
		setTxOverflow((data[2] & 0x08) == 0x08);
		setLockout((data[2] & 0x10) == 0x10);
		setDacOverflow((data[2] & 0x20) == 0x20);
		setCd((data[2] & 0x40) == 0x40);

		// DSTAR space
		setDstarSpace(data[3]);
		// DMR space 1
		// DMR space 2
		// YSF space
		// P25 space
		// NXDN space
		// PCSAG space

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		byte[] buf = new byte[3];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = 3;
		buf[2] = MMDVMFrameType.GET_STATUS.getTypeCode();

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.GET_STATUS;
	}
}
