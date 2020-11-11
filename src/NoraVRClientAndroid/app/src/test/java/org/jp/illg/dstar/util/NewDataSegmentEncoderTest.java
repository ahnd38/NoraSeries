package org.jp.illg.dstar.util;

import org.jp.illg.dstar.model.Header;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class NewDataSegmentEncoderTest {
	
	private NewDataSegmentEncoder encoder;
	private DataSegmentDecoder decoder;
	
	@Before
	public void setUp() throws Exception {
		ShadowLog.stream = System.out;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		lc.stop();
		
		// setup LogcatAppender
		PatternLayoutEncoder encoder2 = new PatternLayoutEncoder();
		encoder2.setContext(lc);
		encoder2.setPattern("[%thread] %msg%n");
		encoder2.start();
		
		LogcatAppender logcatAppender = new LogcatAppender();
		logcatAppender.setContext(lc);
		logcatAppender.setEncoder(encoder2);
		logcatAppender.start();
		
		// add the newly created appenders to the root logger;
		// qualify Logger to disambiguate from org.slf4j.Logger
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.addAppender(logcatAppender);
		root.setLevel(Level.TRACE);
		
		
		encoder = new NewDataSegmentEncoder();
		
		decoder = new DataSegmentDecoder();
	}
	
	private void setHeader(final NewDataSegmentEncoder encoder) {
		final Header header = new Header();
		header.setRepeater1Callsign("RPT1TEST".toCharArray());
		header.setRepeater2Callsign("RPT2TEST".toCharArray());
		header.setYourCallsign("UR  TEST".toCharArray());
		header.setMyCallsign("MY  TEST".toCharArray());
		header.setMyCallsignAdd("TEST".toCharArray());
		header.setFlags(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00});
		encoder.setHeader(header);
	}
	
	private void setShortMessage(final NewDataSegmentEncoder encoder) {
		encoder.setShortMessage("HelloHelloHelloHelloHello");
	}
	
	private void setAprsMessage(final NewDataSegmentEncoder encoder) {
		encoder.setAprsMessage("$GPGGA,132913.00,3553.439500,N,13920.119759,E,2,06,2.3,99.0,M,39.0,M,,*59,JI1ROJ  ,BE  *4E             \r\n");
	}
	
	private void setCodeSquelchCode(final NewDataSegmentEncoder encoder) {
		encoder.setCodeSquelchCode(99);
	}
	
	@Test
	public void encode() {
		final byte[] slowdata = new byte[3];
		
		Map<DataSegmentDecoder.DataSegmentDecoderResult, Boolean> result = null;
		
		encoder.reset();
		setCodeSquelchCode(encoder);
		encoder.setEnableCodeSquelch(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.CSQL
				);
		assertTrue(
				"CSQL not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.CSQL) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.CSQL).booleanValue()
		);
		
		encoder.reset();
		setShortMessage(encoder);
		encoder.setEnableShortMessage(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage
				);
		assertTrue(
				"ShortMessage not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage).booleanValue()
		);
		
		encoder.reset();
		setHeader(encoder);
		encoder.setEnableHeader(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.Header
				);
		assertTrue(
				"Header not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.Header) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.Header).booleanValue()
		);
		
		encoder.reset();
		setAprsMessage(encoder);
		encoder.setEnableAprsMessage(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.APRS
				);
		assertTrue(
				"APRS Message not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.APRS) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.APRS).booleanValue()
		);
		
		encoder.reset();
		setShortMessage(encoder);
		encoder.setEnableShortMessage(true);
		setHeader(encoder);
		encoder.setEnableHeader(true);
		setAprsMessage(encoder);
		encoder.setEnableAprsMessage(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage,
						DataSegmentDecoder.DataSegmentDecoderResult.Header,
						DataSegmentDecoder.DataSegmentDecoderResult.APRS
				);
		assertTrue(
				"Short Message not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage).booleanValue()
		);
		assertTrue(
				"Header not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.Header) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.Header).booleanValue()
		);
		assertTrue(
				"APRS Message not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.APRS) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.APRS).booleanValue()
		);
		
		encoder.reset();
		setCodeSquelchCode(encoder);
		encoder.setEnableCodeSquelch(true);
		setShortMessage(encoder);
		encoder.setEnableShortMessage(true);
		setHeader(encoder);
		encoder.setEnableHeader(true);
		setAprsMessage(encoder);
		encoder.setEnableAprsMessage(true);
		decoder.reset();
		result =
				testSlowdata(
						1000,
						DataSegmentDecoder.DataSegmentDecoderResult.CSQL,
						DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage,
						DataSegmentDecoder.DataSegmentDecoderResult.Header,
						DataSegmentDecoder.DataSegmentDecoderResult.APRS
				);
		assertTrue(
				"CSQL not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.CSQL) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.CSQL).booleanValue()
		);
		assertTrue(
				"Short Message not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.ShortMessage).booleanValue()
		);
		assertTrue(
				"Header not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.Header) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.Header).booleanValue()
		);
		assertTrue(
				"APRS Message not received.",
				result.containsKey(DataSegmentDecoder.DataSegmentDecoderResult.APRS) &&
						result.get(DataSegmentDecoder.DataSegmentDecoderResult.APRS).booleanValue()
		);
	}
	
	private Map<DataSegmentDecoder.DataSegmentDecoderResult, Boolean> testSlowdata(
			final int testSingleFrameCount,
			DataSegmentDecoder.DataSegmentDecoderResult... testTargets
	) {
		final Map<DataSegmentDecoder.DataSegmentDecoderResult, Boolean> results = new HashMap<>();
		
		if(testTargets != null && testTargets.length > 0){
			for(final DataSegmentDecoder.DataSegmentDecoderResult target : testTargets)
				results.put(target, false);
		}
		
		final byte[] slowdata = new byte[3];
		for(int i = 0; i < testSingleFrameCount; i++) {
			encoder.encode(slowdata);
			
			final DataSegmentDecoder.DataSegmentDecoderResult receiveType =
					decoder.decode(slowdata);
			
			if(
					receiveType != null &&
					receiveType != DataSegmentDecoder.DataSegmentDecoderResult.NoResult
			) {results.put(receiveType, true);}
			
			
			boolean complete = true;
			for(final Boolean result : results.values()){
				if(!result){complete = false;}
			}
			if(complete){break;}
		}
		
		return results;
	}
}