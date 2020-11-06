package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlAnalogModemPiGPIOHandler;
import org.jp.illg.dstar.service.web.model.AnalogModemPiGPIOHeaderData;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class AnalogModemPiGPIOFunctions extends ModemFunctions{

	@SuppressWarnings("unused")
	private static final String logTag = AnalogModemPiGPIOFunctions.class.getSimpleName() + " : ";

	public AnalogModemPiGPIOFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlAnalogModemPiGPIOHandler handler
	) {
		final String updateUplinkHeaderEventName =
			"update_uplink_header_" + handler.getWebSocketRoomId();
		server.addEventListener(
			updateUplinkHeaderEventName,
			AnalogModemPiGPIOHeaderData.class,
			new DashboardEventListenerBuilder<>(
				AnalogModemPiGPIOHeaderData.class, updateUplinkHeaderEventName,
				new DashboardEventListener<AnalogModemPiGPIOHeaderData>() {
					@Override
					public void onEvent(
						SocketIOClient client, AnalogModemPiGPIOHeaderData data, AckRequest ackSender
					) {
						if(data != null && client.getAllRooms().contains(handler.getWebSocketRoomId()))
							handler.updateHeaderFromWebRemoteControl(data);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return setup(exceptionListener, AnalogModemPiGPIOFunctions.class, server, handler);
	}
}
