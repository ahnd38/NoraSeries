package org.jp.illg.util.socketio.model;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OperationRequest {

	private int setRequests;
	private int unsetRequests;

	private final Lock locker;

	private static final String logHeader;

	static {
		logHeader = OperationRequest.class.getSimpleName() + " : ";
	}

	public OperationRequest() {
		super();

		locker = new ReentrantLock();

		setRequests = 0;
		unsetRequests = 0;
	}

	public OperationRequest(OperationSet... setOps) {
		this();

		addSetRequests(setOps);
	}

	public OperationRequest combine(OperationRequest req) {
		if(req != null) {
			locker.lock();
			try {
				setRequests |= req.setRequests;
				unsetRequests |= req.unsetRequests;
			}finally {
				locker.unlock();
			}
		}

		return this;
	}

	public boolean addSetRequest(OperationSet ops) {
		if(ops == null) {return false;}

		locker.lock();
		try {
			setRequests |= ops.getValue();
		}finally {
			locker.unlock();
		}

		return true;
	}

	public boolean addSetRequests(OperationSet... ops) {
		if(ops == null) {return false;}

		boolean success = true;
		for(OperationSet req : ops) {
			if(!addSetRequest(req)) {success = false;}
		}

		return success;
	}

	public OperationRequest setRequest(@NonNull OperationSet ops) {
		addSetRequests(ops);

		return this;
	}

	public OperationRequest setRequests(@NonNull OperationSet... ops) {
		addSetRequests(ops);

		return this;
	}

	public void clearSetRequests() {
		locker.lock();
		try {
			setRequests = 0;
		}finally {
			locker.unlock();
		}
	}

	public boolean hasSetRequest(OperationSet ops) {
		if(ops == null) {return false;}

		return hasRequest(setRequests, ops);
	}

	public boolean addUnsetRequest(OperationSet ops) {
		if(ops == null) {return false;}

		locker.lock();
		try {
			unsetRequests |= ops.getValue();
		}finally {
			locker.unlock();
		}

		return true;
	}

	public boolean addUnsetRequests(OperationSet... ops) {
		if(ops == null) {return false;}

		boolean success = true;
		for(OperationSet req : ops) {
			if(!addUnsetRequest(req)) {success = false;}
		}

		return success;
	}

	public OperationRequest unsetRequest(@NonNull OperationSet ops) {
		addUnsetRequests(ops);

		return this;
	}

	public OperationRequest unsetRequests(@NonNull OperationSet... ops) {
		addUnsetRequests(ops);

		return this;
	}

	public void clearUnsetRequests() {
		locker.lock();
		try {
			unsetRequests = 0;
		}finally {
			locker.unlock();
		}
	}

	public boolean hasUnsetRequest(OperationSet ops) {
		if(ops == null) {return false;}

		return hasRequest(unsetRequests, ops);
	}

	public void clearRequests(){
		clearSetRequests();
		clearUnsetRequests();
	}

	public boolean processRequests(SelectionKey key) throws CancelledKeyException{
		if(key == null) {return false;}

		locker.lock();
		try {
			if(log.isTraceEnabled())
				log.trace(logHeader + "Interest ops " + toString());

			if(setRequests > 0)
				key.interestOps(key.interestOps() | setRequests);

			clearSetRequests();

			if(hasUnsetRequest(OperationSet.READ)) {
				log.warn(logHeader + "Unset READ request.\n" + Thread.currentThread().getStackTrace());
			}

			if(unsetRequests > 0)
				key.interestOps(key.interestOps() & (~unsetRequests));

			clearUnsetRequests();
		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuffer sb = new StringBuffer(indent);
		sb.append("[SetRequests]:");
		for(Iterator<OperationSet> it = OperationSet.toTypes(setRequests).iterator(); it.hasNext();) {
			OperationSet ops = it.next();

			sb.append(ops.toString());
			if(it.hasNext()) {sb.append(".");}
		}

		sb.append("/");

		sb.append("[UnsetRequests]:");
		for(Iterator<OperationSet> it = OperationSet.toTypes(unsetRequests).iterator(); it.hasNext();) {
			OperationSet ops = it.next();

			sb.append(ops.toString());
			if(it.hasNext()) {sb.append(".");}
		}

		return sb.toString();
	}

	public static OperationRequest create() {
		return new OperationRequest();
	}

	private boolean hasRequest(final int requests, OperationSet ops) {
		assert ops != null;

		return (requests & ops.getValue()) != 0;
	}
}
