package org.jp.illg.util.irc;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.jp.illg.util.irc.model.IRCMessage;
import org.jp.illg.util.irc.model.IRCMessageQueue;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IRCReceiver extends ThreadBase {

	private static final String logTag = IRCReceiver.class.getSimpleName() + " : ";

	private InputStream is;
	private IRCMessageQueue q;

	private final byte[] bb = new byte[1000];

	private IRCMessage m = new IRCMessage();
	private int state = 0;

	public IRCReceiver(
		ThreadUncaughtExceptionListener exceptionListener,
		InputStream inputStream, IRCMessageQueue messageQueue
	) {
		super(exceptionListener, IRCReceiver.class.getSimpleName(), -1);

		is = inputStream;
		q = messageQueue;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {
		byte b;
		int readBytes = 0;

		try {
			readBytes = is.read(bb);

			if (readBytes <= 0) {
				if (log.isDebugEnabled())
					log.debug(logTag + "readByte EOF2");

				q.signalEOF();

				return ThreadProcessResult.NormalTerminate;
			}
		} catch (EOFException e) {
			if (log.isDebugEnabled())
				log.debug(logTag + "readByte EOF");

			q.signalEOF();

			return ThreadProcessResult.NormalTerminate;
		} catch (IOException ex) {
			if (log.isDebugEnabled())
				log.debug(logTag + "readByte exception", ex);

			q.signalEOF();

			return ThreadProcessResult.NormalTerminate;
		}

		if (log.isTraceEnabled())
			log.trace(logTag + "input " + readBytes  + "bytes.");

		int i;

		for (i = 0; i < readBytes; i++) {
			b = bb[i];

			if (b > 0) {

				if (b == 10) {
					q.putMessage(m);
					m = new IRCMessage();
					state = 0;
				}
				else if (b == 13) {
					// do nothing
				}
				else {
					switch (state) {
					case 0:
						if (b == ':') {
							state = 1;
						}
						else if (b == 32) {
							// do nothing
						}
						else {
							m.setCommand(Character.toString((char) b));
							state = 2;
						}
						break;

					case 1:
						if (b == 32) {
							state = 2;
						}
						else {
							m.setPrefix(m.getPrefix() + Character.toString((char) b));
						}
						break;

					case 2:
						if (b == 32) {
							state = 3;
							m.addParam("");
						}
						else {
							m.setCommand(m.getCommand() + Character.toString((char) b));
						}
						break;

					case 3:
						if (b == 32) {
							m.addParam("");
						}
						else if ((b == ':') && (m.getParam(m.getParamCount() - 1).length() == 0)) {
							state = 4;
						}
						else {
							m.changeParam(
								m.getParamCount() - 1,
								m.getParam(m.getParamCount() - 1) + Character.toString((char) b)
							);
						}
						break;

					case 4:
						m.changeParam(
							m.getParamCount() - 1,
							m.getParam(m.getParamCount() - 1) + Character.toString((char) b)
						);
						break;

					}
				}
			}

		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {

	}
}
