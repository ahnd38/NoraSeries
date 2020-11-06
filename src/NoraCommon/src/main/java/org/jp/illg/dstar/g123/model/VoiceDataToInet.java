/**
 *
 */
package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;

/**
 * @author AHND
 *
 */
public class VoiceDataToInet extends VoiceData
{

	public VoiceDataToInet(
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

	public VoiceDataToInet(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final BackBoneHeader backbone,
		final org.jp.illg.dstar.model.VoiceData voice
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, voice)
		);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		final byte[] data = new byte[27];
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
					(byte)(PacketType.Voice.getValue() | (~PacketType.getMask() & getDVPacket().getFlags()[index - 4]));
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

			case 15:case 16:case 17:case 18:case 19:case 20:case 21:case 22:case 23:
				data[index] = getVoiceData().getVoiceSegment()[index - 15];
				break;
			case 24:case 25:case 26:
				data[index] = getVoiceData().getDataSegment()[index - 24];
				break;

			default:
				break;
			}
		}

		return data;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public G2Packet parseCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}
}
