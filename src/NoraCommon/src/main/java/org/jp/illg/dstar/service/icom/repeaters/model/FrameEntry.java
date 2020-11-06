package org.jp.illg.dstar.service.icom.repeaters.model;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.util.Timer;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class FrameEntry {

	@Getter
	private final int frameID;

	@Getter
	@Setter
	private DSTARPacket headerPacket;

	@Getter
	private final Timer activityTimekeeper;


	public FrameEntry(final int frameID, final DSTARPacket headerPacket) {
		super();

		this.frameID = frameID;
		this.headerPacket = headerPacket;

		activityTimekeeper = new Timer();
	}

}
