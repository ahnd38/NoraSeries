
package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;

public class VoiceDataFromInet extends VoiceData
{
	public VoiceDataFromInet(
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

	public VoiceDataFromInet(
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

	public VoiceDataFromInet(
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			new DVPacket(
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, 0x0),
				new VoiceAMBE()
			)
		);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		throw new UnsupportedOperationException();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public G2Packet parseCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.limit() >= 27 &&
			buffer.get() == 'D' &&
			buffer.get() == 'S' &&
			buffer.get() == 'V' &&
			buffer.get() == 'T' &&
			PacketType.hasPacketType(PacketType.Voice, buffer.get(4))
		){
			for(int index = 4;index < 27; index++) {
				byte data = buffer.get();
				switch(index){
				case 4:case 5:
					getDVPacket().getFlags()[index - 4] = (byte)data;
					break;
				case 6:case 7:	//Reserved
					break;

				case 8:
					getBackBone().setId((byte)data);
					break;
				case 9:
					getBackBone().setDestinationRepeaterID((byte)data);
					break;
				case 10:
					getBackBone().setSendRepeaterID((byte)data);
					break;
				case 11:
					getBackBone().setSendTerminalID((byte)data);
					break;
				case 12:case 13:
					getBackBone().getFrameID()[index - 12] = (byte)data;
					break;
				case 14:
					getBackBone().setManagementInformation((byte)data);
					break;

				case 15:case 16:case 17:case 18:
				case 19:case 20:case 21:case 22:case 23:
					getVoiceData().getVoiceSegment()[index - 15] = (byte)data;
					break;
				case 24:case 25:case 26:
					getVoiceData().getDataSegment()[index - 24] = (byte)data;
					break;

				default:
					break;
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
