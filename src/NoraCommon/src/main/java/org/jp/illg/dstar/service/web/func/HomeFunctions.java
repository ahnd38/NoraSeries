package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.service.web.WebRemoteClientManager;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

public class HomeFunctions {

	public static final String functionRoomName = "home";

	public HomeFunctions() {}

	public static void onJoinRoom(
		final WebRemoteClientManager clientManager,
		final SocketIOServer server,
		final SocketIOClient client, final String roomName,
		final BasicStatusInformation statusInformation

	) {
		if (
			functionRoomName.equalsIgnoreCase(roomName) &&
			statusInformation != null
		) {
			sendBasicStatusInformation(server, client, statusInformation);
		}
	}


	public static boolean sendUpdateBasicStatusInformationBroadcast(
		final WebRemoteClientManager clientManager,
		final SocketIOServer server,
		final BasicStatusInformation statusInformation
	) {
		for(final SocketIOClient client : server.getRoomOperations(functionRoomName).getClients()) {
			if(clientManager.isAuthenticated(client))
				sendBasicStatusInformation(server, client, statusInformation);
		}

		return true;
	}

	public static boolean sendUpdateBasicStatusInformation(
		final WebRemoteClientManager clientManager,
		final SocketIOServer server,
		final SocketIOClient client,
		final BasicStatusInformation statusInformation
	) {
		return Stream.of(client.getAllRooms())
			.anyMatch(new Predicate<String>() {
				@Override
				public boolean test(String roomName) {
					return functionRoomName.equals(roomName);
				}
			}) &&
			clientManager.isAuthenticated(client) &&
			sendBasicStatusInformation(server, client, statusInformation);
	}

	private static boolean sendBasicStatusInformation(
		final SocketIOServer server,
		final SocketIOClient client,
		final BasicStatusInformation statusInformation
	) {
		statusInformation.setCurrentViewers(server.getAllClients().size());

		client.sendEvent("update_status." + functionRoomName, statusInformation);

		return true;
	}
}
