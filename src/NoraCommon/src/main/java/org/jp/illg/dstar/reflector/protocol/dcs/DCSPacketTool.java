package org.jp.illg.dstar.reflector.protocol.dcs;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSConnect;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSHeaderVoice;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacket;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacketImpl;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacketType;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPoll;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

public class DCSPacketTool {

	private DCSPacketTool() {}

	public static Optional<DCSPacket> isValidConnectPacket(final ByteBuffer buffer){
		if(buffer == null) {return Optional.empty();}

		if(buffer.remaining() != 519 && buffer.remaining() != 19 && buffer.remaining() != 14)
			return Optional.empty();

		DCSConnect connect = new DCSConnect();

		int bufferLength = buffer.remaining();
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


		char[] repeaterCallsign = new char[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			repeaterCallsign[i] = (char)buf[i + bufferOffset];

		char repeaterModule = (char)buf[bufferOffset + DSTARDefines.CallsignFullLength];

		repeaterCallsign[DSTARDefines.CallsignFullLength - 1] = repeaterModule;
		connect.setRepeaterCallsign(String.valueOf(repeaterCallsign));

		ReflectorConnectTypes type = null;
		switch(bufferLength) {
		case 519:
			type = ReflectorConnectTypes.LINK;
			break;
		case 19:
			type = ReflectorConnectTypes.UNLINK;
			break;
		case 14:
			type = ReflectorConnectTypes.ACK;
			break;
		default:
			return Optional.empty();
		}

		if(type == ReflectorConnectTypes.LINK || type == ReflectorConnectTypes.UNLINK) {
			char[] reflectorCallsign = new char[DSTARDefines.CallsignFullLength];
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
				reflectorCallsign[i] = (char)buf[bufferOffset + DSTARDefines.CallsignFullLength + 3 + i];

			char reflectorModule = (char)buf[bufferOffset + DSTARDefines.CallsignFullLength + 1];
			reflectorCallsign[DSTARDefines.CallsignFullLength - 1] = reflectorModule;

			connect.setReflectorCallsign(String.valueOf(reflectorCallsign));

			connect.setType(type);
		}
		else if(type == ReflectorConnectTypes.ACK || type == ReflectorConnectTypes.NAK) {
			if(
				buf[bufferOffset + DSTARDefines.CallsignFullLength + 2] == 'A' &&
				buf[bufferOffset + DSTARDefines.CallsignFullLength + 3] == 'C' &&
				buf[bufferOffset + DSTARDefines.CallsignFullLength + 4] == 'K'
			) {
				type = ReflectorConnectTypes.ACK;
			}
			else if(
					buf[bufferOffset + DSTARDefines.CallsignFullLength + 2] == 'N' &&
					buf[bufferOffset + DSTARDefines.CallsignFullLength + 3] == 'A' &&
					buf[bufferOffset + DSTARDefines.CallsignFullLength + 4] == 'K'
			) {
				type = ReflectorConnectTypes.NAK;
			}
			else {return Optional.empty();}

			connect.setType(type);
		}else {return Optional.empty();}


		return Optional.of((DCSPacket)new DCSPacketImpl(connect));
	}

	public static Optional<byte[]> assembleConnectPacket(final DCSPacket packet) {
		if(packet == null || packet.getDCSPacketType() != DCSPacketType.CONNECT)
			return Optional.empty();

		DCSConnect connect = packet.getConnect();
		ReflectorConnectTypes type = connect.getType();
		int bufLength = 0;

		switch(type) {
		case LINK:
			bufLength = 519;
			break;
		case UNLINK:
			bufLength = 19;
			break;
		case ACK:
		case NAK:
			bufLength = 14;
			break;
		default:
			return Optional.empty();
		}

		byte[] buf = new byte[bufLength];
		Arrays.fill(buf, (byte)' ');

		char[] repeaterCallsign =
				DSTARUtils.formatFullLengthCallsign(connect.getRepeaterCallsign()).toCharArray();
		char[] reflectorCallsign =
				DSTARUtils.formatFullLengthCallsign(connect.getReflectorCallsign()).toCharArray();


		for(int i = 0; i < (DSTARDefines.CallsignFullLength - 1); i++)
			buf[i] = (byte)repeaterCallsign[i];

		buf[DSTARDefines.CallsignFullLength] =
			(byte)connect.getRepeaterCallsign().charAt(DSTARDefines.CallsignFullLength - 1);

		switch(type) {
		case LINK:{
			buf[DSTARDefines.CallsignFullLength + 1] = (byte)reflectorCallsign[DSTARDefines.CallsignFullLength - 1];
			buf[DSTARDefines.CallsignFullLength + 2] = (byte)0x00;
			for(int i = 0; i < (DSTARDefines.CallsignFullLength - 1); i++)
				buf[i + 11] = (byte)reflectorCallsign[i];

			char[] html =
				(
					"<table border=\"0\" width=\"95%%\">" +
						"<tbody>" +
							"<tr>" +
								"<td width=\"4%%\"><!--<img border=\"0\" src=" + "" + ">--></td>" +
								"<td width=\"96%%\">" +
									"<font size=\"2\">" +
										"<b>" + connect.getApplicationName() + "</b>" +
										" version" + connect.getApplicationVersion() +
									"</font>" +
								"</td>" +
							"</tr>" +
						"</tbody>" +
					"</table>"
				).toCharArray();

			int offset = 19;
			for(int i = 0; i < html.length && (offset + i) < buf.length; i++)
				buf[i + offset] = (byte)html[i];

			break;
		}
		case UNLINK:{
			buf[DSTARDefines.CallsignFullLength + 1] = (byte)0x20;
			buf[DSTARDefines.CallsignFullLength + 2] = (byte)0x00;
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
				buf[i + 11] = (byte)reflectorCallsign[i];
			break;
		}
		case ACK:{
			buf[DSTARDefines.CallsignFullLength + 1] = (byte)reflectorCallsign[DSTARDefines.CallsignFullLength - 1];
			buf[DSTARDefines.CallsignFullLength + 2] = 'A';
			buf[DSTARDefines.CallsignFullLength + 3] = 'C';
			buf[DSTARDefines.CallsignFullLength + 4] = 'K';
			buf[DSTARDefines.CallsignFullLength + 5] = (byte)0x00;
			break;
		}
		case NAK:{
			buf[DSTARDefines.CallsignFullLength + 1] = (byte)0x20;
			buf[DSTARDefines.CallsignFullLength + 2] = 'N';
			buf[DSTARDefines.CallsignFullLength + 3] = 'A';
			buf[DSTARDefines.CallsignFullLength + 4] = 'K';
			buf[DSTARDefines.CallsignFullLength + 5] = (byte)0x00;
			break;
		}
		default:
			return Optional.empty();
		}

		return Optional.of(buf);
	}

	public static Optional<DCSPacket> isValidPollPacket(final ByteBuffer buffer){
		if(buffer == null || buffer.remaining() <= 0) {return Optional.empty();}

		int packetSize = buffer.remaining();

		ConnectionDirectionType direction = ConnectionDirectionType.Unknown;
		switch(packetSize) {
		case 17:
			direction = ConnectionDirectionType.INCOMING;
			break;
		case 22:
			direction = ConnectionDirectionType.OUTGOING;
			break;
		default:
			return Optional.empty();
		}

		byte[] data = null;
		int bufferOffset = 0;
		if(buffer.hasArray()) {
			data = buffer.array();
			bufferOffset = buffer.arrayOffset();
			buffer.position(buffer.position() + packetSize);
		}else {
			data = new byte[packetSize];
			buffer.get(data);
		}
		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		char[] reflectorCallsign = new char[DSTARDefines.CallsignFullLength];
		ArrayUtil.copyOfRange(reflectorCallsign, data, bufferOffset + 0, DSTARDefines.CallsignFullLength);

		char[] repeaterCallsign = new char[DSTARDefines.CallsignFullLength];

		if(direction == ConnectionDirectionType.INCOMING) {
			ArrayUtil.copyOfRange(repeaterCallsign, data, bufferOffset + 9, bufferOffset + 9 + DSTARDefines.CallsignFullLength);
		}
		else if(direction == ConnectionDirectionType.OUTGOING) {
			ArrayUtil.copyOfRange(repeaterCallsign, data, bufferOffset + 9, bufferOffset + 9 + DSTARDefines.CallsignFullLength - 1);
			repeaterCallsign[DSTARDefines.CallsignFullLength - 1] = (char)data[bufferOffset + 17];
		}

		DCSPoll poll = new DCSPoll();
		poll.setReflectorCallsign(String.valueOf(reflectorCallsign));
		poll.setRepeaterCallsign(String.valueOf(repeaterCallsign));
		poll.setDirection(direction);

		return Optional.of((DCSPacket)new DCSPacketImpl(poll));
	}

	public static Optional<byte[]> assemblePollPacket(final DCSPacket packet) {
		if(packet == null || packet.getDCSPacketType() != DCSPacketType.POLL)
			return Optional.empty();

		final DCSPoll poll = packet.getPoll();

		byte[] buf = null;

		if(poll.getDirection() == ConnectionDirectionType.OUTGOING) {
			buf = new byte[17];
			Arrays.fill(buf, (byte)' ');

			ArrayUtil.copyOfRange(buf, 0, poll.getReflectorCallsign().toCharArray());
			buf[8] = (byte)0x00;
			ArrayUtil.copyOfRange(buf, 9, poll.getRepeaterCallsign().toCharArray());
		}
		else if(poll.getDirection() == ConnectionDirectionType.INCOMING) {
			buf = new byte[22];
			Arrays.fill(buf, (byte)' ');

			ArrayUtil.copyOfRange(buf, 0, poll.getReflectorCallsign().toCharArray());
			buf[8] = (byte)0x00;
			ArrayUtil.copyOfRange(buf, 9, poll.getRepeaterCallsign().toCharArray());
			buf[16] = (byte)' ';
			if(poll.getRepeaterCallsign().length() >= DSTARDefines.CallsignFullLength)
				buf[17] = (byte)poll.getRepeaterCallsign().charAt(DSTARDefines.CallsignFullLength - 1);

			buf[18] = (byte)0x0A;
			buf[19] = (byte)0x00;
		}
		else {return Optional.empty();}

		return Optional.of(buf);
	}

	public static Optional<DCSPacket> isValidHeaderVoicePacket(final ByteBuffer buffer){
		if(buffer == null || buffer.remaining() <= 0) {return Optional.empty();}

		int packetSize = buffer.remaining();

		if(packetSize != 100) {return Optional.empty();}

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

		if(
			buf[bufferOffset + 0] != '0' ||
			buf[bufferOffset + 1] != '0' ||
			buf[bufferOffset + 2] != '0' ||
			buf[bufferOffset + 3] != '1'
		) {
			return Optional.empty();
		}

		final Header header = new Header();

		header.getFlags()[0] = buf[bufferOffset + 4];
		header.getFlags()[1] = buf[bufferOffset + 5];
		header.getFlags()[2] = buf[bufferOffset + 6];

		ArrayUtil.copyOfRange(
			header.getRepeater2Callsign(), buf,
			bufferOffset + 7, bufferOffset + 7 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getRepeater1Callsign(), buf,
			bufferOffset + 15, bufferOffset + 15 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getYourCallsign(), buf,
			bufferOffset + 23, bufferOffset+ 23 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getMyCallsign(), buf,
			bufferOffset + 31, bufferOffset + 31 + DSTARDefines.CallsignFullLength
		);
		ArrayUtil.copyOfRange(
			header.getMyCallsignAdd(), buf,
			bufferOffset + 39, bufferOffset + 39 + DSTARDefines.CallsignShortLength
		);

		header.saveRepeaterCallsign();

		final BackBoneHeader bb = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData
		);
		bb.getFrameID()[0] = buf[bufferOffset + 44];
		bb.getFrameID()[1] = buf[bufferOffset + 43];
		bb.setManagementInformation(buf[bufferOffset + 45]);

		final VoiceAMBE voice = new VoiceAMBE();
		ArrayUtil.copyOfRange(
			voice.getVoiceSegment(), buf,
			bufferOffset + 46, bufferOffset + 46 + DSTARDefines.VoiceSegmentLength
		);
		ArrayUtil.copyOfRange(
			voice.getDataSegment(), buf,
			bufferOffset + 55,  bufferOffset + 55 + DSTARDefines.DataSegmentLength
		);

		final int seq =
			((buf[bufferOffset + 60] << 16) & 0xFF0000) | ((buf[bufferOffset + 59] << 8) & 0xFF00) | (buf[bufferOffset + 58] & 0xFF);

		final DCSPacket packet = new DCSHeaderVoice(header, voice, bb);
		packet.setLongSequence(seq);

		return Optional.of(packet);
	}

