package org.jp.illg.util.irc;

import java.util.Random;

import org.jp.illg.util.irc.model.IRCMessage;
import org.jp.illg.util.irc.model.IRCMessageQueue;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
class IRCProtocol {

	private final String logTag;

	@Getter
	private String currentNick;

	@Getter
	private final String channel;

	@Getter
	private final String debugChannel;

	@Getter
	@Setter
	boolean disconnectRequest;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	boolean disconnected;

	private String name;
	private String nicks[];
	private String password;

	private int state;
	private int timer;
	private int pingTimer;

	private Random r;

//	private final IRCClient ircClient;
	private final IRCApplication app;

	private final boolean isDebug;
	private String version;

	/**
	 * デフォルトコンストラクタ
	 * @param ircClient IRCクライアント
	 * @param a IRCアプリケーションハンドラ
	 * @param ch IRCチャンネル名
	 * @param dbg_chan デバッグIRCチャンネル名
	 * @param n IRCログインユーザー名
	 * @param u IRCニックネーム
	 * @param pass IRCログインパスワード
	 * @param dbg デバッグフラグ
	 * @param v バージョン名
	 */
	public IRCProtocol(
		@NonNull IRCClient ircClient,
		@NonNull IRCApplication a,
		@NonNull String ch,
		String dbg_chan,
		@NonNull String n, @NonNull String[] u, @NonNull String pass,
		boolean dbg, @NonNull String v
	) {

		 logTag =
			 IRCProtocol.class.getSimpleName() +
			 "(" + ircClient.getHost() + ":" + ircClient.getPort() + ") : ";

//		this.ircClient = ircClient;
		app = a;

		name = n;
		nicks = u;
		password = pass;
		channel = ch;
		debugChannel = dbg_chan;

		state = 0;
		timer = 0;
		pingTimer = 60; // 30 seconds
		isDebug = dbg;

		setDisconnectRequest(false);
		setDisconnected(false);

		r = new Random();
		chooseNewNick();

		version = v;
	}

	public void chooseNewNick() {
		int k = r.nextInt(nicks.length);

		currentNick = nicks[k];

		if (app != null) {
			app.setCurrentNick(currentNick);
		}
	}

	public void setNetworkReady(boolean b) {
		if (b == true) {
			if (state != 0) {
				if(log.isWarnEnabled())
					log.warn(logTag + "IRCProtocol/netReady: unexpected");
			}

			setDisconnectRequest(false);
			setDisconnected(false);

			state = 1;
			chooseNewNick();
		} else {
			state = 0;
		}
	}

