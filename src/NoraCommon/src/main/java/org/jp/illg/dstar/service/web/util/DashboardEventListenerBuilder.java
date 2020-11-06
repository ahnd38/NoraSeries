package org.jp.illg.dstar.service.web.util;

import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.listener.PingListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DashboardEventListenerBuilder<T> {

	public static enum DataboardEventType {
		Connect,
		Disconnect,
		Ping,
		Data,
		;
	}

	public static interface DashboardEventListener<T> {
		void onEvent(SocketIOClient client, T data, AckRequest ackSender);
	}

	private final Class<?> functionClass;
	private String eventName;
	private final DashboardEventListener<T> eventListener;
	private ThreadUncaughtExceptionListener exceptionListener;


	public DashboardEventListenerBuilder(
		@NonNull final Class<?> functionClass,
		@NonNull final String eventName,
		@NonNull final DashboardEventListener<T> eventListener,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		super();

		this.functionClass = functionClass;
		this.eventName = eventName;
		this.eventListener = eventListener;
		this.exceptionListener = exceptionListener;
	}

	public DashboardEventListenerBuilder(
		@NonNull final Class<?> functionClass,
		@NonNull final DashboardEventListener<T> eventListener,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		this(functionClass, "", eventListener, exceptionListener);
	}

	public DashboardEventListenerBuilder(
		@NonNull final Class<?> functionClass,
		@NonNull final DashboardEventListener<T> eventListener
	) {
		this(functionClass, "", eventListener, null);
	}

	public DashboardEventListenerBuilder(
		@NonNull final Class<?> functionClass,
		@NonNull final String eventName,
		@NonNull final DashboardEventListener<T> eventListener
	) {
		this(functionClass, eventName, eventListener, null);
	}

	public DashboardEventListenerBuilder<T> setEventName(final String eventName){
		this.eventName = eventName;

		return this;
	}

	public DashboardEventListenerBuilder<T> setExceptionListener(
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		this.exceptionListener = exceptionListener;

		return this;
	}

	public ConnectListener createConnectListener() {
		return new ConnectListener() {
			@Override
			public void onConnect(SocketIOClient client) {
				executeEvent(DataboardEventType.Connect, client, null, null);
			}
		};
	}

	public DisconnectListener createDisconnectListener() {
		return new DisconnectListener() {
			@Override
			public void onDisconnect(SocketIOClient client) {
				executeEvent(DataboardEventType.Disconnect, client, null, null);
			}
		};
	}

	public PingListener createPingListener() {
		return new PingListener() {
			@Override
			public void onPing(SocketIOClient client) {
				executeEvent(DataboardEventType.Ping, client, null, null);
			}
		};
	}

	public DataListener<T> createDataListener() {
		return new DataListener<T>() {
			@Override
			public void onData(SocketIOClient client, T data, AckRequest ackSender) throws Exception {
				executeEvent(DataboardEventType.Data, client, data, ackSender);
			}
		};
	}

	private void executeEvent(
		final DataboardEventType eventType,
		final SocketIOClient client, final T data, final AckRequest ackSender
	) {
		try {
			eventListener.onEvent(client, data, ackSender);
		}catch(Exception ex) {
			if(log.isErrorEnabled()) {
				log.error(
					functionClass.getSimpleName() +
					"(" + "Type:" + eventType + "/EventName:" + eventName + ") : " +
					"Exception occurred while executing listener process", ex
				);
			}

			if(exceptionListener != null)
				exceptionListener.threadUncaughtExceptionEvent(ex, Thread.currentThread());
		}
	}
}
