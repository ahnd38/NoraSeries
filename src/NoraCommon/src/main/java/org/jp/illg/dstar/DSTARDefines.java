package org.jp.illg.dstar;

import org.jp.illg.dstar.util.DSTARUtils;

public class DSTARDefines {

	public static final int CallsignFullLength = 8;
	public static final int CallsignShortLength = 4;

	public static final int DvShortMessageLength = 20;

	public static final String EmptyLongCallsign = DSTARUtils.formatFullLengthCallsign("");
	public static final char[] EmptyLongCallsignChar = EmptyLongCallsign.toCharArray();
	public static final String EmptyShortCallsign = DSTARUtils.formatShortLengthCallsign("");
	public static final char[] EmptyShortCallsignChar = EmptyShortCallsign.toCharArray();

	public static final String EmptyDvShortMessage = String.format("%-" + DvShortMessageLength + "S", "");

	public static final String CQCQCQ = DSTARUtils.formatFullLengthCallsign("CQCQCQ");
	public static final String DIRECT = DSTARUtils.formatFullLengthCallsign("DIRECT");

	public static final int VoiceSegmentLength = 9;
	public static final int DataSegmentLength = 3;
	public static final int DvFrameLength = VoiceSegmentLength + DataSegmentLength;

	public static final int DvFrameIntervalTimeMillis = 20;
	public static final int DvFramePerSeconds = 1000 / DvFrameIntervalTimeMillis;

	public static final byte MaxSequenceNumber = 0x14;
	public static final byte MinSequenceNumber = 0x0;
	public static final byte LastSequenceNumber = MaxSequenceNumber;
	public static final byte PreLastSequenceNumber = LastSequenceNumber - 1;

	public static final byte[] SlowdataSyncBytes =
		new byte[] {(byte)0x55, (byte)0x2D, (byte)0x16};

	public static final byte[] SlowdataNullBytes =
		new byte[] {(byte)0x16, (byte)0x29, (byte)0xF5};

	public static final byte[] SlowdataEndBytes =
		new byte[] {(byte)0x55, (byte)0x55, (byte)0x55};

	public static final byte[] SlowdataLastBytes =
		new byte[] {(byte)0x00, (byte)0x00, (byte)0x00};

	public static final byte[] VoiceSegmentNullBytes =
		new byte[]{(byte)0x9E,(byte)0x8D,(byte)0x32,(byte)0x88,(byte)0x26,(byte)0x1A,(byte)0x3F,(byte)0x61,(byte)0xE8};

	public static final byte[] VoiceSegmentLastBytes =
		new byte[]{(byte)0x55,(byte)0xC8,(byte)0x7A,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};

	public static final byte[] VoiceSegmentLastBytesICOM =
		new byte[]{(byte)0x55,(byte)0xC8,(byte)0x7A,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55};

	public static final byte[] LastPatternBytesICOM =
		new byte[]{(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0xC8,(byte)0x7A};

	public static final String JpTrustServerAddress = "trust.d-star.info";

}