	public boolean processQueues(IRCMessageQueue recvQ, IRCMessageQueue sendQ) {

		if (timer > 0) {timer--;}

		while (recvQ.messageAvailable()) {
			final IRCMessage m = recvQ.getMessage();

			if(isDebug && log.isInfoEnabled())
				log.info(logTag + "[RECEIVE] " + m.getDebugMessage());
			else if(log.isTraceEnabled())
				log.trace(logTag + "[RECEIVE] " + m.getDebugMessage());


			if (m.getCommand().equals("004")) {
				onReceive004(m);
			}
			else if (m.getCommand().equals("PING")) {
				onReceivePING(sendQ, m);
			}
			else if (m.getCommand().equals("JOIN")) {
				onReceiveJOIN(sendQ, m);
			}
			else if (m.getCommand().equals("PONG")) {
				onReceivePONG(sendQ, m);
			}
			else if (m.getCommand().equals("PART")) {
				onReceivePART(sendQ, m);
			}
			else if (m.getCommand().equals("KICK")) {
				if(onReceiveKICK(sendQ, m)) {return false;}
			}
			else if (m.getCommand().equals("QUIT")) {
				onReceiveQUIT(sendQ, m);
			}
			else if (m.getCommand().equals("MODE")) {
				onReceiveMODE(sendQ, m);
			}
			else if (m.getCommand().equals("PRIVMSG")) {
				onReceivePRIVMSG(sendQ, m);
			}
			else if (m.getCommand().equals("352")) { // WHO list
				onReceive352(sendQ, m);
			}
			else if (m.getCommand().equals("433")) { // nick collision
				onReceive433(sendQ, m);
			}
			else if (m.getCommand().equals("332") || m.getCommand().equals("TOPIC")) { // topic
				onReceiveTOPIC(sendQ, m);
			}
		}

		IRCMessage m;

		switch (state) {
		case 1:
			m = new IRCMessage();
			m.setCommand("PASS");
			m.addParam(password);		//0
			sendQ.putMessage(m);

			m = new IRCMessage();
			m.setCommand("NICK");
			m.addParam(currentNick);	//0
			sendQ.putMessage(m);

			timer = 10; // wait for possible nick collision message
			state = 2;
			break;

		case 2:
			if (timer == 0) {
				m = new IRCMessage();
				m.setCommand("USER");
				m.addParam(name);		//0
				m.addParam("0");		//1
				m.addParam("*");		//2
				m.addParam(version);	//3
				sendQ.putMessage(m);

				timer = 30;
				state = 4; // wait for login message
			}
			break;

		case 3:
			if (timer == 0) {
				chooseNewNick();
				m = new IRCMessage();
				m.setCommand("NICK");
				m.addParam(currentNick);	//0
				sendQ.putMessage(m);

				timer = 10; // wait for possible nick collision message
				state = 2;
			}
			break;

		case 4:
			if (timer == 0) {
				// no login message received -> disconnect
				return false;
			}
			break;

		case 5:
			m = new IRCMessage();
			m.setCommand("JOIN");
			m.addParam(channel);	//0
			sendQ.putMessage(m);

			timer = 30;
			state = 6; // wait for join message
			break;

		case 6:
			if (timer == 0) {
				// no join message received -> disconnect
				return false;
			}
			break;

		case 7:
			if (debugChannel == null) {
				return false; // this state cannot be processed if there is no debug_channel
			}

			m = new IRCMessage();
			m.setCommand("JOIN");
			m.addParam(debugChannel);	//0
			sendQ.putMessage(m);

			timer = 30;
			state = 8; // wait for join message
			break;

		case 8:
			if (timer == 0) {
				// no join message received -> disconnect
				return false;
			}
			break;

		case 10:
			m = new IRCMessage();
			m.setCommand("WHO");
			m.addParam(channel);	//0
			m.addParam("*");		//1
			sendQ.putMessage(m);

			timer = pingTimer;
			state = 11; // wait for timer and then send ping

			if (app != null) {
				app.setSendQ(sendQ); // this switches the application on
			}
			break;

		case 11:
			if(!isDisconnected() && isDisconnectRequest()) {
				putQuitCommand(sendQ);
				setDisconnected(true);
				state = 0;
			}
			else if (timer == 0) {
				m = new IRCMessage();
				m.setCommand("PING");
				m.addParam(currentNick);
				sendQ.putMessage(m);

				timer = pingTimer;
				state = 12; // wait for pong
			}
			break;

		case 12:
			if (timer == 0) {	// Ping timeout
				return false;
			}
			break;
		}

		if(!isDisconnected() && isDisconnectRequest()) {
			if(state != 11 && state != 12) {
				putQuitCommand(sendQ);
				setDisconnected(true);
			}
		}

		return true;
	}

	private void putQuitCommand(IRCMessageQueue sendMessageQueue) {
		final IRCMessage quitMessage = new IRCMessage("QUIT");
		quitMessage.addParam(currentNick);
		sendMessageQueue.putMessage(quitMessage);
	}

	private boolean onReceive004(final IRCMessage m) {
		if (state == 4) {state = 5;}	// next: JOIN

		return true;
	}

