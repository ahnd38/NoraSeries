/**
 *
 */
package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;

/**
 * @author AHND
 *
 */
public class VoiceHeaderFromInet extends VoiceHeader
{
	public VoiceHeaderFromInet(
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

	public VoiceHeaderFromInet(
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

	public VoiceHeaderFromInet(
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			new DVPacket(
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, 0x0),
				new Header()
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
			buffer.limit() >= 56 &&
			buffer.get() == 'D' &&
			buffer.get() == 'S' &&
			buffer.get() == 'V' &&
			buffer.get() == 'T' &&
			PacketType.hasPacketType(PacketType.Header, buffer.get(4))
		){
			for(int index = 4;index < 56; index++) {
				final byte data = buffer.get();
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
				case 15:case 16:case 17:
					getRfHeader().getFlags()[index - 15] = (byte)data;
					break;

				case 18:case 19:case 20:case 21:
				case 22:case 23:case 24:case 25:
					getRfHeader().getRepeater2Callsign()[index - 18] = (char)data;
					break;
				case 26:case 27:case 28:case 29:
				case 30:case 31:case 32:case 33:
					getRfHeader().getRepeater1Callsign()[index - 26] = (char)data;
					break;
				case 34:case 35:case 36:case 37:
				case 38:case 39:case 40:case 41:
					getRfHeader().getYourCallsign()[index - 34] = (char)data;
					break;
				case 42:case 43:case 44:case 45:
				case 46:case 47:case 48:case 49:
					getRfHeader().getMyCallsign()[index - 42] = (char)data;
					break;
				case 50:case 51:case 52:case 53:
					getRfHeader().getMyCallsignAdd()[index - 50] = (char)data;
					break;
				case 54:case 55:
					getRfHeader().getCrc()[index - 54] = (byte)data;
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
