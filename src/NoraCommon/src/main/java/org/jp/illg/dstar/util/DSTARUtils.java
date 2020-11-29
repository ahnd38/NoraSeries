/**
 *
 */
package org.jp.illg.dstar.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;

import lombok.NonNull;

/**
 * @author AHND
 *
 */
public class DSTARUtils {

	private static final Pattern callsignCharPattern =
		Pattern.compile("[^0-9A-Z/]");

	private static final Pattern illegalCQCQCQPattern =
		Pattern.compile("^(\\s|\\/){0,6}(CQ){1,2}(\\s|\\/){0,6}$");

	private DSTARUtils() {super();}


	public static byte[] getNullAMBE(){
		return DSTARDefines.VoiceSegmentNullBytes;
	}

	public static byte[] getEndAMBE() {
		return DSTARDefines.VoiceSegmentNullBytes;
	}

	public static byte[] getLastAMBE() {
		return DSTARDefines.VoiceSegmentLastBytes;
	}

	public static byte[] getEndSlowdata() {
		return DSTARDefines.SlowdataEndBytes;
	}

	public static byte[] getLastSlowdata() {
		return DSTARDefines.SlowdataLastBytes;
	}

	public static int calcCRC(byte[] data, int length) {
		return DSTARCRCCalculator.calcCRC(data, length);
	}

	private static final Random frameidRandom = new Random(System.currentTimeMillis() ^ 0x43AFD46E);
	private static final Random queryidRandom = new Random(System.currentTimeMillis() ^ 0x3FD4323D);


	public static boolean isValidCallsignFullLength(String... callsigns) {
		return isValidCallsignLength(false, callsigns);
	}

	public static boolean isValidCallsignFullLength(char[]... callsigns) {
		return isValidCallsignLength(false, callsigns);
	}

	public static boolean isValidCallsignShortLegth(String... callsigns) {
		return isValidCallsignLength(true, callsigns);
	}

	public static boolean isValidCallsignShortLegth(char[]... callsigns) {
		return isValidCallsignLength(true, callsigns);
	}

	private static boolean isValidCallsignLength(boolean shortLength, String... callsigns) {
		if(callsigns == null) {return false;}

		for(String callsign : callsigns) {
			if(callsign == null) {return false;}

			if(
					(shortLength && callsign.length() != DSTARDefines.CallsignShortLength) ||
					(!shortLength && callsign.length() != DSTARDefines.CallsignFullLength)
			) {return false;}
		}

		return true;
	}

	private static boolean isValidCallsignLength(boolean shortLength, char[]... callsigns) {
		if(callsigns == null) {return false;}

		for(char[] callsign : callsigns) {
			if(callsign == null) {return false;}

			if(
					(shortLength && callsign.length != DSTARDefines.CallsignShortLength) ||
					(!shortLength && callsign.length != DSTARDefines.CallsignFullLength)
			) {return false;}
		}

		return true;
	}

	public static boolean isValidFrameID(final int frameID) {
		return frameID > 0x0000 && frameID <= 0xFFFF;
	}

	public static int generateFrameID() {
		synchronized (frameidRandom) {
			return frameidRandom.nextInt(0xFFFF) + 1;
		}
	}

	public static int generateQueryID() {
		synchronized (queryidRandom) {
			return queryidRandom.nextInt(0xFFFF) + 1;
		}
	}

	public static String convertAreaRepeaterCallToRepeaterCall(String areaRepeaterCallsign) {
		if(areaRepeaterCallsign == null) {return DSTARDefines.EmptyLongCallsign;}

		final String callsign = areaRepeaterCallsign.length() >= DSTARDefines.CallsignFullLength ?
			areaRepeaterCallsign : DSTARUtils.formatFullLengthCallsign(areaRepeaterCallsign);

		return callsign.substring(1, 7) + " " + callsign.charAt(7);
	}

	public static String convertRepeaterCallToAreaRepeaterCall(String repeaterCallsign) {
		if(repeaterCallsign == null) {return "/       ";}

		final String callsign = repeaterCallsign.length() >= DSTARDefines.CallsignFullLength ?
			repeaterCallsign : DSTARUtils.formatFullLengthCallsign(repeaterCallsign);


		return "/" + callsign.substring(0, 6) + callsign.charAt(7);
	}

