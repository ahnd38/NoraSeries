/**
 *
 */
package org.jp.illg.dstar.util;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Locale;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author AHND
 *
 */
@Slf4j
public class DataSegmentDecoder extends DataSegmentProcessor{


	public static enum DataSegmentDecoderResult {
		NoResult,
		SYNC,
		CSQL,
		ShortMessage,
		APRS,
		Header,
		;
	}

	private static enum DecodeStates{
		WAIT_SYNC,
		WAIT_MINIHEADER
	}
	private DecodeStates decodeState;

	private int packetSequence;

	@Getter
	private int csqlCode;


	private final byte[] segmentCache = new byte[6];

	private final byte[] shortMessage = new byte[20];
	private final boolean[] shortMessageBlockReceived = new boolean[4];

	private final char[] aprsMessageBuffer = new char[1024];
	private int aprsMessagePtr;

	@Getter
	private String aprsMessage;

	private final byte[] headerBuffer = new byte[45];
	private int headerPtr;

	@Getter
	private Header header;

	/**
	 *
	 */
	public DataSegmentDecoder() {
		super();

		this.reset();
	}


	public void reset() {
		this.decodeState = DecodeStates.WAIT_SYNC;
		this.packetSequence = 0;
		Arrays.fill(this.segmentCache, (byte)0x00);
		Arrays.fill(this.shortMessage	,(byte)0x00);
		Arrays.fill(this.aprsMessageBuffer,(char)0x00);
		this.aprsMessagePtr = 0;
		Arrays.fill(this.headerBuffer, (byte)0x00);
		this.headerPtr = 0;
		this.header = null;
	}

	public String getShortMessageString() {
		String shortMessage = "";
		try {
			shortMessage =
				String.format(Locale.JAPANESE, "%-20s", new String(this.shortMessage, "SJIS"));
		}catch(UnsupportedEncodingException ex) {
			throw new RuntimeException(ex);
		}

		return shortMessage;
	}

	public char[] getShortMessage() {
		final char[] shortMessage = new char[DSTARDefines.DvShortMessageLength];
		Arrays.fill(shortMessage, ' ');
		final String shortMessageString = getShortMessageString();
		for(int i = 0; i < shortMessageString.length(); i++)
			shortMessage[i] = shortMessageString.charAt(i);

		return shortMessage;
	}

	public DataSegmentDecoderResult decode(byte[] dataSegment) {
		if(dataSegment == null || dataSegment.length != 3)
			return DataSegmentDecoderResult.NoResult;

		DataSegmentDecoderResult result = DataSegmentDecoderResult.NoResult;


		switch(this.decodeState) {
		case WAIT_SYNC:
			if(Arrays.equals(syncCode, dataSegment)) {
				this.packetSequence++;
				this.decodeState = DecodeStates.WAIT_MINIHEADER;

				result = DataSegmentDecoderResult.SYNC;
			}else {
				this.packetSequence = 0;
			}
			Arrays.fill(this.segmentCache, (byte)0x00);
			Arrays.fill(this.shortMessage, (byte)0x00);
			break;

		case WAIT_MINIHEADER:
			if((this.packetSequence % 2) == 1) {
				//magic number xor
				for(int index = 0;index < this.segmentCache.length && index < magicCode.length;index++) {
					this.segmentCache[index] = (byte)(dataSegment[index] ^ magicCode[index]);
				}
			}else {
				for(int index = 0;(3 + index) < this.segmentCache.length && index < magicCode.length;index++) {
					this.segmentCache[3 + index] = (byte)(dataSegment[index] ^ magicCode[index]);
				}

				switch(this.segmentCache[0]) {
				// Short Message
				case 0x40:
					for(int index = 0;index < 5;index++)
						this.shortMessage[index] = (byte)this.segmentCache[1 + index];

					shortMessageBlockReceived[0] = true;
					break;
				case 0x41:
					for(int index = 0;index < 5;index++)
						this.shortMessage[5 + index] = (byte)this.segmentCache[1 + index];

					shortMessageBlockReceived[1] = true;
					break;
				case 0x42:
					for(int index = 0;index < 5;index++)
						this.shortMessage[10 + index] = (byte)this.segmentCache[1 + index];

					shortMessageBlockReceived[2] = true;
					break;
				case 0x43:
					for(int index = 0;index < 5;index++)
						this.shortMessage[15 + index] = (byte)this.segmentCache[1 + index];

					shortMessageBlockReceived[3] = true;

					boolean allBlockReceived = true;
					for(final boolean received : shortMessageBlockReceived) {
						if(!received) {allBlockReceived = false;}
					}

					if(allBlockReceived) {
						result = DataSegmentDecoderResult.ShortMessage;

						for(int i = 0; i < shortMessageBlockReceived.length; i++) {
							shortMessageBlockReceived[i] = false;
						}
					}
					break;

				//CSQL
				case (byte) 0xC2:
					this.csqlCode = (((this.segmentCache[2] & 0xF0) >>>4) * 10) + (this.segmentCache[2] & 0x0F);
					result = DataSegmentDecoderResult.CSQL;
					break;

				//APRS Message
				case 0x31:
				case 0x32:
				case 0x33:
				case 0x34:
				case 0x35:
					int length = this.segmentCache[0] & 0x0f;
					if(length <= 0) {break;}
					boolean overflow = (aprsMessagePtr + length) > aprsMessageBuffer.length;
					boolean end = false;
					for(int i = 0; i < length; i++) {
						if(segmentCache[1 + i] == '\r' || segmentCache[1 + i] == '\n') {end = true;}
					}

					for(
						int index = 0;
						index < length && index < 5 && aprsMessagePtr < aprsMessageBuffer.length;
						index++
					) {
						aprsMessageBuffer[aprsMessagePtr++] = (char)segmentCache[1 + index];
					}

					if(!overflow && end && aprsMessagePtr >= 2) {
						aprsMessage = String.valueOf(aprsMessageBuffer, 0, aprsMessagePtr);
//						aprsMessage = aprsMessage.replaceAll("[\r\n]", "");
						if(aprsMessage.length() >= 1)
							result = DataSegmentDecoderResult.APRS;
					}
					if(end) {
						Arrays.fill(this.aprsMessageBuffer,(char)0x00);
						aprsMessagePtr = 0;
					}

					break;

				//Header
				case 0x51:{
					addHeaderBuffer();

					byte[] sortedHeader = generateSortedHeaderBuffer(new byte[this.headerBuffer.length]);

					Header header = null;
					if((header = extractHeader(sortedHeader)) != null) {
						this.header = header;
						result = DataSegmentDecoderResult.Header;
					}

					break;
				}

				case 0x55:
					addHeaderBuffer();
					break;

				default:
					break;
				}
			}

			if(this.packetSequence >= 0x14) {
				this.packetSequence = 0;
				this.decodeState = DecodeStates.WAIT_SYNC;
			}else {
				this.packetSequence++;
			}
			break;
		}

		switch(result) {
		case ShortMessage:
			if(log.isDebugEnabled()) {
				log.debug("Short message received..." + getShortMessageString());
			}
			break;

		case CSQL:
			if(log.isDebugEnabled()){
				log.debug(
						"CSQL code detect...code:" +
								String.format(Locale.getDefault(), "%02d", getCsqlCode())
				);
			}
			break;

		case APRS:
			if(log.isDebugEnabled()) {
				log.debug("APRS message received...\n    " + getAprsMessage());
			}
			break;

		case Header:
			if(log.isDebugEnabled()) {
				log.debug("Header received...\n" + getHeader().toString(4));
			}
			break;

			default:
				break;
		}

		return result;
	}

