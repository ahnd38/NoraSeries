package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class PRODID extends DV3KControlPacketBase {

	@Getter
	@Setter
	private String productionID;

	public PRODID() {
		super(DV3KControlPacketType.PRODID);

		setProductionID("");
	}

	@Override
	public PRODID clone() {
		PRODID copy = null;

		copy = (PRODID)super.clone();

		copy.productionID = this.productionID;

		return copy;
	}

	@Override
	protected int getRequestControlFieldDataLength() {
		return 0;
	}

	@Override
	protected boolean assembleControlFieldData(ByteBuffer buffer) {
		return true;
	}

	@Override
	protected boolean parseControlFieldData(ByteBuffer buffer, int fieldLength) {
		char[] idBuffer = new char[fieldLength];

		for(int i = 0; i < idBuffer.length && buffer.hasRemaining(); i++) {
			char c = (char)buffer.get();
			if(c == (char)0x0) {continue;}

			idBuffer[i] = c;
		}

		setProductionID(String.valueOf(idBuffer).trim());

		return true;
	}

}
