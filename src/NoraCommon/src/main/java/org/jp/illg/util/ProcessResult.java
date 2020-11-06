package org.jp.illg.util;

import lombok.Getter;
import lombok.Setter;

public class ProcessResult<T> {

	@Getter
	@Setter
	private T result;

	public ProcessResult() {
		super();
	}

	public ProcessResult(T result) {
		this();

		setResult(result);
	}

}
