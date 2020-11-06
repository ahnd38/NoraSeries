package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class IRCDDBAppLoginUserEntry{

	private String nick;
	private String name;
	private String host;
	private boolean op;
	private int usn;

	@Getter
	@Setter
	private static int counter;

	private static final Lock counterLock;

	static {
		counter = 0;
		counterLock = new ReentrantLock();
	}

	public IRCDDBAppLoginUserEntry() {
		this("", "", "");
	}

	public IRCDDBAppLoginUserEntry(String nick, String name, String host) {
		setNick(nick);
		setName(name);
		setHost(host);
		setOp(false);
		setUsn(getCounter());

		counterLock.lock();
		try {
			setCounter(getCounter() + 1);
		}finally {
			counterLock.unlock();
		}
	}
}
