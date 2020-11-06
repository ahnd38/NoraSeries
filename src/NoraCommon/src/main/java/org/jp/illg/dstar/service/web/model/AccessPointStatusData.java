package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AccessPointStatusData extends ModemStatusData {
	
	public AccessPointStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
