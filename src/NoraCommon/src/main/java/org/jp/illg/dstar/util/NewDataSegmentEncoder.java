package org.jp.illg.dstar.util;

import static org.jp.illg.dstar.util.DataSegmentProcessor.*;

import java.util.Arrays;
import java.util.Locale;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NewDataSegmentEncoder {

	@Getter
	@Setter
	private boolean enableCodeSquelch;

	@Getter
	@Setter
	private int codeSquelchCode;

	@Getter
	@Setter
	private boolean enableShortMessage;

	@Getter
	private final char[] shortMessage = new char[DSTARDefines.DvShortMessageLength];

	@Setter
	@Getter
	private boolean enableEncode;

	@Setter
	@Getter
	private String aprsMessage;

	@Setter
	@Getter
	private boolean enableAprsMessage;

	@Setter
	@Getter
	private Header header;

	@Setter
	@Getter
	private boolean enableHeader;

	private int shortSequence;

	private byte[] interleavedBlockHeader;
	private byte[] interleavedBlockBody;

	private int interleavedBlockHeaderPtr;
	private int interleavedBlockBodyPtr;

	private final byte[] encodedCodeSquelch = new byte[DSTARDefines.DataSegmentLength * 2 * 1];
	private final byte[] encodedShortMessage = new byte[DSTARDefines.DataSegmentLength * 2 * 4];
	private byte[] encodedAprsMessage;
	private final byte[] encodedHeader = new byte[DSTARDefines.DataSegmentLength * 2 * 9];

	public NewDataSegmentEncoder(){
		super();

		reset();
	}

	public void reset(){
		setEnableCodeSquelch(false);
		setCodeSquelchCode(0);
		setEnableShortMessage(false);
		setShortMessage("");
		setHeader(null);
		setEnableHeader(false);

		shortSequence = 0x0;
		interleavedBlockHeader = null;
		interleavedBlockHeaderPtr = 0;
		interleavedBlockBody = null;
		interleavedBlockBodyPtr = 0;
	}

	public void setShortMessage(final String shortMessage){
		if(shortMessage != null){
			Arrays.fill(getShortMessage(), ' ');

			for(int i = 0; i < shortMessage.length() && i < getShortMessage().length; i++)
				getShortMessage()[i] = shortMessage.charAt(i);
		}
	}

	public boolean encode(byte[] dataSegment) {
		if (dataSegment == null || dataSegment.length != 3) {
			return false;
		}

		if(
				interleavedBlockHeader == null ||
				interleavedBlockBody == null
		){buildBlock();}

		if(shortSequence == 0x0){
			for(int i = 0; i < dataSegment.length && i < syncCode.length; i++)
				dataSegment[i] = syncCode[i];
		}
		else{
			if(
					interleavedBlockHeader != null && interleavedBlockHeader.length >= 6 &&
					interleavedBlockHeaderPtr < interleavedBlockHeader.length
			){
				dataSegment[0] =
						(byte)(interleavedBlockHeader[interleavedBlockHeaderPtr++] ^ magicCode[0]);
				dataSegment[1] =
						(byte)(interleavedBlockHeader[interleavedBlockHeaderPtr++] ^ magicCode[1]);
				dataSegment[2] =
						(byte)(interleavedBlockHeader[interleavedBlockHeaderPtr++] ^ magicCode[2]);
			}
			else if(
					interleavedBlockBody != null && interleavedBlockBody.length >= 6
			){
				if(interleavedBlockBody.length <= interleavedBlockBodyPtr) {
					interleavedBlockBodyPtr = 0;
				}

				dataSegment[0] =
						(byte)(interleavedBlockBody[interleavedBlockBodyPtr++] ^ magicCode[0]);
				dataSegment[1] =
						(byte)(interleavedBlockBody[interleavedBlockBodyPtr++] ^ magicCode[1]);
				dataSegment[2] =
						(byte)(interleavedBlockBody[interleavedBlockBodyPtr++] ^ magicCode[2]);
			}
			else{
				dataSegment[0] =
						(byte)(0x66 ^ magicCode[0]);
				dataSegment[1] =
						(byte)(0x66 ^ magicCode[1]);
				dataSegment[2] =
						(byte)(0x66 ^ magicCode[2]);
			}
		}

		shortSequence = (shortSequence + 1) % 0x15;

		return true;
	}

	private void buildBlock() {
		if(isEnableHeader()){encodeHeader();}
		if(isEnableShortMessage()){encodeShortMessage();}
		if(isEnableAprsMessage()){encodeAprsMessage();}
		if(isEnableCodeSquelch()){encodeCodeSquelch();}

		buildBlockHeader();
		buildBlockBody();
	}

	private void buildBlockHeader() {
		final boolean validAprs =
			isEnableAprsMessage() && encodedAprsMessage != null && encodedAprsMessage.length > 0;

		int length =
			(isEnableShortMessage() ? encodedShortMessage.length : 0) +
			(validAprs ? encodedAprsMessage.length : 0);
		int frameSequence = 0;

		if(isEnableCodeSquelch()) {
			frameSequence =
					length != 0 ?
							(int)Math.ceil(((double)length / (double)(DSTARDefines.DataSegmentLength * 2 * 9))) : 1;
		}
		else{
			frameSequence =
					length != 0 ?
							(int)Math.ceil(((double)length / (double)(DSTARDefines.DataSegmentLength * 2 * 10))) : 0;
		}

		length = frameSequence * (DSTARDefines.DataSegmentLength * 2 * 10);

		interleavedBlockHeader = new byte[length];

		if(length > 0){
			if(log.isTraceEnabled())
				log.trace("Building interleaved header data.");

			Arrays.fill(interleavedBlockHeader, (byte)0x66);

			final StringBuffer sb =
					log.isTraceEnabled() ? new StringBuffer("[Interleaved Slowdata Segment(Header)]\n") : null;

			int ptr = 0;
			int shortMessagePtr = 0;
			int aprsMessagePtr = 0;
			for(int frameSeqNo = 0; frameSeqNo < frameSequence; frameSeqNo++){
				if(log.isTraceEnabled())
					sb.append(String.format(Locale.getDefault(), "    [%03d] ", frameSeqNo));

				for(int blockNo = 0; blockNo < 10; blockNo++) {
					if(isEnableCodeSquelch() && blockNo == 0) {
						if(log.isTraceEnabled()){sb.append(" C");}

						for(int i = 0; i < encodedCodeSquelch.length; i++)
							interleavedBlockHeader[ptr++] = encodedCodeSquelch[i];
					}
					else if(isEnableShortMessage() && shortMessagePtr < encodedShortMessage.length) {
						if(log.isTraceEnabled()){sb.append(" T");}

						for(int i = 0; i < 6; i++)
							interleavedBlockHeader[ptr++] = encodedShortMessage[shortMessagePtr++];
					}
					else if(
							isEnableAprsMessage() && validAprs && encodedAprsMessage.length >= 6 &&
							aprsMessagePtr < encodedAprsMessage.length
					){
						if(log.isTraceEnabled()){sb.append(" A");}

						for(int i = 0; i < 6; i++)
							interleavedBlockHeader[ptr++] = encodedAprsMessage[aprsMessagePtr++];
					}
					else {
						if(log.isTraceEnabled()){sb.append(" F");}

						for(int i = 0; i < 6; i++)
							interleavedBlockHeader[ptr++] = (byte)0x66;
					}
				}

				if(log.isTraceEnabled()){sb.append("\n");}
			}
			if(log.isTraceEnabled()) {log.trace(sb.toString());}

		}
	}

	private void buildBlockBody() {
		final boolean validAprs =
				isEnableAprsMessage() && encodedAprsMessage != null && encodedAprsMessage.length > 0;

		int length =
				(isEnableHeader() ? encodedHeader.length : 0) +
				(validAprs ? encodedAprsMessage.length : 0);
		int frameSequence = 0;

		if(isEnableCodeSquelch()) {
			frameSequence =
					length != 0 ?
							(int)Math.ceil(((double)length / (double)(DSTARDefines.DataSegmentLength * 2 * 9))) : 1;
		}
		else{
			frameSequence =
					length != 0 ?
							(int)Math.ceil(((double)length / (double)(DSTARDefines.DataSegmentLength * 2 * 10))) : 0;
		}

		length = frameSequence * (DSTARDefines.DataSegmentLength * 2 * 10);

		interleavedBlockBody = new byte[length];

		if(length > 0){
			if(log.isTraceEnabled())
				log.trace("Building interleaved body data.");

			Arrays.fill(interleavedBlockBody, (byte)0x66);

			final StringBuffer sb =
				log.isTraceEnabled() ? new StringBuffer("[Interleaved Slowdata Segment(Body)]\n") : null;

			int ptr = 0;
			int headerPtr = 0;
			int aprsMessagePtr = 0;
			for(int frameSeqNo = 0; frameSeqNo < frameSequence; frameSeqNo++){
				if(log.isTraceEnabled())
					sb.append(String.format(Locale.getDefault(), "    [%03d] ", frameSeqNo));

				for(int blockNo = 0; blockNo < 10; blockNo++) {
					if(isEnableCodeSquelch() && blockNo == 0) {
						if(log.isTraceEnabled()){sb.append(" C");}

						for(int i = 0; i < encodedCodeSquelch.length; i++)
							interleavedBlockBody[ptr++] = encodedCodeSquelch[i];
					}
					else if(isEnableHeader() && headerPtr < encodedHeader.length) {
						if(log.isTraceEnabled()){sb.append(" H");}

						for(int i = 0; i < 6; i++)
							interleavedBlockBody[ptr++] = encodedHeader[headerPtr++];
					}
					else if(
							isEnableAprsMessage() && encodedAprsMessage != null && encodedAprsMessage.length >= 6 &&
							aprsMessagePtr < encodedAprsMessage.length
					){
						if(log.isTraceEnabled()){sb.append(" A");}

						for(int i = 0; i < 6; i++)
							interleavedBlockBody[ptr++] = encodedAprsMessage[aprsMessagePtr++];
					}
					else {
						if(log.isTraceEnabled()){sb.append(" F");}

						for(int i = 0; i < 6; i++)
							interleavedBlockBody[ptr++] = (byte)0x66;
					}
				}

				if(log.isTraceEnabled()){sb.append("\n");}
			}
			if(log.isTraceEnabled()) {log.trace(sb.toString());}

		}
	}

	private void encodeCodeSquelch() {
		final byte hex = getCodeSquelchCode() != 0 ?
				(byte)(((getCodeSquelchCode() / 10) * 16) + (getCodeSquelchCode() % 10)) : (byte)0;

		encodedCodeSquelch[0] = (byte)0xC2;
		encodedCodeSquelch[1] = hex;
		encodedCodeSquelch[2] = hex;
		encodedCodeSquelch[3] = (byte)0x66;
		encodedCodeSquelch[4] = (byte)0x66;
		encodedCodeSquelch[5] = (byte)0x66;
	}

	private void encodeShortMessage() {
		int charPtr = 0;
		for(int i = 0; i <= 3; i++){
			encodedShortMessage[i * 6    ] = (byte)(0x40 + i);
			encodedShortMessage[i * 6 + 1] = (byte)getShortMessage()[charPtr++];
			encodedShortMessage[i * 6 + 2] = (byte)getShortMessage()[charPtr++];
			encodedShortMessage[i * 6 + 3] = (byte)getShortMessage()[charPtr++];
			encodedShortMessage[i * 6 + 4] = (byte)getShortMessage()[charPtr++];
			encodedShortMessage[i * 6 + 5] = (byte)getShortMessage()[charPtr++];
		}
	}

	private void encodeHeader() {
		Arrays.fill(encodedHeader, (byte)0x66);

		if(getHeader() != null){
			getHeader().calcCRC();

			encodedHeader[0] = (byte)0x55;
			encodedHeader[1] = getHeader().getFlags()[0];
			encodedHeader[2] = getHeader().getFlags()[1];
			encodedHeader[3] = getHeader().getFlags()[2];
			encodedHeader[4] = (byte)getHeader().getRepeater2Callsign()[0];
			encodedHeader[5] = (byte)getHeader().getRepeater2Callsign()[1];

			encodedHeader[6] = (byte)0x55;
			encodedHeader[7] = (byte)getHeader().getRepeater2Callsign()[2];
			encodedHeader[8] = (byte)getHeader().getRepeater2Callsign()[3];
			encodedHeader[9] = (byte)getHeader().getRepeater2Callsign()[4];
			encodedHeader[10] = (byte)getHeader().getRepeater2Callsign()[5];
			encodedHeader[11] = (byte)getHeader().getRepeater2Callsign()[6];

			encodedHeader[12] = (byte)0x55;
			encodedHeader[13] = (byte)getHeader().getRepeater2Callsign()[7];
			encodedHeader[14] = (byte)getHeader().getRepeater1Callsign()[0];
			encodedHeader[15] = (byte)getHeader().getRepeater1Callsign()[1];
			encodedHeader[16] = (byte)getHeader().getRepeater1Callsign()[2];
			encodedHeader[17] = (byte)getHeader().getRepeater1Callsign()[3];

			encodedHeader[18] = (byte)0x55;
			encodedHeader[19] = (byte)getHeader().getRepeater1Callsign()[4];
			encodedHeader[20] = (byte)getHeader().getRepeater1Callsign()[5];
			encodedHeader[21] = (byte)getHeader().getRepeater1Callsign()[6];
			encodedHeader[22] = (byte)getHeader().getRepeater1Callsign()[7];
			encodedHeader[23] = (byte)getHeader().getYourCallsign()[0];

			encodedHeader[24] = (byte)0x55;
			encodedHeader[25] = (byte)getHeader().getYourCallsign()[1];
			encodedHeader[26] = (byte)getHeader().getYourCallsign()[2];
			encodedHeader[27] = (byte)getHeader().getYourCallsign()[3];
			encodedHeader[28] = (byte)getHeader().getYourCallsign()[4];
			encodedHeader[29] = (byte)getHeader().getYourCallsign()[5];

			encodedHeader[30] = (byte)0x55;
			encodedHeader[31] = (byte)getHeader().getYourCallsign()[6];
			encodedHeader[32] = (byte)getHeader().getYourCallsign()[7];
			encodedHeader[33] = (byte)getHeader().getMyCallsign()[0];
			encodedHeader[34] = (byte)getHeader().getMyCallsign()[1];
			encodedHeader[35] = (byte)getHeader().getMyCallsign()[2];

			encodedHeader[36] = (byte)0x55;
			encodedHeader[37] = (byte)getHeader().getMyCallsign()[3];
			encodedHeader[38] = (byte)getHeader().getMyCallsign()[4];
			encodedHeader[39] = (byte)getHeader().getMyCallsign()[5];
			encodedHeader[40] = (byte)getHeader().getMyCallsign()[6];
			encodedHeader[41] = (byte)getHeader().getMyCallsign()[7];

			encodedHeader[42] = (byte)0x55;
			encodedHeader[43] = (byte)getHeader().getMyCallsignAdd()[0];
			encodedHeader[44] = (byte)getHeader().getMyCallsignAdd()[1];
			encodedHeader[45] = (byte)getHeader().getMyCallsignAdd()[2];
			encodedHeader[46] = (byte)getHeader().getMyCallsignAdd()[3];
			encodedHeader[47] = getHeader().getCrc()[0];

			encodedHeader[48] = (byte)0x51;
			encodedHeader[49] = getHeader().getCrc()[1];
			encodedHeader[50] = (byte)0x66;
			encodedHeader[51] = (byte)0x66;
			encodedHeader[52] = (byte)0x66;
			encodedHeader[53] = (byte)0x66;
		}
	}

	private void encodeAprsMessage() {

		final int messageLength = getAprsMessage() != null ? getAprsMessage().length() : 0;
		final int frameLength =
				messageLength != 0 ? (int)Math.ceil((double)messageLength / (double)5) : 0;

		encodedAprsMessage = new byte[frameLength * 6];
		Arrays.fill(encodedAprsMessage, (byte)0x66);

		if(frameLength >= 1) {
			int charPtr = 0;

			do {
				int length = getAprsMessage().length() - charPtr;
				if(length > 5) { length = 5; }

				final int offset = (charPtr != 0 ? charPtr / 5 : 0) * 6;

				encodedAprsMessage[offset] = (byte)(0x30 | length);
				for(int i = 0; i < length; i++) {
					encodedAprsMessage[offset + 1 + i] =
							(byte)getAprsMessage().charAt(charPtr++);
				}
			}while(charPtr < getAprsMessage().length());
		}
	}
}
