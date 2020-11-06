package org.jp.illg.util;

import java.nio.ByteBuffer;

import org.apache.commons.lang3.time.DateFormatUtils;

public class FormatUtil {

	private static final String defaultDateFormat = "yyyy/MM/dd HH:mm:ss.SSS";

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String byteToHex(byte data) {

		char[] hexChars = new char[2];

		int v = data & 0xFF;
		hexChars[0] = hexArray[v >>> 4];
		hexChars[1] = hexArray[v & 0x0F];

		return new String(hexChars);
	}

	public static String bytesToHex(byte[] bytes) {
		if(bytes == null) {return "";}

		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static String bytesToHex(byte[] bytes,int limit) {
		if(bytes == null) {return "";}

		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length && j < limit; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static String byteBufferToHex(ByteBuffer buffer) {
		if(buffer == null) {return "";}

		buffer.mark();
		char[] hexChars = new char[buffer.remaining() * 2];
		for ( int j = 0; buffer.hasRemaining(); j++ ) {
			int v = buffer.get() & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		buffer.reset();
		return new String(hexChars);
	}

	public static String byteBufferToHex(ByteBuffer buffer, int limit) {
		if(buffer == null) {return "";}

		buffer.mark();
		char[] hexChars = new char[buffer.remaining() * 2];
		for ( int j = 0; buffer.hasRemaining() && j < limit; j++ ) {
			int v = buffer.get() & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		buffer.reset();
		return new String(hexChars);
	}

	public static String bytesToHexDump(byte[] bytes) {
		return bytesToHexDump(bytes, 0);
	}

	public static String bytesToHexDump(byte[] bytes, int indentLevel) {
		if(bytes == null) {return "";}

		return bytesToHexDump(bytes, -1, indentLevel);
	}

	public static String bytesToHexDump(byte[] bytes, int limit, int indentLevel) {
		if(bytes == null) {return "";}

		return byteBufferToHexDump(ByteBuffer.wrap(bytes), limit, indentLevel);
	}

	public static String byteBufferToHexDump(ByteBuffer buffer) {
		return byteBufferToHexDump(buffer, -1, 0);
	}

	public static String byteBufferToHexDump(ByteBuffer buffer, int indentLevel) {
		return byteBufferToHexDump(buffer, -1, indentLevel);
	}

	public static String byteBufferToHexDump(ByteBuffer buffer, int limit, int indentLevel) {
		if(buffer == null) {return "";}

		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		final StringBuilder sb = new StringBuilder();

		buffer.mark();

		int byteLength = 0;
		if(limit < 0 || buffer.remaining() < limit)
			byteLength = buffer.remaining();
		else
			byteLength = limit;

		int lines = byteLength / 16 + 1;

		 for(int l = 0; l < lines; l++) {
			sb.append(indent);

			int lineBytes = byteLength - (l * 16);
			if(lineBytes > 16) {lineBytes = 16;}

			final byte[] bytes = new byte[lineBytes];
			for(int i = 0; i < lineBytes && buffer.hasRemaining(); i++) {bytes[i] = buffer.get();}

			for(int i = 0; i < 16; i++) {
				sb.append(i < bytes.length ? byteToHex(bytes[i]) : "  ");
				sb.append(" ");

				if(i == 7) {sb.append(" ");}
			}

			sb.append("  ");

			for(int i = 0; i < 16; i++) {
				String hex =
					i < bytes.length ?
						String.valueOf((bytes[i] >= (byte)0x20 && bytes[i] <= (byte)0x7E) ? (char)bytes[i] : (char)0x20) : " ";
				if(hex == null || "".equals(hex)) {hex = " ";}

				sb.append(hex);
			}

			if((l + 1) < lines) {sb.append("\n");}
		}

		buffer.reset();

		return sb.toString();
	}

	public static String dateFormat(String dateFormat, long timestamp) {
		return DateFormatUtils.format(timestamp, dateFormat);
	}

	public static String dateFormat(long timestamp) {
		return dateFormat(defaultDateFormat, timestamp);
	}

	public static boolean addIndent(final StringBuilder sb, int indentLevel) {
		if(sb == null || indentLevel < 0) {return false;}

		for(int c = 0; c < indentLevel; c++) {sb.append(' ');}

		return true;
	}
}