	private void addHeaderBuffer() {
		log.trace("Receive slow header segment..." + FormatUtil.bytesToHex(this.segmentCache));
		for(int i = 1; i < this.segmentCache.length; i++) {
			this.headerBuffer[this.headerPtr++] = this.segmentCache[i];

			if(this.headerPtr >= this.headerBuffer.length) {this.headerPtr = 0;}
		}
	}

	private byte[] generateSortedHeaderBuffer(byte[] dst) {
		assert dst != null && dst.length == this.headerBuffer.length;

		for(int dstPtr = 0; dstPtr < dst.length; dstPtr++) {
			dst[dstPtr] = this.headerBuffer[this.headerPtr++];

			if(this.headerPtr >= this.headerBuffer.length) {this.headerPtr = 0;}
		}

		return dst;
	}

	private Header extractHeader(byte[] src) {
		assert src != null && src.length == this.headerBuffer.length;

		int calcCRC = DSTARCRCCalculator.calcCRC(src, 39);
		int srcCRC = ((src[40] << 8) & 0xFF00) | (src[39] & 0xFF);

		if(log.isDebugEnabled()) {
			log.debug(
				"Try extract slow data header...ReceiveCRC:" + String.format("%04X", srcCRC) +
				"/CalcCRC:" + String.format("%04X", calcCRC) +
				(calcCRC != srcCRC? "[FAILED]" : "[MATCH]") + "\n" +
				"    SourceBuffer:" + FormatUtil.bytesToHex(src)
				);
		}

		if(calcCRC != srcCRC) {return null;}

		Header header = new Header();

		for(int index = 0; index < src.length; index++) {
			switch(index) {
			case 0:case 1:case 2:
				header.getFlags()[index - 0] = (byte)src[index];
				break;
			case 3:case 4:case 5:case 6:
			case 7:case 8:case 9:case 10:
				header.getRepeater2Callsign()[index - 3] = (char)src[index];
				break;
			case 11:case 12:case 13:case 14:
			case 15:case 16:case 17:case 18:
				header.getRepeater1Callsign()[index - 11] = (char)src[index];
				break;
			case 19:case 20:case 21:case 22:
			case 23:case 24:case 25:case 26:
				header.getYourCallsign()[index - 19] = (char)src[index];
				break;
			case 27:case 28:case 29:case 30:
			case 31:case 32:case 33:case 34:
				header.getMyCallsign()[index - 27] = (char)src[index];
				break;
			case 35:case 36:case 37:case 38:
				header.getMyCallsignAdd()[index - 35] = (char)src[index];
				break;
			case 39:case 40:
				header.getCrc()[index - 39] = (byte)src[index];
			}
		}

		return header;
	}
}
