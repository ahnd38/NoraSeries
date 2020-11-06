package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.Getter;
import lombok.Setter;

public class Poll extends G2PacketBase implements Cloneable {

	@Getter
	private List<String> repeaters;

	@Getter
	@Setter
	private int remoteId;


	public Poll(
	) {
		super(
			null,
			ConnectionDirectionType.Unknown,
			new DVPacket(new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader), new Header())
		);

		getDVPacket().setPacketType(PacketType.Poll);

		repeaters = new ArrayList<>();

		setRemoteId(0x0);
	}

	@Override
	public Poll clone() {
		Poll copy = (Poll)super.clone();

		copy.remoteId = remoteId;
		copy.repeaters = new ArrayList<>(repeaters);

		return copy;
	}

	@Override
	public byte[] assembleCommandData() {

		int dataLength = 5 + 2 + DSTARDefines.CallsignFullLength + repeaters.size() + 1;
		byte[] data = new byte[dataLength];
		Arrays.fill(data, (byte)0x00);

		for(int index = 0;index < data.length; index++) {
			switch(index) {
			case 0:
				data[index] = 'D';
				break;
			case 1:
				data[index] = 'S';
				break;
			case 2:
				data[index] = 'V';
				break;
			case 3:
				data[index] = 'T';
				break;

			case 4:
				data[index] = PacketType.Poll.getValue();
				break;

			case 5:case 6:
				data[index] = (byte)((remoteId >> 8) & 0xFF);
				remoteId = remoteId << 8;
				break;

			case 7:case 8:case 9:case 10:
			case 11:case 12:case 13:case 14:
				data[index] = (byte)getRepeater1Callsign()[index - 7];
				break;

			default:
				byte w = 0x00;
				int i = index - 15;
				if(repeaters.size() > i)
					w = (byte)DSTARUtils.formatFullLengthCallsign(repeaters.get(i)).charAt(DSTARDefines.CallsignFullLength - 1);
				else
					w = 0x00;

				data[index] = w;
				break;
			}
		}

		return data;
	}

	@Override
	public G2Packet parseCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.remaining() >= 16 &&
			buffer.get() == 'D' &&
			buffer.get() == 'S' &&
			buffer.get() == 'V' &&
			buffer.get() == 'T' &&
			PacketType.hasPacketType(PacketType.Poll, buffer.get())
		){
			repeaters.clear();
			setRemoteId(0x0);

			for(int index = 5; buffer.hasRemaining();index++) {
				byte data = buffer.get();

				if(index >= 5 && index <= 6) {
					remoteId = ((remoteId << 8) & ~0xFF) | (data & 0xFF);
				}
				else if(index >= 7 && index <= 14) {
					// Gateway callsign
					getRepeater1Callsign()[index - 7] = (char)data;
				}
				else {
					// Repeater modules
					if(data == 0x0) {break;}

					String gatewayCallsign =
						DSTARUtils.formatFullCallsign(String.valueOf(getRepeater1Callsign()));

					repeaters.add(DSTARUtils.formatFullCallsign(gatewayCallsign, (char)data));
				}
			}

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}
		else {
			buffer.rewind();
			return null;
		}
	}

}
