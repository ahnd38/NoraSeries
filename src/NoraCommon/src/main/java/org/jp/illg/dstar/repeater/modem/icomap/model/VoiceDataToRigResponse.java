/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
public class VoiceDataToRigResponse extends AccessPointCommandBase
implements Cloneable{

	/**
	 * コンストラクタ
	 */
	public VoiceDataToRigResponse() {
		super();
	}

	@Override
	public VoiceDataToRigResponse clone() {
		final VoiceDataToRigResponse cloneInstance = (VoiceDataToRigResponse)super.clone();

		return cloneInstance;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			(
				(buffer.limit() >= 5 && buffer.array()[3] == (byte)0x00) ||
				(buffer.limit() >= 10 && buffer.array()[3] == (byte)0x01)
			) &&
			buffer.get() == (byte)0x04 &&
			buffer.get() == (byte)0x23
		){
			boolean ext = false;

			for(int index = 2;index < 5;index++) {
				byte data = buffer.get();
				switch(index){
				case 3:
					if(data == (byte)0x01) {ext = true;}
				default:
					break;
				}
			}

			if(ext) {
				for(int index = 5;index < 10;index++) {
					@SuppressWarnings("unused")
					byte data = buffer.get();
					switch(index){

					default:
						break;
					}
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
