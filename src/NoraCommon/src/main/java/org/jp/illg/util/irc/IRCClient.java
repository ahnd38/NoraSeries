package org.jp.illg.util.irc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.jp.illg.util.irc.model.IRCMessage;
import org.jp.illg.util.irc.model.IRCMessageQueue;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IRCClient extends ThreadBase {

	private final String logTag;

	private IRCApplication ircApp;

	private IRCProtocol proto;

	private final boolean isDebug;

	private int timer = 0;
	private int state = 0;

	private IRCReceiver recv;
	private IRCMessageQueue recvQ;
	private IRCMessageQueue sendQ;
	private Socket socket;
	private OutputStream outputStream;

	@Getter
	private String host;

	@Getter
	private int port;

	public IRCClient(
		ThreadUncaughtExceptionListener exceptionListener,
		IRCApplication ircApp,
		String host, int port,
		String channel, String debugChannel, String name, String[] nicks, String password,
		boolean debug, String version
	) {
		super(exceptionListener, IRCClient.class.getSimpleName(), TimeUnit.MILLISECONDS.toMillis(500), true);

		logTag =
			IRCClient.class.getSimpleName() +
			"(" + host + ":" + port + "/" + channel + (debugChannel != null ? ("*" + debugChannel) : "") + ")" +
			" : ";

		this.ircApp = ircApp;

		this.recv = null;
		this.recvQ = null;
		this.sendQ = null;
		this.socket = null;
		this.outputStream = null;

		this.host = host;
		this.port = port;

		this.isDebug = debug;

		this.proto =
			new IRCProtocol(this, ircApp, channel, debugChannel, name, nicks, password, debug, version);
	}

	public String getChannel() {
		return proto.getChannel();
	}

	private boolean init() {

		closeConnection();

		InetAddress adr[] = null;

		try {
			adr = InetAddress.getAllByName(host);
		} catch (UnknownHostException ex) {
			if(log.isTraceEnabled())
				log.error(logTag + "unknown host " + ex.toString());

			return false;
		}

		if (adr != null) {
			int num = adr.length;

			if ((num > 0) && (num < 15)) {

				int i;

				if(log.isTraceEnabled())
					log.trace(logTag + "found " + num + " addresses:");

				int shuffle[] = new int[num];

				for (i = 0; i < num; i++) {
					log.trace("  " + adr[i].getHostAddress());
					shuffle[i] = i;
				}

				Random r = new Random();

				for (i = 0; i < (num - 1); i++) {
					if (r.nextBoolean()) {
						int tmp;
						tmp = shuffle[i];
						shuffle[i] = shuffle[i + 1];
						shuffle[i + 1] = tmp;
					}
				}

				for (i = (num - 1); i > 0; i--) {
					if (r.nextBoolean()) {
						int tmp;
						tmp = shuffle[i];
						shuffle[i] = shuffle[i - 1];
						shuffle[i - 1] = tmp;
					}
				}

				socket = null;

				for (i = 0; i < num; i++) {
					InetAddress a = adr[shuffle[i]];

					if (a instanceof Inet4Address) {
						if(log.isTraceEnabled())
							log.trace(logTag + "trying: " + a.getHostAddress());

						try {
							socket = new java.net.Socket();
							InetSocketAddress endp = new InetSocketAddress(a, port);

							socket.connect(endp, 5000); // 5 seconds timeout
						} catch (IOException ex) {
							if(log.isWarnEnabled())
								log.warn(logTag + "IOException", ex);

							socket = null;
						}

						if (socket != null) {
							break;
						}
					}
				}

			} else {
				if(log.isErrorEnabled())
					log.error(logTag + "Invalid number of addresses: " + adr.length);

				return false;
			}
		}

		if (socket == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "No connection");

			return false;
		}

		recvQ = new IRCMessageQueue();
		sendQ = new IRCMessageQueue();

		try {
			outputStream = socket.getOutputStream();
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "getOutputStream: " + ex.toString());

			return false;
		}

		InputStream is;

		try {
			is = socket.getInputStream();
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "getInputStream: " + ex);

			return false;
		}

		recv = new IRCReceiver(super.getExceptionListener() ,is, recvQ);

		if(!recv.start()) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not start IRCReceiver.");

			return false;
		}

		proto.setNetworkReady(true);

		return true;
	}

	void closeConnection() {
		if (ircApp != null) {
			ircApp.setSendQ(null);
			ircApp.userListReset();
		}

		if(recv != null) {
			if(recv.isRunning()) {recv.stop();}

			recv = null;
		}

		if (socket != null) {
			try {
				socket.shutdownInput();
			} catch (IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logTag + "socket.shutdownInput: " + ex, ex);
			}

			try {
				socket.close();
			} catch (IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logTag + "socket.close: " + ex, ex);
			}

			socket = null;
		}

		recvQ = null;
		sendQ = null;
		outputStream = null;

		proto.setNetworkReady(false);
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {
		if (timer == 0) {
			switch (state) {
			case 0:
				if(log.isTraceEnabled())
					log.trace(logTag + "Connect request");

				if(!super.isWorkerThreadAvailable()) {
					timer = 0;
					state = 2;
				}
				else if (init()) {
					if(log.isTraceEnabled())
						log.trace(logTag + "Connected");

					state = 1;
					timer = 1;
				} else {
					timer = 1;
					state = 2;
				}
				break;

			case 1:
				if(!super.isWorkerThreadAvailable() && !proto.isDisconnectRequest()) {
					proto.setDisconnectRequest(true);
					timer = 0;
					state = 1;
				}
				else if (recvQ.isEOF()) {
					timer = 0;
					state = 2;
				} else if (!proto.processQueues(recvQ, sendQ)) {
					timer = 0;
					state = 2;
				}

				while ((state == 1) && sendQ.messageAvailable()) {
					final IRCMessage m = sendQ.getMessage();
					m.writeMessage(outputStream);

					if(isDebug && log.isInfoEnabled()) {
						log.info(logTag + "[SEND] " + m.getDebugMessage());
					}
					else if(log.isTraceEnabled())
						log.trace(logTag + "[SEND] " + m.getDebugMessage());
				}

				if(proto.isDisconnected()) {
					timer = 0;
					state = 2;
				}
				break;

			case 2:
				closeConnection();
				timer = 30; // wait 15 seconds
				state = 0;

				if(!super.isWorkerThreadAvailable())
					super.setWorkerThreadTerminateRequest(true);

				break;
			}
		} else {
			timer--;
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {

	}

	public String getCurrentNick() {
		return proto.getCurrentNick();
	}

	public boolean isConnected() {
		return state == 1;
	}
}