	public static String formatFullCallsign(String callsign, char module){
		if(callsign == null){callsign = "";}

		char[] formattedCallsign = new char[DSTARDefines.CallsignFullLength];
		Arrays.fill(formattedCallsign, ' ');

		int callsignLength = callsign.length() > 7 ? 7 : callsign.length();

		for(int index = 0; index < callsignLength; index++)
			formattedCallsign[index] = callsign.charAt(index);

		formattedCallsign[DSTARDefines.CallsignFullLength - 1] = module;

		return String.valueOf(formattedCallsign);
	}

	public static String formatFullCallsign(String callsign){
		return formatFullCallsign(callsign, ' ');
	}

	public static String formatFullLengthCallsign(String callsign) {
		if(callsign == null) {callsign = "";}
		final String formatedCallsign =
			String.format("%-" + DSTARDefines.CallsignFullLength + "S", callsign);

		return formatedCallsign.substring(0, DSTARDefines.CallsignFullLength);
	}

	public static String formatShortLengthCallsign(String callsign) {
		if(callsign == null) {callsign = "";}
		final String formatedCallsign =
			String.format("%-" + DSTARDefines.CallsignShortLength + "S", callsign);

		return formatedCallsign.substring(0, DSTARDefines.CallsignShortLength);
	}

	public static String formatLengthShortMessage(String shortMessage) {
		final String format = "%-" + DSTARDefines.DvShortMessageLength + "s";

		if(shortMessage == null) {shortMessage = "";}

		if(shortMessage.length() > DSTARDefines.DvShortMessageLength)
			shortMessage = shortMessage.substring(0, DSTARDefines.DvShortMessageLength);

		return String.format(format, shortMessage);
	}

	public static String replaceCallsignUnderbarToSpace(String callsign) {
		if(callsign != null)
			return callsign.replaceAll("_", " ");
		else
			return "";
	}

	public static String replaceCallsignSpaceToUnderbar(String callsign) {
		if(callsign != null)
			return callsign.replaceAll(" ", "_");
		else
			return "";
	}

	public static boolean writeInt32BigEndian(byte[] dstBuffer, int dstBufferOffset, int value32) {
		if(dstBuffer == null || dstBuffer.length < (dstBufferOffset + 4))
			return false;

		int value = value32;

		for(int c = 0; c < 4 && dstBuffer.length > (dstBufferOffset + c); c++) {
			dstBuffer[(dstBufferOffset + 3) - c] = (byte)value;
			value = value >> 8;
		}

		return true;
	}

	public static boolean writeInt32BigEndian(byte[] dstBuffer, int dstBufferOffset, long value32) {
		if(dstBuffer == null || dstBuffer.length < (dstBufferOffset + 4))
			return false;

		long value = value32;

		for(int c = 0; c < 4 && dstBuffer.length > (dstBufferOffset + c); c++) {
			dstBuffer[(dstBufferOffset + 3) - c] = (byte)value;
			value = value >> 8;
		}

		return true;
	}

	public static boolean writeBooleanBigEndian(byte[] dstBuffer, int dstBufferOffset, boolean bool) {
		return writeInt32BigEndian(dstBuffer, dstBufferOffset, bool ? 1 : 0);
	}

	public static boolean writeFullCallsignToBuffer(byte[] dstBuffer, int dstBufferOffset, char[] callsign) {
		if(dstBuffer == null || dstBufferOffset < 0 || callsign == null) {return false;}

		int copyLength = callsign.length;
		if(copyLength > DSTARDefines.CallsignFullLength) {copyLength = DSTARDefines.CallsignFullLength;}

		int overflow = (dstBufferOffset + copyLength) - dstBuffer.length;
		if(overflow > 0) {copyLength -= overflow;}

		for(int i = 0; i < copyLength; i++)
			dstBuffer[i + dstBufferOffset] = (byte)callsign[i];

		return true;
	}

	public static DVPacket createPreLastVoicePacket(
		final int frameID, final byte currentSequence,
		final Header header
	) {
		final VoiceAMBE voice = new VoiceAMBE(
			DSTARDefines.VoiceSegmentNullBytes, DSTARDefines.SlowdataEndBytes
		);

		final BackBoneHeader backbone = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID,
			getNextShortSequence(currentSequence)
		);

