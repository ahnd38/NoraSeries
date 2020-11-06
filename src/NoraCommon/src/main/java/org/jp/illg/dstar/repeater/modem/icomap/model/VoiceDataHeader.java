package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;

public class VoiceDataHeader extends AccessPointCommandBase {

	public VoiceDataHeader() {
		super();

		final BackBoneHeader backbone = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, 0x0
		);
		final Header header = new Header();
		setDvPacket(new DVPacket(backbone, header));
	}

	@Override
	public VoiceDataHeader clone() {
		final VoiceDataHeader cloneInstance = (VoiceDataHeader)super.clone();

		return cloneInstance;
	}

	@Override
	public byte[] assembleCommandData() {
		final byte[] data = new byte[42];
		Arrays.fill(data, (byte)0x00);
		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:
				data[index] = (byte)0x29;
				break;
			case 1:
				data[index] = (byte)0x20;
				break;

			case 2:case 3:case 4:
				data[index] = super.getDvHeader().getFlags()[index - 2];break;
			case 5:case 6:case 7:case 8:
			case 9:case 10:case 11:case 12:
				data[index] = (byte)super.getDvHeader().getRepeater2Callsign()[index - 5];break;
			case 13:case 14:case 15:case 16:
			case 17:case 18:case 19:case 20:
				data[index] = (byte)super.getDvHeader().getRepeater1Callsign()[index - 13];break;
			case 21:case 22:case 23:case 24:
			case 25:case 26:case 27:case 28:
				data[index] = (byte)super.getDvHeader().getYourCallsign()[index - 21];break;
			case 29:case 30:case 31:case 32:
			case 33:case 34:case 35:case 36:
				data[index] = (byte)super.getDvHeader().getMyCallsign()[index - 29];break;
			case 37:case 38:case 39:case 40:
				data[index] = (byte)super.getDvHeader().getMyCallsignAdd()[index - 37];break;
			case 41:
				data[index] = (byte)0xFF;break;

			default:
				break;
			}
		}

		return data;
	}

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

}
