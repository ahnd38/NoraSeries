/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
@Deprecated
public class VoiceDataFromRig extends AccessPointCommandBase
implements Cloneable{

	/**
	 * コンストラクタ
	 */
	public VoiceDataFromRig() {
		super();
	}

	@Override
	public VoiceDataFromRig clone() {
		VoiceDataFromRig copy = null;

		copy = (VoiceDataFromRig)super.clone();

		return copy;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		throw new UnsupportedOperationException();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.limit() >= 17 &&
			buffer.get() == (byte)0x10 &&
			buffer.get() == (byte)0x12
		){
			for(int index = 2;index < 17;index++) {
				byte data = buffer.get();
				switch(index){
				case 2:
					break;
				case 3:
					getBackBone().setManagementInformation(data);
					break;
				case 4:case 5:case 6:case 7:case 8:case 9:case 10:case 11:case 12:
					getVoiceData().getVoiceSegment()[index - 4] = data;
					break;
				case 13: case 14:case 15:
					getDataSegment()[index - 13] = data;
					break;
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

/*
	@Override
	public String toString() {
		String str = "VoiceData:";
		str += FormatUtil.bytesToHex(this.getVoiceSegment());
		str += "/Data:";
		str += FormatUtil.bytesToHex(this.getDataSegment());

		return str;
	}
*/
}
