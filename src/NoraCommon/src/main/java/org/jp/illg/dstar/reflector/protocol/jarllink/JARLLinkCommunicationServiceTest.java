package org.jp.illg.dstar.reflector.protocol.jarllink;

import org.jp.illg.util.FormatUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JARLLinkCommunicationServiceTest {

	@Test
	public void test() {
		final char[] dmonitor140MD5 = "145e0943f342d2f71bf8141eb570c8b2".toCharArray();
		final byte[] buffer = new byte[dmonitor140MD5.length];
		for(int i = 0; i < buffer.length && i < dmonitor140MD5.length; i++)
			buffer[i] = (byte)dmonitor140MD5[i];

		for(int a = 0; a < buffer.length; a += 4) {
			int b = 0;
			for(int i = 0; i < 0x20; i += 8) {
				buffer[a + b] = (byte)(buffer[a + b] ^ (1591874340 >> i));

				b++;
			}
		}

		System.out.println(FormatUtil.bytesToHexDump(buffer));
	}

}
