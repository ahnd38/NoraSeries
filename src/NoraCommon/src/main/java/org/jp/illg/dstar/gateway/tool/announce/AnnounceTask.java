package org.jp.illg.dstar.gateway.tool.announce;

import java.util.LinkedList;
import java.util.Queue;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARPacket;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class AnnounceTask {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Queue<DSTARPacket> announceVoice;

	@Getter
	@Setter
	private DSTARPacket header;

	@Getter
	@Setter
	private String shortMessage;

	public AnnounceTask(@NonNull final DSTARPacket header) {
		super();

		setCreatedTime(System.currentTimeMillis());
		setAnnounceVoice(new LinkedList<>());

		setHeader(header);
		setShortMessage(DSTARDefines.EmptyDvShortMessage);
	}

}
