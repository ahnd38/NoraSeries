package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NewAccessPointStatusData extends ModemStatusData {
	
	public NewAccessPointStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
