package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import org.jp.illg.dstar.routing.service.jptrust.JpTrustDefine;

import lombok.Getter;
import lombok.Setter;

public abstract class StatusBase implements Cloneable{

	@Getter
	private char[] statusHeaderID;

	@Getter
	@Setter
	private InetAddress ipAddress;

	@Getter
	@Setter
	private long entryUpdateTime;

	@Getter
	@Setter
	private int port;

	@Getter
	private byte[] reserve;

	{
		statusHeaderID = Arrays.copyOf(JpTrustDefine.statusHeaderID, 6);
		reserve = new byte[2];
	}

	public StatusBase() {
		super();
	}

	public StatusBase clone() {
		StatusBase copy = null;
		try {
			copy = (StatusBase)super.clone();

			copy.statusHeaderID = Arrays.copyOf(statusHeaderID, statusHeaderID.length);
			copy.entryUpdateTime = entryUpdateTime;
			copy.ipAddress = ipAddress;
			copy.port = port;
			copy.reserve = Arrays.copyOf(reserve, reserve.length);

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException();
		}
	}

	public ByteBuffer assemblePacket() {
		final int packetSize =
			6 +		// StatusID
			2 +		// StatusType
			8 +		// EntryUpdateTime
			16 +	// IP Address
			2 +		// Port
			2 +		// Reserve
			getPacketDataSize();

		final ByteBuffer buffer = ByteBuffer.allocate(packetSize);

		//StatusHeaderID
		for(int i = 0; i < 6; i++) {
			if(i < getStatusHeaderID().length)
				buffer.put((byte)getStatusHeaderID()[i]);
			else
				buffer.put((byte)0x00);
		}

		//StatusType
		final StatusType statusType = getStatusType();
		if(statusType == null) {throw new RuntimeException("getStatusType return null.");}
		final String statusStr = String.format(Locale.getDefault(), "%02X", statusType.getValue());
		for(int i = 0; i < 2; i++) {
			if(i < statusStr.length())
				buffer.put((byte)statusStr.charAt(i));
			else
				buffer.put((byte)0x00);
		}

		//EntryUpdateTime
		long updateTime = getEntryUpdateTime();
		buffer.put((byte)(updateTime & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));
		buffer.put((byte)((updateTime >>= 8) & 0xFF));

		//IP Address
		byte[] ip = getIpAddress() != null ? getIpAddress().getAddress() : null;
		for(int i = 0; i < 16; i++) {
			if(ip != null && i < ip.length)
				buffer.put((byte)ip[i]);
			else
				buffer.put((byte)0x00);
		}

		//Port
		for(int i = 0; i< 2; i++)
			buffer.put((byte)0x00);

		//Reserve
		for(int i = 0; i < 2; i++) {
			if(getReserve() != null && i < getReserve().length)
				buffer.put((byte)getReserve()[i]);
			else
				buffer.put((byte)0x00);
		}

		if(assemblePacketData(buffer)) {
			buffer.flip();

			return buffer;
		}else {
			return null;
		}
	}

	public abstract StatusType getStatusType();

	protected abstract boolean assemblePacketData(final ByteBuffer buffer);

	protected abstract int getPacketDataSize();

	protected void putCallsignShort(final ByteBuffer buffer, final char[] callsign) {
		putCallsign(buffer, callsign, 4);
	}

	protected void putCallsignLong(final ByteBuffer buffer, final char[] callsign) {
		putCallsign(buffer, callsign, 8);
	}

	private void putCallsign(final ByteBuffer buffer, final char[] callsign, final int length) {
		for(int i = 0; i < length; i++) {
			if(callsign != null && i < callsign.length)
				buffer.put((byte)callsign[i]);
			else
				buffer.put((byte)' ');
		}
	}
}
