package org.jp.illg.util.io.fum;

import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileUpdateMonitoringToolTest {

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);
	}
	@Test
	public void test() {

	}

}
