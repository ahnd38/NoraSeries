package org.jp.illg.util.ambe.dv3k.model;

import org.jp.illg.util.ambe.dv3k.DV3KPacket;

import lombok.Getter;

public enum DV3KControllerEvent {
	ReceivePacket(true, DV3KPacket.class),
	;

	@Getter
	private final boolean hasAttachment;

	@Getter
	private final Class<?> attachmentClass;

	private DV3KControllerEvent() {
		this(false, null);
	}

	private DV3KControllerEvent(
		final boolean hasAttachment,
		final Class<?> attachmentClass
	) {
		this.hasAttachment = hasAttachment;
		this.attachmentClass = attachmentClass;
	}
}
