package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.ModemTypes;

import lombok.Getter;
import lombok.NonNull;

public class ModemDashboardInfo {

	@Getter
	private final int modemId;

	@Getter
	private final ModemTypes modemType;

	@Getter
	private final String webSocketRoomId;


	public ModemDashboardInfo(
		final int modemId, @NonNull final ModemTypes modemType, @NonNull final String webSocketRoomId
	) {
		super();

		this.modemId = modemId;
		this.modemType = modemType;
		this.webSocketRoomId = webSocketRoomId;
	}

}
