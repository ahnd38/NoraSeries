package org.jp.illg.dstar.repeater.modem.noravr;

import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemEvent;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNoraVRHandler;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;

public class NoraVR extends org.jp.illg.nora.vr.NoraVR implements WebRemoteControlNoraVRHandler{

	public NoraVR(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		super(exceptionListener, workerExecutor, gateway, repeater, eventListener);
	}

	public NoraVR(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(exceptionListener, workerExecutor, gateway, repeater, eventListener, socketIO);
	}

}
