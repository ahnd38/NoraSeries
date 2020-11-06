/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author AHND
 *
 */
@Deprecated
public class VoiceDataToRig extends AccessPointCommandBase
implements Cloneable{

	/**
	 * コンストラクタ
	 */
	public VoiceDataToRig() {
		super();
	}

	@Override
	public VoiceDataToRig clone() {
		VoiceDataToRig copy = null;

		copy = (VoiceDataToRig)super.clone();

		return copy;
	}

	private final Object packetCounterLock = new Object();

	private int packetCounterMain = 0;
	private int packetCounterSub = 0;

	public void incrementPacketCounter() {
		synchronized(packetCounterLock) {
			if(packetCounterMain >= 0xff)
				packetCounterMain = 0;
			else
				packetCounterMain++;

			if(packetCounterSub >= 0x14)
				packetCounterSub = 0;
			else
				packetCounterSub++;
		}
	}

	public void clearPacketCounter() {
		synchronized(packetCounterLock) {
			packetCounterMain = 0;
			packetCounterSub = 0;
		}
	}

	public void setPacketCounter(int main,int sub) {
		synchronized(packetCounterLock) {
			this.packetCounterMain = main;
			this.packetCounterSub = sub;
		}
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[17];
		Arrays.fill(data, (byte)0x00);
		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:
				data[index] = (byte)0x10;break;
			case 1:
				data[index] = (byte)0x22;break;
			case 2:
				data[index] = (byte)packetCounterMain;break;
			case 3:
				data[index] = (byte)packetCounterSub;break;

			case 4:case 5:case 6:case 7:case 8:case 9:case 10:case 11:case 12:
				data[index] = super.getVoiceData().getVoiceSegment()[index - 4];break;
			case 13:case 14:case 15:
				data[index] = super.getVoiceData().getDataSegment()[index - 13];break;
			case 16:
				data[index] = (byte)0xFF;break;

			default:
				break;
			}
		}

		return data;
	}

	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}
}
