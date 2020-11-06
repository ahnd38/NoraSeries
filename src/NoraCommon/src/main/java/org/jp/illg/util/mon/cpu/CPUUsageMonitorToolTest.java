package org.jp.illg.util.mon.cpu;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import oshi.SystemInfo;

@RunWith(JUnit4.class)
public class CPUUsageMonitorToolTest {

	@Test
	public void testTemp() {
		SystemInfo si = new SystemInfo();
		for(int i = 0; i < 10; i++) {
			System.out.println(si.getHardware().getSensors().getCpuTemperature());

			try {
				Thread.sleep(1000);
			}catch(InterruptedException ex) {}
		}
	}

}
