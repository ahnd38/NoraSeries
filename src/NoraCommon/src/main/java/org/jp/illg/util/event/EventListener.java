package org.jp.illg.util.event;

public interface EventListener<E> {

	public void event(E event, Object attachment);
}
