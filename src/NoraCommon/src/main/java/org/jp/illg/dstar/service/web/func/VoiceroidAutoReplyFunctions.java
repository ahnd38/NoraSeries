package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlVoiceAutoReplyHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class VoiceroidAutoReplyFunctions extends RepeaterFunctions {

	private VoiceroidAutoReplyFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlVoiceAutoReplyHandler handler
	) {
		return setupStatusFunction(exceptionListener, VoiceroidAutoReplyFunctions.class, server, handler);
	}
}
