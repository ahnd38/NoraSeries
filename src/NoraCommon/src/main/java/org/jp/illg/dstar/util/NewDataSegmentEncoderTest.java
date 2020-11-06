package org.jp.illg.dstar.util;

import static org.junit.Assert.*;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;

public class NewDataSegmentEncoderTest {

	private NewDataSegmentEncoder instance;

	public NewDataSegmentEncoderTest() {
		super();
	}

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);

		instance = new NewDataSegmentEncoder();
	}

	@Test
	public void testHeaderEncode() {

		instance.setHeader(
			new Header(
				"YOURCALL".toCharArray(),
				"RPT1CALL".toCharArray(), "RPT2CALL".toCharArray(),
				"MY  CALL".toCharArray(), "MY 2".toCharArray()
			)
		);
		instance.setEnableHeader(true);

		final DataSegmentDecoder decoder = new DataSegmentDecoder();

		boolean headerReceived = false;
		final byte[] slowdata = new byte[3];
		for(int c = 0; c < (21 * 10); c++) {
			instance.encode(slowdata);

			final DataSegmentDecoderResult result = decoder.decode(slowdata);
			if(result == DataSegmentDecoderResult.Header) {
				headerReceived = true;
				break;
			}
		}

		assertEquals(headerReceived, true);
	}
}
