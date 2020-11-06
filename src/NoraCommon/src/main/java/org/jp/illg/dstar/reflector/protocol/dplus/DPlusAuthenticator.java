package org.jp.illg.dstar.reflector.protocol.dplus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtil.BufferProcessResult;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIOEntryTCPClient;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.model.OperationSet;
import org.jp.illg.util.thread.ThreadProcessResult;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DPlusAuthenticator {

	private static final int serverPortDefault = 20001;

	private static final Pattern callsignPattern =
		Pattern.compile("^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z][ ]*)|(([R][E][F])[0-9]{3}[ ]{2}))$");

	private String logHeader;

	@Getter
	private final DPlusCommunicationService service;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String loginCalllsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String gatewayCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String serverAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int serverPort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private char id;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enablePoll;

	private SocketIOEntryTCPClient serverChannel;
	private final Lock serverChannelLocker;

	private final Timer authenticateTimer;
	private final Timer pollTimer;


	private enum State{
		Initialize,
		WaitPeriod,
		AuthenticateConnect,
		Authenticate,
		PollConnect,
		Poll,
		Wait,
		;
	}

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private State currentState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private State nextState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private State callbackState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	@Getter(AccessLevel.PRIVATE)
	private final Timer stateTimeKeeper;

	private int stateRetryCount;

	private final Lock stateLocker;

	private boolean serverConnected;
	private boolean serverConnectionError;

	private final ByteBuffer receiveBuffer;
	private BufferState receiveBufferState;
	private final Timer receiveBufferTimeKeeper;
	private final Lock receiveBufferLocker;

	private final Map<ReflectorHostInfoKey, ReflectorHostInfo> receiveHosts;
	private boolean hostsReceived;

	private DPlusAuthenticator(DPlusCommunicationService service) {
		super();

		if(service == null)
			throw new IllegalArgumentException("Service must not null.");

		this.service = service;

		logHeader = DPlusAuthenticator.class.getSimpleName();

		serverChannel = null;
		serverChannelLocker = new ReentrantLock();

		authenticateTimer = new Timer(1, TimeUnit.HOURS);
		pollTimer = new Timer();

		setCurrentState(State.Initialize);
		setNextState(State.Initialize);
		setCallbackState(State.Initialize);
		stateTimeKeeper = new Timer();

		stateRetryCount = 0;
		stateLocker = new ReentrantLock();

		serverConnected = false;
		serverConnectionError = false;

		receiveBuffer = ByteBuffer.allocateDirect(524288);
		receiveBufferState = BufferState.INITIALIZE;
		receiveBufferTimeKeeper = new Timer(5, TimeUnit.SECONDS);
		receiveBufferLocker = new ReentrantLock();

		receiveHosts = new HashMap<>();
		hostsReceived = false;

		setServerPort(serverPortDefault);
	}

	public DPlusAuthenticator(
		DPlusCommunicationService service,
		String loginCallsign, String gatewayCallsign,
		String serverAddress, int serverPort, char id, boolean enablePoll
	) {
		this(service);

		String formatedGatewayCallsign =
			gatewayCallsign != null ?
				gatewayCallsign.substring(0, DSTARDefines.CallsignFullLength - 1).trim() : "";

		String formatedLoginCallsign =
			loginCallsign != null ? loginCallsign.trim() : formatedGatewayCallsign;

		setLoginCalllsign(formatedLoginCallsign);
		setGatewayCallsign(formatedGatewayCallsign);

		setServerAddress(serverAddress);
		setServerPort(serverPort);
		setId(id);
		setEnablePoll(enablePoll);

		logHeader += "(" + serverAddress + ") : ";
	}

	public ThreadProcessResult process() {
		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		stateLocker.lock();
		try {
			boolean reProcess;
			do {
				reProcess = false;

				setStateChanged(getCurrentState() != getNextState());

				if(log.isDebugEnabled() && isStateChanged()) {
					log.debug(
						logHeader +
						"State changed " +
							getCurrentState().toString() + " -> " + getNextState().toString()
					);
				}

				setCurrentState(getNextState());

				switch(getCurrentState()) {
				case Initialize:
					processResult = onStateInitialize();
					break;

				case WaitPeriod:
					processResult = onStateWaitPeriod();
					break;

				case AuthenticateConnect:
					processResult = onStateAuthenticateConnect();
					break;

				case Authenticate:
					processResult = onStateAuthenticate();
					break;

				case PollConnect:
					processResult = onStatePollConnect();
					break;

				case Poll:
					processResult = onStatePoll();
					break;

				case Wait:
					processResult = onStateWait();
					break;

				default:
					break;
				}

				if(
					getCurrentState() != getNextState() &&
					processResult == ThreadProcessResult.NoErrors
				) {reProcess = true;}

			}while(reProcess);

		}finally {stateLocker.unlock();}

		return processResult;
	}

	private ThreadProcessResult onStateInitialize() {

		toWaitState(5, TimeUnit.SECONDS, State.WaitPeriod);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWaitPeriod() {

		if(authenticateTimer.isTimeout()) {
			stateRetryCount = 0;

			authenticateTimer.setTimeoutTime(6, TimeUnit.HOURS);
			authenticateTimer.updateTimestamp();

			setNextState(State.AuthenticateConnect);
		}
		else if(pollTimer.isTimeout() && isEnablePoll()) {
			stateRetryCount = 0;

			pollTimer.setTimeoutTime(1, TimeUnit.MINUTES);
			pollTimer.updateTimestamp();

			setNextState(State.PollConnect);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateAuthenticateConnect() {

		if(isStateChanged()) {
			closeServerChannel();

			stateTimeKeeper.setTimeoutTime(5, TimeUnit.SECONDS);

			if(!connectAuthenticateServer()) {
				if(stateRetryCount < 3) {
					toWaitState(5, TimeUnit.SECONDS, State.AuthenticateConnect);
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					authenticateTimer.setTimeoutTime(30, TimeUnit.MINUTES);
					authenticateTimer.updateTimestamp();

					setNextState(State.WaitPeriod);

					if(log.isWarnEnabled())
						log.warn(logHeader + "Failed create server connection, will retry in 30 minutes.");
				}
			}
			else {
				serverConnected = false;
				serverConnectionError = false;
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			closeServerChannel();

			if(stateRetryCount < 3) {
				toWaitState(5, TimeUnit.SECONDS, State.AuthenticateConnect);
				stateRetryCount++;
			}
			else {
				stateRetryCount = 0;

				authenticateTimer.setTimeoutTime(30, TimeUnit.MINUTES);
				authenticateTimer.updateTimestamp();

				setNextState(State.WaitPeriod);

				if(log.isWarnEnabled())
					log.warn(logHeader + "Failed connect to authenticate server, will retry in 30 minutes.");
			}
		}
		else if(serverConnected) {
			serverConnected = false;

			setNextState(State.Authenticate);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateAuthenticate() {

		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			clearReceiveBuffer();

			if(!sendAuthticateRequest()) {
				if(stateRetryCount < 3) {
					toWaitState(5, TimeUnit.SECONDS, State.Authenticate);
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					authenticateTimer.setTimeoutTime(30, TimeUnit.MINUTES);
					authenticateTimer.updateTimestamp();

					setNextState(State.WaitPeriod);

					if(log.isWarnEnabled())
						log.warn(logHeader + "Failed send authenticate request, will retry in 30 minutes.");
				}
			}

			hostsReceived = false;
		}
		else if(serverConnectionError) {
			serverConnectionError = false;

			closeServerChannel();

			stateRetryCount = 0;

			authenticateTimer.setTimeoutTime(30, TimeUnit.MINUTES);
			authenticateTimer.updateTimestamp();

			setNextState(State.WaitPeriod);

			if(log.isWarnEnabled())
				log.warn(logHeader + "Connection error from server, will retry in 30 minutes.");
		}
		else if(!hostsReceived && stateTimeKeeper.isTimeout()) {

			if(stateRetryCount < 3) {
				toWaitState(5, TimeUnit.SECONDS, State.Authenticate);
				stateRetryCount++;
			}
			else {
				closeServerChannel();

				stateRetryCount = 0;

				authenticateTimer.setTimeoutTime(30, TimeUnit.MINUTES);
				authenticateTimer.updateTimestamp();

				setNextState(State.WaitPeriod);

				if(log.isWarnEnabled())
					log.warn(logHeader + "No responce from server, will retry in 30 minutes.");
			}

		}
		else {
			if(parseReceiveBuffer()){
				stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimeKeeper.updateTimestamp();
				hostsReceived = true;
			}

			if(hostsReceived && stateTimeKeeper.isTimeout()) {
				receiveBufferLocker.lock();
				try {
					receiveBufferState = BufferState.toREAD(receiveBuffer, receiveBufferState);
					if(receiveBuffer.remaining() > 0) {
						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"Illegal receive end condition.\n" + FormatUtil.byteBufferToHexDump(receiveBuffer, 4)
							);
						}
					}

					registReflectorHosts();

				}finally {receiveBufferLocker.unlock();}

				closeServerChannel();

				authenticateTimer.setTimeoutTime(6, TimeUnit.HOURS);
				authenticateTimer.updateTimestamp();

				setNextState(State.WaitPeriod);
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStatePollConnect() {

		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(5, TimeUnit.SECONDS);

			if(!connectAuthenticateServer()) {
				if(stateRetryCount < 3) {
					toWaitState(5, TimeUnit.SECONDS, State.PollConnect);
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					pollTimer.setTimeoutTime(30, TimeUnit.MINUTES);
					pollTimer.updateTimestamp();

					setNextState(State.WaitPeriod);

					if(log.isWarnEnabled())
						log.warn(logHeader + "Failed create server connection, will retry in 30 minutes.");
				}
			}
			else {
				serverConnected = false;
				serverConnectionError = false;
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(stateRetryCount < 3) {
				closeServerChannel();

				toWaitState(5, TimeUnit.SECONDS, State.PollConnect);
				stateRetryCount++;
			}
			else {
				stateRetryCount = 0;

				pollTimer.setTimeoutTime(30, TimeUnit.MINUTES);
				pollTimer.updateTimestamp();

				setNextState(State.WaitPeriod);

				if(log.isWarnEnabled())
					log.warn(logHeader + "Failed connect to authenticate server, will retry in 30 minutes.");
			}
		}
		else if(serverConnected) {
			serverConnected = false;

			setNextState(State.Poll);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStatePoll() {

		if(isStateChanged()) {
			if(!sendAuthticatePoll()) {
				if(stateRetryCount < 3) {
					toWaitState(5, TimeUnit.SECONDS, State.Poll);
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					pollTimer.setTimeoutTime(30, TimeUnit.MINUTES);
					pollTimer.updateTimestamp();

					setNextState(State.WaitPeriod);

					if(log.isWarnEnabled())
						log.warn(logHeader + "Failed send authenticate request, will retry in 30 minutes.");
				}
			}
			else {
				closeServerChannel();

				pollTimer.setTimeoutTime(1, TimeUnit.MINUTES);
				pollTimer.updateTimestamp();

				setNextState(State.WaitPeriod);
			}
		}


		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(getStateTimeKeeper().isTimeout())
			setNextState(getCallbackState());

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(int time, TimeUnit timeUnit, State callbackState) {
		assert timeUnit != null && callbackState != null;

		if(time < 0) {time = 0;}

		if(time > 0) {
			setNextState(State.Wait);
			setCallbackState(callbackState);
			getStateTimeKeeper().setTimeoutTime(time, timeUnit);
		}
		else {
			setNextState(callbackState);
		}
	}

	private boolean parseReceiveBuffer() {

		boolean received = false;

		receiveBufferLocker.lock();
		try {

			receiveBufferState = BufferState.toREAD(receiveBuffer, receiveBufferState);

			boolean match;
			do {
				match = false;

				receiveBuffer.rewind();

				boolean receiveComplete = false;
				int length = 0;
				if(receiveBuffer.remaining() >= 3 && receiveBuffer.limit() != receiveBuffer.capacity()) {
					int lengthL = (int)(receiveBuffer.get() & 0xFF);
					int lengthH = (int)(receiveBuffer.get() & 0xFF);

					length = ((lengthH & 0x0F) << 8) | lengthL;

					if((receiveBuffer.remaining() + 2) >= length && length >= 8) {
						receiveComplete = true;
					}
				}
				receiveBuffer.rewind();

				if(receiveComplete) {
					byte[] buf = null;
					int bufferOffset = 0;
					if(receiveBuffer.hasArray()) {
						buf = receiveBuffer.array();
						bufferOffset = receiveBuffer.arrayOffset();
						receiveBuffer.position(length);
					}else {
						buf = new byte[length];
						receiveBuffer.get(buf);
					}

					if ((buf[1 + bufferOffset] & 0xC0) == 0xC0 && buf[2 + bufferOffset] == 0x01) {


						for(int i = 8; (i + 25) < length; i += 26) {

							StringBuffer addressBuf = new StringBuffer(16);
							for(int l = i + 0; l < (i + 0 + 16); l++) {
								byte b = buf[l + bufferOffset];
								if(b != 0x0)
									addressBuf.append((char)b);
								else
									break;
							}
							String address = addressBuf.toString().trim();

							StringBuffer callsignBuf = new StringBuffer(8);
							for(int l = i + 16; l < (i + 0 + 16 + DSTARDefines.CallsignFullLength); l++) {
								byte b = buf[l + bufferOffset];
								if(b != 0x0)
									callsignBuf.append((char)b);
								else
									break;
							}
							String callsign = callsignBuf.toString().trim();
							callsign = DSTARUtils.formatFullLengthCallsign(callsign);

							boolean active = (buf[i + bufferOffset + 25] & 0x80) == 0x80;

							if (
								address.length() > 0 &&
								callsignPattern.matcher(callsign).matches() &&
								active
							){
								final ReflectorHostInfo hostInfo =
									new ReflectorHostInfo(
										DSTARProtocol.DPlus,
										DSTARProtocol.DPlus.getPortNumber(),
										callsign, address,
										ReflectorHostInfo.priorityNormal,
										System.currentTimeMillis() / 1000,
										getServerAddress() + ":" + getServerPort(),
										""
									);

								if(log.isTraceEnabled())
									log.trace(logHeader + "Receive host info.\n" + hostInfo.toString(4));

								receiveHosts.put(
									new ReflectorHostInfoKey(hostInfo.getReflectorCallsign(), hostInfo.getDataSource()),
									hostInfo
								);
							}
							else {
								if(log.isTraceEnabled())
									log.trace(logHeader + "Ignore received host callsign:" + callsign + "/address:" + address);
							}
						}
					}
					else if(log.isDebugEnabled()){
						log.debug(logHeader + "Illegal header received.\n" + FormatUtil.bytesToHexDump(buf));
					}

					receiveBuffer.compact();
					receiveBuffer.limit(receiveBuffer.position());
					receiveBuffer.rewind();

					received = true;

					match = true;
				}

			}while(match);

		}finally {receiveBufferLocker.unlock();}

		return received;
	}

	private void registReflectorHosts() {
		if(!receiveHosts.isEmpty()) {

			if(log.isInfoEnabled())
				log.info(logHeader + "Update " + receiveHosts.size() + " hosts to ReflectorNameService.");

			getService().getGateway().loadReflectorHosts(
				receiveHosts,
				getServerAddress() + ":" + getServerPort(),
				true
			);

			receiveHosts.clear();
		}
	}

	public void readEvent(
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, ByteBuffer buffer
	) {
		if(localAddress == null || remoteAddress == null || buffer == null) {return;}

		InetSocketAddress serverAddr = null;

		stateLocker.lock();
		try {
			serverChannelLocker.lock();
			try {
				if(serverChannel != null)
					serverAddr = serverChannel.getRemoteAddress();
				else
					return;
			}finally {serverChannelLocker.unlock();}
		}finally {stateLocker.unlock();}

		if(serverAddr.equals(remoteAddress)) {
			receiveBufferLocker.lock();
			try {
//				if(log.isTraceEnabled()) {
//					log.trace(logHeader + "Put " + buffer.remaining() + " bytes to receive buffer.");
//				}

				receiveBufferState = BufferState.toWRITE(receiveBuffer, receiveBufferState);

				BufferUtilObject putResult =
					BufferUtil.putBuffer(
						logHeader, receiveBuffer, receiveBufferState, receiveBufferTimeKeeper, buffer
					);
				receiveBufferState = putResult.getBufferState();
				if(putResult.getProcessResult() == BufferProcessResult.Overflow)
					log.warn(logHeader + "Receive buffer overflow detected.");

//				if(log.isTraceEnabled()) {
//					log.trace(logHeader + "Receive buffer status = " + receiveBuffer.toString());
//					log.trace(logHeader + "Receive buffer.\n" + FormatUtil.byteBufferToHexDump(receiveBuffer, 4));
//				}

			}finally {receiveBufferLocker.unlock();}
		}

		buffer.rewind();
	}

	public OperationRequest connectedEvent(
		SelectionKey key, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(key == null || localAddress == null || remoteAddress == null) {return null;}

		OperationRequest ops = null;

		stateLocker.lock();
		try {
			serverChannelLocker.lock();
			try {
				if(
					serverChannel != null &&
					serverChannel.getKey() == key
				) {
					serverConnected = true;

					log.debug(logHeader + "Connected to " + remoteAddress + ".");

					ops = new OperationRequest(OperationSet.READ);
					ops.addUnsetRequest(OperationSet.CONNECT);
				}
			}finally {serverChannelLocker.unlock();}
		}finally {stateLocker.unlock();}

		return ops;
	}

	public void disconnectedEvent(
		SelectionKey key, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(key == null || localAddress == null || remoteAddress == null) {return;}
	}

	public void errorEvent(
		SelectionKey key, InetSocketAddress localAddress, InetSocketAddress remoteAddress,
		Exception ex
	) {
		if(key == null || localAddress == null || remoteAddress == null) {return;}

		if(
			serverChannel != null &&
			serverChannel.getKey() == key
		) {
			serverConnectionError = true;

			log.warn(logHeader + "Connection error.", ex);
		}
	}

	private boolean connectAuthenticateServer() {

		closeServerChannel();

		InetSocketAddress serverAddr =
			new InetSocketAddress(getServerAddress(), getServerPort());
		if(serverAddr.isUnresolved()) {return false;}

		serverChannelLocker.lock();
		try {
			serverChannel =
				getService().getSocketIO().registTCPClient(
					serverAddr, getService().getHandler(),
					this.getClass().getSimpleName() + "@->" + getServerAddress() + ":" + getServerPort()
				);
		}finally {
			serverChannelLocker.unlock();
		}

		return serverChannel != null;
	}

	private boolean sendAuthticateRequest() {
		serverChannelLocker.lock();
		try {
			if(serverChannel == null || !serverChannel.getChannel().isOpen())
				return false;


			byte[] buf = new byte[56];
			Arrays.fill(buf, (byte)' ');

			buf[0] = (byte) 0x38;
			buf[1] = (byte) 0xC0;
			buf[2] = (byte) 0x01;
			buf[3] = (byte) 0x00;

			ArrayUtil.copyOfRange(buf, 4, getLoginCalllsign().toCharArray());

			buf[12] = 'D';
			buf[13] = 'V';
			buf[14] = '0';
			buf[15] = '1';
			buf[16] = '9';
			buf[17] = '9';
			buf[18] = '9';
			buf[19] = '9';

			buf[28] = 'W';
			buf[29] = '7';
			buf[30] = 'I';
			buf[31] = 'B';
			buf[32] = (byte) getId();

			buf[40] = 'D';
			buf[41] = 'H';
			buf[42] = 'S';
			buf[43] = '0';
			buf[44] = '2';
			buf[45] = '5';
			buf[46] = '7';

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Send authenticate to " + serverChannel.getRemoteAddress() + ".\n" +
					FormatUtil.bytesToHexDump(buf, 4)
				);
			}

			return getService().writeTCPPacket(serverChannel.getKey(), ByteBuffer.wrap(buf));

		}finally {
			serverChannelLocker.unlock();
		}
	}

	private boolean sendAuthticatePoll() {
		serverChannelLocker.lock();
		try {
			if(serverChannel == null || !serverChannel.getChannel().isOpen())
				return false;


			byte[] buf = new byte[56];
			Arrays.fill(buf, (byte)' ');

			buf[0] = (byte) 0x38;
			buf[1] = (byte) 0x20;
			buf[2] = (byte) 0x01;
			buf[3] = (byte) 0x01;

			ArrayUtil.copyOfRange(buf, 4, getLoginCalllsign().toCharArray());

			buf[12] = 'D';
			buf[13] = 'V';
			buf[14] = '0';
			buf[15] = '1';
			buf[16] = '9';
			buf[17] = '9';
			buf[18] = '9';
			buf[19] = '9';

			ArrayUtil.copyOfRange(buf, 20, getLoginCalllsign().toCharArray());

			buf[28] = 'W';
			buf[29] = '7';
			buf[30] = 'I';
			buf[31] = 'B';
			buf[32] = (byte) getId();

			buf[40] = 'D';
			buf[41] = 'H';
			buf[42] = 'S';
			buf[43] = '0';
			buf[44] = '2';
			buf[45] = '5';
			buf[46] = '7';

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Send poll to " + serverChannel.getRemoteAddress() + ".\n" +
					FormatUtil.bytesToHexDump(buf)
				);
			}

			return getService().writeTCPPacket(serverChannel.getKey(), ByteBuffer.wrap(buf));

		}finally {
			serverChannelLocker.unlock();
		}
	}

	private void closeServerChannel() {
		serverChannelLocker.lock();
		try {
			if(serverChannel != null && serverChannel.getChannel().isOpen()) {
				serverChannel.getChannel().close();
			}

			serverChannel = null;

		}catch(IOException ex) {
			log.debug(logHeader + "Error occurred at channel close.", ex);
		}finally {
			serverChannelLocker.unlock();
		}
	}

	private void clearReceiveBuffer() {
		receiveBufferLocker.lock();
		try {
			receiveBuffer.clear();
			receiveBufferState = BufferState.INITIALIZE;
		}finally {receiveBufferLocker.unlock();}
	}
}
