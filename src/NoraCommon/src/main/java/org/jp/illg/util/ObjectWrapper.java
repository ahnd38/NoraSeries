package org.jp.illg.util;

import lombok.Getter;
import lombok.Setter;

public class ObjectWrapper<T> {

	@Getter
	@Setter
	private T object;

	public ObjectWrapper() {
		super();
	}

	public ObjectWrapper(T object) {
		this();

		setObject(object);
	}

}
