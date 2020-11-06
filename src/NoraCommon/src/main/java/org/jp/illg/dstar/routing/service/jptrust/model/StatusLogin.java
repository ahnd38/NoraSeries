package org.jp.illg.dstar.routing.service.jptrust.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import lombok.Getter;

public class StatusLogin extends StatusBase {

	@Getter
	private char[] userID;

	@Getter
	private char[] password;

	public StatusLogin() {
		super();

		userID = new char[16];
		Arrays.fill(userID, ' ');

		password = new char[64];
		Arrays.fill(password, (char)0x00);
	}

	@Override
	public StatusLogin clone() {
		final StatusLogin copy = (StatusLogin)super.clone();

		copy.userID = Arrays.copyOf(userID, userID.length);
		copy.password = Arrays.copyOf(password, password.length);

		return copy;
	}

	@Override
	public StatusType getStatusType() {
		return StatusType.Login;
	}

	@Override
	protected boolean assemblePacketData(ByteBuffer buffer) {
		if(buffer == null || buffer.remaining() < getPacketDataSize())
			return false;

		for(int i = 0; i < 16; i++) {
			if(getUserID() != null && i < getUserID().length)
				buffer.put((byte)getUserID()[i]);
			else
				buffer.put((byte)' ');
		}

		for(int i = 0; i < 64; i++) {
			if(getPassword() != null && i < getPassword().length)
				buffer.put((byte)getPassword()[i]);
			else
				buffer.put((byte)0x00);
		}

		for(int i = 0; i < 4; i++) {buffer.put((byte)0x00);}

		return true;
	}

	@Override
	protected int getPacketDataSize() {
		return
			16 +	// userID
			64 +	// password
			4;		// reserved
	}

}
