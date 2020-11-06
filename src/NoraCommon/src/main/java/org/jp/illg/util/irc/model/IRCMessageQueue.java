package org.jp.illg.util.irc.model;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class IRCMessageQueue {

	private Lock lock;

	private boolean eof;

	private Queue<IRCMessage> messageQueue;

	public IRCMessageQueue() {
		super();

		lock = new ReentrantLock();

		messageQueue = new LinkedList<>();

		eof = false;
	}

	public boolean isEOF(){
		return eof;
	}

	public void signalEOF(){
		eof = true;
	}

	public boolean messageAvailable()
	{
		lock.lock();
		try {
			return !messageQueue.isEmpty();
		}finally {
			lock.unlock();
		}
	}

	public IRCMessage peekFirst(){
		lock.lock();
		try {
			return messageQueue.peek();
		}finally {
			lock.unlock();
		}
	}

	public IRCMessage getMessage(){
		lock.lock();
		try {
				return messageQueue.poll();
		}finally {
			lock.unlock();
		}
	}

	public void putMessage( IRCMessage m ){
		if(m == null) {return;}

		lock.lock();
		try {
			messageQueue.add(m);
		}finally {
			lock.unlock();
		}
	}

}
