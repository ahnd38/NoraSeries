package org.jp.illg.dstar.service.remotecontrol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReconnectType;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommand;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlRepeater;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlUserEntry;
import org.jp.illg.dstar.service.remotecontrol.model.command.AckCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.CallsignCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.GetCallsignCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.GetRepeaterCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.GetStarnetCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.HashCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.LinkCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.LinkScrCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.LoginCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.LogoffCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.LogoutCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.NakCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.RandomCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.RepeaterCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.StarnetCommand;
import org.jp.illg.dstar.service.remotecontrol.model.command.UnlinkCommand;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.HashUtil;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class RemoteControlService extends SocketIOHandlerWithThread<BufferEntry> {


	private final UUID systemID;

	private SocketIOEntryUDP channel;

	private DSTARGateway gateway;

	private static InetAddress localhost;

	private Map<String, RemoteControlUserEntry> userEntries;
	private final Lock userEntriesLock = new ReentrantLock();

	private final Random randomGen = new Random(System.currentTimeMillis());

	@Getter
	@Setter
	private String connectPassword;
	@Getter
	private static final String connectPasswordDefault = "NoraRemotePass";

	@Getter
	@Setter
	private int portNumber;
	@Getter
	private static final int portNumberDefault = 62115;


	private final RemoteControlCommand cmdLogin = new LoginCommand();
	private final RemoteControlCommand cmdLinkScr = new LinkScrCommand();
	private final RemoteControlCommand cmdHash = new HashCommand();
	private final RemoteControlCommand cmdGetCallsign = new GetCallsignCommand();
	private final RemoteControlCommand cmdGetRepeater = new GetRepeaterCommand();
	private final RemoteControlCommand cmdGetStarnet = new GetStarnetCommand();
	private final RemoteControlCommand cmdLink = new LinkCommand();
	private final RemoteControlCommand cmdUnlink = new UnlinkCommand();
	private final RemoteControlCommand cmdLogoff = new LogoffCommand();
	private final RemoteControlCommand cmdLogout = new LogoutCommand();

	static {
		try {
			localhost = InetAddress.getLocalHost();
		}catch(UnknownHostException ex) {
			localhost = null;
		}
	}

	public RemoteControlService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway
	) {
		this(systemID, exceptionListener, gateway, null);

		setProcessLoopIntervalTimeMillis(1000L);
	}

	public RemoteControlService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull DSTARGateway gateway,
		final SocketIO socketIO
	) {
		super(
			exceptionListener,
			RemoteControlService.class,
			socketIO, BufferEntry.class, HostIdentType.RemoteAddressPort
		);

		this.systemID = systemID;

		this.gateway = gateway;

		userEntries = new HashMap<>();

		setPortNumber(0);
		setConnectPassword("");
	}

	@Override
	public boolean start() {
		if(getPortNumber() <= 1023) {
			log.warn("Illegal remote control port number = " + getPortNumber() + ", replace default port number = " + portNumberDefault + ".");
			setPortNumber(portNumberDefault);
		}
		if(getConnectPassword() == null || "".equals(getConnectPassword())) {
			log.warn("Illegal remote control password = " + getConnectPassword() + ", replace default password = " + connectPasswordDefault + ".");
			setConnectPassword(connectPasswordDefault);
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						channel =
							getSocketIO().registUDP(
								new InetSocketAddress(getPortNumber()),
								RemoteControlService.this.getHandler(),
								RemoteControlService.this.getClass().getSimpleName()
							);
					}
				}
			) ||
			this.channel == null
		){
			this.stop();

			return false;
		}

		return true;
	}

	public void stop() {
		super.stop();

		if(this.channel != null && this.channel.getChannel().isOpen()) {
			try {
				this.channel.getChannel().close();
				this.channel = null;
			}catch(IOException ex) {}
		}
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {

	}

	@Override
	public void errorEvent(
			SelectionKey key, ChannelProtocol protocol,
			InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {

	}

	@Override
	protected void threadFinalize() {

	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processThread() {

		parseNetworkPacket();

		userEntriesLock.lock();
		try {
			for(Iterator<RemoteControlUserEntry> userEntryIt = userEntries.values().iterator(); userEntryIt.hasNext();) {
				RemoteControlUserEntry userEntry = userEntryIt.next();

				boolean userEntryremove = false;

				while(!userEntry.getReceiveCommand().isEmpty()) {
					RemoteControlCommand recvCmd = userEntry.getReceiveCommand().poll();

					if(
							(
								!userEntry.getRemoteAddress().equals(localhost) ||
								recvCmd.getType() != RemoteControlCommandType.LINKSCR
							) &&
							(
								!userEntry.isLoggedin() &&
								(
									recvCmd.getType() != RemoteControlCommandType.LOGIN &&
									recvCmd.getType() != RemoteControlCommandType.HASH
								)
							)
					) {
						sendNak(userEntry, "You are not logged in");
						continue;
					}

					switch(recvCmd.getType()) {
					case LOGOUT:
						onReceiveLogout(userEntry, recvCmd);
						userEntryremove = true;
						break;

					case LOGIN:
						onReceiveLogin(userEntry, recvCmd);
						break;

					case HASH:
						onReceiveHash(userEntry, recvCmd);
						break;

					case GETCALLSIGNS:
						onReceiveGetCallsign(userEntry, recvCmd);
						break;

					case GETREPEATERS:
						onReceiveGetRepeater(userEntry, recvCmd);
						break;

					case GETSTARNET:
						onReceiveGetStarnet(userEntry, recvCmd);
						break;

					case LINK:
						onReceiveLink(userEntry, recvCmd);
						break;

					case UNLINK:
						onReceiveUnlink(userEntry, recvCmd);
						break;

					case LINKSCR:
						onReceiveLinkscr(userEntry, recvCmd);
						break;

					case LOGOFF:
						onReceiveLogoff(userEntry, recvCmd);
						break;

					default:
						if(log.isDebugEnabled())
							log.debug("Illegal commad received " + recvCmd.toString());

						break;

					}
				}

				// clean user entry
				if(
					userEntryremove ||
					(System.currentTimeMillis() > (userEntry.getLastActivityTime() + TimeUnit.MINUTES.toMillis(60)))
				){
					userEntryIt.remove();

					if(log.isTraceEnabled())
						log.trace("Remove user entry\n" + userEntry.toString());
				}
			}
		}finally {
			userEntriesLock.unlock();
		}

		return ThreadProcessResult.NoErrors;
	}


	private void parseNetworkPacket() {

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry bufferEntry = opEntry.get();

			bufferEntry.getLocker().lock();
			try {
				if(!bufferEntry.isUpdate()) {continue;}

				String remoteHost =
						bufferEntry.getRemoteAddress().getAddress().getHostAddress() + ":" + bufferEntry.getRemoteAddress().getPort();

				RemoteControlUserEntry userEntry = null;
				userEntriesLock.lock();
				try {
					userEntry =  userEntries.get(remoteHost);
					//ユーザーエントリーがなければ作る
					if(userEntry == null) {
						userEntry =new RemoteControlUserEntry(
								bufferEntry.getRemoteAddress().getAddress(), bufferEntry.getRemoteAddress().getPort());

						userEntry.updateLastActivityTime();

						userEntries.put(remoteHost, userEntry);

						log.trace("Create user entry\n" + userEntry.toString());
					}
				}finally {
					userEntriesLock.unlock();
				}

				bufferEntry.setBufferState(
						BufferState.toREAD(bufferEntry.getBuffer(), bufferEntry.getBufferState())
				);

				while(!bufferEntry.getBufferPacketInfo().isEmpty()) {
					final PacketInfo packetInfo = bufferEntry.getBufferPacketInfo().poll();
					int receiveBytes = packetInfo.getPacketBytes();

					ByteBuffer recvBuf = ByteBuffer.allocate(receiveBytes);
					for(int c = 0; c < receiveBytes; c++)
						recvBuf.put(bufferEntry.getBuffer().get());

					BufferState.toREAD(recvBuf, BufferState.WRITE);

					Optional<RemoteControlCommand> cmd = null;
					if(
						((cmd = cmdLogin.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdLinkScr.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdHash.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdGetCallsign.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdGetRepeater.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdGetStarnet.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdLink.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdUnlink.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdLogoff.isValidCommand(recvBuf)).isPresent()) ||
						((cmd = cmdLogout.isValidCommand(recvBuf)).isPresent())
					) {
						RemoteControlCommand cmdCopy = cmd.get().clone();
						cmdCopy.setRemoteAddress(userEntry.getRemoteAddress());
						cmdCopy.setRemotePort(userEntry.getRemotePort());

						userEntry.getReceiveCommand().add(cmdCopy);

						userEntry.updateLastActivityTime();

						log.trace("Command receive..." + cmdCopy.toString());
					}
				}

				bufferEntry.setUpdate(false);

			}finally {
				bufferEntry.getLocker().unlock();
			}
		}
	}

	private void onReceiveLogout(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null && command != null;

		userEntry.setLoggedin(false);

		log.info("[Remote Control Host Logout] " + userEntry.getRemoteHostAddress());

		sendAck(userEntry);
	}

	private void onReceiveLogoff(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null && command != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.LOGOFF ||
				!(command instanceof LogoffCommand)
		) {return;}

		LogoffCommand cmd = (LogoffCommand) command;

		logoff(userEntry, cmd.getCallsign(), cmd.getUser());
	}

	private void onReceiveHash(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.HASH ||
				!(command instanceof HashCommand)
		) {return;}

		HashCommand cmd = (HashCommand) command;

		byte[] receiveHash = cmd.getHash();

		char[] password = connectPassword.toCharArray();

		byte[] calcInput = new byte[4 + password.length];
		DSTARUtils.writeInt32BigEndian(calcInput, 0, userEntry.getRandomValue());
		for(int i = 0; i < password.length; i++)
			calcInput[i + 4] = (byte)password[i];

		byte[] calcHash = HashUtil.calcSHA256(calcInput);

		boolean valid = Arrays.equals(receiveHash, calcHash);

		if(valid) {
			userEntry.setLoggedin(true);
			log.info("[Remote Control Host Login] " + userEntry.getRemoteHostAddress());
			sendAck(userEntry);
		}else {
			userEntry.setLoggedin(false);
			log.warn(
					"[Remote Control Host Login FAILED] Reason:Invalid password, "  +
					userEntry.getRemoteHostAddress()
			);
			sendNak(userEntry, "Invalid password");
		}
	}

	private void onReceiveGetCallsign(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null && command != null;

		sendCallsign(userEntry);
	}

	private void onReceiveGetRepeater(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null && command != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.GETREPEATERS ||
				!(command instanceof GetRepeaterCommand)
		) {return;}

		GetRepeaterCommand cmd = (GetRepeaterCommand) command;

		sendRepeater(userEntry, cmd.getCallsign());
	}

	private void onReceiveGetStarnet(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		sendStarnetGroup(userEntry);
	}

	private void onReceiveLogin(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		sendRandom(userEntry);
	}

	private void onReceiveLink(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.LINK ||
				!(command instanceof LinkCommand)
		) {return;}

		LinkCommand cmd = (LinkCommand) command;

		String repeaterCallsign = cmd.getRepeaterCallsign();
		String reflectorCallsign = cmd.getReflectorCallsign();

		boolean unlink = DSTARDefines.EmptyLongCallsign.equals(reflectorCallsign);

		if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
			log.warn("Illegal repeater callsign " + repeaterCallsign + ", Could not link.");
			sendNak(userEntry, "Illegal repeater callsign");
			return;
		}
		if(!unlink && !CallSignValidator.isValidReflectorCallsign(reflectorCallsign)) {
			log.warn("Illegal reflector callsign " + reflectorCallsign + ", Could not link.");
			sendNak(userEntry, "Illegal reflector callsign");
			return;
		}

		if(!unlink)
			link(userEntry,repeaterCallsign, cmd.getReconnectType(), reflectorCallsign, true);
		else
			unlink(userEntry, repeaterCallsign, null, reflectorCallsign);
	}

	private void onReceiveLinkscr(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.LINKSCR ||
				!(command instanceof LinkScrCommand)
		) {return;}

		LinkScrCommand cmd = (LinkScrCommand) command;

		String repeaterCallsign = cmd.getReflectorCallsign();
		String reflectorCallsign = cmd.getReflectorCallsign();

		if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
			log.warn("Illegal repeater callsign " + repeaterCallsign + ", Could not link.");
			return;
		}
		if(!CallSignValidator.isValidReflectorCallsign(reflectorCallsign)) {
			log.warn("Illegal reflector callsign " + reflectorCallsign + ", Could not link.");
			return;
		}

		link(userEntry,repeaterCallsign, cmd.getReconnectType(), reflectorCallsign, false);
	}

	private void onReceiveUnlink(RemoteControlUserEntry userEntry, RemoteControlCommand command) {
		assert userEntry != null;

		if(
				command == null ||
				command.getType() != RemoteControlCommandType.UNLINK ||
				!(command instanceof UnlinkCommand)
		) {return;}

		UnlinkCommand cmd = (UnlinkCommand) command;

		unlink(userEntry, cmd.getRepeaterCallsign(), cmd.getProtocol(), cmd.getReflectorCallsign());
	}

	private boolean sendAck(RemoteControlUserEntry userEntry) {
		assert userEntry != null;

		AckCommand cmd = new AckCommand();

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendNak(RemoteControlUserEntry userEntry, String message) {
		assert userEntry != null;

		if(message == null) {message = "";}

		NakCommand cmd = new NakCommand();
		cmd.setMessage(message);

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendRandom(RemoteControlUserEntry userEntry) {
		assert userEntry != null;

		userEntry.setRandomValue(randomGen.nextInt());

		RandomCommand cmd = new RandomCommand();
		cmd.setRandomValue(userEntry.getRandomValue());

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendCallsign(RemoteControlUserEntry userEntry) {
		assert userEntry != null;

		CallsignCommand cmd = new CallsignCommand();
		for(DSTARRepeater repeater : gateway.getRepeaters())
			cmd.getRepeaterCallsigns().add(repeater.getRepeaterCallsign());

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendRepeater(RemoteControlUserEntry userEntry, String repeaterCallsign) {
		assert userEntry != null;

		final DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
		if(repeater == null)
			return sendNak(userEntry, "Invalid repeater callsign");

		RemoteControlRepeater repeaterInfo = new RemoteControlRepeater();
		repeaterInfo.setRepeaterCallsign(repeater.getRepeaterCallsign());
		repeaterInfo.setReconnectType(ReconnectType.ReconnectNEVER);
		repeaterInfo.setStartupReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		for(ReflectorCommunicationService reflectorService : gateway.getReflectorCommunicationServiceAll()) {
			repeaterInfo.getLinks().addAll(reflectorService.getLinkInformation(repeater));
		}

		RepeaterCommand cmd = new RepeaterCommand();
		cmd.setRemoteRepeater(repeaterInfo);

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendStarnetGroup(RemoteControlUserEntry userEntry) {
		assert userEntry != null;

		StarnetCommand cmd = new StarnetCommand();

		return sendCommand(cmd, userEntry.getRemoteAddress(), userEntry.getRemotePort());
	}

	private boolean sendCommand(RemoteControlCommand command, InetAddress remoteAddress, int remotePort) {
		assert command != null && remoteAddress != null && remotePort > 0;

		InetSocketAddress dst = new InetSocketAddress(remoteAddress, remotePort);



		Optional<byte[]> data = command.assembleCommand();
		if(data.isPresent()) {
			byte[] sendbuf = data.get();

			log.trace(
					"Send " + command.getClass().getSimpleName() + " to " + remoteAddress + ":" + remotePort + ".\n" +
					sendbuf.length + "bytes [" + FormatUtil.bytesToHex(sendbuf) +"]"
			);

			return super.writeUDPPacket(channel.getKey(), dst, ByteBuffer.wrap(data.get()));
		}else
			return false;
	}

	private boolean link(
		RemoteControlUserEntry userEntry, String repeaterCallsign, ReconnectType reconnectType,
		String reflectorCallsign,
		boolean response
	) {
		assert repeaterCallsign != null && reflectorCallsign != null;

		if(reconnectType == null || reconnectType == ReconnectType.ReconnectUnknown)
			reconnectType = ReconnectType.ReconnectNEVER;

		final DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
		if(repeater == null)
			return sendNak(userEntry, "Invalid repeater callsign");

		final Optional<ReflectorHostInfo> reflectorInfo =
				gateway.findReflectorByCallsign(DSTARUtils.replaceCallsignUnderbarToSpace(reflectorCallsign));

		if(!reflectorInfo.isPresent()) {
			return sendNak(userEntry, "Unknown reflector callsign");
		}

		final boolean success =
			gateway.linkReflector(
				repeater, reflectorCallsign, reflectorInfo.get()
			);

		if(!success)
			return sendNak(userEntry, "Link request failed");
		else if(response)
			return sendAck(userEntry);
		else
			return true;
	}

	private boolean unlink(
			RemoteControlUserEntry userEntry, String repeaterCallsign, DSTARProtocol protocol, String reflectorCallsign
	) {
		assert userEntry != null && protocol != null && reflectorCallsign != null;

		final DSTARRepeater repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);
		if(repeater == null)
			return sendNak(userEntry, "Invalid repeater callsign");

		this.gateway.unlinkReflector(repeater);

		return sendAck(userEntry);
	}

	private boolean logoff(RemoteControlUserEntry userEntry, String callsign, String user) {
		return sendAck(userEntry);
	}
}
