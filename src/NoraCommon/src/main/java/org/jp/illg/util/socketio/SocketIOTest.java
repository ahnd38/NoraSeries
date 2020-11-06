package org.jp.illg.util.socketio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jp.illg.util.Timer;
import org.jp.illg.util.logback.LogbackUtil;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.model.OperationSet;
import org.jp.illg.util.socketio.napi.SocketIOHandler;
import org.jp.illg.util.socketio.napi.SocketIOHandlerInterface;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(JUnit4.class)
public class SocketIOTest {

	private SocketIO socketio;
	private SocketIOHandler<BufferEntry> socketioHandler;

	private boolean isConnected;

	public SocketIOHandlerInterface socketioHandlerInterface = new SocketIOHandlerInterface() {

		@Override
		public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
			// TODO 自動生成されたメソッド・スタブ

		}

		@Override
		public OperationRequest readEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
		) {

			return null;
		}

		@Override
		public void errorEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
		) {

		}

		@Override
		public void disconnectedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
		) {
			isConnected = false;

			log.trace("Disconnected");
		}

		@Override
		public OperationRequest connectedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
		) {
			isConnected = true;

			log.trace("Connected");

			return OperationRequest.create().setRequest(OperationSet.READ);
		}

		@Override
		public OperationRequest acceptedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
		) {

			return null;
		}
	};

	private ThreadUncaughtExceptionListener exceptionListener = new ThreadUncaughtExceptionListener() {

		@Override
		public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {
			log.error("", ex);
		}

		@Override
		public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
		}
	};

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);

		final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

		socketio = new SocketIO(exceptionListener, workerExecutor);
		socketio.start();

		socketioHandler = new SocketIOHandler<>(
			socketioHandlerInterface, socketio, exceptionListener,
			BufferEntry.class, HostIdentType.RemoteLocalAddressPort
		);
	}

	@After
	public void dispose() {
		socketio.stop();
	}

	@Test
	public void testSocketIO() {
		socketio.waitThreadInitialize(10000);

		for(int c = 0; c < 100; c++) {
			isConnected = false;

			SocketIOEntryTCPClient channel = null;
			try {
				channel =
					socketio.registTCPClient(
						new InetSocketAddress(InetAddress.getLocalHost(), 29999), socketioHandler,
						this.getClass().getSimpleName()
					);
			} catch (UnknownHostException ex) {
				log.error("", ex);
				break;
			}

			if(channel == null) {
				log.error("Could not create channel.");
				continue;
			}

			final Timer timeLimit = new Timer(5, TimeUnit.SECONDS);
			timeLimit.updateTimestamp();

			while(!isConnected && !timeLimit.isTimeout()) {
				try {
					Thread.sleep(1);
				}catch(InterruptedException ex) {
					break;
				}
			}

			try {
				Thread.sleep(100);
			}catch(InterruptedException ex) {break;}

			SocketIOHandler.closeChannel(channel);
		}
	}
}
