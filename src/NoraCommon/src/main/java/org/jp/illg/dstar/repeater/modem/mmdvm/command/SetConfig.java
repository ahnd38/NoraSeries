package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class SetConfig extends MMDVMCommandBase {

	@Setter
	@Getter
	private boolean rxInvert;

	@Getter
	@Setter
	private boolean txInvert;

	@Getter
	@Setter
	private boolean pttInvert;

	@Getter
	@Setter
	private boolean debug;

	@Getter
	@Setter
	private boolean duplex;

	@Getter
	@Setter
	private int txDelay;

	@Getter
	@Setter
	private float rxLevel;

	@Getter
	@Setter
	private float txLevel;

	@Getter
	@Setter
	private long txDCOffset;

	@Getter
	@Setter
	private long rxDCOffset;



	public SetConfig() {
		super();
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}


	@Override
	public Optional<ByteBuffer> assembleCommand() {
		byte[] buf = new byte[21];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte)buf.length;
		buf[2] = MMDVMFrameType.SET_CONFIG.getTypeCode();

		byte d = (byte) 0x0;
		if(isRxInvert()) {d |= (byte) 0x01;}
		if(isTxInvert()) {d |= (byte) 0x02;}
		if(isPttInvert()) {d |= (byte) 0x04;}
		if(isDebug()) {d |= (byte) 0x10;}
		if(!isDuplex()) {d |= (byte) 0x80;}
		buf[3] = d;

		buf[4] = (byte) 0x01;	// enable only DSTAR

		// Tx delay
		buf[5] = (byte) (getTxDelay() / 10);
		// MODE
		buf[6] = (byte) 0x0;

		// Rx Level
		buf[7] = (byte)(getRxLevel() * 2.55F + 0.5F);

		// cwIdTxLevel
		buf[8] = (byte) (50 * 2.55F + 0.5F);
		//DMR color code
		buf[9] = (byte) 0x2;
		// DMR Delay
		buf[10] = (byte) 0x0;
		// OSC offset
		buf[11] = (byte) 128;

		// DSTAR tx level
		buf[12] = (byte) (getTxLevel() * 2.55F + 0.5F);
		// DMR tx level
		buf[13] = (byte) (50 * 2.55F + 0.5F);
		// YSF tx level
		buf[14] = (byte) (50 * 2.55F + 0.5F);
		// P25 tx level
		buf[15] = (byte) (50 * 2.55F + 0.5F);

		buf[16] = (byte) (getTxDCOffset() + 128);
		buf[17] = (byte) (getRxDCOffset() + 128);

		// NXDN tx level
		buf[18] = (byte) (50 * 2.55F + 0.5F);

		//YSF tx hang
		buf[19] = (byte) 0x0A;
		// POCSAG tx level
		buf[20] = (byte) (50 * 2.55F + 0.5F);

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.SET_CONFIG;
	}

}
