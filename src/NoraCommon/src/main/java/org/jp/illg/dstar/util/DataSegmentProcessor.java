package org.jp.illg.dstar.util;

import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

public class DataSegmentProcessor {

	protected static final byte[] syncCode = DSTARDefines.SlowdataSyncBytes;

	protected static final byte[] magicCode = new byte[] {(byte)0x70,(byte)0x4F,(byte)0x93};

	public DataSegmentProcessor() {
		super();
	}

	/**
	 * ショートメッセージを生成して返却する
	 * @param message
	 */
	public static char[] buildShortMessage(String message) {
		char[] result = new char[20];
		Arrays.fill(result, (char)0x20);
		if(message != null && !"".equals(message)) {
			for(int index = 0;index < result.length && index < message.length();index++)
				result[index] = message.charAt(index);
		}

		return result;
	}
}
