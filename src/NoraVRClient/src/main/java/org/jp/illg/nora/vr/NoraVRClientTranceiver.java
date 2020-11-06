package org.jp.illg.nora.vr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.nora.vr.protocol.model.AccessLog;
import org.jp.illg.nora.vr.protocol.model.Ack;
import org.jp.illg.nora.vr.protocol.model.LoginAck;
import org.jp.illg.nora.vr.protocol.model.LoginChallengeCode;
import org.jp.illg.nora.vr.protocol.model.Nak;
import org.jp.illg.nora.vr.protocol.model.NoraVRPacket;
import org.jp.illg.nora.vr.protocol.model.Pong;
import org.jp.illg.nora.vr.protocol.model.ReflectorLink;
import org.jp.illg.nora.vr.protocol.model.RepeaterInfo;
import org.jp.illg.nora.vr.protocol.model.RoutingService;
import org.jp.illg.nora.vr.protocol.model.UserList;
import org.jp.illg.nora.vr.protocol.model.VTAMBE;
import org.jp.illg.nora.vr.protocol.model.VTOPUS;
import org.jp.illg.nora.vr.protocol.model.VTPCM;
import org.jp.illg.util.FormatUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRClientTranceiver implements Runnable {

	private static final int receiveBufferSize = 1024;

	private final ByteBuffer receiveBuffer;

	private final Lock notifyLocker;
	private final Condition notifyCondition;

	private Thread workerThread;
	private boolean workerThreadAvailable;

//	private final DatagramSocket socket;
	private final DatagramChannel socket;
	private final Queue<NoraVRPacket> receiveQueue;
	private final Lock receiveQueueLocker;

	private final Ack cmdAck = new Ack();
	private final Nak cmdNak = new Nak();
	private final LoginAck cmdLoginAck = new LoginAck();
	private final LoginChallengeCode cmdLoginChallengeCode = new LoginChallengeCode();
	private final Pong cmdPong = new Pong();
	private final VTAMBE cmdVTAMBE = new VTAMBE();
	private final VTOPUS cmdVTOPUS = new VTOPUS();
	private final VTPCM cmdVTPCM = new VTPCM();
	private final RepeaterInfo cmdRepeaterInfo = new RepeaterInfo();
	private final ReflectorLink cmdReflectorLink = new ReflectorLink();
	private final RoutingService cmdRoutingService = new RoutingService();
	private final AccessLog cmdAccessLog = new AccessLog();
	private final UserList cmdUserList = new UserList();


	private class WriteEntry {
		InetSocketAddress destinationAddress;
		ByteBuffer buffer;
	}

	private class WriteQueue {
		final Queue<WriteEntry> queue;
		final Lock locker;
		SelectionKey key;

		WriteQueue() {
			super();

			queue = new LinkedList<NoraVRClientTranceiver.WriteEntry>();
			locker = new ReentrantLock();
		}
	}

	private final WriteQueue writeQueue;

	public NoraVRClientTranceiver(
		@NonNull final DatagramChannel socket,
		@NonNull final Queue<NoraVRPacket> receiveQueue,
		@NonNull final Lock receiveQueueLocker,
		final Lock notifyLocker,
		final Condition notifyCondition
	) {
		super();

		this.socket = socket;
		this.receiveQueue = receiveQueue;
		this.receiveQueueLocker = receiveQueueLocker;
		this.notifyLocker = notifyLocker;
		this.notifyCondition = notifyCondition;

		receiveBuffer = ByteBuffer.allocateDirect(receiveBufferSize);

		writeQueue = new WriteQueue();
	}

	public boolean start() {
		if(isRunning()) {stop();}

		workerThread = new Thread(this);
		workerThread.setName(
			NoraVRClientTranceiver.class.getSimpleName() + "_" + workerThread.getId()
		);
		workerThread.setPriority(Thread.MAX_PRIORITY);
		workerThreadAvailable = true;
		workerThread.start();

		return true;
	}

	public void stop() {
		workerThreadAvailable = false;

		if(
			workerThread != null && workerThread.isAlive() &&
			workerThread.getId() != Thread.currentThread().getId()
		) {
			workerThread.interrupt();
			try {
				workerThread.join();
			}catch(InterruptedException ex) {}
		}
	}

	public boolean isRunning() {
		return workerThread != null && workerThread.isAlive() && workerThreadAvailable;
	}

	public boolean isInitialized() {
		return isRunning() && writeQueue.key != null;
	}

	public boolean write(
		@NonNull final ByteBuffer buffer, @NonNull final InetSocketAddress destinationAddress
	) {
		writeQueue.locker.lock();
		try {
			if(writeQueue.key == null) {return false;}

			final WriteEntry entry = new WriteEntry();
			entry.buffer = buffer;
			entry.destinationAddress = destinationAddress;

			final boolean success = writeQueue.queue.add(entry);
			if(success) {
				try {
					writeQueue.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				}catch(CancelledKeyException ex) {}
				return true;
			}
			else
				return false;
		}finally {writeQueue.locker.unlock();}
	}

	@Override
	public void run() {

		try (final Selector selector = Selector.open()){

			final SelectionKey regdKey = socket.register(selector, SelectionKey.OP_READ, writeQueue);
			writeQueue.key = regdKey;

			while(workerThreadAvailable || hasWriteEntry()) {

				selector.select(100);

				final Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while(it.hasNext()) {
					final SelectionKey key = it.next();
					it.remove();

					try {
						if(key.isReadable()) {readInternal(key);}

						if(key.isWritable()) {writeInternal(key);}
					}catch(CancelledKeyException ex) {}
				}
			}
		} catch (IOException ex) {
			log.error("Error ocurred selector.", ex);
		}
	}

	private void readInternal(final SelectionKey key) {
		try {
			receiveBuffer.clear();
			SocketAddress addr = ((DatagramChannel)key.channel()).receive(receiveBuffer);
			if(addr == null || !(addr instanceof InetSocketAddress)) {return;}
			receiveBuffer.flip();

			if(receiveBuffer.hasRemaining()) {
				if(log.isTraceEnabled()) {
					log.trace(
						receiveBuffer.remaining() + "bytes received from " + addr + ".\n" +
						FormatUtil.byteBufferToHexDump(receiveBuffer, receiveBuffer.remaining(), 4)
					);
					receiveBuffer.rewind();
				}

				final ByteBuffer buf = receiveBuffer;
//				buf.limit(dgPacket.getLength());

				boolean packetAvailable = false;
				boolean match = false;
				do {
					NoraVRPacket packet = null;
					if(
						(packet = cmdAck.parsePacket(buf)) != null ||
						(packet = cmdNak.parsePacket(buf)) != null ||
						(packet = cmdLoginAck.parsePacket(buf)) != null ||
						(packet = cmdLoginChallengeCode.parsePacket(buf)) != null ||
						(packet = cmdPong.parsePacket(buf)) != null ||
						(packet = cmdVTAMBE.parsePacket(buf)) != null ||
						(packet = cmdVTOPUS.parsePacket(buf)) != null ||
						(packet = cmdVTPCM.parsePacket(buf)) != null ||
						(packet = cmdRepeaterInfo.parsePacket(buf)) != null ||
						(packet = cmdReflectorLink.parsePacket(buf)) != null ||
						(packet = cmdRoutingService.parsePacket(buf)) != null ||
						(packet = cmdAccessLog.parsePacket(buf)) != null ||
						(packet = cmdUserList.parsePacket(buf)) != null
					) {
						match = packetAvailable = true;

						receiveQueueLocker.lock();
						try {
							receiveQueue.add(packet.clone());
						}finally {
							receiveQueueLocker.unlock();
						}
					}
					else {
						match = false;
					}
				}while(match);

				if(packetAvailable && notifyLocker != null && notifyCondition != null) {
					notifyLocker.lock();
					try {
						notifyCondition.signalAll();
					}finally {
						notifyLocker.unlock();
					}
				}
			}
		} catch (IOException ex) {}
	}

	private void writeInternal(final SelectionKey key) {
		if(!(key.attachment() instanceof WriteQueue)) {return;}
		final WriteQueue writeQueue = (WriteQueue)key.attachment();

		writeQueue.locker.lock();
		try {
			while(!writeQueue.queue.isEmpty()) {
				final WriteEntry entry = writeQueue.queue.poll();
				if(entry == null) {break;}

				try {
					if(log.isTraceEnabled()) {
						log.trace(
							"Transmit " + entry.buffer.remaining() + "bytes to " + entry.destinationAddress + "\n" +
							FormatUtil.byteBufferToHexDump(entry.buffer, 4)
						);
						entry.buffer.rewind();
					}
					((DatagramChannel)key.channel()).send(entry.buffer, entry.destinationAddress);
				}catch(IOException ex) {}
			}

			if(!writeQueue.queue.isEmpty())
				key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			else
				key.interestOps(SelectionKey.OP_READ);
		}finally {writeQueue.locker.unlock();}
	}

	private boolean hasWriteEntry() {
		writeQueue.locker.lock();
		try {
			return !writeQueue.queue.isEmpty();
		}finally {writeQueue.locker.unlock();}
	}

}
