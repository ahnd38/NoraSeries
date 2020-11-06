/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
public class VoiceDataHeaderToRigResponse extends AccessPointCommandBase
implements Cloneable{

	/**
	 * コンストラクタ
	 */
	public VoiceDataHeaderToRigResponse() {
		super();
	}

	@Override
	public VoiceDataHeaderToRigResponse clone() {
		VoiceDataHeaderToRigResponse copy = null;

		copy = (VoiceDataHeaderToRigResponse)super.clone();

		return copy;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.limit() >= 4 &&
			buffer.get() == (byte)0x03 &&
			buffer.get() == (byte)0x21
		){

			for(int index = 2;index < 4;index++) {
				@SuppressWarnings("unused")
				byte data = buffer.get();
				switch(index){

				default:
					break;
				}
			}

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}else {
			buffer.rewind();
			return null;
		}
	}

	@Override
	public byte[] assembleCommandData() {
		throw new UnsupportedOperationException();
	}

}