	public static Optional<byte[]> assembleHeaderVoicePacket(final DCSPacket packet) {
		if(packet == null || packet.getDCSPacketType() != DCSPacketType.HEADERVOICE)
			return Optional.empty();

		final byte[] buf = new byte[100];
		Arrays.fill(buf, (byte)0x00);

		buf[0]  = '0';
		buf[1]  = '0';
		buf[2]  = '0';
		buf[3]  = '1';

		final Header header = packet.getRfHeader();
		buf[4] = header.getFlags()[0];
		buf[5] = header.getFlags()[1];
		buf[6] = header.getFlags()[2];

		ArrayUtil.copyOfRange(buf, 7, header.getRepeater2Callsign());
		ArrayUtil.copyOfRange(buf, 15, header.getRepeater1Callsign());
		ArrayUtil.copyOfRange(buf, 23, header.getYourCallsign());
		ArrayUtil.copyOfRange(buf, 31, header.getMyCallsign());
		ArrayUtil.copyOfRange(buf, 39, header.getMyCallsignAdd());

		final BackBoneHeader bb = packet.getBackBone();
		buf[43] = bb.getFrameID()[1];
		buf[44] = bb.getFrameID()[0];
		buf[45] = bb.getManagementInformation();

		final VoiceData voice = packet.getVoiceData();
		ArrayUtil.copyOfRange(buf, 46, voice.getVoiceSegment());
		ArrayUtil.copyOfRange(buf, 55, voice.getDataSegment());

		if(bb.isEndSequence()) {
			buf[55] = (byte)0x55;
			buf[56] = (byte)0x55;
			buf[57] = (byte)0x55;
		}

		buf[58] = (byte)((packet.getLongSequence() >> 0)  & 0xFF);
		buf[59] = (byte)((packet.getLongSequence() >> 8)  & 0xFF);
		buf[60] = (byte)((packet.getLongSequence() >> 16) & 0xFF);

		buf[61] = (byte)0x01;
		buf[62] = (byte)0x00;

		buf[63] = (byte)0x21;

		String text = packet.getText();
		if(text != null && !"".equals(text)) {
			for(int i = 0; i < text.length() && (i + 64) < (buf.length - 1); i++)
				buf[i + 64] = (byte)text.charAt(i);
		}

		return Optional.of(buf);
	}
}
