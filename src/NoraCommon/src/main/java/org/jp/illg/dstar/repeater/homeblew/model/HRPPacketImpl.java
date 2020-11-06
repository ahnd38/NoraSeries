package org.jp.illg.dstar.repeater.homeblew.model;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.SerializationUtils;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARPacketBase;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.util.FormatUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HRPPacketImpl extends DSTARPacketBase implements HRPPacket, Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPPacketType hrpPacketType;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPPollData pollData;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPTextData textData;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPStatusData statusData;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPRegisterData registerData;

	@Getter
	@Setter
	private int errors;

	@Getter
	@Setter
	private InetAddress remoteAddress;

	@Getter
	@Setter
	private int remotePort;


	public HRPPacketImpl(HRPPacketType packetType) {
		super(DSTARPacketType.DV, DSTARProtocol.Homeblew, null);

		setCreatedTimestamp(System.currentTimeMillis());

		setHrpPacketType(packetType);
	}

	public HRPPacketImpl(HRPPollData poll) {
		this(HRPPacketType.Poll);

		setPollData(poll);
	}

	public HRPPacketImpl(
		final PacketType packetType, final DVPacket packet,
		final boolean isBusy
	) {
		super(DSTARPacketType.DV, DSTARProtocol.Homeblew, null);

		setCreatedTimestamp(System.currentTimeMillis());

		switch(packetType) {
		case Header:
			if(!isBusy)
				setHrpPacketType(HRPPacketType.Header);
			else
				setHrpPacketType(HRPPacketType.BusyHeader);

			break;

		case Voice:
			if(!isBusy)
				setHrpPacketType(HRPPacketType.AMBE);
			else
				setHrpPacketType(HRPPacketType.BusyAMBE);

			break;

		default:
			throw new IllegalArgumentException();
		}

		setProtocol(DSTARProtocol.Homeblew);

		setDVPacket(packet);
	}

	public HRPPacketImpl(
		final PacketType packetType, final DVPacket packet,
		final boolean isBusy,
		final InetAddress remoteAddress, final int remotePort
	) {
		this(packetType, packet, isBusy);

		setRemoteAddress(remoteAddress);
		setRemotePort(remotePort);
	}

	public HRPPacketImpl(HRPTextData text) {
		this(HRPPacketType.Text);

		setTextData(text);
	}

	public HRPPacketImpl(HRPStatusData status) {
		this(HRPPacketType.Status);

		setStatusData(status);
	}

	public HRPPacketImpl(HRPRegisterData register) {
		this(HRPPacketType.Register);

		setRegisterData(register);
	}

	@Override
	public HRPPacketImpl clone() {
		final HRPPacketImpl copy = (HRPPacketImpl)super.clone();

		copy.createdTimestamp = createdTimestamp;
		copy.hrpPacketType = hrpPacketType;

		if(pollData != null) {copy.pollData = pollData.clone();}
		if(textData != null) {copy.textData = textData.clone();}
		if(statusData != null) {copy.statusData = statusData.clone();}
		if(registerData != null) {copy.registerData = registerData.clone();}

		copy.errors = errors;

		copy.remoteAddress = SerializationUtils.clone(remoteAddress);
		copy.remotePort = remotePort;

		return copy;
	}

	public static HRPPacket isValidHeader(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 49) {return null;}

		byte[] data = new byte[buffer.remaining()];

		boolean busy = false;
		if(
				(
					buffer.rewind() != null &&
					(data[0] = buffer.get()) == 'D' &&
					(data[1] = buffer.get()) == 'S' &&
					(data[2] = buffer.get()) == 'R' &&
					(data[3] = buffer.get()) == 'P' &&
					(data[4] = buffer.get()) == (byte)0x20	// Normal Header
				) ||
				(
					buffer.rewind() != null &&
					(data[0] = buffer.get()) == 'D' &&
					(data[1] = buffer.get()) == 'S' &&
					(data[2] = buffer.get()) == 'R' &&
					(data[3] = buffer.get()) == 'P' &&
					(busy = (data[4] = buffer.get()) == (byte)0x22)	// Busy Header
				)
		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			final int frameID = ((data[5] << 8) & 0xFF00) | (data[6] & 0x00FF);
			final BackBoneHeader backbone =
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);

			final int errors = data[7];

			final Header header = new Header();

			header.getFlags()[0] = data[8];
			header.getFlags()[1] = data[9];
			header.getFlags()[2] = data[10];

			for(int index = 0; index < DSTARDefines.CallsignFullLength; index++)
				header.getRepeater2Callsign()[index] = (char)data[11 + index];

			for(int index = 0; index < DSTARDefines.CallsignFullLength; index++)
				header.getRepeater1Callsign()[index] = (char)data[19 + index];

			for(int index = 0; index < DSTARDefines.CallsignFullLength; index++)
				header.getYourCallsign()[index] = (char)data[27 + index];

			for(int index = 0; index < DSTARDefines.CallsignFullLength; index++)
				header.getMyCallsign()[index] = (char)data[35 + index];

			for(int index = 0; index < DSTARDefines.CallsignShortLength; index++)
				header.getMyCallsignAdd()[index] = (char)data[43 + index];

				header.getCrc()[0] = data[47];
				header.getCrc()[0] = data[48];

				final HRPPacket packet = new HRPPacketImpl(
					PacketType.Header, new DVPacket(backbone, header), busy
				);

				packet.setErrors(errors);

				return packet;
		}
		else {
			buffer.rewind();
			return null;
		}
	}

	public static HRPPacket isValidAMBE(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 21) {return null;}

		byte[] data = new byte[buffer.remaining()];

		boolean busy = false;
		if(
			(
				buffer.rewind() != null &&
				(data[0] = buffer.get()) == 'D' &&
				(data[1] = buffer.get()) == 'S' &&
				(data[2] = buffer.get()) == 'R' &&
				(data[3] = buffer.get()) == 'P' &&
				(data[4] = buffer.get()) == (byte)0x21	// Normal AMBE
			) ||
			(
				buffer.rewind() != null &&
				(data[0] = buffer.get()) == 'D' &&
				(data[1] = buffer.get()) == 'S' &&
				(data[2] = buffer.get()) == 'R' &&
				(data[3] = buffer.get()) == 'P' &&
				(busy = (data[4] = buffer.get()) == (byte)0x23)	// Busy AMBE
			)
		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			final int frameID = ((data[5] << 8) & 0xFF00) | (data[6] & 0x00FF);
			final BackBoneHeader backbone =
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID);

			backbone.setManagementInformation(data[7]);

			final int errors = data[8];

			final VoiceAMBE voice = new VoiceAMBE();
			for(int index = 0; index < DSTARDefines.VoiceSegmentLength; index++)
				voice.getVoiceSegment()[index] = data[9 + index];

			for(int index = 0; index < DSTARDefines.DataSegmentLength; index++)
				voice.getDataSegment()[index] = data[18 + index];


			final HRPPacket packet = new HRPPacketImpl(
				PacketType.Voice, new DVPacket(backbone, voice), busy
			);
			packet.setErrors(errors);

			return packet;
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static HRPPacket isValidText(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 34) {return null;}

		byte[] data = new byte[buffer.remaining()];

		if(
			buffer.rewind() != null &&
			(data[0] = buffer.get()) == 'D' &&
			(data[1] = buffer.get()) == 'S' &&
			(data[2] = buffer.get()) == 'R' &&
			(data[3] = buffer.get()) == 'P' &&
			(data[4] = buffer.get()) == (byte)0x00

		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			StringBuilder sb = new StringBuilder();
			for(int index = 5; index < data.length && index < 25; index++)
				sb.append((char)data[index]);

			final String text = sb.toString();

			HomebrewReflectorLinkStatus reflectorLinkStatus = HomebrewReflectorLinkStatus.getTypeByValue(data[25]);

			sb = new StringBuilder();
			for(int index = 26; index < data.length && index < 34; index++)
				sb.append((char)data[index]);

			final String reflectorCallsign = sb.toString();

			final HRPPacket packet = new HRPPacketImpl(new HRPTextData(reflectorLinkStatus, reflectorCallsign, text));

			return packet;
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static HRPPacket isValidTempText(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 25) {return null;}

		byte[] data = new byte[buffer.remaining()];

		if(
			buffer.rewind() != null &&
			(data[0] = buffer.get()) == 'D' &&
			(data[1] = buffer.get()) == 'S' &&
			(data[2] = buffer.get()) == 'R' &&
			(data[3] = buffer.get()) == 'P' &&
			(data[4] = buffer.get()) == (byte)0x01

		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			final StringBuilder sb = new StringBuilder();
			for(int index = 5; index < data.length && index < 25; index++)
				sb.append((char)data[index]);

			final String text = sb.toString();

			final HRPPacket packet = new HRPPacketImpl(new HRPTextData(text));

			return packet;
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static HRPPacket isValidPoll(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(
			buffer.rewind() != null &&
			buffer.remaining() >= 6 &&
			buffer.get() == 'D' &&
			buffer.get() == 'S' &&
			buffer.get() == 'R' &&
			buffer.get() == 'P' &&
			buffer.get() == (byte)0x0A	// Poll
		) {
			StringBuilder sb = new StringBuilder();

			byte data;
			while(buffer.hasRemaining() && (data = buffer.get()) != (byte)0x00)
				sb.append((char)data);

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return new HRPPacketImpl(new HRPPollData(sb.toString()));
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static HRPPacket isValidStatus(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 7) {return null;}

		final byte[] data = new byte[buffer.remaining()];

		if(
			buffer.rewind() != null &&
			(data[0] = buffer.get()) == 'D' &&
			(data[1] = buffer.get()) == 'S' &&
			(data[2] = buffer.get()) == 'R' &&
			(data[3] = buffer.get()) == 'P' &&
			(data[4] = buffer.get()) == (byte)0x04

		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			final StringBuilder sb = new StringBuilder();
			for(int index = 6; index < data.length && data[index] != 0x00; index++)
				sb.append((char)data[index]);

			final HRPPacket packet =
				new HRPPacketImpl(new HRPStatusData(data[5], sb.toString()));

			return packet;
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static HRPPacket isValidRegister(ByteBuffer buffer) {
		assert buffer != null && buffer.position() == 0;

		if(buffer.remaining() < 6) {return null;}

		byte[] data = new byte[buffer.remaining()];

		if(
			buffer.rewind() != null &&
			(data[0] = buffer.get()) == 'D' &&
			(data[1] = buffer.get()) == 'S' &&
			(data[2] = buffer.get()) == 'R' &&
			(data[3] = buffer.get()) == 'P' &&
			(data[4] = buffer.get()) == (byte)0x0B

		) {
			for(int index = 5; index < data.length && buffer.hasRemaining(); index++)
				data[index] = buffer.get();

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			final StringBuilder sb = new StringBuilder();
			for(int index = 5; index < data.length && data[index] != 0x00; index++)
				sb.append((char)data[index]);

			final HRPPacket packet =
				new HRPPacketImpl(new HRPRegisterData(sb.toString()));

			return packet;
		}
		else {
			buffer.rewind();

			return null;
		}
	}

	public static byte[] assembleHeader(HRPPacket packet) {
		if(
			packet == null ||
			(
				packet.getHrpPacketType() != HRPPacketType.Header &&
				packet.getHrpPacketType() != HRPPacketType.BusyHeader
			) ||
			!packet.getDVPacket().hasPacketType(PacketType.Header)
		) {return null;}

		final byte[] data = new byte[49];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = packet.getHrpPacketType() == HRPPacketType.BusyHeader ? (byte)0x22 : (byte)0x20;

		data[5] = packet.getDVPacket().getBackBone().getFrameID()[0];
		data[6] = packet.getDVPacket().getBackBone().getFrameID()[1];

		data[7] = (byte)0x00;

		data[8] = packet.getDVPacket().getRfHeader().getFlags()[0];
		data[9] = packet.getDVPacket().getRfHeader().getFlags()[1];
		data[10] = packet.getDVPacket().getRfHeader().getFlags()[2];

		int offset = 11;
		for(int index = offset; index < (offset + DSTARDefines.CallsignFullLength); index++)
			data[index] = (byte)packet.getDVPacket().getRfHeader().getRepeater2Callsign()[index - offset];

		offset += DSTARDefines.CallsignFullLength;
		for(int index = offset; index < (offset + DSTARDefines.CallsignFullLength); index++)
			data[index] = (byte)packet.getDVPacket().getRfHeader().getRepeater1Callsign()[index - offset];

		offset += DSTARDefines.CallsignFullLength;
		for(int index = offset; index < (offset + DSTARDefines.CallsignFullLength); index++)
			data[index] = (byte)packet.getDVPacket().getRfHeader().getYourCallsign()[index - offset];

		offset += DSTARDefines.CallsignFullLength;
		for(int index = offset; index < (offset + DSTARDefines.CallsignFullLength); index++)
			data[index] = (byte)packet.getDVPacket().getRfHeader().getMyCallsign()[index - offset];

		offset += DSTARDefines.CallsignFullLength;
		for(int index = offset; index < (offset + DSTARDefines.CallsignShortLength); index++)
			data[index] = (byte)packet.getDVPacket().getRfHeader().getMyCallsignAdd()[index - offset];

		final int crc = DSTARCRCCalculator.calcCRC(
			data,
			8,
			3 + (4 * DSTARDefines.CallsignFullLength) + DSTARDefines.CallsignShortLength
		);
		data[47] = (byte)(crc & 0xff);
		data[48] = (byte)((crc >> 8) & 0xff);

		return data;
	}

	public static byte[] assembleAMBE(HRPPacket packet) {
		if(
			packet == null ||
			(
				packet.getHrpPacketType() != HRPPacketType.AMBE &&
				packet.getHrpPacketType() != HRPPacketType.BusyAMBE
			) ||
			!packet.getDVPacket().hasPacketType(PacketType.Voice)
		) {return null;}

		final byte[] data = new byte[21];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = packet.getHrpPacketType() == HRPPacketType.BusyAMBE ?
			HRPPacketType.BusyAMBE.getValue() : HRPPacketType.AMBE.getValue();

		data[5] = packet.getDVPacket().getBackBone().getFrameID()[0];
		data[6] = packet.getDVPacket().getBackBone().getFrameID()[1];

		data[7] = packet.getDVPacket().getBackBone().getManagementInformation();

		data[8] = (byte)packet.getErrors();

		byte[] voiceSegment = packet.getDVPacket().getVoiceData().getVoiceSegment();
		for(int index = 0;index < voiceSegment.length;index++)
			data[index + 9] = voiceSegment[index];

		byte[] dataSegment = packet.getDVPacket().getVoiceData().getDataSegment();
		for(int index = 0;index < dataSegment.length;index++)
			data[index + 18] = dataSegment[index];

		return data;
	}

	public static byte[] assemblePoll(HRPPacket packet) {
		if(
			packet == null ||
			packet.getHrpPacketType() != HRPPacketType.Poll ||
			packet.getPollData().getData() == null
		) {return null;}

		final int length = 5 + packet.getPollData().getData().length() + 1;
		final byte[] data = new byte[length];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = HRPPacketType.Poll.getValue();

		int offset = 5;
		for(int index = 0; index < packet.getPollData().getData().length(); index++)
			data[offset + index] = (byte)packet.getPollData().getData().charAt(index);

		offset += packet.getPollData().getData().length();
		data[offset] = (byte)0x00;

		return data;
	}

	public static byte[] assembleText(HRPPacket packet) {
		if(
			packet == null ||
			packet.getHrpPacketType() != HRPPacketType.Text
		) {return null;}

		final int length = packet.getTextData().isTemporary() ? 25 : 34;
		final byte[] data = new byte[length];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = packet.getTextData().isTemporary() ?
				HRPPacketType.TempText.getValue() : HRPPacketType.Text.getValue();

		int offset = 5;
		for(int index = 0; index < 20; index++) {
			if(packet.getTextData().getText() != null && index < packet.getTextData().getText().length())
				data[offset + index] = (byte)packet.getPollData().getData().charAt(index);
			else
				data[offset + index] = (byte)' ';
		}
		offset += 20;

		if(!packet.getTextData().isTemporary()) {
			data[offset] = packet.getTextData().getReflectorLinkStatus() != null ?
					packet.getTextData().getReflectorLinkStatus().getValue() : HomebrewReflectorLinkStatus.LS_NONE.getValue();
			offset += 1;

			for(int index = 0; index < DSTARDefines.CallsignFullLength; index++) {
				if(packet.getTextData().getReflectorCallsign() != null && index < packet.getTextData().getReflectorCallsign().length())
					data[offset + index] = (byte)packet.getTextData().getReflectorCallsign().charAt(index);
				else
					data[offset + index] = (byte)' ';
			}
		}

		return data;
	}

	public static byte[] assembleStatus(HRPPacket packet) {
		if(
			packet == null ||
			packet.getHrpPacketType() != HRPPacketType.Status
		) {return null;}

		final byte[] data = new byte[26];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = HRPPacketType.Status.getValue();

		data[5] = (byte)packet.getStatusData().getStatusNumber();

		int offset = 6;
		for(int index = 0; index < 20; index++) {
			if(packet.getStatusData().getStatusText() != null && index < packet.getStatusData().getStatusText().length())
				data[offset + index] = (byte)packet.getStatusData().getStatusText().charAt(index);
			else
				data[offset + index] = (byte)' ';
		}

		return data;
	}

	public static byte[] assembleRegister(HRPPacket packet) {
		if(
			packet == null ||
			packet.getHrpPacketType() != HRPPacketType.Register
		) {return null;}

		final String name = packet.getRegisterData().getName() != null ? packet.getRegisterData().getName() : "";
		final int length = 5 + name.length() + 1;
		final byte[] data = new byte[length];

		data[0] = 'D';
		data[1] = 'S';
		data[2] = 'R';
		data[3] = 'P';

		data[4] = HRPPacketType.Register.getValue();

		int offset = 5;
		for(int index = 0; index < name.length(); index++) {
			if(index < name.length())
				data[offset + index] = (byte)name.charAt(index);
			else
				data[offset + index] = (byte)' ';
		}
		offset += name.length();

		data[offset] = (byte)0x00;

		return data;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indent) {
		if(indent < 0) {indent = 0;}

		StringBuilder sb = new StringBuilder();

		for(int count = 0; count < indent; count++)
			sb.append(' ');

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append("CreatedTimestamp=");
		sb.append(FormatUtil.dateFormat(getCreatedTimestamp()));

		sb.append(" / ");

		sb.append("PacketType=");
		sb.append(getPacketType().toString());

		sb.append(" / ");

		sb.append("Errors=");
		sb.append(getErrors());

		sb.append(" / ");

		sb.append("RemoteAddress=");
		if(getRemoteAddress() != null)
			sb.append(getRemoteAddress().toString() + ":" + getRemotePort());
		else
			sb.append("null");

		sb.append("\n");

		switch(getHrpPacketType()) {
		case Poll:
			if(getPollData() != null) {sb.append(getPollData().toString(indent + 4));}
			break;
		case Header:
		case BusyHeader:
		case AMBE:
		case BusyAMBE:
			if(getDVPacket() != null) {sb.append(getDVPacket().toString(indent + 4));}
			break;
		case Text:
		case TempText:
			if(getTextData() != null) {sb.append(getTextData().toString(indent + 4));}
			break;
		case Status:
			if(getStatusData() != null) {sb.append(getStatusData().toString(indent + 4));}
			break;
		case Register:
			if(getRegisterData() != null) {sb.append(getRegisterData().toString(indent + 4));}
			break;
		default:
			break;
		}

		return sb.toString();
	}
}
