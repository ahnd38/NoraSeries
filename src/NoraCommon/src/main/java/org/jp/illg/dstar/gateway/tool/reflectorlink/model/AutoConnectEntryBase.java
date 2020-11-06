package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManagerImpl;
import org.jp.illg.dstar.model.DSTARRepeater;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class AutoConnectEntryBase implements AutoConnectEntry{

	@Getter(AccessLevel.PROTECTED)
	private final ReflectorLinkManagerImpl manager;

	@Getter
	@Setter
	private boolean enable;

	@Getter
	private final String repeaterCallsign;


	public AutoConnectEntryBase(
		final ReflectorLinkManagerImpl manager,
		final String repeaterCallsign
	) {
		super();

		this.manager = manager;
		this.repeaterCallsign = repeaterCallsign;
	}

	public AutoConnectEntryBase(
		final ReflectorLinkManagerImpl manager,
		final String repeaterCallsign,
		final boolean enable
	) {
		this(manager, repeaterCallsign);

		setEnable(enable);
	}

	@Override
	public DSTARRepeater getTargetRepeater() {
		return manager.getRepeater(repeaterCallsign);
	}

	@Override
	public Optional<AutoConnectRequestData> getLinkDataIfAvailable() {
		if(isEnable())
			return getLinkDataIfAvailableInt();
		else
			return Optional.empty();
	}

	public abstract Optional<AutoConnectRequestData> getLinkDataIfAvailableInt();
}
