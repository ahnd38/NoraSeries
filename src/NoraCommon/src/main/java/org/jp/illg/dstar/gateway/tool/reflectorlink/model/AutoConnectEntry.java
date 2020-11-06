package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import org.jp.illg.dstar.model.DSTARRepeater;

import com.annimon.stream.Optional;

public interface AutoConnectEntry {

	public boolean isEnable();
	public void setEnable(boolean enable);

	public DSTARRepeater getTargetRepeater();

	public AutoConnectMode getMode();

	public Optional<AutoConnectRequestData> getLinkDataIfAvailable();
}
