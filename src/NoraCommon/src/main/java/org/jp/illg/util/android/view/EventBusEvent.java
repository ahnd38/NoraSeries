package org.jp.illg.util.android.view;

import lombok.Getter;
import lombok.Setter;

public abstract class EventBusEvent<T> {

	@Getter
	@Setter
	private T eventType;

	@Getter
	@Setter
	private Object attachment;

	private EventBusEvent(){
		super();
	}

	public EventBusEvent(T eventType){
		this();

		setEventType(eventType);
	}

	public EventBusEvent(T eventType, Object attachment){
		this(eventType);

		setAttachment(attachment);
	}
}
