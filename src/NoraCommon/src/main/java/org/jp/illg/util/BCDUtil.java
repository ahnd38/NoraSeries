package org.jp.illg.util;

public class BCDUtil {

	public static byte[] DecToBCDArray(long num) {
		int digits = 0;

		long temp = num;
		while (temp != 0) {
			digits++;
			temp /= 10;
		}

		final int byteLen = digits % 2 == 0 ? digits / 2 : (digits + 1) / 2;
		final boolean isOdd = digits % 2 != 0;

		final byte bcd[] = new byte[byteLen];

		for (int i = 0; i < digits; i++) {
			final byte tmp = (byte) (num % 10);

			if (i == digits - 1 && isOdd)
				bcd[i / 2] = tmp;
			else if (i % 2 == 0)
				bcd[i / 2] = tmp;
			else {
				byte foo = (byte) (tmp << 4);
				bcd[i / 2] |= foo;
			}

			num /= 10;
		}

		for (int i = 0; i < byteLen / 2; i++) {
			final byte tmp = bcd[i];
			bcd[i] = bcd[byteLen - i - 1];
			bcd[byteLen - i - 1] = tmp;
		}

		return bcd;
	}

	public static String BCDtoString(final byte bcd) {
		final StringBuffer sb = new StringBuffer();

		byte high = (byte) (bcd & 0xf0);
		high >>>= (byte) 4;
		high = (byte) (high & 0x0f);
		final byte low = (byte) (bcd & 0x0f);

		sb.append(high);
		sb.append(low);

		return sb.toString();
	}

	public static String BCDtoString(final byte[] bcd) {
		final StringBuffer sb = new StringBuffer();

		for (int i = 0; i < bcd.length; i++)
			sb.append(BCDtoString(bcd[i]));

		return sb.toString();
	}

}
