package org.jp.illg.dstar.service.web.model;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.text.RandomStringGenerator;
import org.jp.illg.util.Timer;

import com.corundumstudio.socketio.SocketIOClient;
import com.google.common.util.concurrent.RateLimiter;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ClientEntry {

	@Getter
	private final Lock locker;

	@Getter
	private final UUID id;

	@Getter
	private final SocketIOClient socketIOClient;

	@Getter
	private final long connectTime;

	@Getter
	private final Timer pingTimekeeper;

	@Getter
	private final RateLimiter requestRateLimitter;

	@Getter
	@Setter
	private ClientState clientState;

	@Getter
	private String passwordToken;

	@Getter
	@Setter
	private String username;

	@Getter
	@Setter
	private String passwordHash;

	@Getter
	@Setter
	private String loginToken;

	@Getter
	@Setter
	private WebRemoteUserGroup group;


	public ClientEntry(
		@NonNull final SocketIOClient socketIOClient
	) {
		super();

		this.socketIOClient = socketIOClient;

		locker = new ReentrantLock();

		id = UUID.randomUUID();

		connectTime = System.currentTimeMillis();

		clientState = ClientState.Connected;

		pingTimekeeper = new Timer();
		pingTimekeeper.updateTimestamp();

		requestRateLimitter = RateLimiter.create(1.0d);

		final RandomStringGenerator rgen = new RandomStringGenerator.Builder()
		.withinRange('a', 'z')
		.build();

		passwordToken = rgen.generate(10);

		username = "";
		passwordHash = "";
		passwordToken = "";
		loginToken = "";
		group = WebRemoteUserGroup.Guests;
	}


}