		return header != null ?
			new DVPacket(backbone, header, voice) : new DVPacket(backbone, voice);
	}

	public static DVPacket createPreLastVoicePacket(
		final int frameID, final byte currentSequence
	) {
		return createPreLastVoicePacket(frameID, currentSequence, null);
	}

	public static DVPacket createLastVoicePacket(
		final int frameID, final byte currentSequence,
		final Header header
	) {
		final VoiceAMBE voice = new VoiceAMBE(
			DSTARDefines.VoiceSegmentLastBytes, DSTARDefines.SlowdataLastBytes
		);

		final BackBoneHeader backbone = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataLastFrame, frameID,
			getNextShortSequence(currentSequence)
		);

		return header != null ?
			new DVPacket(backbone, header, voice) : new DVPacket(backbone, voice);
	}

	public static DVPacket createLastVoicePacket(
		final int frameID, final byte currentSequence
	) {
		return createLastVoicePacket(frameID, currentSequence, null);
	}

	public static byte getNextShortSequence(final byte sequence) {
		return (byte)((sequence + 0x1) % (DSTARDefines.MaxSequenceNumber + 1));
	}

	public static boolean replaceNullCharToSpace(@NonNull final char[] target) {
		for(int i = 0; i < target.length; i++) {
			if(target[i] == (char)0x0) {target[i] = ' ';}
		}

		return true;
	}

	/**
	 * コールサインにて使用できる文字以外をスペースに置き換える
	 * @param callsign
	 * @return
	 */
	public static String replaceCallsignIllegalCharToSpace(final String callsign) {
		if(callsign == null || "".equals(callsign)) {return DSTARDefines.EmptyLongCallsign;}

		final String newCallsign =
			callsignCharPattern.matcher(callsign).replaceAll(" ");

		return newCallsign;
	}

	/**
	 * 誤ったCQCQCQを正しいCQCQCQに置き換える
	 * @param callsign コールサイン
	 * @return 修正済みコールサイン
	 */
	public static String reformatIllegalCQCQCQCallsign(@NonNull final String callsign) {
		if(callsign == null || "".equals(callsign)) {return DSTARDefines.EmptyLongCallsign;}

		if(illegalCQCQCQPattern.matcher(callsign).matches())
			return DSTARDefines.CQCQCQ;
		else
			return callsign;
	}

	/**
	 * ループブロックIDを生成する
	 * @return ループブロックID
	 */
	public static UUID generateLoopBlockID() {
		return UUID.randomUUID();
	}

	public static boolean hasControlFlag(final RepeaterControlFlag flag) {
		return flag != null &&
			flag != RepeaterControlFlag.Unknown &&
			flag != RepeaterControlFlag.NOTHING_NULL &&
			flag != RepeaterControlFlag.AUTO_REPLY;
	}

	public static Queue<DSTARPacket> createReplyPacketsSingle(
		final int frameID,
		@NonNull final RepeaterControlFlag flag,
		@NonNull final String myCallsign,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign
	) {
		return createReplyPackets(
			frameID,
			1,
			flag,
			myCallsign,
			yourCallsign,
			repeater1Callsign, repeater2Callsign,
			"", ""
		);
	}

	public static Queue<DSTARPacket> createReplyPackets1Frame(
		final int frameID,
		@NonNull final RepeaterControlFlag flag,
		@NonNull final String myCallsign,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign
	) {
		return createReplyPackets(
			frameID,
			DSTARDefines.MaxSequenceNumber + 1,
			flag,
			myCallsign,
			yourCallsign,
			repeater1Callsign, repeater2Callsign,
			"", ""
		);
	}

	public static Queue<DSTARPacket> createReplyPackets1Frame(
		final int frameID,
		@NonNull final RepeaterControlFlag flag,
		@NonNull final String myCallsign,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign,
		final String messageOnSuccess,
		final String messageOnFail
	) {
		return createReplyPackets(
			frameID,
			DSTARDefines.MaxSequenceNumber + 1,
			flag,
			myCallsign,
			yourCallsign,
			repeater1Callsign, repeater2Callsign,
			messageOnSuccess, messageOnFail
		);
	}

	public static Queue<DSTARPacket> createReplyPacketsICOM(
		final int frameID,
		@NonNull final RepeaterControlFlag flag,
		@NonNull final String myCallsign,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign
	) {
		return createReplyPackets(
			frameID,
			1,
			flag,
			myCallsign,
			yourCallsign,
			repeater1Callsign, repeater2Callsign,
			null, null
		);
	}

	private static Queue<DSTARPacket> createReplyPackets(
		final int frameID,
		final int voicePacketLength,
		@NonNull final RepeaterControlFlag flag,
		@NonNull final String myCallsign,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign,
		final String messageOnSuccess,
		final String messageOnFail
	) {
		if(
			!DSTARUtils.isValidFrameID(frameID) ||
			voicePacketLength < 1
		) {return null;}

		final Queue<DSTARPacket> packets = new LinkedList<>();

		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final Header header = new Header(
			yourCallsign.toCharArray(),
			repeater1Callsign.toCharArray(),
			repeater2Callsign.toCharArray(),
			myCallsign.toCharArray(),
			DSTARDefines.EmptyShortCallsign.toCharArray()
		);
		header.setRepeaterControlFlag(flag);

		final BackBoneHeader backbone =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);
		backbone.setDestinationRepeaterID((byte)0xFF);
		backbone.setSendRepeaterID((byte)0xFF);
		backbone.setSendTerminalID((byte)0xFF);

		packets.add(
			new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, new DVPacket(backbone, header))
		);

		final NewDataSegmentEncoder encoder = new NewDataSegmentEncoder();
		if(flag == RepeaterControlFlag.NO_REPLY || flag == RepeaterControlFlag.AUTO_REPLY) {
			encoder.setShortMessage(
				(messageOnSuccess != null && !"".equals(messageOnSuccess)) ? messageOnSuccess : "OK (^o^)b"
			);
		}
		else {
			encoder.setShortMessage(
				(messageOnFail != null && !"".equals(messageOnFail)) ? messageOnFail : "Fail (;_;)"
			);
		}

		encoder.setEnableShortMessage(true);
		encoder.setEnableEncode(voicePacketLength >= DSTARDefines.MaxSequenceNumber);

		byte seq = 0;
		for(int packetCount = 0; packetCount < voicePacketLength; packetCount++) {
			final VoiceAMBE voice = new VoiceAMBE();
			final BackBoneHeader bb = backbone.clone();
			bb.setFrameType(BackBoneHeaderFrameType.VoiceData);
			bb.setSequenceNumber((byte)seq);

			final DVPacket voicePacket = new DVPacket(bb, voice);

			if((packetCount + 1) >= voicePacketLength) {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getLastAMBE());
				voicePacket.getVoiceData().setDataSegment(DSTARUtils.getLastSlowdata());

				voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataLastFrame);
			}
			else if((packetCount + 2) >= voicePacketLength) {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
				voicePacket.getVoiceData().setDataSegment(DSTARUtils.getEndSlowdata());

				voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceData);
			}
			else {
				voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
				encoder.encode(voicePacket.getVoiceData().getDataSegment());

				voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceData);
			}

			packets.add(
				new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, voicePacket)
			);

			seq = DSTARUtils.getNextShortSequence(seq);
		}

		return packets;
	}

	/**
	 * 仕様外の幹線ヘッダを可能な限り修正する
	 * @param backbone 幹線ヘッダ
	 * @return 利用可能な幹線ヘッダであればtrue
	 */
	public static boolean fixBackboneHeader(@NonNull final BackBoneHeader backbone) {
		boolean isValid = true;

		if(backbone.getType() == null) {
			isValid = false;
		}

		if(backbone.getFrameType() == null) {
			isValid = false;
		}
		else if(backbone.getFrameType() == BackBoneHeaderFrameType.VoiceDataHeader) {
			backbone.setSequenceNumber((byte)0x0);
		}
		else if(
			backbone.getFrameType() == BackBoneHeaderFrameType.VoiceData ||
			backbone.getFrameType() == BackBoneHeaderFrameType.VoiceDataLastFrame
		) {
			if(backbone.getSequenceNumber() > DSTARDefines.MaxSequenceNumber)
				backbone.setSequenceNumber(DSTARDefines.MaxSequenceNumber);
			else if(backbone.getSequenceNumber() < DSTARDefines.MinSequenceNumber)
				backbone.setSequenceNumber(DSTARDefines.MinSequenceNumber);
		}

		return isValid;
	}

	/**
	 * 仕様外のRFヘッダを可能な限り修正する
	 * @param header RFヘッダ
	 * @return 可能可能なRFヘッダであればtrue
	 */
	public static boolean fixHeader(@NonNull final Header header) {
		boolean isValid = true;

		if(
			header.getRepeaterRouteFlag() == RepeaterRoute.Unknown ||
			header.getRepeaterRouteFlag() == null ||
			header.getRepeaterControlFlag()	== RepeaterControlFlag.Unknown ||
			header.getRepeaterControlFlag() == null
		){
			isValid = false;
		}

		//仕様外のコールサイン文字をスペースで埋める
		header.replaceCallsignsIllegalCharToSpace();

		return isValid;
	}
}
