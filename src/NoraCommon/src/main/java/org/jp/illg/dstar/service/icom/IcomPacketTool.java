package org.jp.illg.dstar.service.icom;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.BackBoneManagementData;
import org.jp.illg.dstar.model.BackBonePacket;
import org.jp.illg.dstar.model.BackBonePacketDirectionType;
import org.jp.illg.dstar.model.BackBonePacketType;
import org.jp.illg.dstar.model.DDPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.HeardPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ArrayUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IcomPacketTool {

	private static final String logTag = IcomPacketTool.class.getSimpleName() + " : ";

	private static final byte[] initHeader = "INIT".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] dstrHeader = "DSTR".getBytes(StandardCharsets.US_ASCII);

	private IcomPacketTool() {}

	public static byte[] assembleDummyPacket(@NonNull final BackBonePacket packet) {
		if(packet.getManagementData().getType() != BackBonePacketType.Check)
			return null;

		packet.getManagementData().setType(BackBonePacketType.Check);
		packet.getManagementData().setLength(0);

		final byte[] buffer = new byte[10];

		if(!assembleHeader(buffer, dstrHeader, packet.getManagementData())) {
			if(log.isWarnEnabled())
				log.warn(logTag + "DummyPacket assemble error\n" + packet.toString(4));

			return null;
		}

		return buffer;
	}

	public static BackBonePacket parseDummyPacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() != 10) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if((packet = parseHeader(dstrHeader, buffer)) == null) {
			buffer.position(savedPos);
			return null;
		}

		if(packet.getManagementData().getLength() != 0) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Illegal dummy packet length = " + packet.getManagementData().getLength() + "bytes");

			buffer.position(savedPos);
			return null;
		}
		else if(
			packet.getManagementData().getType() != BackBonePacketType.Check &&
			packet.getManagementData().getType() != BackBonePacketType.DVPacket
		) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Illegal dummy packet type = " + packet.getManagementData().getType());

			buffer.position(savedPos);
			return null;
		}

		packet.getManagementData().setType(BackBonePacketType.Check);

		return packet;
	}

	public static byte[] assembleDDPacket(@NonNull final BackBonePacket packet) {
		if(packet.getManagementData().getType() != BackBonePacketType.DDPacket)
			return null;

		packet.getManagementData().setDirectionType(BackBonePacketDirectionType.Send);

		final int dataLength = packet.getDDData() != null ? packet.getDDData().length : 0;
		packet.getManagementData().setLength(7 + dataLength);

		final byte[] buffer = new byte[58 + dataLength];

		if(
			!assembleHeader(buffer, dstrHeader, packet.getManagementData()) ||
			!assembleBackboneHeader(buffer, 10, packet.getBackBone()) ||
			!assembleRFHeader(buffer, 17, packet.getRfHeader()) ||
			!assembleDDData(buffer, 58, packet.getDDData())
		) {
			if(log.isWarnEnabled())
				log.warn(logTag + "DDPacket assemble error\n" + packet.toString(4));

			return null;
		}

		return buffer;
	}

	public static BackBonePacket parseDDPacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() < 59) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if(
			(packet = parseHeader(dstrHeader, buffer)) == null ||
			packet.getManagementData().getType() != BackBonePacketType.DDPacket
		) {
			buffer.position(savedPos);
			return null;
		}

		final BackBoneHeader backbone = parseBackboneHeader(buffer);
		if(backbone == null) {
			buffer.position(savedPos);
			return null;
		}

		final Header header = parseRFHeader(buffer);
		if(header == null) {
			buffer.position(savedPos);
			return null;
		}

		final byte[] ddData = parseDDData(buffer);
		if(ddData == null) {
			buffer.position(savedPos);
			return null;
		}

		packet.setDDPacket(new DDPacket(header, backbone, ddData));
		packet.setPacketType(DSTARPacketType.DD);

		return packet;
	}

	public static byte[] assembleDVPacket(
		@NonNull final PacketType packetType,
		@NonNull final BackBonePacket packet
	) {
		if(
			(packetType != PacketType.Header && packetType != PacketType.Voice) ||
			packet.getManagementData().getType() != BackBonePacketType.DVPacket
		) {
			return null;
		}

		packet.getManagementData().setDirectionType(BackBonePacketDirectionType.Send);

		final int length =
			packetType == PacketType.Header ? 48 : (packet.isLastFrame() ? 22 : 19);

		packet.getManagementData().setLength(length);

		final byte[] buffer = new byte[10 + length];

		if(packetType == PacketType.Header) {
			if(
				!assembleHeader(buffer, dstrHeader, packet.getManagementData()) ||
				!assembleBackboneHeader(buffer, 10, packet.getBackBone()) ||
				!assembleRFHeader(buffer, 17, packet.getRfHeader())
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "DVPacket(Header) assemble error\n" + packet.toString(4));

				return null;
			}
		}
		else if(packetType == PacketType.Voice) {
			if(
				!assembleHeader(buffer, dstrHeader, packet.getManagementData()) ||
				!assembleBackboneHeader(buffer, 10, packet.getBackBone()) ||
				!assembleVoice(buffer, 17, (VoiceAMBE)packet.getVoiceData(), packet.isLastFrame())
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "DVPacket(Header) assemble error\n" + packet.toString(4));

				return null;
			}
		}

		return buffer;
	}

	public static BackBonePacket parseDVPacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() < 29) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if(
			(packet = parseHeader(dstrHeader, buffer)) == null ||
			packet.getManagementData().getType() != BackBonePacketType.DVPacket
		) {
			buffer.position(savedPos);
			return null;
		}

		final BackBoneHeader backbone = parseBackboneHeader(buffer);
		if(backbone == null) {
			buffer.position(savedPos);
			return null;
		}

		final BackBoneHeaderFrameType frameType = backbone.getFrameType();
		if(frameType == BackBoneHeaderFrameType.VoiceDataHeader) {
			final Header header = parseRFHeader(buffer);
			if(header == null) {
				buffer.position(savedPos);
				return null;
			}

			packet.setDVPacket(new DVPacket(backbone, header));
		}
		else if(
			frameType == BackBoneHeaderFrameType.VoiceData ||
			frameType == BackBoneHeaderFrameType.VoiceDataLastFrame
		) {
			final VoiceAMBE voice = parseVoice(buffer);
			if(voice == null) {
				buffer.position(savedPos);
				return null;
			}

			packet.setDVPacket(new DVPacket(backbone, voice));

			if(buffer.remaining() >= 3) {
				for(int i = 0; i < 3; i++) {buffer.get();}

				packet.getDVPacket().getBackBone().setEndSequence();
			}
		}

		packet.setPacketType(DSTARPacketType.DV);

		return packet;
	}

	public static byte[] assembleInitPacket(
		@NonNull final BackBonePacket packet
	) {
		if(packet.getManagementData().getType() != BackBonePacketType.Check)
			return null;

		packet.getManagementData().setDirectionType(BackBonePacketDirectionType.Send);
		packet.getManagementData().setType(BackBonePacketType.Check);
		packet.getManagementData().setLength(0);

		final byte[] buffer = new byte[10];

		if(!assembleHeader(buffer, initHeader, packet.getManagementData())) {
			if(log.isWarnEnabled())
				log.warn(logTag + "INITPacket assemble error\n" + packet.toString(4));

			return null;
		}

		return buffer;
	}

	public static BackBonePacket parseInitPacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() < 10) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if(
			(packet = parseHeader(initHeader, buffer)) == null ||
			packet.getManagementData().getType() != BackBonePacketType.Check
		) {
			buffer.position(savedPos);
			return null;
		}

		return packet;
	}

	public static BackBonePacket parseErrorPacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() < 10) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if(
			(packet = parseHeader(dstrHeader, buffer)) == null ||
			packet.getManagementData().getType() != BackBonePacketType.Error
		) {
			buffer.position(savedPos);
			return null;
		}

		byte[] errorData = null;
		if((errorData = parseError(buffer)) != null) {
			buffer.position(savedPos);
			return null;
		}
		packet.setErrorData(errorData);

		return packet;
	}

	public static BackBonePacket parsePositionUpdatePacket(@NonNull final ByteBuffer buffer) {
		if(buffer.remaining() < 26) {return null;}

		final int savedPos = buffer.position();

		BackBonePacket packet = null;
		if(
			(packet = parseHeader(dstrHeader, buffer)) == null ||
			packet.getManagementData().getLength() != 16 ||
			packet.getManagementData().getType() != BackBonePacketType.PositionUpdate
		) {
			buffer.position(savedPos);
			return null;
		}

		HeardPacket heardPacket = null;
		if((heardPacket = parsePositionUpdate(buffer)) == null) {
			buffer.position(savedPos);
			return null;
		}
		packet.setHeardPacket(heardPacket);
		packet.setPacketType(DSTARPacketType.UpdateHeard);

		return packet;
	}



	private static HeardPacket parsePositionUpdate(final ByteBuffer buffer) {
		if(buffer.remaining() < DSTARDefines.CallsignFullLength * 2) {return null;}

		final byte[] terminalCallsignValue = new byte[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {terminalCallsignValue[i] = buffer.get();}
		final String terminalCallsign = DSTARUtils.formatFullLengthCallsign(
			new String(terminalCallsignValue, StandardCharsets.US_ASCII)
		);

		final byte[] areaRepeaterCallsignValue = new byte[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {areaRepeaterCallsignValue[i] = buffer.get();}
		final String areaRepeaterCallsign = DSTARUtils.formatFullLengthCallsign(
			new String(areaRepeaterCallsignValue, StandardCharsets.US_ASCII)
		);

		return new HeardPacket(terminalCallsign, areaRepeaterCallsign);
	}

	private static boolean assembleHeader(
		final byte[] buffer,
		final byte[] header,
		final BackBoneManagementData managementData
	) {
		ArrayUtil.copyOf(buffer, header);

		return assembleManagementData(buffer, 4, managementData);
	}

	private static BackBonePacket parseHeader(
		final byte[] specifiedHeader,
		final ByteBuffer buffer
	) {
		if(buffer.remaining() < 10) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Too short packet length = " + buffer.remaining());

			return null;
		}

		final int savedPos = buffer.position();

		final byte[] header = new byte[4];
		for(int i = 0; i < 4; i++) {header[i] = buffer.get();}

		if(!Arrays.equals(header, specifiedHeader)) {
			buffer.position(savedPos);
			return null;
		}

		BackBoneManagementData managementData = null;
		if((managementData = parseManagementData(buffer)) == null) {
			buffer.position(savedPos);
			return null;
		}

		return new BackBonePacket(header, managementData);
	}

	private static boolean assembleManagementData(
		final byte[] buffer, final int offset,
		final BackBoneManagementData managementData
	) {
		if(buffer.length < (offset + 6)) {return false;}

		buffer[offset + 0] = (byte)((managementData.getMagicNumber() >> 8) & 0xFF);
		buffer[offset + 1] = (byte)(managementData.getMagicNumber() & 0xFF);
		buffer[offset + 2] = (byte)managementData.getDirectionType().getValue();
		buffer[offset + 3] = (byte)managementData.getType().getValue();
		buffer[offset + 4] = (byte)((managementData.getLength() >> 8) & 0xFF);
		buffer[offset + 5] = (byte)(managementData.getLength() & 0xFF);

		return true;
	}

	private static BackBoneManagementData parseManagementData(
		final ByteBuffer buffer
	) {
		if(buffer.remaining() < 6) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Too short management data length = " + buffer.remaining());

			return null;
		}

		final int savedPos = buffer.position();

		final int magicNumber = (int)(((buffer.get() << 8) & 0xFF00) | (buffer.get() & 0x00FF));
		final int dirValue = buffer.get();
		final BackBonePacketDirectionType dir =
			BackBonePacketDirectionType.getTypeByValue(dirValue);
		if(dir == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown direction type value = 0x" + String.format("%02X", dirValue));

			buffer.position(savedPos);
			return null;
		}

		final int packetTypeValue = buffer.get();
		final BackBonePacketType packetType = BackBonePacketType.getTypeByValue(packetTypeValue);
		if(packetType == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown packet type value = 0x" + String.format("%02X", dirValue));

			buffer.position(savedPos);
			return null;
		}

		final int length = (int)(((buffer.get() << 8) & 0xFF00) | (buffer.get() & 0x00FF));

		return new BackBoneManagementData(magicNumber, dir, packetType, length);
	}

	private static boolean assembleBackboneHeader(
		final byte[] buffer, final int offset,
		final BackBoneHeader backbone
	) {
		if(buffer.length < (offset + 7)) {return false;}

		buffer[offset + 0] = backbone.getId();
		buffer[offset + 1] = backbone.getDestinationRepeaterID();
		buffer[offset + 2] = backbone.getSendRepeaterID();
		buffer[offset + 3] = backbone.getSendTerminalID();
		buffer[offset + 4] = backbone.getFrameID()[0];
		buffer[offset + 5] = backbone.getFrameID()[1];
		buffer[offset + 6] = backbone.getManagementInformation();

		return true;
	}

	private static BackBoneHeader parseBackboneHeader(final ByteBuffer buffer) {
		if(buffer.remaining() < 7) {return null;}

		final int savedPos = buffer.position();

		final byte id = buffer.get();
		final byte destinationRepeaterID = buffer.get();
		final byte sendRepeaterID = buffer.get();
		final byte sendTerminalID = buffer.get();
		final int frameID = (buffer.get() << 8) | buffer.get();
		final byte managementInformation = buffer.get();

		final BackBoneHeaderType type = BackBoneHeaderType.getTypeByValue(id);
		if(type == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown backbone header type = " + String.format("0x%02X", id));

			buffer.position(savedPos);
			return null;
		}

		final BackBoneHeaderFrameType frameType =
			BackBoneHeaderFrameType.valueOf(managementInformation);
		if(frameType == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown backbone frame type = " + String.format("0x%02X", managementInformation));

			buffer.position(savedPos);
			return null;
		}

		final BackBoneHeader backbone = new BackBoneHeader(type, frameType, frameID);
		backbone.setId(id);
		backbone.setDestinationRepeaterID(destinationRepeaterID);
		backbone.setSendRepeaterID(sendRepeaterID);
		backbone.setSendTerminalID(sendTerminalID);
		backbone.setFrameIDNumber(frameID);
		backbone.setManagementInformation(managementInformation);

		return backbone;
	}

	private static boolean assembleRFHeader(
		final byte[] buffer, final int offset,
		final Header header
	) {
		if(buffer.length < (offset + 41)) {return false;}

		for(int i = 0; i < 3; i++)
			buffer[offset + 0 + i] = header.getFlags()[i];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			buffer[offset + 3 + i] = (byte)header.getRepeater2Callsign()[i];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			buffer[offset + 11 + i] = (byte)header.getRepeater1Callsign()[i];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			buffer[offset + 19 + i] = (byte)header.getYourCallsign()[i];

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			buffer[offset + 27 + i] = (byte)header.getMyCallsign()[i];

		for(int i = 0; i < DSTARDefines.CallsignShortLength; i++)
			buffer[offset + 35 + i] = (byte)header.getMyCallsignAdd()[i];

		for(int i = 0; i < 2; i++)
			buffer[offset + 39 + i] = (byte)header.getCrc()[i];

		return true;
	}

	private static Header parseRFHeader(final ByteBuffer buffer) {
		if(buffer.remaining() < 41) {return null;}

		final int savedPos = buffer.position();

		final Header header = new Header();

		buffer.get(header.getFlags());
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			header.getRepeater2Callsign()[i] = (char)buffer.get();

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			header.getRepeater1Callsign()[i] = (char)buffer.get();

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			header.getYourCallsign()[i] = (char)buffer.get();

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++)
			header.getMyCallsign()[i] = (char)buffer.get();

		for(int i = 0; i < DSTARDefines.CallsignShortLength; i++)
			header.getMyCallsignAdd()[i] = (char)buffer.get();

		header.setCrcInt((buffer.get() & 0x00FF) | ((buffer.get() << 8) & 0xFF00));

		header.saveRepeaterCallsign();

		final RepeaterControlFlag repeaterControlFlag = header.getRepeaterControlFlag();
		if(
			repeaterControlFlag == null ||
			repeaterControlFlag == RepeaterControlFlag.Unknown
		) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown repeater control flag = " + String.format("0x%02X", header.getFlags()[0]));

			buffer.position(savedPos);
			return null;
		}

		return header;
	}

	private static boolean assembleVoice(
		final byte[] buffer, final int offset,
		final VoiceAMBE voice,
		final boolean isLastFrame
	) {
		if(buffer.length < (offset + (isLastFrame ? 15 : 12))) {return false;}

		for(int i = 0; i < DSTARDefines.VoiceSegmentLength; i++)
			buffer[offset + 0 + i] = voice.getVoiceSegment()[i];

		for(int i = 0; i < DSTARDefines.DataSegmentLength; i++)
			buffer[offset + 9 + i] = voice.getDataSegment()[i];

		if(isLastFrame) {
			for(int i = 0; i < 3; i++)
				buffer[offset + 12 + i] = (byte)0x00;
		}

		return true;
	}

	private static VoiceAMBE parseVoice(final ByteBuffer buffer) {
		if(buffer.remaining() < 12) {return null;}

		final VoiceAMBE voice = new VoiceAMBE();
		buffer.get(voice.getVoiceSegment());
		buffer.get(voice.getDataSegment());

		return voice;
	}

	private static boolean assembleDDData(
		final byte[] buffer, final int offset,
		final byte[] data
	) {
		if(buffer.length < (offset + data.length)) {return false;}

		ArrayUtil.copyOfRange(buffer, offset, data);

		return true;
	}

	private static byte[] parseDDData(final ByteBuffer buffer) {
		final byte[] data = new byte[buffer.remaining()];

		if(data.length > 0) {buffer.get(data);}

		return data;
	}

	private static byte[] parseError(final ByteBuffer buffer) {
		final byte[] data = new byte[buffer.remaining()];

		if(data.length > 0) {buffer.get(data);}

		return data;
	}
}
