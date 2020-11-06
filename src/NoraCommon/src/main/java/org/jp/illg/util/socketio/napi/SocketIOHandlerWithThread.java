package org.jp.illg.util.socketio.napi;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;
import org.jp.illg.util.socketio.SocketIOEntry;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SocketIOHandlerWithThread<BUFT extends BufferEntry>
extends ThreadBase
implements SocketIOHandlerInterface {

	private static final String logHeader;

	private final SocketIOHandler<BUFT> handler;

	static {
		logHeader = SocketIOHandlerWithThread.class.getSimpleName() + " : ";
	}

	public SocketIOHandlerWithThread(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull Class<?> processorClass,
		final Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		this(exceptionListener, processorClass, null, bufferEntryClass, hostIdentType);
	}

	public SocketIOHandlerWithThread(
		final ThreadUncaughtExceptionListener exceptionListener,
		Class<?> processorClass,
		final SocketIO socketIO,
		@NonNull Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		super(exceptionListener, processorClass.getSimpleName());

		handler =
			new SocketIOHandler<>(this, socketIO, exceptionListener, bufferEntryClass, hostIdentType);
	}

	public SocketIO getSocketIO() {
		return handler.getSocketIO();
	}

	public SocketIOProcessingHandlerInterface getHandler() {
		return handler;
	}

	@Override
	public boolean start() {
		return start((Runnable[])null);
	}

	public boolean start(final boolean threadStart, Runnable... processBeforeThreadStart) {
		if(
			!(
				handler.isSocketIOInternal() &&
				!handler.getSocketIO().isRunning() &&
				handler.getSocketIO().start() &&
				handler.getSocketIO().waitThreadInitialize(TimeUnit.SECONDS.toMillis(10))
			) &&
			!(
				!handler.isSocketIOInternal() &&
				handler.getSocketIO().isRunning()
			)
		) {
			this.stop();

			if(log.isErrorEnabled()) {
				log.error(
					logHeader +
					"Could not start " + this.getClass().getSimpleName() +
					",SocketIO=" + (handler.isSocketIOInternal() ? "Internal" : "External") + "."
				);
			}

			return false;
		}

		if(processBeforeThreadStart != null) {
			for(Runnable process : processBeforeThreadStart) {
				if(process != null) {process.run();}
			}
		}

		if(threadStart && !super.start()) {return false;}

		return true;
	}

	public boolean start(Runnable... processBeforeThreadStart) {
		return start(true, processBeforeThreadStart);
	}

	@Override
	public void stop() {
		super.stop();

		if(handler.isSocketIOInternal() && handler.getSocketIO().isRunning()) {
			handler.getSocketIO().stop();
		}
	}

	@Override
	public void wakeupProcessThread() {
		super.wakeupProcessThread();
	}

	@Override
	protected final ThreadProcessResult process() {

		final ThreadProcessResult result = processThread();

		cleanBuffer();

		return result;
	}

	protected abstract ThreadProcessResult processThread();

	@Override
	protected void threadFinalize() {
		handler.clearBuffer();
	}

	protected void closeChannel(SocketIOEntry<? extends SelectableChannel> entry) {
		SocketIOHandler.closeChannel(entry);
	}

	protected void closeChannel(SelectionKey key) {
		SocketIOHandler.closeChannel(key);
	}

	protected boolean disconnectTCP(SelectionKey key) {
		return handler.disconnectTCP(key);
	}

	protected void setBufferSizeTCP(final int bufferSize) {
		handler.setBufferSizeTCP(bufferSize);
	}

	protected int getBufferSizeTCP() {
		return handler.getBufferSizeTCP();
	}

	protected void setBufferSizeUDP(final int bufferSize) {
		handler.setBufferSizeUDP(bufferSize);
	}

	protected int getBufferSizeUDP() {
		return handler.getBufferSizeUDP();
	}

	protected boolean hasReceivedReadBuffer() {
		return getReceivedReadBuffer().isPresent();
	}

	protected Optional<BUFT> getReceivedReadBuffer() {
		return handler.getReceivedReadBuffer();
	}

	protected @NonNull Optional<BUFT> getReadBufferUDP(
		@NonNull final SelectionKey key, @NonNull final InetSocketAddress dstAddress
	){
		return getReadBuffer(key, dstAddress);
	}

	protected @NonNull Optional<BUFT> getReadBufferTCP(
		@NonNull final SelectionKey key
	){
		return getReadBuffer(key, null);
	}

	protected @NonNull Optional<BUFT> getReadBuffer(
		@NonNull final SelectionKey key, final InetSocketAddress dstAddress
	){
		return handler.getReadBuffer(key, dstAddress);
	}

	protected boolean writeUDP(
		@NonNull SelectionKey key, @NonNull InetSocketAddress dstAddress, @NonNull ByteBuffer buffer
	) {
		return handler.writeUDP(key, dstAddress, buffer);
	}

	protected boolean writeUDPPacket(
		@NonNull SelectionKey key, @NonNull InetSocketAddress dstAddress, @NonNull ByteBuffer buffer
	) {
		return handler.writeUDPPacket(key, dstAddress, buffer);
	}

	protected boolean writeTCP(
		@NonNull SelectionKey key, @NonNull ByteBuffer buffer
	) {
		return handler.writeTCP(key, buffer);
	}

	protected boolean writeTCPPacket(
		@NonNull SelectionKey key, @NonNull ByteBuffer buffer
	) {
		return handler.writeTCPPacket(key, buffer);
	}

	protected boolean isWriteCompleted(@NonNull SelectionKey key) {
		return handler.isWriteCompleted(key);
	}

	protected void cleanBuffer() {
		handler.cleanBuffer();
	}

	abstract public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes);

	abstract public OperationRequest readEvent(SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress);
	abstract public OperationRequest acceptedEvent(SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress);
	abstract public OperationRequest connectedEvent(SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress);
	abstract public void disconnectedEvent(SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress);
	abstract public void errorEvent(SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex);

}
