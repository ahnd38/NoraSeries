/**
 *
 */
package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.DSTARCRCCalculator;

/**
 * @author AHND
 *
 */
public class VoiceHeaderToInet
	extends VoiceHeader
{

	public VoiceHeaderToInet(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket packet
	) {
		super(
			loopBlockID,
			connectionDirection,
			packet
		);
	}

	public VoiceHeaderToInet(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final BackBoneHeader backbone,
		final Header header
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, header)
		);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		final byte[] data = new byte[56];
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
				data[index] =
					(byte)(PacketType.Header.getValue() | (~PacketType.getMask() & getDVPacket().getFlags()[index - 4]));
				break;
			case 5:
				data[index] = getDVPacket().getFlags()[index - 4];
				break;
			case 6:case 7:	//Reserved
				data[index] = (byte)0x0;
				break;

			case 8:
				data[index] = getBackBone().getId();
				break;
			case 9:
				data[index] = getBackBone().getDestinationRepeaterID();
				break;
			case 10:
				data[index] = getBackBone().getSendRepeaterID();
				break;
			case 11:
				data[index] = getBackBone().getSendTerminalID();
				break;
			case 12:case 13:
				data[index] = getBackBone().getFrameID()[index - 12];
				break;
			case 14:
				data[index] = getBackBone().getManagementInformation();
				break;
			case 15:case 16:case 17:
				data[index] = getRfHeader().getFlags()[index - 15];
				break;

			case 18:case 19:case 20:case 21:
			case 22:case 23:case 24:case 25:
				data[index] = (byte)getRfHeader().getRepeater2Callsign()[index - 18];
				break;
			case 26:case 27:case 28:case 29:
			case 30:case 31:case 32:case 33:
				data[index] = (byte)getRfHeader().getRepeater1Callsign()[index - 26];
				break;
			case 34:case 35:case 36:case 37:
			case 38:case 39:case 40:case 41:
				data[index] = (byte)getRfHeader().getYourCallsign()[index - 34];
				break;
			case 42:case 43:case 44:case 45:
			case 46:case 47:case 48:case 49:
				data[index] = (byte)getRfHeader().getMyCallsign()[index - 42];
				break;
			case 50:case 51:case 52:case 53:
				data[index] = (byte)getRfHeader().getMyCallsignAdd()[index - 50];
				break;

			default:
				break;
			}
		}

		//CRC計算
		final int crc = DSTARCRCCalculator.calcCRCRange(data, 15, 53);
		data[54] = (byte)(crc & 0xff);
		data[55] = (byte)((crc >> 8) & 0xff);

		return data;
	}

	@Override
	public G2Packet parseCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

}
