package org.jp.illg.dstar.service.reflectorname.importers;

import java.io.InputStream;
import java.util.Properties;

import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;
import org.jp.illg.util.thread.ThreadProcessResult;

public interface ReflectorHostsImporter {

	public boolean setProperties(Properties properties);
	public Properties getProperties();

	public ThreadProcessResult processImporter();

	public ReflectorHostsImporterType getImporterType();

	public boolean startImporter();
	public void stopImporter();
	public boolean isRunning();

	public String getTargetName();

	public boolean hasUpdateReflectorHosts();
	public InputStream getReflectorHosts();
}
