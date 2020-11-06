package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManagerImpl;

import com.annimon.stream.Optional;

import lombok.Getter;

public class AutoConnectFixed extends AutoConnectEntryBase implements AutoConnectEntry{

	@Getter
	private final String linkRelectorCallsign;


	public AutoConnectFixed(
		final ReflectorLinkManagerImpl manager,
		final String repeaterCallsign,
		final String linkReflectorCallsign
	) {
		super(manager, repeaterCallsign, true);

		this.linkRelectorCallsign = linkReflectorCallsign;
	}

	@Override
	public AutoConnectMode getMode() {
		return AutoConnectMode.Fixed;
	}

	@Override
	public Optional<AutoConnectRequestData> getLinkDataIfAvailableInt() {
		return Optional.of(new AutoConnectRequestData(getTargetRepeater(), getLinkRelectorCallsign()));
	}

}
