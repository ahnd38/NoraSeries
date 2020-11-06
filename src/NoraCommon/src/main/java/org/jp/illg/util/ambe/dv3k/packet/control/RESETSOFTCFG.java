package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class RESETSOFTCFG extends DV3KControlPacketBase {

	@Getter
	@Setter
	private byte cfg0,cfg1,cfg2;

	@Getter
	@Setter
	private byte mask0,mask1,mask2;

	public RESETSOFTCFG() {
		super(DV3KControlPacketType.RESETSOFTCFG);

		setCfg0((byte)0x0);
		setCfg1((byte)0x0);
		setCfg2((byte)0x0);

		setMask0((byte)0x0);
		setMask1((byte)0x0);
		setMask2((byte)0x0);
	}

	@Override
	protected int getRequestControlFieldDataLength() {
		return 6;
	}

	@Override
	protected boolean assembleControlFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < getRequestControlFieldDataLength()) {
			return false;
		}

		buffer.put(getCfg0());
		buffer.put(getCfg1());
		buffer.put(getCfg2());

		buffer.put(getMask0());
		buffer.put(getMask1());
		buffer.put(getMask2());

		return true;
	}

	@Override
	protected boolean parseControlFieldData(ByteBuffer buffer, int fieldLength) {
		return true;
	}

}
