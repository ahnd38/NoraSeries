package org.jp.illg.dstar.reflector.protocol.dextra.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.reflector.model.ReflectorPacket;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.util.FormatUtil;

import com.annimon.stream.Optional;

public class DExtraPacketImpl extends ReflectorPacket implements DExtraPacket{

	private static final Pattern xrfPattern = Pattern.compile("^(([X][R][F])[0-9]{3}[ ][A-Z])$");

	public static enum DExtraPacketType{
		Unknown,
		HEADER,
		VOICE,
		POLL,
		CONNECT,
		;
	}

	private DExtraPacketType dextraPacketType;

	private DExtraPoll poll;

	private DExtraConnectInfo connectInfo;


	public DExtraPacketImpl(
		final DExtraPacketType dextraPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(
			DSTARProtocol.DExtra,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			packet
		);

		setDExtraPacketType(dextraPacketType);
	}

	public DExtraPacketImpl(
		final DExtraPoll poll
	) {
		this(
			DExtraPacketType.POLL,
			null,
			ConnectionDirectionType.Unknown,
			(InetSocketAddress)null,
			(InetSocketAddress)null,
			null
		);

		setPoll(poll);
	}

	public DExtraPacketImpl(
		final DExtraConnectInfo dextraConnectInfo
	) {
		this(
			DExtraPacketType.CONNECT,
			null,
			ConnectionDirectionType.Unknown,
			(InetSocketAddress)null,
			(InetSocketAddress)null,
			null
		);

		setConnectInfo(dextraConnectInfo);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DExtraPacketImpl(
		final DExtraPacketType dextraPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dextraPacketType,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);

		if(dextraPacketType != DExtraPacketType.HEADER && dextraPacketType != DExtraPacketType.VOICE)
			throw new IllegalArgumentException();
	}

