package org.jp.illg.util.thread;

import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ThreadBaseTest {

	private static class TestThread extends ThreadBase{

		public TestThread() {
			super(null, TestThread.class.getSimpleName(), 10L, false);
		}

		@Override
		protected void threadFinalize() {

		}

		@Override
		protected ThreadProcessResult threadInitialize() {
			return ThreadProcessResult.NoErrors;
		}

		@Override
		protected ThreadProcessResult process() {
			return ThreadProcessResult.NoErrors;
		}

	}

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);
	}

	@Test
	public void test() {
		final TestThread thread = new TestThread();
		thread.start();

		try {
			Thread.sleep(60000);

			thread.stop();
		}catch(InterruptedException ex) {}
	}
}
