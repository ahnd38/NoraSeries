package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReflectorEchoAutoReplyRepeaterStatusData extends RepeaterStatusData {
	
	public ReflectorEchoAutoReplyRepeaterStatusData(
		@NonNull final String webSocketRoomId
	) {
		super(webSocketRoomId);
	}
	
}