	private boolean onReceivePING(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		final IRCMessage pongMessage = new IRCMessage();
		pongMessage.setCommand("PONG");
		pongMessage.addParam(receiveMessage.getParam(0));

		sendMessageQueue.putMessage(pongMessage);

		return true;
	}

	private boolean onReceiveJOIN(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (receiveMessage.getParamCount() >= 1) {
			if (receiveMessage.getParam(0).equals(channel)) {
				if (receiveMessage.getPrefixNick().equals(currentNick) && (state == 6)) {
					if (debugChannel != null)
						state = 7; // next: join debug_channel
					else
						state = 10; // next: WHO *
				}
				else if (app != null) {
					app.userJoin(
						receiveMessage.getPrefixNick(), receiveMessage.getPrefixName(), receiveMessage.getPrefixHost()
					);
				}
			}
			else if (receiveMessage.getParam(0).equals(debugChannel)) {
				if (receiveMessage.getPrefixNick().equals(currentNick) && (state == 8)) {
					state = 10; // next: WHO *
				}
			}
		}

		return true;
	}

	private boolean onReceivePONG(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (state == 12) {
			timer = pingTimer;
			state = 11;
		}

		return true;
	}

	private boolean onReceivePART(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (receiveMessage.getParamCount() >= 1 && receiveMessage.getParam(0).equals(channel) && app != null) {
			app.userLeave(receiveMessage.getPrefixNick());
		}

		return true;
	}

	private boolean onReceiveKICK(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		boolean disconnectRequest = false;

		if (receiveMessage.getParamCount() >= 2 && receiveMessage.getParam(0).equals(channel)) {
			if (receiveMessage.getParam(1).equals(currentNick)) {
				disconnectRequest = true;
			}
			else if (app != null) {
				app.userLeave(receiveMessage.getParam(1));
			}
		}

		return !disconnectRequest;
	}

	private boolean onReceiveQUIT(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (app != null) {
			app.userLeave(receiveMessage.getPrefixNick());
		}

		return true;
	}

	private boolean onReceiveMODE(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (receiveMessage.getParamCount() >= 3 && receiveMessage.getParam(0).equals(channel) && app != null) {
			int i;
			String mode = receiveMessage.getParam(1);

			for (i = 1; (i < mode.length()) && (receiveMessage.getParamCount() >= (i + 2)); i++) {
				if (mode.charAt(i) == 'o') {
					if (mode.charAt(0) == '+') {
						app.userChanOp(receiveMessage.getParam(i + 1), true);
					} else if (mode.charAt(0) == '-') {
						app.userChanOp(receiveMessage.getParam(i + 1), false);
					}
				}
			}
		}

		return true;
	}

	private boolean onReceivePRIVMSG(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if ((receiveMessage.getParamCount() == 2) && (app != null)) {
			if (receiveMessage.getParam(0).equals(channel)) {
				app.msgChannel(receiveMessage);
			} else if (receiveMessage.getParam(0).equals(currentNick)) {
				app.msgQuery(receiveMessage);
			}
		}

		return true;
	}

	private boolean onReceive352(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (
			receiveMessage.getParamCount() >= 7 &&
			receiveMessage.getParam(0).equals(currentNick) &&
			receiveMessage.getParam(1).equals(channel)
		) {
			if (app != null) {
				app.userJoin(receiveMessage.getParam(5), receiveMessage.getParam(2), receiveMessage.getParam(3));
				app.userChanOp(receiveMessage.getParam(5), receiveMessage.getParam(6).equals("H@"));
			}
		}

		return true;
	}

	private boolean onReceive433(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (state == 2) {
			state = 3;
			timer = 10;
		}

		return true;
	}

	private boolean onReceiveTOPIC(final IRCMessageQueue sendMessageQueue, final IRCMessage receiveMessage) {
		if (receiveMessage.getParamCount() == 2 && receiveMessage.getParam(0).equals(channel) && app != null) {
			app.setTopic(receiveMessage.getParam(1));
		}

		return true;
	}
}
