package org.jp.illg.util.socketio.model;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public enum OperationSet {

	ACCEPT(SelectionKey.OP_ACCEPT),
	CONNECT(SelectionKey.OP_CONNECT),
	READ(SelectionKey.OP_READ),
	WRITE(SelectionKey.OP_WRITE),
	;

	@Getter
	private final int value;

	private OperationSet(final int value) {
		this.value = value;
	}

	public static List<OperationSet> toTypes(final int value) {
		List<OperationSet> result = new ArrayList<>();

		for(OperationSet ops : values()) {
			if((ops.getValue() & value) != 0) {result.add(ops);}
		}

		return result;
	}
}
