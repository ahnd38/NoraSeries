package org.jp.illg.dstar.reflector.protocol.dplus.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

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
import org.jp.illg.dstar.reflector.model.ReflectorPacket;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class DPlusPacketImpl extends ReflectorPacket implements DPlusPacket{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DPlusPacketType dPlusPacketType;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DPlusPoll poll;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DPlusConnect connect;


	public DPlusPacketImpl(
		final DPlusPacketType dplusPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(DSTARProtocol.DCS, loopBlockID, connectionDirection, remoteAddress, localAddress, packet);

		setDPlusPacketType(dplusPacketType);
	}

	public DPlusPacketImpl(final DPlusPoll poll) {
		this(
			DPlusPacketType.POLL,
			null,
			ConnectionDirectionType.Unknown,
			(InetSocketAddress)null,
			(InetSocketAddress)null,
			null
		);

		setPoll(poll);
	}

	public DPlusPacketImpl(final DPlusConnect connect) {
		this(
			DPlusPacketType.CONNECT,
			null,
			ConnectionDirectionType.Unknown,
			(InetSocketAddress)null,
			(InetSocketAddress)null,
			null
		);

		setConnect(connect);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DPlusPacketImpl(
		final DPlusPacketType dplusPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dplusPacketType,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);
	}

	public DPlusPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			DPlusPacketType.HEADER,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public DPlusPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			DPlusPacketType.VOICE,
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

	public DPlusPacketImpl(
		final DPlusPacketType dplusPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dplusPacketType,
			loopBlockID,
			connectionDirection,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public DPlusPacketImpl(
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

	public DPlusPacketImpl(
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

	public DPlusPacketImpl(
		final DPlusPacketType dplusPacketType,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			dplusPacketType,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public DPlusPacketImpl(
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			backbone
		);
	}

	public DPlusPacketImpl(
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			voice,
			backbone
		);
	}


	@Override
	public DVPacket getDvPacket() {
		return this.getDVPacket();
	}

	@Override
	public BackBoneHeader getBackBone() {
		return super.getBackBone();
	}

	@Override
	public Header getRfHeader() {
		return super.getRfHeader();
	}

	@Override
	public VoiceData getVoiceData() {
		return super.getVoiceData();
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
	public DPlusPacketImpl clone() {
		DPlusPacketImpl copy = null;

		copy = (DPlusPacketImpl)super.clone();

		copy.dPlusPacketType = this.dPlusPacketType;

		if(this.poll != null) {copy.poll = this.poll.clone();}

		if(this.connect != null) {copy.connect = this.connect.clone();}

		return copy;
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
		sb.append(getDPlusPacketType());

		sb.append("\n");

		switch(getDPlusPacketType()) {
		case CONNECT:
			sb.append(getConnect().toString(indentLevel + 4));
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

	public static Optional<DPlusPacket> isValidConnectPacket(ByteBuffer buffer){
		if(buffer == null) {return Optional.empty();}

		if(buffer.remaining() != 5 && buffer.remaining() != 8 && buffer.remaining() != 28)
			return Optional.empty();

		final int bufferLength = buffer.remaining();
		byte[] buf = null;
		int bufferOffset = 0;
		if(buffer.hasArray()) {
			buf = buffer.array();
			bufferOffset = buffer.arrayOffset();
			buffer.position(buffer.position() + bufferLength);
		}else {
			buf = new byte[bufferLength];
			buffer.get(buf);
		}
		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		ReflectorConnectTypes type = null;
		String repeaterCallsign = DSTARDefines.EmptyLongCallsign;
		boolean readonly = false;

		switch(bufferLength) {
		case 5:
			if(buf[bufferOffset + 4] == (byte)0x01)
				type = ReflectorConnectTypes.LINK;
			else if(buf[bufferOffset + 4] == (byte)0x00)
				type = ReflectorConnectTypes.UNLINK;
			else
				return Optional.empty();

			break;

		case 8:
			final byte[] reply = Arrays.copyOfRange(buf, bufferOffset + 4, bufferOffset + 8);

			if(Arrays.equals(reply, "OKRW".getBytes()))
				type = ReflectorConnectTypes.ACK;
			else if(Arrays.equals(reply, "OKRO".getBytes())) {
				type = ReflectorConnectTypes.ACK;
				readonly = true;
			}
			else
				type = ReflectorConnectTypes.NAK;

			break;

		case 28:
			final StringBuffer sb = new StringBuffer();
			for(int i = 4; i < 28; i++) {
				if(buf[bufferOffset + i] == 0x0) {break;}
				sb.append((char)buf[bufferOffset + i]);
			}
			repeaterCallsign = sb.toString();

			type = ReflectorConnectTypes.LINK2;
			break;

		default:
			return Optional.empty();
		}

		final DPlusConnect connect = new DPlusConnect(type);
		connect.setCallsign(repeaterCallsign);
		connect.setReadonly(readonly);

		return Optional.of((DPlusPacket)new DPlusPacketImpl(connect));
	}

	public static Optional<byte[]> assembleConenctPacket(DPlusPacket packet) {
		if(packet == null || packet.getDPlusPacketType() != DPlusPacketType.CONNECT)
			return Optional.empty();

		final DPlusConnect connect = packet.getConnect();
		ReflectorConnectTypes type = connect.getType();
		int bufLength = 0;

		byte[] buf = null;

		switch(type) {
		case LINK:
			bufLength = 5;
			buf = new byte[bufLength];
			buf[0] = (byte) 0x05;
			buf[1] = (byte) 0x00;
			buf[2] = (byte) 0x18;
			buf[3] = (byte) 0x00;
			buf[4] = (byte) 0x01;
			break;

		case LINK2:
			bufLength = 28;
			buf = new byte[bufLength];
			buf[0]  = (byte) 0x1C;
			buf[1]  = (byte) 0xC0;
			buf[2]  = (byte) 0x04;
			buf[3]  = (byte) 0x00;

			for(int i = 4; i < 20; i++) {buf[i] = 0x00;}

			String callsign = connect.getCallsign();
			if(callsign != null)
				callsign = callsign.trim();
			else
				callsign = DSTARDefines.EmptyLongCallsign;

			for (int i = 0; i < callsign.length(); i++)
				buf[i + 4] = (byte) callsign.charAt(i);

			buf[20] = 'D';
			buf[21] = 'V';
			buf[22] = '0';
			buf[23] = '1';
			buf[24] = '9';
			buf[25] = '9';
			buf[26] = '9';
			buf[27] = '9';
			break;

		case UNLINK:
			bufLength = 5;
			buf = new byte[bufLength];
			buf[0] = (byte) 0x05;
			buf[1] = (byte) 0x00;
			buf[2] = (byte) 0x18;
			buf[3] = (byte) 0x00;
			buf[4] = (byte) 0x00;
			break;

		case ACK:
			bufLength = 8;
			buf = new byte[bufLength];
			buf[0] = (byte) 0x08;
			buf[1] = (byte) 0xC0;
			buf[2] = (byte) 0x04;
			buf[3] = (byte) 0x00;
			buf[4] = 'O';
			buf[5] = 'K';
			buf[6] = 'R';
			buf[7] = 'W';
			break;

		case NAK:
			bufLength = 8;
			buf = new byte[bufLength];
			buf[0] = (byte) 0x08;
			buf[1] = (byte) 0xC0;
			buf[2] = (byte) 0x04;
			buf[3] = (byte) 0x00;
			buf[4] = 'B';
			buf[5] = 'U';
			buf[6] = 'S';
			buf[7] = 'Y';
			break;

		default:
			return Optional.empty();
		}

		return Optional.of(buf);
	}

	public static Optional<DPlusPacket> isValidPollPacket(ByteBuffer buffer){
		if(buffer == null || buffer.remaining() <= 0) {return Optional.empty();}

		if(buffer.remaining() != 3) {return Optional.empty();}

		final int bufferLength = buffer.remaining();
		byte[] buf = null;
		if(buffer.hasArray()) {
			buf = buffer.array();
			buffer.position(buffer.position() + bufferLength);
		}else {
			buf = new byte[bufferLength];
			buffer.get(buf);
		}
		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		final DPlusPoll poll = new DPlusPoll();

		return Optional.of((DPlusPacket)new DPlusPacketImpl(poll));
	}

	public static Optional<byte[]> assemblePollPacket(DPlusPacket packet) {
		if(packet == null || packet.getDPlusPacketType() != DPlusPacketType.POLL)
			return Optional.empty();

		final byte[] buf = new byte[3];
		buf[0] = 0x03;
		buf[1] = 0x60;
		buf[2] = 0x00;

		return Optional.of(buf);
	}

	public static Optional<DPlusPacket> isValidHeaderPacket(ByteBuffer buffer){
		if(buffer == null || buffer.remaining() <= 0) {return Optional.empty();}

		final int packetSize = buffer.remaining();

		if(packetSize < 58) {return Optional.empty();}
		final byte[] head = new byte[2];
		head[0] = buffer.get();
		head[1] = buffer.get();
		buffer.rewind();

		if(head[0] != (byte)0x3A || head[1] != (byte)0x80)
			return Optional.empty();

		byte[] buf = null;
		int bufferOffset = 0;
		if(buffer.hasArray()) {
			buf = buffer.array();
			bufferOffset = buffer.arrayOffset();
			buffer.position(buffer.position() + packetSize);
		}else {
			buf = new byte[packetSize];
			buffer.get(buf);
		}
		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();


		final BackBoneHeader bb =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader);
		bb.setDestinationRepeaterID(buf[bufferOffset + 11]);
		bb.setSendRepeaterID(buf[bufferOffset + 12]);
		bb.setSendTerminalID(buf[bufferOffset + 13]);
		bb.getFrameID()[0] = buf[bufferOffset + 15];
		bb.getFrameID()[1] = buf[bufferOffset + 14];

		final Header header = new Header();
		header.getFlags()[0] = buf[bufferOffset + 17];
		header.getFlags()[1] = buf[bufferOffset + 18];
		header.getFlags()[2] = buf[bufferOffset + 19];

		ArrayUtil.copyOfRange(
			header.getRepeater2Callsign(), buf,
			bufferOffset + 20, bufferOffset + 20 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getRepeater1Callsign(), buf,
			bufferOffset + 28, bufferOffset + 28 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getYourCallsign(), buf,
			bufferOffset + 36, bufferOffset + 36 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getMyCallsign(), buf,
			bufferOffset + 44, bufferOffset + 44 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getMyCallsignAdd(), buf,
			bufferOffset + 52, bufferOffset + 52 + DSTARDefines.CallsignShortLength
		);

		header.saveRepeaterCallsign();

		final DPlusPacket packet = new DPlusPacketImpl(header, bb);

		return Optional.of(packet);
	}

	public static Optional<byte[]> assembleHeaderPacket(DPlusPacket packet) {
		if(packet == null || packet.getDPlusPacketType() != DPlusPacketType.HEADER)
			return Optional.empty();

		final byte[] buf = new byte[58];
		Arrays.fill(buf, (byte)0x00);

		buf[0]  = (byte) 0x3A;
		buf[1]  = (byte) 0x80;

		buf[2]  = 'D';
		buf[3]  = 'S';
		buf[4]  = 'V';
		buf[5]  = 'T';

		buf[6]  = (byte) 0x10;
		buf[7]  = (byte) 0x00;
		buf[8]  = (byte) 0x00;
		buf[9]  = (byte) 0x00;
		buf[10] = (byte) 0x20;

		buf[11] = packet.getBackBone().getDestinationRepeaterID();
		buf[12] = packet.getBackBone().getSendRepeaterID();
		buf[13] = packet.getBackBone().getSendTerminalID();

		buf[14] = packet.getBackBone().getFrameID()[0];
		buf[15] = packet.getBackBone().getFrameID()[1];

		buf[16] = (byte) 0x80;

		buf[17] = (byte) 0x00;
		buf[18] = (byte) 0x00;
		buf[19] = (byte) 0x00;

		ArrayUtil.copyOfRange(buf, 20, packet.getRfHeader().getRepeater2Callsign());
		ArrayUtil.copyOfRange(buf, 28, packet.getRfHeader().getRepeater1Callsign());
		ArrayUtil.copyOfRange(buf, 36, packet.getRfHeader().getYourCallsign());
		ArrayUtil.copyOfRange(buf, 44, packet.getRfHeader().getMyCallsign());
		ArrayUtil.copyOfRange(buf, 52, packet.getRfHeader().getMyCallsignAdd());

		int crc = DSTARCRCCalculator.calcCRCRange(buf, 17, 55);
		buf[56] = (byte)(crc & 0xff);
		buf[57] = (byte)((crc >> 8) & 0xff);

		return Optional.of(buf);
	}

	public static Optional<DPlusPacket> isValidVoicePacket(ByteBuffer buffer){
		if(buffer == null || buffer.remaining() <= 0) {return Optional.empty();}

		final int packetSize = buffer.remaining();

		if(packetSize < 29) {return Optional.empty();}
		final int[] head = new int[2];
		head[0] = (int)(buffer.get() & 0xFF);
		head[1] = (int)(buffer.get() & 0xFF);
		buffer.rewind();

		if((head[0] != 0x1D && head[0] != 0x20) || head[1] != 0x80)
			return Optional.empty();

		byte[] buf = null;
		int bufferOffset = 0;
		if(buffer.hasArray()) {
			buf = buffer.array();
			bufferOffset = buffer.arrayOffset();
			buffer.position(buffer.position() + packetSize);
		}else {
			buf = new byte[packetSize];
			buffer.get(buf);
		}
		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();


		final BackBoneHeader bb = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData
		);
		bb.setDestinationRepeaterID(buf[bufferOffset + 11]);
		bb.setSendRepeaterID(buf[bufferOffset + 12]);
		bb.setSendTerminalID(buf[bufferOffset + 13]);
		bb.getFrameID()[0] = buf[bufferOffset + 15];
		bb.getFrameID()[1] = buf[bufferOffset + 14];
		bb.setManagementInformation(buf[bufferOffset + 16]);

		final VoiceData voice = new VoiceAMBE();
		ArrayUtil.copyOfRange(
			voice.getVoiceSegment(), buf, bufferOffset + 17, bufferOffset + 17 + DSTARDefines.VoiceSegmentLength
		);
		ArrayUtil.copyOfRange(
			voice.getDataSegment(), buf, bufferOffset + 26, bufferOffset + 26 + DSTARDefines.DataSegmentLength
		);

		final DPlusPacket packet = new DPlusPacketImpl(voice, bb);

		return Optional.of(packet);
	}

	public static Optional<byte[]> assembleVoicePacket(DPlusPacket packet) {
		if(packet == null || packet.getDPlusPacketType() != DPlusPacketType.VOICE)
			return Optional.empty();

		final boolean isLastFrame = packet.getDvPacket().isEndVoicePacket();

		final byte[] buf = new byte[isLastFrame ? 32 : 29];

		Arrays.fill(buf, (byte)0x00);

		if(isLastFrame) {
			buf[0]  = (byte) 0x20;
			buf[1]  = (byte) 0x80;
		} else {
			buf[0]  = (byte) 0x1D;
			buf[1]  = (byte) 0x80;
		}

		buf[2]  = 'D';
		buf[3]  = 'S';
		buf[4]  = 'V';
		buf[5]  = 'T';

		buf[6]  = (byte) 0x20;
		buf[7]  = (byte) 0x00;
		buf[8]  = (byte) 0x00;
		buf[9]  = (byte) 0x00;
		buf[10] = (byte) 0x20;

		buf[11] = packet.getBackBone().getDestinationRepeaterID();
		buf[12] = packet.getBackBone().getSendRepeaterID();
		buf[13] = packet.getBackBone().getSendTerminalID();

		buf[14] = packet.getBackBone().getFrameID()[0];
		buf[15] = packet.getBackBone().getFrameID()[1];

		buf[16] = packet.getBackBone().getSequenceNumber();

		if(isLastFrame) {
			ArrayUtil.copyOfRange(buf, 17, DSTARDefines.VoiceSegmentLastBytesICOM);
			ArrayUtil.copyOfRange(buf, 26, DSTARDefines.LastPatternBytesICOM);
		}
		else {
			ArrayUtil.copyOfRange(buf, 17, packet.getVoiceData().getVoiceSegment());
			ArrayUtil.copyOfRange(buf, 26, packet.getVoiceData().getDataSegment());
		}

		return Optional.of(buf);
	}

}

