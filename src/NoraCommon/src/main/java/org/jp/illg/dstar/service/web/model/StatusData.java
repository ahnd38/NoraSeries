package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.NonNull;

@Data
public abstract class StatusData {
	
	private String webSocketRoomId;
	
	protected StatusData(@NonNull final String webSocketRoomId) {
		super();
		
		this.webSocketRoomId = webSocketRoomId;
	}
	
}
