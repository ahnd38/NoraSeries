package org.jp.illg.dstar.service;

import org.jp.illg.util.thread.ThreadProcessResult;

public interface Service extends AutoCloseable{

	public boolean start();

	public void stop();

	public boolean isRunning();

	public ThreadProcessResult processService();
}