	public DExtraPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			DExtraPacketType.HEADER,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public DExtraPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			DExtraPacketType.VOICE,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, voice)
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DExtraPacketImpl(
		final DExtraPacketType dextraPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dextraPacketType,
			loopBlockID,
			connectionDirection,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public DExtraPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			null,
			null,
			header,
			backbone
		);
	}

	public DExtraPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			null,
			null,
			voice,
			backbone
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DExtraPacketImpl(
		final DExtraPacketType dextraExtraPacketType,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dextraExtraPacketType,
			null,
			ConnectionDirectionType.Unknown,
			header,
			voice,
			backbone
		);
	}

	public DExtraPacketImpl(
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			header,
			backbone
		);
	}

	public DExtraPacketImpl(
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			voice,
			backbone
		);
	}

	@Override
	public DExtraPacketImpl clone() {
		DExtraPacketImpl copy = null;

		try {
			copy = (DExtraPacketImpl)super.clone();

			copy.dextraPacketType = this.dextraPacketType;

			if(this.poll != null) {copy.poll = this.poll.clone();}

			if(this.connectInfo != null) {copy.connectInfo = this.connectInfo.clone();}

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}


	public DExtraPacketType getDExtraPacketType() {
		return dextraPacketType;
	}

	private void setDExtraPacketType(DExtraPacketType dextraPacketType) {
		this.dextraPacketType = dextraPacketType;
	}

	public DExtraPoll getPoll() {
		return poll;
	}

	public void setPoll(DExtraPoll poll) {
		this.poll = poll;
	}

	public DExtraConnectInfo getConnectInfo() {
		return connectInfo;
	}

	public void setConnectInfo(DExtraConnectInfo connectInfo) {
		this.connectInfo = connectInfo;
	}

	@Override
	public DVPacket getDvPacket() {
		return super.getDVPacket();
	}

	@Override
	public char[] getRepeater2Callsign() {
		return super.getRfHeader().getRepeater2Callsign();
	}

	@Override
	public char[] getRepeater1Callsign() {
		return super.getRfHeader().getRepeater1Callsign();
	}

	@Override
	public char[] getYourCallsign() {
		return super.getRfHeader().getYourCallsign();
	}

	@Override
	public char[] getMyCallsign() {
		return super.getRfHeader().getMyCallsign();
	}

	@Override
	public char[] getMyCallsignAdd() {
		return super.getRfHeader().getMyCallsignAdd();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indentLevel);

		sb.append("[PacketType]:");
		sb.append(getDExtraPacketType());

		sb.append("\n");

		switch(getDExtraPacketType()) {
		case CONNECT:
			sb.append(getConnectInfo().toString(indentLevel + 4));
			break;
		case POLL:
			sb.append(getPoll().toString(indentLevel + 4));
			break;
		case HEADER:
			sb.append(getRfHeader().toString(indentLevel + 4));
			sb.append("\n");
			sb.append(getBackBone().toString(indentLevel + 4));
			break;
		case VOICE:
			sb.append(getVoiceData().toString(indentLevel + 4));
			sb.append("\n");
			sb.append(getBackBone().toString(indentLevel + 4));
			break;
		default:
			break;
		}

		return sb.toString();
	}

	public static Optional<DExtraPacket> isValidConnectInfoPacket(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.limit() != 11 && buffer.limit() != 14) {return Optional.empty();}

		final int bufferLength = buffer.limit();
		byte[] data = new byte[bufferLength];
		buffer.get(data);

		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		final DExtraConnectInfo info = new DExtraConnectInfo();
		if(data[9] == ' ')
			info.setType(ReflectorConnectTypes.UNLINK);
		else
			info.setType(ReflectorConnectTypes.LINK);

		info.setCallsign(
			String.valueOf(
				new char[] {(char)data[0], (char)data[1], (char)data[2], (char)data[3], (char)data[4], (char)data[5], (char)data[6], (char)data[8]}
			)
		);
		info.setCallsignModule((char)data[8]);
		info.setReflectorModule((char)data[9]);
		Matcher xrf = xrfPattern.matcher(info.getCallsign());
		if(data[10] == 11)
			info.setRevision(1);
		else if(xrf.matches())
			info.setRevision(2);
		else
			info.setRevision(0);

		if(bufferLength >= 14) {
			if(
				data[DSTARDefines.CallsignFullLength + 2] == 'A' &&
				data[DSTARDefines.CallsignFullLength + 3] == 'C' &&
				data[DSTARDefines.CallsignFullLength + 4] == 'K'
			) {
				info.setType(ReflectorConnectTypes.ACK);
			}
			else if(
				data[DSTARDefines.CallsignFullLength + 2] == 'N' &&
				data[DSTARDefines.CallsignFullLength + 3] == 'A' &&
				data[DSTARDefines.CallsignFullLength + 4] == 'K'
			) {
				info.setType(ReflectorConnectTypes.NAK);
			}
		}

		return Optional.of((DExtraPacket)new DExtraPacketImpl(info));
	}

	public static Optional<byte[]> assembleConnectInfoPacket(DExtraPacket packet){
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.CONNECT)
			return Optional.empty();

		int bufferSize = 0;
		switch(packet.getConnectInfo().getType()) {
		case LINK:
		case UNLINK:
			bufferSize = 11;
			break;

		case ACK:
		case NAK:
			bufferSize = 14;
			break;

		default:
			return Optional.empty();
		}

		final byte[] buffer = new byte[bufferSize];

		Arrays.fill(buffer, 0, DSTARDefines.CallsignFullLength, (byte)' ');
		for(int i = 0; i < (DSTARDefines.CallsignFullLength - 1); i++)
			buffer[i] =(byte)packet.getConnectInfo().getCallsign().charAt(i);

		buffer[DSTARDefines.CallsignFullLength] = (byte)packet.getConnectInfo().getCallsignModule();

		switch(packet.getConnectInfo().getType()) {
		case LINK:{
			buffer[DSTARDefines.CallsignFullLength + 1] = (byte)packet.getConnectInfo().getReflectorModule();
			buffer[DSTARDefines.CallsignFullLength + 2] = 0x00;
			break;
		}
		case UNLINK:{
			buffer[DSTARDefines.CallsignFullLength + 1] = ' ';
			buffer[DSTARDefines.CallsignFullLength + 2] = 0x00;
			break;
		}
		case ACK:{
			buffer[DSTARDefines.CallsignFullLength + 1] = (byte)packet.getConnectInfo().getReflectorModule();
			buffer[DSTARDefines.CallsignFullLength + 2] = 'A';
			buffer[DSTARDefines.CallsignFullLength + 3] = 'C';
			buffer[DSTARDefines.CallsignFullLength + 4] = 'K';
			buffer[DSTARDefines.CallsignFullLength + 5] = 0x00;
			break;
		}
		case NAK:{
			buffer[DSTARDefines.CallsignFullLength + 1] = (byte)packet.getConnectInfo().getReflectorModule();
			buffer[DSTARDefines.CallsignFullLength + 2] = 'N';
			buffer[DSTARDefines.CallsignFullLength + 3] = 'A';
			buffer[DSTARDefines.CallsignFullLength + 4] = 'K';
			buffer[DSTARDefines.CallsignFullLength + 5] = 0x00;
			break;
		}
		default:
			return Optional.empty();
		}

		return Optional.of(buffer);
	}

	public static Optional<DExtraPacket> isValidPollPacket(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.limit() != 9) {return Optional.empty();}

		final byte[] data = new byte[9];
		buffer.get(data);

		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		final DExtraPoll poll = new DExtraPoll();
		poll.setCallsign(
			String.valueOf(
				new char[] {(char)data[0], (char)data[1], (char)data[2], (char)data[3], (char)data[4], (char)data[5], (char)data[6], (char)data[7]}
			)
		);
		poll.setDongle(data[8] != 0x0);

		return Optional.of((DExtraPacket)new DExtraPacketImpl(poll));
	}

	public static Optional<byte[]> assemblePollPacket(DExtraPacket packet){
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.POLL) {return Optional.empty();}

		final byte[] buffer = new byte[9];
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			buffer[i] = (byte)packet.getPoll().getCallsign().charAt(i);

		buffer[8] = 0x00;

		return Optional.of(buffer);
	}

	public static Optional<DExtraPacket> isValidHeaderPacket(ByteBuffer buffer){
		assert buffer != null && buffer.position() == 0;

		if(buffer.limit() < 56) {return Optional.empty();}

		byte[] data = new byte[56];
		buffer.get(data);

		if(
			data[0] == 'D' && data[1] == 'S' && data[2] == 'V' && data[3] == 'T' &&
			PacketType.hasPacketType(PacketType.Header, data[4]) && data[8] == 0x20
		) {
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();
		}
		else {
			buffer.rewind();
			return Optional.empty();
		}

		final DExtraPacket packet = new DExtraPacketImpl(
			new Header(),
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader)
		);
		for(int index = 4 ;index < data.length; index++) {
			final byte d = data[index];

			switch(index){
			case 4:case 5:
				packet.getDVPacket().getFlags()[index - 4] = (byte)d;
				break;
			case 6:case 7:	//Reserved
				break;

			case 8:
				packet.getBackBone().setId((byte)d);
				break;
			case 9:
				packet.getBackBone().setDestinationRepeaterID((byte)d);
				break;
			case 10:
				packet.getBackBone().setSendRepeaterID((byte)d);
				break;
			case 11:
				packet.getBackBone().setSendTerminalID((byte)d);
				break;
			case 12:case 13:
				packet.getBackBone().getFrameID()[index - 12] = (byte)d;
				break;
			case 14:
				packet.getBackBone().setManagementInformation((byte)d);
				break;

			case 15:case 16:case 17:
				packet.getRfHeader().getFlags()[index - 15] = (byte)d;
				break;
			case 18:case 19:case 20:case 21:
			case 22:case 23:case 24:case 25:
				packet.getRfHeader().getRepeater2Callsign()[index - 18] = (char)d;
				break;
			case 26:case 27:case 28:case 29:
			case 30:case 31:case 32:case 33:
				packet.getRfHeader().getRepeater1Callsign()[index - 26] = (char)d;
				break;
			case 34:case 35:case 36:case 37:
			case 38:case 39:case 40:case 41:
				packet.getRfHeader().getYourCallsign()[index - 34] = (char)d;
				break;
			case 42:case 43:case 44:case 45:
			case 46:case 47:case 48:case 49:
				packet.getRfHeader().getMyCallsign()[index - 42] = (char)d;
				break;
			case 50:case 51:case 52:case 53:
				packet.getRfHeader().getMyCallsignAdd()[index - 50] = (char)d;
				break;
			case 54:case 55:
				packet.getRfHeader().getCrc()[index - 54] = (byte)d;
			}
		}

		packet.getRfHeader().saveRepeaterCallsign();

		return Optional.of(packet);
	}

	public static Optional<byte[]> assembleHeaderPacket(DExtraPacket packet){
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.HEADER) {return Optional.empty();}

		byte[] buffer = new byte[56];
		Arrays.fill(buffer, (byte)0x00);
		for(int index = 0;index < buffer.length;index++) {
			switch(index) {
			case 0:
				buffer[index] = 'D';
				break;
			case 1:
				buffer[index] = 'S';
				break;
			case 2:
				buffer[index] = 'V';
				break;
			case 3:
				buffer[index] = 'T';
				break;

			case 4:
				buffer[index] = PacketType.Header.getValue();
				break;
			case 5:
				buffer[index] = packet.getDVPacket().getFlags()[index - 4];
				break;
			case 6:case 7:	//Reserved
				buffer[index] = (byte)0x0;
				break;

			case 8:
				buffer[index] = packet.getBackBone().getId();break;
			case 9:
				buffer[index] = packet.getBackBone().getDestinationRepeaterID();break;
			case 10:
				buffer[index] = packet.getBackBone().getSendRepeaterID();break;
			case 11:
				buffer[index] = packet.getBackBone().getSendTerminalID();break;
			case 12:case 13:
				buffer[index] = packet.getBackBone().getFrameID()[index - 12];break;
			case 14:
				buffer[index] = packet.getBackBone().getManagementInformation();break;

			case 15:case 16:case 17:
				buffer[index] = packet.getRfHeader().getFlags()[index - 15];break;
			case 18:case 19:case 20:case 21:
			case 22:case 23:case 24:case 25:
				buffer[index] = (byte)packet.getRfHeader().getRepeater2Callsign()[index - 18];break;
			case 26:case 27:case 28:case 29:
			case 30:case 31:case 32:case 33:
				buffer[index] = (byte)packet.getRfHeader().getRepeater1Callsign()[index - 26];break;
			case 34:case 35:case 36:case 37:
			case 38:case 39:case 40:case 41:
				buffer[index] = (byte)packet.getRfHeader().getYourCallsign()[index - 34];break;
			case 42:case 43:case 44:case 45:
			case 46:case 47:case 48:case 49:
				buffer[index] = (byte)packet.getRfHeader().getMyCallsign()[index - 42];break;
			case 50:case 51:case 52:case 53:
				buffer[index] = (byte)packet.getRfHeader().getMyCallsignAdd()[index - 50];break;

			default:
				break;
			}
		}

		final int crc = DSTARCRCCalculator.calcCRCRange(buffer,15, 53);
		buffer[54] = (byte)(crc & 0xff);
		buffer[55] = (byte)((crc >> 8) & 0xff);

		return Optional.of(buffer);
	}

	public static Optional<DExtraPacket> isValidVoicePacket(ByteBuffer buffer){
		assert buffer != null;

		if(buffer.limit() < 27) {return Optional.empty();}

		byte[] data = new byte[27];
		buffer.get(data);

		if(
			data[0] == 'D' && data[1] == 'S' && data[2] == 'V' && data[3] == 'T' &&
			PacketType.hasPacketType(PacketType.Voice, data[4]) && data[8] == 0x20
		) {
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();
		}
		else {
			buffer.rewind();
			return Optional.empty();
		}

		final DExtraPacket packet = new DExtraPacketImpl(
			new VoiceAMBE(),
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData)
		);
		for(int index = 4 ;index < data.length; index++) {
			final byte d = data[index];

			switch(index){
			case 4:case 5:
				packet.getDVPacket().getFlags()[index - 4] = (byte)d;
				break;
			case 6:case 7:	//Reserved
				break;

			case 8:
				packet.getBackBone().setId((byte)d);
				break;
			case 9:
				packet.getBackBone().setDestinationRepeaterID((byte)d);
				break;
			case 10:
				packet.getBackBone().setSendRepeaterID((byte)d);
				break;
			case 11:
				packet.getBackBone().setSendTerminalID((byte)d);
				break;
			case 12:case 13:
				packet.getBackBone().getFrameID()[index - 12] = (byte)d;
				break;
			case 14:
				packet.getBackBone().setManagementInformation((byte)d);
				break;

			case 15:case 16:case 17:case 18:
			case 19:case 20:case 21:case 22:case 23:
				packet.getVoiceData().getVoiceSegment()[index - 15] = (byte)d;
				break;
			case 24:case 25:case 26:
				packet.getVoiceData().getDataSegment()[index - 24] = (byte)d;
				break;

			default:
				break;
			}
		}


		return Optional.of(packet);
	}

	public static Optional<byte[]> assembleVoicePacket(DExtraPacket packet){
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.VOICE) {return Optional.empty();}

		final byte[] buffer = new byte[27];
		Arrays.fill(buffer, (byte)0x00);
		for(int index = 0;index < buffer.length;index++) {
			switch(index) {
			case 0:
				buffer[index] = 'D';
				break;
			case 1:
				buffer[index] = 'S';
				break;
			case 2:
				buffer[index] = 'V';
				break;
			case 3:
				buffer[index] = 'T';
				break;

			case 4:
				buffer[index] = PacketType.Voice.getValue();
				break;
			case 5:
				buffer[index] = packet.getDVPacket().getFlags()[index - 4];
				break;
			case 6:case 7:	//Reserved
				buffer[index] = (byte)0x0;
				break;

			case 8:
				buffer[index] = packet.getBackBone().getId();
				break;
			case 9:
				buffer[index] = packet.getBackBone().getDestinationRepeaterID();
				break;
			case 10:
				buffer[index] = packet.getBackBone().getSendRepeaterID();
				break;
			case 11:
				buffer[index] = packet.getBackBone().getSendTerminalID();
				break;
			case 12:case 13:
				buffer[index] = packet.getBackBone().getFrameID()[index - 12];
				break;
			case 14:
				buffer[index] = packet.getBackBone().getManagementInformation();
				break;

			case 15:case 16:case 17:case 18:case 19:case 20:case 21:case 22:case 23:
				buffer[index] = packet.getVoiceData().getVoiceSegment()[index - 15];
				break;
			case 24:case 25:case 26:
				buffer[index] = packet.getVoiceData().getDataSegment()[index - 24];
				break;

			default:
				break;
			}
		}

		return Optional.of(buffer);
	}
}
