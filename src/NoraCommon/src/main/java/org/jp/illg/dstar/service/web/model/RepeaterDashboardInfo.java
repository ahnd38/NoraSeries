package org.jp.illg.dstar.service.web.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;

public class RepeaterDashboardInfo {

	@Getter
	private final String repeaterCallsign;

	@Getter
	private final List<ModemDashboardInfo> modemInfos;

	@Getter
	private final String webSocketRoomId;

	public RepeaterDashboardInfo(
		@NonNull final String repeaterCallsign, @NonNull final String webSocketRoomId
	) {
		super();

		this.repeaterCallsign = repeaterCallsign;
		this.modemInfos = new ArrayList<>();
		this.webSocketRoomId = webSocketRoomId;
	}

}
