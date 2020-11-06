package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMHardwareType;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class SetFrequency extends MMDVMCommandBase {

	@Getter
	@Setter
	private MMDVMHardwareType hardwareType;

	@Getter
	@Setter
	private float rfLevel;

	@Getter
	@Setter
	private long rxFrequency;

	@Getter
	@Setter
	private long txFrequency;


	public SetFrequency() {
		super();

		setHardwareType(MMDVMHardwareType.Unknown);
		setRfLevel(0);
		setRxFrequency(0);
		setTxFrequency(0);
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<MMDVMCommand> parseCommand(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		int length = 0;

		byte[] buf = null;

		if(getHardwareType() == MMDVMHardwareType.DVMEGA) {
			length = 12;
			buf = new byte[length];
		}
		else {
			length = 17;
			buf = new byte[length];

			buf[12] = (byte)(getRfLevel() * 2.55F + 0.5F);

			//POCSAG is non support
			buf[13] = (byte)((getTxFrequency() >> 0) & 0xFF);
			buf[14] = (byte)((getTxFrequency() >> 8) & 0xFF);
			buf[15] = (byte)((getTxFrequency() >> 16) & 0xFF);
			buf[16] = (byte)((getTxFrequency() >> 24) & 0xFF);
		}

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte) length;
		buf[2] = MMDVMFrameType.SET_FREQ.getTypeCode();
		buf[3] = (byte) 0x0;

		buf[4] = (byte)((getRxFrequency() >> 0) & 0xFF);
		buf[5] = (byte)((getRxFrequency() >> 8) & 0xFF);
		buf[6] = (byte)((getRxFrequency() >> 16) & 0xFF);
		buf[7] = (byte)((getRxFrequency() >> 24) & 0xFF);

		buf[8]  = (byte)((getTxFrequency() >> 0) & 0xFF);
		buf[9]  = (byte)((getTxFrequency() >> 8) & 0xFF);
		buf[10] = (byte)((getTxFrequency() >> 16) & 0xFF);
		buf[11] = (byte)((getTxFrequency() >> 24) & 0xFF);

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.SET_FREQ;
	}

}
