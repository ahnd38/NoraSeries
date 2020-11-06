package org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;

public class Table<T extends Record> {

	@Getter
	private Lock locker;

	@Getter
	private Map<String, T> table;

	public Table(final int initialCapacity) {
		super();

		locker = new ReentrantLock();
		table = new HashMap<>(initialCapacity);
	}

}
