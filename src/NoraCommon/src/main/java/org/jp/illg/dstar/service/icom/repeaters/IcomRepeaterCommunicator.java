package org.jp.illg.dstar.service.icom.repeaters;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.service.icom.model.RepeaterCommunicationService;
import org.jp.illg.dstar.service.icom.repeaters.model.CommunicatorEvent;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class IcomRepeaterCommunicator
implements RepeaterCommunicationService{

	private final String logTag;

	@Getter(AccessLevel.PROTECTED)
	private final Lock locker;

	@Getter
	private long processIntervalTimeMillis;

	@Getter(AccessLevel.PROTECTED)
	private final UUID systemID;

	@Getter(AccessLevel.PROTECTED)
	private final ThreadUncaughtExceptionListener exceptionListener;

	@Getter(AccessLevel.PROTECTED)
	private final ExecutorService workerExecutor;

	@Getter(AccessLevel.PROTECTED)
	private final SocketIO socketio;

	@Getter(AccessLevel.PROTECTED)
	private final EventListener<CommunicatorEvent> eventListener;

	private final List<String> managementRepeaterCallsigns;

	private final Queue<DSTARPacket> toServicePackets;
	private final Queue<DSTARPacket> fromServicePackets;

	private final RunnableTask receivePacketEventExecutor = new RunnableTask(getExceptionListener()) {
		@Override
		public void task() {
			getEventListener().event(CommunicatorEvent.ReceivePacketFromController, null);
		}
	};

	public IcomRepeaterCommunicator(
		@NonNull final Class<? extends RepeaterCommunicationService> implClass,
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		final EventListener<CommunicatorEvent> eventListener,
		final long processIntervalTime,
		@NonNull final TimeUnit processIntervalTimeUnit
	) {
		super();

		logTag =
			IcomRepeaterCommunicator.class.getSimpleName() +
			"(" + this.getClass().getSimpleName() + ") : ";

		locker = new ReentrantLock();
		managementRepeaterCallsigns = new ArrayList<>(16);

		this.systemID = systemID;
		this.exceptionListener = exceptionListener;
		this.workerExecutor = workerExecutor;
		this.socketio = socketio;
		this.eventListener = eventListener;

		setProcessIntervalTime(processIntervalTime, processIntervalTimeUnit);

		toServicePackets = new ConcurrentLinkedQueue<>();
		fromServicePackets = new ConcurrentLinkedQueue<>();
	}

	protected void setProcessIntervalTime(
		final long processIntervalTime, final TimeUnit processIntervalTimeUnit
	) {
		processIntervalTimeMillis =
			TimeUnit.MILLISECONDS.convert(processIntervalTime, processIntervalTimeUnit);
	}

	@Override
	public final DSTARPacket readPacket() {
		final DSTARPacket packet = toServicePackets.poll();
		if(packet == null) {return null;}

		final Header header = packet.getRFHeader();
		if(header != null) {
			final String repeaterCallsign = header.getRepeater1CallsignString();

			locker.lock();
			try {
				boolean found = false;
				for(final String callsign : managementRepeaterCallsigns) {
					if(callsign.equals(repeaterCallsign)) {found = true;}
				}
				if(!found) {
					managementRepeaterCallsigns.add(repeaterCallsign);

					if(log.isDebugEnabled())
						log.debug(logTag + "Management repeater " + repeaterCallsign + " added.");
				}
			}finally {
				locker.unlock();
			}
		}

		return packet;
	}

	@Override
	public final boolean hasReadPacket() {
		return toServicePackets.isEmpty();
	}

	@Override
	public final boolean writePacket(@NonNull DSTARPacket packet) {
		if(!isReady()) {return false;}

		boolean isSuccess = false;
		locker.lock();
		try {
			while(fromServicePackets.size() > 1000) {fromServicePackets.poll();}

			isSuccess  = fromServicePackets.add(packet);
		}finally {
			locker.unlock();
		}

		if(isSuccess)
			eventListener.event(CommunicatorEvent.RequestRunProcess, null);

		return isSuccess;
	}

	@Override
	public List<String> getManagementRepeaterCallsigns() {
		locker.lock();
		try {
			return new ArrayList<>(managementRepeaterCallsigns);
		}finally {
			locker.unlock();
		}
	}

	protected DSTARPacket readPacketFromSystem() {
		return fromServicePackets.poll();
	}

	protected boolean hasReadablePacketFromSystem() {
		return !fromServicePackets.isEmpty();
	}

	protected boolean writePacketToSystem(@NonNull DSTARPacket packet) {
		boolean isSuccess = false;
		locker.lock();
		try {
			while(toServicePackets.size() > 1000) {toServicePackets.poll();}

			isSuccess = toServicePackets.add(packet);
		}finally {
			locker.unlock();
		}

		if(isSuccess)
			workerExecutor.submit(receivePacketEventExecutor);

		return isSuccess;
	}

	protected void clearManagementRepeaterCallsigns() {
		locker.lock();
		try {
			managementRepeaterCallsigns.clear();
		}finally {
			locker.unlock();
		}
	}


	public abstract boolean serviceInitialize();

	public abstract void serviceFinalize();

	public abstract ThreadProcessResult serviceProcess();

	public abstract boolean isReady();

	public abstract boolean setProperties(Properties prop);
	public abstract Properties getProperties();
}
