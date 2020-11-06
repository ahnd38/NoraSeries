package org.jp.illg.dstar.routing.service.jptrust.model;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

import lombok.Getter;
import lombok.Setter;

public abstract class Status extends StatusBase {

	@Getter
	private char[] repeater1Callsign;

	@Getter
	private char[] repeater2Callsign;

	@Getter
	private char[] yourCallsign;

	@Getter
	private char[] myCallsignLong;

	@Getter
	private char[] myCallsignShort;

	@Getter
	private char[] shortMessage;

	@Getter
	@Setter
	private double latitude;

	@Getter
	@Setter
	private double longitude;

	public Status() {
		super();

		repeater1Callsign =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		repeater2Callsign =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		yourCallsign =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		myCallsignLong =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		myCallsignShort =
			Arrays.copyOf(DSTARDefines.EmptyShortCallsign.toCharArray(), DSTARDefines.CallsignShortLength);
		shortMessage = new char[DSTARDefines.DvShortMessageLength];
		Arrays.fill(shortMessage, ' ');
	}

	public Status clone() {
		Status copy = (Status)super.clone();

		copy.repeater1Callsign = Arrays.copyOf(repeater1Callsign, repeater1Callsign.length);
		copy.repeater2Callsign = Arrays.copyOf(repeater2Callsign, repeater2Callsign.length);
		copy.yourCallsign = Arrays.copyOf(yourCallsign, yourCallsign.length);
		copy.myCallsignLong = Arrays.copyOf(myCallsignLong, myCallsignLong.length);
		copy.myCallsignShort = Arrays.copyOf(myCallsignShort, myCallsignShort.length);
		copy.shortMessage = Arrays.copyOf(shortMessage, shortMessage.length);
		copy.latitude = latitude;
		copy.longitude = longitude;

		return copy;
	}

	protected boolean assemblePacketData(final ByteBuffer buffer) {
		if(buffer == null || buffer.remaining() < getPacketDataSize())
			return false;

		putCallsignLong(buffer, getRepeater2Callsign());
		putCallsignLong(buffer, getRepeater1Callsign());
		putCallsignLong(buffer, getYourCallsign());
		putCallsignLong(buffer, getMyCallsignLong());
		putCallsignShort(buffer, getMyCallsignShort());

		byte[] shortMessageSJIS = null;
		try{
			shortMessageSJIS =
				String.valueOf(getShortMessage()).getBytes("SJIS");
		}catch(UnsupportedEncodingException ex) {
			new RuntimeException(ex);
		}
		for(int i = 0; i < 20; i++) {
			if(shortMessageSJIS != null && i < shortMessageSJIS.length)
				buffer.put((byte)shortMessageSJIS[i]);
			else
				buffer.put((byte)' ');
		}

		long latitude = (long)((getLatitude() == 0.0d ? 360.0d : getLatitude()) * 10000);
		buffer.put((byte)(latitude & 0xFF));
		buffer.put((byte)((latitude >>= 8) & 0xFF));
		buffer.put((byte)((latitude >>= 8) & 0xFF));
		buffer.put((byte)((latitude >>= 8) & 0xFF));

		long longitude = (long)((getLongitude() == 0.0d ? 360.0d : getLongitude()) * 10000);
		buffer.put((byte)(longitude & 0xFF));
		buffer.put((byte)((longitude >>= 8) & 0xFF));
		buffer.put((byte)((longitude >>= 8) & 0xFF));
		buffer.put((byte)((longitude >>= 8) & 0xFF));

		return true;
	}

	protected int getPacketDataSize() {
		return
			8 +		// Repeater2 Callsign
			8 +		// Repeater1 Callsign
			8 +		// Your Callsign
			8 +		// My Long Callsign
			4 +		// My Short Callsign
			20 +	// Short Message
			4 +		// Latitude
			4;		// Longitude
	}
}
