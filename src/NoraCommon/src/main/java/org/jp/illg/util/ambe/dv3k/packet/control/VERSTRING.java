package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class VERSTRING extends DV3KControlPacketBase {

	@Getter
	@Setter
	private String versionString;

	public VERSTRING() {
		super(DV3KControlPacketType.VERSTRING);

		setVersionString("");

	}

	@Override
	public VERSTRING clone() {
		VERSTRING copy = null;

		copy = (VERSTRING)super.clone();

		copy.versionString = this.versionString;

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
		char[] verBuffer = new char[fieldLength];

		for(int i = 0; i < verBuffer.length && buffer.hasRemaining(); i++) {
			char c = (char)buffer.get();
			if(c == (char)0x0) {continue;}

			verBuffer[i] = c;
		}

		setVersionString(String.valueOf(verBuffer).trim());

		return true;
	}

}
