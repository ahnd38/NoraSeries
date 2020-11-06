package org.jp.illg.util.socketio.napi.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.support.HostIdentType;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class BufferEntry {

	@Getter
	private final Lock locker;

	@Getter
	private final SelectionKey key;

	@Getter
	private final HostIdentType HostIdentType;

	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	@Getter
	@Setter
	private InetSocketAddress localAddress;

	private final ByteBuffer buffer;

	@Getter
	@Setter
	private BufferState bufferState;

	@Getter
	private final Queue<PacketInfo> bufferPacketInfo;

	@Getter
	private final Timer activityTimestamp;

	@Getter
	@Setter
	private boolean update;

	public BufferEntry(
		@NonNull SelectionKey key, @NonNull HostIdentType hostIdentType,
		final int bufferCapacity, final boolean directBuffer
	) {
		this(key, hostIdentType, bufferCapacity, directBuffer, null, null);
	}

	public BufferEntry(
		@NonNull SelectionKey key, @NonNull HostIdentType hostIdentType,
		final int bufferCapacity, final boolean directBuffer,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress
	) {
		super();

		locker = new ReentrantLock();

		this.key = key;
		this.HostIdentType = hostIdentType;

		this.remoteAddress = remoteAddress;
		this.localAddress = localAddress;

		buffer =
			directBuffer ? ByteBuffer.allocateDirect(bufferCapacity) : ByteBuffer.allocate(bufferCapacity);
		bufferState = BufferState.INITIALIZE;
		bufferPacketInfo = new LinkedList<>();

		activityTimestamp = new Timer();
		activityTimestamp.updateTimestamp();

		update = false;
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}

	public boolean isActivityTimeout(final long duration, @NonNull TimeUnit timeUnit) {
		return activityTimestamp.isTimeout(duration, timeUnit);
	}

	public void updateActivityTime() {
		activityTimestamp.updateTimestamp();
	}

	public void clear() {
		locker.lock();
		try {
			if(buffer != null) {buffer.clear();}
			bufferState = BufferState.INITIALIZE;
			bufferPacketInfo.clear();
		}finally {
			locker.unlock();
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < indentLevel; c++) {sb.append(' ');}

		sb.append("key=");
		sb.append(key);

		sb.append('/');

		sb.append("HostIdentType=");
		sb.append(HostIdentType);

		sb.append('/');

		sb.append("RemoteAddress=");
		sb.append(remoteAddress);

		sb.append('/');

		sb.append("LocalAddress=");
		sb.append(localAddress);

		sb.append('/');

		sb.append("Buffer=");
		sb.append(buffer);

		sb.append('/');

		sb.append("BufferState=");
		sb.append(bufferState);

		sb.append('/');

		sb.append("BufferPacketInfo=");
		sb.append(bufferPacketInfo);

		return sb.toString();
	}
}
