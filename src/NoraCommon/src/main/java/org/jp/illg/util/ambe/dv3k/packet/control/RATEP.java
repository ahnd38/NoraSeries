package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class RATEP extends DV3KControlPacketBase {


	@Getter
	@Setter
	private int rcw0,rcw1,rcw2,rcw3,rcw4,rcw5;

	@Getter
	@Setter
	private int result;

	public RATEP() {
		super(DV3KControlPacketType.RATEP);

		setRcw0(0x0);
		setRcw1(0x0);
		setRcw2(0x0);
		setRcw3(0x0);
		setRcw4(0x0);
		setRcw5(0x0);
		setResult(0x0);
	}

	@Override
	public RATEP clone() {
		RATEP copy = null;

		copy = (RATEP)super.clone();

		copy.rcw0 = this.rcw0;
		copy.rcw1 = this.rcw1;
		copy.rcw2 = this.rcw2;
		copy.rcw3 = this.rcw3;
		copy.rcw4 = this.rcw4;
		copy.rcw5 = this.rcw5;
		copy.result = this.result;

		return copy;
	}

	@Override
	protected int getRequestControlFieldDataLength() {
		return 12;
	}

	@Override
	protected boolean assembleControlFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < 12) {return false;}

		buffer.put((byte)((getRcw0() >> 8) & 0xFF));
		buffer.put((byte)(getRcw0() & 0xFF));

		buffer.put((byte)((getRcw1() >> 8) & 0xFF));
		buffer.put((byte)(getRcw1() & 0xFF));

		buffer.put((byte)((getRcw2() >> 8) & 0xFF));
		buffer.put((byte)(getRcw2() & 0xFF));

		buffer.put((byte)((getRcw3() >> 8) & 0xFF));
		buffer.put((byte)(getRcw3() & 0xFF));

		buffer.put((byte)((getRcw4() >> 8) & 0xFF));
		buffer.put((byte)(getRcw4() & 0xFF));

		buffer.put((byte)((getRcw5() >> 8) & 0xFF));
		buffer.put((byte)(getRcw5() & 0xFF));

		return true;
	}

	@Override
	protected boolean parseControlFieldData(ByteBuffer buffer, int fieldLength) {
		if(fieldLength < 1 || !buffer.hasRemaining()) {return false;}

		setResult(buffer.get());

		return true;
	}

}
