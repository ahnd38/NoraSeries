package org.jp.illg.dstar.routing.service.jptrust;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.routing.service.jptrust.model.PositionQueryResponse;
import org.jp.illg.util.logback.LogbackUtil;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JpTrustLogTransporterTest {

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);
	}

	@Test
	public void test() {
		final JpTrustLogTransporter transporter = new JpTrustLogTransporter(new ThreadUncaughtExceptionListener() {
			@Override
			public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {
				// TODO 自動生成されたメソッド・スタブ

			}

			@Override
			public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
				// TODO 自動生成されたメソッド・スタブ

			}
		});

		transporter.setEnableLogTransport(true);
		transporter.setGatewayCallsign("JI1ROJ G");
		transporter.setTrustServerAddress("localhost");
		try {
			transporter.start();

			for(int i = 0; i < 60; i++) {
				final PositionQueryResponse resp = new PositionQueryResponse();
				resp.setRepeater1Callsign("JJ0TPX B".toCharArray());
				resp.setRepeater2Callsign("JJ0TPX G".toCharArray());

				transporter.addLogTransportEntry(
					new Header(
						"JJ0TPX  ".toCharArray(),
						"JI1ROJ Z".toCharArray(),
						"JI1ROJ G".toCharArray(),
						"JI1ROJ  ".toCharArray(),
						String.format("%04d", i).toCharArray()
					),
					resp
				);

				try {
					Thread.sleep(1000);
				}catch(InterruptedException ex) {}
			}

			try {
				Thread.sleep(70000);
			}catch(InterruptedException ex) {}

		}finally {transporter.stop();}
	}

}
