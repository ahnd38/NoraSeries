package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;

import lombok.Getter;

public class ReflectorDashboardInfo {
	
	@Getter
	private final ReflectorProtocolProcessorTypes reflectorType;
	
	@Getter
	private final String webSocketRoomId;
	
	public ReflectorDashboardInfo(
		final ReflectorProtocolProcessorTypes reflectorType,
		final String webSocketRoomId
	) {
		super();
		
		this.reflectorType = reflectorType;
		this.webSocketRoomId = webSocketRoomId;
	}
	
}
