package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Data
public class EnableDashboard {

	@Getter
	@Setter
	private String roomId;

	@Getter
	@Setter
	private boolean enable;

	public EnableDashboard() {
		super();
	}

	public EnableDashboard(@NonNull final String roomId, final boolean enable) {
		this();

		this.roomId = roomId;
		this.enable = enable;
	}

}
