package org.jp.illg.util.ambe.dv3k;

import lombok.Getter;

public enum DV3KEvent {
	ReceivePacket(true, DV3KPacket.class),
	;

	@Getter
	private final boolean hasAttachment;

	@Getter
	private final Class<?> attachmentClass;

	private DV3KEvent() {
		this(false, null);
	}

	private DV3KEvent(
		final boolean hasAttachment,
		final Class<?> attachmentClass
	) {
		this.hasAttachment = hasAttachment;
		this.attachmentClass = attachmentClass;
	}
}
