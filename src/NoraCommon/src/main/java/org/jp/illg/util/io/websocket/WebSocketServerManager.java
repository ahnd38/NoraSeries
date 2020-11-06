package org.jp.illg.util.io.websocket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class WebSocketServerManager {

	private static final int defaultPort = 3000;

	private static WebSocketServerManager instance;
	private static final Lock instanceLocker;

	private final Lock locker;

	private final Map<UUID, SocketIOServer> servers;
	private UUID defaultServerID;

	private final Map<UUID, List<String>> serverRooms;

	static {
		instance = null;
		instanceLocker = new ReentrantLock();
	}

	private WebSocketServerManager() {
		super();

		locker = new ReentrantLock();

		servers = new ConcurrentHashMap<>();
		defaultServerID = null;

		serverRooms = new ConcurrentHashMap<>();
	}

	public static WebSocketServerManager getInstance() {
		instanceLocker.lock();
		try {
			if(instance == null)
				instance = new WebSocketServerManager();

			return instance;
		}finally {
			instanceLocker.unlock();
		}
	}

	public SocketIOServer getDefaultServer() {
		locker.lock();
		try {
			if(defaultServerID != null)
				return getServer(defaultServerID);
			else
				return getServer(createServer());
		}finally {
			locker.unlock();
		}
	}

	public UUID getDefaultServerID() {
		locker.lock();
		try {
			if(defaultServerID != null)
				return defaultServerID;
			else
				return createServer();
		}finally {
			locker.unlock();
		}
	}

	public SocketIOServer getServer(@NonNull final UUID serverID) {
		return servers.get(serverID);
	}

	public UUID createServer(@NonNull final Configuration config) {
		final UUID id = UUID.randomUUID();

		locker.lock();
		try {
			final SocketIOServer server = new SocketIOServer(config);

			if(
				servers.put(id, server) == null &&
				serverRooms.put(id, new ArrayList<String>()) == null
			) {
				if(defaultServerID == null)
					defaultServerID = id;

				return id;
			}
			else {
				return null;
			}
		}finally {
			locker.unlock();
		}

	}

	public UUID createServer() {
		final Configuration config = new Configuration();
		config.setPort(defaultPort);

		return createServer(config);
	}

	public boolean removeServer(@NonNull final UUID serverID) {
		return removeServer(serverID, true);
	}

	public boolean removeServer(@NonNull final UUID serverID, final boolean stop) {
		locker.lock();
		try {
			final SocketIOServer server = getServer(serverID);

			if(server != null) {
				if(stop) {server.stop();}

				servers.remove(serverID);

				removeServerRoom(serverID);

				if(defaultServerID == null || serverID.equals(defaultServerID)) {
					final Iterator<UUID> idIt = servers.keySet().iterator();
					if(idIt.hasNext())
						defaultServerID = idIt.next();
					else
						defaultServerID = null;
				}

				return true;
			}
			else {
				return false;
			}
		}finally {
			locker.unlock();
		}
	}

	public void removeServer() {
		locker.lock();
		try {
			for(final UUID id : servers.keySet().toArray(new UUID[0])) {
				removeServer(id);
			}
		}finally {
			locker.unlock();
		}
	}

	public boolean startServer(@NonNull final UUID serverID) {
		final SocketIOServer server = getServer(serverID);
		if(server != null) {
			server.start();

			return true;
		}
		else {
			return false;
		}
	}

	public boolean stopServer(@NonNull final UUID serverID) {
		final SocketIOServer server = getServer(serverID);
		if(server != null) {
			server.stop();

			return true;
		}
		else {
			return false;
		}
	}

	public boolean addServerRoom(@NonNull final UUID serverID, @NonNull final String roomName ) {
		locker.lock();
		try {
			final SocketIOServer server = getServer(serverID);
			final List<String> rooms = serverRooms.get(serverID);
			if(server != null && rooms != null) {
				if(!rooms.contains(roomName))
					rooms.add(roomName);

				return true;
			}
			else {
				return false;
			}
		}finally {
			locker.unlock();
		}
	}

	public boolean removeServerRoom(@NonNull final UUID serverID, @NonNull final String roomName ) {
		locker.lock();
		try {
			final SocketIOServer server = getServer(serverID);
			final List<String> rooms = serverRooms.get(serverID);
			if(server != null && rooms != null && rooms.contains(roomName)) {
				return rooms.remove(roomName);
			}
			else {
				return false;
			}
		}finally {
			locker.unlock();
		}
	}

	public boolean removeServerRoom(@NonNull final UUID serverID) {
		locker.lock();
		try {
			final SocketIOServer server = getServer(serverID);

			if(server != null) {
				return serverRooms.remove(serverID) != null;
			}
			else {
				return false;
			}
		}finally {
			locker.unlock();
		}
	}

	public List<String> getServerRoom(@NonNull final UUID serverID) {
		locker.lock();
		try {
			final List<String> roomlist = serverRooms.get(serverID);
			if(roomlist != null) {
				return new ArrayList<>(roomlist);
			}
			else {
				return new ArrayList<>();
			}
		}finally {
			locker.unlock();
		}
	}
}
