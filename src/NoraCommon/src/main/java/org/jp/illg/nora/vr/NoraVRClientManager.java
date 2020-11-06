package org.jp.illg.nora.vr;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.nora.vr.model.NoraVRClientEntry;
import org.jp.illg.nora.vr.model.NoraVRClientState;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;

import lombok.NonNull;


//@Slf4j
public class NoraVRClientManager {

//	private static final String logHeader = NoraVRClientManager.class.getSimpleName() + " : ";

	private final long clientKeepaliveTimeoutSeconds;

	private final Map<Long, NoraVRClientEntry> clients;
	private final Lock clientsLocker;

	public NoraVRClientManager(final long clientKeepaliveTimeoutSeconds) {
		super();

		this.clientKeepaliveTimeoutSeconds = clientKeepaliveTimeoutSeconds;


		clients = new HashMap<>();
		clientsLocker = new ReentrantLock();
	}

	public NoraVRClientEntry createClient(
		@NonNull final String loginCallsign,
		final String applicationName,
		final String applicationVersion,
		@NonNull final InetSocketAddress remoteHostAddress
	) {

		clientsLocker.lock();
		try {
			long clientID = 0;
			do {
				clientID = NoraVRUtil.createClientID();
			}while(isClientConnected(clientID));

			NoraVRClientEntry client =
				new NoraVRClientEntry(
					clientID,
					NoraVRClientState.LoginChallenge,
					loginCallsign,
					remoteHostAddress,
					new NoraVRConfiguration(),
					applicationName,
					applicationVersion,
					clientKeepaliveTimeoutSeconds, TimeUnit.SECONDS
				);

			clients.put(clientID, client);

			return client;
		}finally {clientsLocker.unlock();}
	}

	public boolean isClientConnected(long clientID) {
		clientsLocker.lock();
		try {
			return !findClientList(clientID, null, null).isEmpty();
		}finally {clientsLocker.unlock();}
	}

	public boolean isClientConnected(@NonNull String loginCallsign) {
		clientsLocker.lock();
		try {
			return !findClientList(-1, loginCallsign, null).isEmpty();
		}finally {clientsLocker.unlock();}
	}

	public NoraVRClientEntry findClientSingle(
		final InetSocketAddress remoteHostAddress
	){
		return findClientSingle(-1, null, remoteHostAddress);
	}

	public NoraVRClientEntry findClientSingle(
		final long clientID,
		final InetSocketAddress remoteHostAddress
	){
		return findClientSingle(clientID, null, remoteHostAddress);
	}

	public NoraVRClientEntry findClientSingle(
		final long clientID,
		final String loginCallsign,
		final InetSocketAddress remoteHostAddress
	){
		clientsLocker.lock();
		try {
			Optional<NoraVRClientEntry> client =
				findClient(clientID, loginCallsign, remoteHostAddress, -1, null).findFirst();

			if(client.isPresent())
				return client.get();
			else
				return null;
		}finally {clientsLocker.unlock();}
	}

	public List<NoraVRClientEntry> findClientList(
		final long clientID,
		final String loginCallsign,
		final InetSocketAddress remoteHostAddress
	){
		clientsLocker.lock();
		try {
			return findClient(clientID, loginCallsign, remoteHostAddress, -1, null).toList();
		}finally {clientsLocker.unlock();}
	}

	public Stream<NoraVRClientEntry> findClient(
		final long clientID,
		final String loginCallsign,
		final InetSocketAddress remoteHostAddress,
		final int protocolVersion,
		final NoraVRClientState clientState
	) {
		clientsLocker.lock();
		try {
			return
				Stream.of(clients.values())
				.filter(new Predicate<NoraVRClientEntry>() {
					@Override
					public boolean test(NoraVRClientEntry client) {
						final boolean match =
							(
								clientID <= -1 ||
								(clientID == client.getClientID())
							) &&
							(
								loginCallsign == null ||
								(client.getLoginCallsign().equals(loginCallsign))
							) &&
							(
								remoteHostAddress == null ||
								(client.getRemoteHostAddress().equals(remoteHostAddress))
							) &&
							(
								protocolVersion < 0 ||
								client.getProtocolVersion() >= protocolVersion
							) &&
							(
								clientState == null ||
								clientState == client.getClientState()
							);
						return match;
					}

				});
		}finally {clientsLocker.unlock();}
	}

	public boolean removeClient(@NonNull final NoraVRClientEntry client) {
		clientsLocker.lock();
		try {
			final boolean success = clients.remove(client.getClientID()) != null;

			return success;
		}finally {
			clientsLocker.unlock();
		}
	}

	public List<NoraVRClientEntry> getAllClients() {
		clientsLocker.lock();
		try {
			return new ArrayList<>(clients.values());
		}finally {
			clientsLocker.unlock();
		}
	}

	public int getClientCount() {
		clientsLocker.lock();
		try {
			return clients.size();
		}finally {clientsLocker.unlock();}
	}
}
