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
public class VoiceDataHeaderFromRig extends AccessPointCommandBase
implements Cloneable{

	/**
	 * コンストラクタ
	 */
	public VoiceDataHeaderFromRig() {
		super();
	}

	@Override
	public VoiceDataHeaderFromRig clone() {
		VoiceDataHeaderFromRig copy = null;

		copy = (VoiceDataHeaderFromRig)super.clone();

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
			buffer.limit() >= 45 &&
			buffer.get() == (byte)0x2c &&
			buffer.get() == (byte)0x10
		){
			for(int index = 2;index < 45;index++) {
				byte data = buffer.get();

				switch(index){
				case 2:case 3:case 4:
					getDvHeader().getFlags()[index - 2] = data;
					break;

				case 5:case 6:case 7:case 8:
				case 9:case 10:case 11:case 12:
					getRepeater2Callsign()[index - 5] = (char) data;
					break;

				case 13:case 14:case 15:case 16:
				case 17:case 18:case 19:case 20:
					getRepeater1Callsign()[index - 13] = (char) data;
					break;

				case 21:case 22:case 23:case 24:
				case 25:case 26:case 27:case 28:
					getYourCallsign()[index - 21] = (char) data;
					break;

				case 29:case 30:case 31:case 32:
				case 33:case 34:case 35:case 36:
					getMyCallsign()[index - 29] = (char) data;
					break;

				case 37:case 38:case 39:case 40:
					getMyCallsignAdd()[index - 37] = (char)data;
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
		String str = "UR:";
		str += String.valueOf(this.getYourCallsign());
		str += "/RPT1:";
		str += String.valueOf(this.getRepeater1Callsign());
		str += "/RPT2:";
		str += String.valueOf(this.getRepeater2Callsign());
		str += "/MY:";
		str += String.valueOf(this.getMyCallsign());

		return str;
	}
*/
}
