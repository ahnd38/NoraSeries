package org.jp.illg.dstar.util;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.PhoneticConvertions;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.ambe.AMBEBinaryFileReader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DvVoiceTool {

	private DvVoiceTool() {}

	public static boolean generateVoiceCallsign(
			VoiceCharactors voiceCharactor, char[] callsign, ByteBuffer buffer, boolean enableModule
	) {
		if(callsign == null || callsign.length < DSTARDefines.CallsignFullLength) {
			log.warn("Bad callsign length." + (callsign != null ? String.valueOf(callsign):""));
			return false;
		}else if(buffer == null) {
			log.warn("Buffer is null.");
			return false;
		}

		//キャラクタが指定されていなければデフォルト指定
		if(voiceCharactor == null) {voiceCharactor = VoiceCharactors.KizunaAkari;}

		char[] srcCallsign;
		if(enableModule) {
				srcCallsign = callsign;
		}
		else{
			Matcher matcher = Pattern.compile("^([0-9A-Z]*)[ ]([A-Z ])$").matcher(String.valueOf(callsign));
			if(matcher.matches() && matcher.groupCount() >= 2){
				srcCallsign = matcher.group(1).toCharArray();
			}
			else
				return false;
		}

		boolean success = true;
		for(int index = 0; index < DSTARDefines.CallsignFullLength && index < srcCallsign.length; index++) {
			char c = srcCallsign[index];

			byte[] data = null;
			if(c != ' ') {
				String readFilename = null;
				PhoneticConvertions phonetic = PhoneticConvertions.Unknown;
				if(
					index == (DSTARDefines.CallsignFullLength - 1) &&
					(phonetic = PhoneticConvertions.getTypeByAlphabet(c)) != PhoneticConvertions.Unknown
				) {
					readFilename = phonetic.getPhoneticCode();
				}
				else {readFilename = String.valueOf(c);}

				if(SystemUtil.IS_Android) {
					data = AMBEBinaryFileReader.readAMBEBinaryFileFromAndroidAsset(
						voiceCharactor.getVoiceDataAndroidAssetPath() + readFilename + ".ambe"
					);
				}
				else {
					data = AMBEBinaryFileReader.readAMBEBinaryFile(
						voiceCharactor.getVoiceDataDirectoryPath() + readFilename + ".ambe"
					);
				}

				if(data != null) {
					int writeBytes = buffer.remaining() > data.length? data.length : buffer.remaining();
					buffer.put(data, 0, writeBytes);
				}else {success = false; continue;}
			}
		}

		return success;
	}

	/**
	 * ファイルからボイスデータを作成する
	 * @param voiceCharactor ボイスキャラクタ
	 * @param fileName ファイル名
	 * @param buffer ボイスデータ格納バッファ
	 * @return 正常終了ならtrue
	 *
	 * fileNameに半角スペース1文字を与えると、無音データを作成する
	 */
	public static boolean generateVoiceByFilename(VoiceCharactors voiceCharactor, String fileName, ByteBuffer buffer) {
		if(fileName == null || "".equals(fileName)) {
			if(log.isWarnEnabled())
				log.warn("Filename is not null and nothing.");

			return false;
		}else if(buffer == null) {
			if(log.isWarnEnabled())
				log.warn("Buffer is null.");

			return false;
		}

		if(voiceCharactor == null) {voiceCharactor = VoiceCharactors.Silent;}

		byte[] data = null;
		if(!fileName.equals(" ") && voiceCharactor != VoiceCharactors.Silent) {
			if(SystemUtil.IS_Android){
				data = AMBEBinaryFileReader.readAMBEBinaryFileFromAndroidAsset(
					voiceCharactor.getVoiceDataAndroidAssetPath() + fileName + ".ambe"
				);
			}
			else{
				data = AMBEBinaryFileReader.readAMBEBinaryFile(
					voiceCharactor.getVoiceDataDirectoryPath() + fileName + ".ambe"
				);
			}
		}
		else {
			data = new byte[DSTARDefines.VoiceSegmentLength * 10];
			for(int p = 0; p < data.length; p += DSTARDefines.VoiceSegmentLength)
				for(int i = 0; i < DSTARUtils.getNullAMBE().length; i++) {data[p + i] = DSTARUtils.getNullAMBE()[i];}
		}

		if(data != null) {
			int writeBytes = buffer.remaining() > data.length? data.length : buffer.remaining();
			buffer.put(data, 0, writeBytes);

			return true;
		}else {return false;}
	}

	public static Queue<DVPacket> generateVoicePacketFromBuffer(
		@NonNull final ByteBuffer srcBuffer,
		final int frameID,
		@NonNull final NewDataSegmentEncoder encoder
	) {
		final Queue<DVPacket> result = new LinkedList<>();

		byte sequence = 0;
		while(srcBuffer.remaining() >= DSTARDefines.VoiceSegmentLength) {
			final VoiceAMBE voice = new VoiceAMBE();

			for(int index = 0; index < voice.getVoiceSegment().length && srcBuffer.hasRemaining(); index++)
				voice.getVoiceSegment()[index] = srcBuffer.get();

			if(encoder != null)
				encoder.encode(voice.getDataSegment());

			final BackBoneHeader backbone =
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID);
			backbone.setSequenceNumber(sequence);

			final DVPacket packet = new DVPacket(backbone, voice);

			result.add(packet);

			sequence = DSTARUtils.getNextShortSequence(sequence);
		}

		result.add(DSTARUtils.createPreLastVoicePacket(frameID, sequence));
		sequence = DSTARUtils.getNextShortSequence(sequence);
		result.add(DSTARUtils.createLastVoicePacket(frameID, sequence));

		return result;
	}
}
