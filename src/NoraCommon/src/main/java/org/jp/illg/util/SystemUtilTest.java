package org.jp.illg.util;

import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(JUnit4.class)
public class SystemUtilTest {

	public SystemUtilTest() {}

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);
	}

	@Test
	public void test() {
		log.info("JavaVersion = " + SystemUtil.getJavaVersion());
	}
}
