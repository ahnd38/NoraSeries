package org.jp.illg.dstar.service.repeatername;

import java.util.List;
import java.util.Properties;

import org.jp.illg.dstar.model.defines.RepeaterListImporterType;
import org.jp.illg.dstar.service.repeatername.model.RepeaterData;
import org.jp.illg.util.thread.ThreadProcessResult;

public interface RepeaterListImporter {

	public boolean setProperties(Properties properties);
	public Properties getProperties();

	public ThreadProcessResult processImporter();

	public RepeaterListImporterType getImporterType();

	public boolean startImporter();
	public void stopImporter();

	public boolean hasUpdateRepeaterList();
	public List<RepeaterData> getRepeaterList();
}
