package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.VoiceAMBE;

public class VoiceData extends AccessPointCommandBase {

	private int packetCounterMain = 0;
	private int packetCounterSub = 0;

	public VoiceData() {
		super();

		final BackBoneHeader backbone = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, 0x0
		);
		final VoiceAMBE voice = new VoiceAMBE();
		setDvPacket(new DVPacket(backbone, voice));

		clearPacketCounter();
	}

	public void incrementPacketCounter() {
		if(packetCounterMain >= 0xff)
			packetCounterMain = 0;
		else
			packetCounterMain++;

		if(packetCounterSub >= 0x14)
			packetCounterSub = 0;
		else
			packetCounterSub++;
	}

	public void clearPacketCounter() {
		packetCounterMain = 0;
		packetCounterSub = 0;
	}

	public void setPacketCounter(int main,int sub) {
		packetCounterMain = main;
		packetCounterSub = sub;
	}

	@Override
	public VoiceData clone() {
		final VoiceData cloneInstance = (VoiceData)super.clone();

		cloneInstance.packetCounterMain = packetCounterMain;
		cloneInstance.packetCounterSub = packetCounterSub;

		return cloneInstance;
	}

	@Override
	public byte[] assembleCommandData() {
		final byte[] data = new byte[17];
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

}
