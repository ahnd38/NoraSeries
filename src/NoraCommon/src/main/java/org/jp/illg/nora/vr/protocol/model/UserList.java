package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class UserList extends NoraVRPacketBase {

	public static final int maxUserEntry = 10;

	public static class UserListEntryFlag implements Cloneable {

		private static final int localUser = 0x80;
		private static final int remoteUser = 0x40;
		private static final int loginUser = 0x01;

		@Getter
		@Setter
		private int value;

		public UserListEntryFlag() {
			super();

			setValue(0x0);
		}

		public UserListEntryFlag(
			final boolean isLocalUser,
			final boolean isRemoteUser,
			final boolean isLoginUser
		) {
			this();

			setLocalUser(isLocalUser);
			setRemoteUser(isRemoteUser);
			setLoginUser(isLoginUser);
		}

		public boolean isLocalUser() {
			return (getValue() & localUser) != 0x0;
		}

		public void setLocalUser(final boolean enable) {
			if(enable)
				setValue(getValue() | localUser);
			else
				setValue(getValue() & ~localUser);
		}

		public boolean isRemoteUser() {
			return (getValue() & remoteUser) != 0x0;
		}

		public void setRemoteUser(final boolean enable) {
			if(enable)
				setValue(getValue() | remoteUser);
			else
				setValue(getValue() & ~remoteUser);
		}

		public boolean isLoginUser() {
			return (getValue() & loginUser) != 0x0;
		}

		public void setLoginUser(final boolean isLogin) {
			if(isLogin)
				setValue(getValue() | loginUser);
			else
				setValue(getValue() & ~loginUser);
		}

		@Override
		public UserListEntryFlag clone() {
			UserListEntryFlag copy = null;
			try {
				copy = (UserListEntryFlag)super.clone();

				copy.value = this.value;

			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}

			return copy;
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indentLevel) {
			if(indentLevel < 0) {indentLevel = 0;}

			StringBuilder sb = new StringBuilder();

			indentLevel += 4;

			String indent = "";
			for(int i = 0; i < indentLevel; i++) {indent += " ";}

			sb.append("\n");

			sb.append(indent);
			sb.append("LocalUser:");
			sb.append(isLocalUser());
			sb.append('/');
			sb.append("RemoteUser:");
			sb.append(isRemoteUser());
			sb.append('/');
			sb.append("Login:");
			sb.append(isLoginUser());

			return sb.toString();
		}
	}

	public static class UserListEntry implements Cloneable {

		@Getter
		@Setter
		private UserListEntryFlag flag;

		@Getter
		@Setter
		private String myCallsign;

		@Getter
		@Setter
		private String myCallsignShort;


		public UserListEntry(
			@NonNull final UserListEntryFlag flag,
			@NonNull final String myCallsign,
			@NonNull final String myCallsignShort
		) {
			super();

			this.flag = flag;
			this.myCallsign = myCallsign;
			this.myCallsignShort = myCallsignShort;
		}

		public UserListEntry() {
			this(
				new UserListEntryFlag(),
				DSTARDefines.EmptyLongCallsign,
				DSTARDefines.EmptyShortCallsign
			);
		}

		@Override
		public UserListEntry clone() {
			UserListEntry copy = null;
			try {
				copy = (UserListEntry)super.clone();

				copy.flag = this.flag.clone();
				copy.myCallsign = this.myCallsign;
				copy.myCallsignShort = this.myCallsignShort;

			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}

			return copy;
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indentLevel) {
			if(indentLevel < 0) {indentLevel = 0;}

			StringBuilder sb = new StringBuilder();

			indentLevel += 4;

			String indent = "";
			for(int i = 0; i < indentLevel; i++) {indent += " ";}

			sb.append(indent);
			sb.append("My Callsign:");
			sb.append(getMyCallsign());
			sb.append('_');
			sb.append(getMyCallsignShort());
			sb.append('/');
			sb.append("Flags:(");
			sb.append(getFlag());
			sb.append(")");

			return sb.toString();
		}
	}

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private long requestID;

	@Getter
	@Setter
	private int blockIndex;

	@Getter
	@Setter
	private int blockTotal;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private List<UserListEntry> userList;


	public UserList() {
		super(NoraVRCommandType.USLST);

		setClientCode(0x0);
		setRequestID(0x0);
		setBlockIndex(0);
		setBlockTotal(0);
		setUserList(new LinkedList<>());
	}

	@Override
	protected boolean assembleField(ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		// Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		// Request No
		buffer.put((byte)((getRequestID() >> 24) & 0xFF));
		buffer.put((byte)((getRequestID() >> 16) & 0xFF));
		buffer.put((byte)((getRequestID() >> 8) & 0xFF));
		buffer.put((byte)(getRequestID() & 0xFF));

		// Block Index
		buffer.put((byte)((getBlockIndex() >> 8) & 0xFF));
		buffer.put((byte)(getBlockIndex() & 0xFF));

		// Block Total
		buffer.put((byte)((getBlockTotal() >> 8) & 0xFF));
		buffer.put((byte)(getBlockTotal() & 0xFF));

		// Reserved
		for(int i = 0; i < 4; i++) {buffer.put((byte)0x00);}

		int c = 0;
		for(final UserListEntry e : userList) {
			if(c >= maxUserEntry) {break;}

			buffer.put((byte)e.getFlag().getValue());
			for(int i = 0; i < 3; i++) {buffer.put((byte)0x0);}

			final String myCallsign =
				DSTARUtils.formatFullLengthCallsign(e.getMyCallsign());
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
				if(myCallsign.length() > i)
					buffer.put((byte)myCallsign.charAt(i));
				else
					buffer.put((byte)' ');
			}

			// My Callsign Short
			final String myCallsignShort =
				DSTARUtils.formatShortLengthCallsign(e.getMyCallsignShort());
			for(int i = 0; i < DSTARDefines.CallsignShortLength; i++) {
				if(myCallsignShort.length() > i)
					buffer.put((byte)myCallsignShort.charAt(i));
				else
					buffer.put((byte)' ');
			}

			c++;
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return
			4 +	// Client Code
			4 +	// Request No
			2 +	// Block Index
			2 +	// Block Total
			4 +	// Reserved
			(16 * (userList.size() <= maxUserEntry ? userList.size() : maxUserEntry));	// User Entries
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 16) {return false;}

		// Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		// Request ID
		long requestID = (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		setRequestID(requestID);

		// Block Index
		int blockIndex = (buffer.get() & 0xFF);
		blockIndex = (blockIndex << 8) | (buffer.get() & 0xFF);
		setBlockIndex(blockIndex);

		// Block Total
		int blockTotal = (buffer.get() & 0xFF);
		blockTotal = (blockTotal << 8) | (buffer.get() & 0xFF);
		setBlockTotal(blockTotal);

		// Reserved
		for(int i = 0; i < 4; i++) {buffer.get();}

		// User List
		getUserList().clear();
		for(int b = 0; b < maxUserEntry && buffer.remaining() >= 16; b++) {
			final UserListEntry usr = new UserListEntry();
			usr.getFlag().setValue(buffer.get());
			for(int c = 0; c < 3; c++) {buffer.get();}

			final StringBuffer myCallsign =
				new StringBuffer(DSTARDefines.CallsignFullLength);
			for(int c = 0; c < DSTARDefines.CallsignFullLength; c++) {
				myCallsign.append((char)buffer.get());
			}
			usr.setMyCallsign(myCallsign.toString());

			final StringBuffer myCallsignShort =
				new StringBuffer(DSTARDefines.CallsignShortLength);
			for(int c = 0; c < DSTARDefines.CallsignShortLength; c++) {
				myCallsignShort.append((char)buffer.get());
			}
			usr.setMyCallsignShort(myCallsignShort.toString());

			getUserList().add(usr);
		}

		return true;
	}

	@Override
	public UserList clone() {
		final UserList copy = (UserList)super.clone();

		copy.clientCode = this.clientCode;
		copy.requestID = this.requestID;
		copy.blockIndex = this.blockIndex;
		copy.blockTotal = this.blockTotal;
		copy.userList = new LinkedList<>();
		for(final UserListEntry e : userList) {copy.userList.add(e.clone());}

		return copy;
	}
}
