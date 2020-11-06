package org.jp.illg.dstar.service.icom.model;

import java.util.List;
import java.util.Properties;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.util.thread.ThreadProcessResult;

public interface RepeaterCommunicationService {

	public boolean start();
	public void stop();
	public boolean isRunning();

	public ICOMRepeaterType getRepeaterControllerType();

	public long getProcessIntervalTimeMillis();

	public boolean serviceInitialize();
	public void serviceFinalize();
	public ThreadProcessResult serviceProcess();

	public DSTARPacket readPacket();
	public boolean hasReadPacket();

	public boolean writePacket(DSTARPacket packet);

	public List<String> getManagementRepeaterCallsigns();

	public boolean setProperties(Properties prop);
	public Properties getProperties();
}
