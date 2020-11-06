package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.FormatUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class AccessLog extends NoraVRPacketBase {

	public static final int maxLogEntry = 10;

	public enum AccessLogRoute {
		Unknown(-1),
		LocalToLocal(0x00),
		LocalToGateway(0x01),
		GatewayToLocal(0x02),
		;

		private final int value;

		@Getter
		private static final int mask = 0x3;

		private AccessLogRoute(final int value) {
			this.value = value;
		}

		public int getValue() {
			return value & mask;
		}

		public static AccessLogRoute getTypeByValue(final int value) {
			for(final AccessLogRoute v : values()) {
				if(v.getValue() == (value & mask)) {return v;}
			}

			return Unknown;
		}
	}

	public static class AccessLogEntryFlag implements Cloneable{

		@Getter
		private AccessLogRoute route;

		public void setValue(final int value) {
			route = AccessLogRoute.getTypeByValue(value);
		}

		public int getValue() {
			return route.getValue();
		}

		public AccessLogEntryFlag() {
			this(AccessLogRoute.Unknown.getValue());
		}

		public AccessLogEntryFlag(final int value) {
			super();

			setValue(value);
		}

		public AccessLogEntryFlag(@NonNull AccessLogRoute route) {
			super();

			setValue(route.getValue());
		}

		public AccessLogEntryFlag clone() {
			AccessLogEntryFlag copy = null;
			try {
				copy = (AccessLogEntryFlag)super.clone();

				copy.route = this.route;

				return copy;
			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indentLevel) {
			if(indentLevel < 0) {indentLevel = 0;}

			StringBuilder sb = new StringBuilder();

			String indent = "";
			for(int i = 0; i < indentLevel; i++) {indent += " ";}

			sb.append(indent);

			sb.append("Route:");
			sb.append(getRoute());

			return sb.toString();
		}
	}

	public static class AccessLogEntry implements Cloneable{

		@Getter
		@Setter
		private AccessLogEntryFlag flag;

		@Getter
		@Setter
		private int logNo;

		@Getter
		@Setter
		private long timestamp;

		@Getter
		@Setter
		private String yourCallsign;

		@Getter
		@Setter
		private String myCallsign;

		@Getter
		@Setter
		private String myCallsignShort;


		public AccessLogEntry() {
			super();

			this.flag = new AccessLogEntryFlag();
			this.logNo = 0;
			this.timestamp = 0;
			this.yourCallsign = DSTARDefines.EmptyLongCallsign;
			this.myCallsign = DSTARDefines.EmptyLongCallsign;
			this.myCallsignShort = DSTARDefines.EmptyShortCallsign;
		}

		public AccessLogEntry(
			@NonNull final AccessLogEntryFlag flag,
			final int logNo,
			final long timestamp,
			final String yourCallsign,
			final String myCallsign,
			final String myCallsignShort
		) {
			super();

			setFlag(flag);
			setLogNo(logNo);
			setTimestamp(timestamp);
			setYourCallsign(yourCallsign);
			setMyCallsign(myCallsign);
			setMyCallsignShort(myCallsignShort);
		}

		@Override
		public AccessLogEntry clone() {
			AccessLogEntry copy = null;
			try {
				copy = (AccessLogEntry)super.clone();

				copy.flag = this.flag.clone();
				copy.logNo = this.logNo;
				copy.timestamp = this.timestamp;
				copy.yourCallsign = this.yourCallsign;
				copy.myCallsign = this.myCallsign;
				copy.myCallsignShort = this.myCallsignShort;

				return copy;
			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indentLevel) {
			if(indentLevel < 0) {indentLevel = 0;}

			StringBuilder sb = new StringBuilder();

			String indent = "";
			for(int i = 0; i < indentLevel; i++) {indent += " ";}

			sb.append(indent);

			sb.append("Flag:");
			sb.append(getFlag());

			sb.append('/');

			sb.append("LogNo:");
			sb.append(getLogNo());

			sb.append('/');

			sb.append("Timestamp:");
			sb.append(FormatUtil.dateFormat(getTimestamp() * 1000));

			sb.append('/');

			sb.append("UR:");
			sb.append(getYourCallsign());

			sb.append('/');

			sb.append("MY:");
			sb.append(getMyCallsign());
			sb.append('_');
			sb.append(getMyCallsignShort());

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
	private List<AccessLogEntry> logs;


	public AccessLog() {
		this(new AccessLogEntry[0]);
	}

	public AccessLog(AccessLogEntry... logs) {
		super(NoraVRCommandType.ACLOG);

		setClientCode(0x0);
		setRequestID(0x0);
		setBlockIndex(0);
		setBlockTotal(0);
		setLogs(new LinkedList<>());

		if(logs != null) {
			for(final AccessLogEntry log : logs) {
				if(log != null) {getLogs().add(log);}
			}
		}
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

		// Logs
		int entryCount = 0;
		for(final AccessLogEntry log : logs) {
			if(entryCount >= maxLogEntry) {break;}

			// Flag
			buffer.put((byte)log.getFlag().getValue());

			// Log No
			buffer.put((byte)log.getLogNo());

			// Reserved
			for(int i = 0; i < 2; i++) {buffer.put((byte)0x0);}

			// Timestamp
			final long timestamp = log.getTimestamp() != 0 ? log.getTimestamp() / 1000 : 0;
			buffer.put((byte)((timestamp >> 56) & 0xFF));
			buffer.put((byte)((timestamp >> 48) & 0xFF));
			buffer.put((byte)((timestamp >> 40) & 0xFF));
			buffer.put((byte)((timestamp >> 32) & 0xFF));
			buffer.put((byte)((timestamp >> 24) & 0xFF));
			buffer.put((byte)((timestamp >> 16) & 0xFF));
			buffer.put((byte)((timestamp >> 8) & 0xFF));
			buffer.put((byte)(timestamp & 0xFF));

			// Your Callsign
			final String yourCallsign =
				DSTARUtils.formatFullLengthCallsign(log.getYourCallsign());
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
				if(yourCallsign.length() > i)
					buffer.put((byte)yourCallsign.charAt(i));
				else
					buffer.put((byte)' ');
			}

			// My Callsign
			final String myCallsign =
				DSTARUtils.formatFullLengthCallsign(log.getMyCallsign());
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
				if(myCallsign.length() > i)
					buffer.put((byte)myCallsign.charAt(i));
				else
					buffer.put((byte)' ');
			}

			// My Callsign Short
			final String myCallsignShort =
				DSTARUtils.formatShortLengthCallsign(log.getMyCallsignShort());
			for(int i = 0; i < DSTARDefines.CallsignShortLength; i++) {
				if(myCallsignShort.length() > i)
					buffer.put((byte)myCallsignShort.charAt(i));
				else
					buffer.put((byte)' ');
			}

			entryCount++;
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
			(
				(
					1 +	// Flag
					1 +	// Log No
					2 +	// Reserved
					8 +	// Timestamp
					8 +	// Your Callsign
					8 +	// My Callsign
					4	// MY Callsign Short
				) * (logs.size() <= maxLogEntry ? logs.size() : maxLogEntry)
			);
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

		// Request No
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

		// Logs
		logs.clear();
		for(int b = 0; b < maxLogEntry && buffer.remaining() >= 32; b++) {
			final AccessLogEntry log = new AccessLogEntry();

			//Flag
			log.getFlag().setValue(buffer.get());

			// Log No
			log.setLogNo(buffer.get());

			// Reserved
			for(int i = 0; i < 2; i++) {buffer.get();}

			// Timestamp
			long timestamp = (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			timestamp = (timestamp << 8) | (buffer.get() & 0xFF);
			log.setTimestamp(timestamp * 1000);

			// Your Callsign
			final StringBuffer yourCallsign =
				new StringBuffer(DSTARDefines.CallsignFullLength);
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
				yourCallsign.append((char)buffer.get());
			}
			log.setYourCallsign(DSTARUtils.formatFullLengthCallsign(yourCallsign.toString()));

			// My Callsign
			final StringBuffer myCallsign =
				new StringBuffer(DSTARDefines.CallsignFullLength);
			for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
				myCallsign.append((char)buffer.get());
			}
			log.setMyCallsign(DSTARUtils.formatFullLengthCallsign(myCallsign.toString()));

			// My Callsign Short
			final StringBuffer myCallsignShort =
				new StringBuffer(DSTARDefines.CallsignShortLength);
			for(int i = 0; i < DSTARDefines.CallsignShortLength; i++) {
				myCallsignShort.append((char)buffer.get());
			}
			log.setMyCallsignShort(DSTARUtils.formatShortLengthCallsign(myCallsignShort.toString()));

			logs.add(log);
		}

		return true;
	}

	@Override
	public AccessLog clone() {
		final AccessLog copy = (AccessLog)super.clone();

		copy.clientCode = this.clientCode;
		copy.requestID = this.requestID;
		copy.blockIndex = this.blockIndex;
		copy.blockTotal = this.blockTotal;
		copy.logs = new LinkedList<>();
		for(final AccessLogEntry e : logs) {copy.logs.add(e.clone());}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb = new StringBuilder();

		sb.append(super.toString(indentLevel));

		indentLevel += 4;

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		sb.append("\n");

		sb.append(indent);
		sb.append("ClientCode:");
		sb.append(String.format("0x%04X", getClientCode()));
		sb.append('/');
		sb.append("RequestID:");
		sb.append(String.format("0x%04X", getRequestID()));
		sb.append('/');
		sb.append("BlockIndex:");
		sb.append(getBlockIndex());
		sb.append('/');
		sb.append("BlockTotal:");
		sb.append(getBlockTotal());
		sb.append('/');
		sb.append("\n");
		sb.append(indent);
		sb.append("Logs:");
		sb.append('\n');
		for(final Iterator<AccessLogEntry> it = logs.iterator(); it.hasNext();) {
			final AccessLogEntry log = it.next();

			sb.append(log.toString(indentLevel + 4));
			if(it.hasNext()) {sb.append('\n');}
		}

		return sb.toString();
	}

}
