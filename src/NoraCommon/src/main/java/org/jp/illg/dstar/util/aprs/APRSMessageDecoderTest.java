package org.jp.illg.dstar.util.aprs;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.jp.illg.dstar.util.aprs.APRSMessageDecoder.APRSMessageDecoderResult;
import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class APRSMessageDecoderTest {


	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testNMEAGPRMCMessage() {
		final String gpsMessage = "$GPRMC,131921.00,A,3552.4427,N,14000.8616,E,0.000,-0.000,050419,,E,A*3E\r\n";

		final APRSMessageDecoderResult result =
			APRSMessageDecoder.decodeDPRS(gpsMessage);

		assertThat(result, is(notNullValue()));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testNMEAGPGGAMessage() {
		final String gpsMessage = "$GPGGA,085120.307,3541.1493,N,13945.3994,E,1,08,1.0,6.9,M,35.9,M,,0000*5E\r\n";

		final APRSMessageDecoderResult result =
			APRSMessageDecoder.decodeDPRS(gpsMessage);

		assertThat(result, is(notNullValue()));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testNMEAGPGGAMessageID51() {
		final String gpsMessage = "$GPGGA,041857.00,000.000,N,000.000,E,1,3,0.0,,M,,M,,*50\r\n";

		final APRSMessageDecoderResult result =
			APRSMessageDecoder.decodeDPRS(gpsMessage);

		assertThat(result, is(notNullValue()));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testDPRSMessage() {
		final String gpsMessage = "$$CRCABF4,JH1RDA-A>API51,DSTAR*:!3552.43N/14000.85E>/\r";

		final APRSMessageDecoderResult result =
			APRSMessageDecoder.decodeDPRS(gpsMessage);

		assertThat(result, is(notNullValue()));
	}
}
