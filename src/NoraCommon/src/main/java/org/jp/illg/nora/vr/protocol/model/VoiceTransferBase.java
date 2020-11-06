package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.util.ArrayUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class VoiceTransferBase<T> extends NoraVRPacketBase implements NoraVRVoicePacket<T> {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private int frameID;

	@Getter
	@Setter
	private int longSequence;

	@Getter
	@Setter
	private int shortSequence;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private byte[] flags;

	@Getter
	@Setter
	private String repeater2Callsign;

	@Getter
	@Setter
	private String repeater1Callsign;

	@Getter
	@Setter
	private String yourCallsign;

	@Getter
	@Setter
	private String myCallsignLong;

	@Getter
	@Setter
	private String myCallsignShort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private byte[] slowdata;

	@Getter
	private Queue<T> audio;


	protected VoiceTransferBase(@NonNull final NoraVRCommandType type) {
		this(type, null);
	}

	protected VoiceTransferBase(@NonNull final NoraVRCommandType type, final Header header) {
		super(type);

		flags = new byte[3];

		audio = new LinkedList<T>();

		slowdata = new byte[3];

		if(header != null) {
			ArrayUtil.copyOf(flags, header.getFlags());
			repeater2Callsign = String.valueOf(header.getRepeater2Callsign());
			repeater1Callsign = String.valueOf(header.getRepeater1Callsign());
			yourCallsign = String.valueOf(header.getYourCallsign());
			myCallsignLong = String.valueOf(header.getMyCallsign());
			myCallsignShort = String.valueOf(header.getMyCallsignAdd());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public VoiceTransferBase<T> clone() {
		VoiceTransferBase<T> copy = null;

		copy = (VoiceTransferBase<T>)super.clone();

		copy.clientCode = this.clientCode;
		copy.frameID = this.frameID;
		copy.longSequence = this.longSequence;
		copy.shortSequence = this.shortSequence;
		copy.flags = Arrays.copyOf(this.flags, this.flags.length);
		copy.repeater2Callsign = this.repeater2Callsign;
		copy.repeater1Callsign = this.repeater1Callsign;
		copy.yourCallsign = this.yourCallsign;
		copy.myCallsignLong = this.myCallsignLong;
		copy.myCallsignShort = this.myCallsignShort;
		copy.slowdata = Arrays.copyOf(this.slowdata, this.slowdata.length);

		copy.audio = new LinkedList<T>();
		if(audio != null && !audio.isEmpty()) {
			for(final T voice : audio) {copy.audio.add(voice);}
		}

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		//Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		//FrameID
		buffer.put((byte)((getFrameID() >> 8) & 0xFF));
		buffer.put((byte)(getFrameID() & 0xFF));

		//Long Sequence
		buffer.put((byte)((getLongSequence() >> 8) & 0xFF));
		buffer.put((byte)(getLongSequence() & 0xFF));

		//Short Sequence
		buffer.put((byte)(getShortSequence()));

		//Flags
		buffer.put(getFlags()[0]);
		buffer.put(getFlags()[1]);
		buffer.put(getFlags()[2]);

		//Repeater2 Callsign
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			if(i < getRepeater2Callsign().length())
				buffer.put((byte)getRepeater2Callsign().charAt(i));
			else
				buffer.put((byte)' ');
		}

		//Repeater1 Callsign
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			if(i < getRepeater1Callsign().length())
				buffer.put((byte)getRepeater1Callsign().charAt(i));
			else
				buffer.put((byte)' ');
		}

		//Your Callsign
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			if(i < getYourCallsign().length())
				buffer.put((byte)getYourCallsign().charAt(i));
			else
				buffer.put((byte)' ');
		}

		//My Callsign
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			if(i < getMyCallsignLong().length())
				buffer.put((byte)getMyCallsignLong().charAt(i));
			else
				buffer.put((byte)' ');
		}
		//My Callsign
		for(int i = 0; i < DSTARDefines.CallsignShortLength; i++) {
			if(i < getMyCallsignShort().length())
				buffer.put((byte)getMyCallsignShort().charAt(i));
			else
				buffer.put((byte)' ');
		}

		//Reserved
		for(int i = 0; i < 5; i++) {
			buffer.put((byte)0x00);
		}

		//Slowdata
		for(int i = 0; i < 3 && i < this.slowdata.length; i++) {
			buffer.put(getSlowdata()[i]);
		}

		return assembleVoiceField(buffer);
	}

	@Override
	protected int getAssembleFieldLength() {
		return 56 + getVoiceFieldLength();
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 56)
			return false;

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		//FrameID
		int frameID = (buffer.get() & 0xFF);
		frameID = (frameID << 8) | (buffer.get() & 0xFF);
		setFrameID(frameID);

		//Long Sequence
		int longSequence = (buffer.get() & 0xFF);
		longSequence = (longSequence << 8) | (buffer.get() & 0xFF);
		setLongSequence(longSequence);

		//Short Sequence
		byte shortSequence = buffer.get();
		setShortSequence(shortSequence);

		//Flags
		getFlags()[0] = buffer.get();
		getFlags()[1] = buffer.get();
		getFlags()[2] = buffer.get();

		//Repeater2 Callsign
		final char[] repeater2Callsign = new char[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < repeater2Callsign.length; i++) {
			repeater2Callsign[i] = (char)buffer.get();
		}
		setRepeater2Callsign(String.valueOf(repeater2Callsign));

		//Repeater1 Callsign
		final char[] repeater1Callsign = new char[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < repeater1Callsign.length; i++) {
			repeater1Callsign[i] = (char)buffer.get();
		}
		setRepeater1Callsign(String.valueOf(repeater1Callsign));

		//Your Callsign
		final char[] yourCallsign = new char[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < yourCallsign.length; i++) {
			yourCallsign[i] = (char)buffer.get();
		}
		setYourCallsign(String.valueOf(yourCallsign));

		//My Callsign
		final char[] myCallsignLong = new char[DSTARDefines.CallsignFullLength];
		for(int i = 0; i < myCallsignLong.length; i++) {
			myCallsignLong[i] = (char)buffer.get();
		}
		setMyCallsignLong(String.valueOf(myCallsignLong));

		//My Callsign
		final char[] myCallsignShort = new char[DSTARDefines.CallsignShortLength];
		for(int i = 0; i < myCallsignShort.length; i++) {
			myCallsignShort[i] = (char)buffer.get();
		}
		setMyCallsignShort(String.valueOf(myCallsignShort));

		//Reserved
		for(int i = 0; i < 5; i++) {
			buffer.get();
		}

		//Slowdata
		for(int i = 0; i < 3 && i < getSlowdata().length; i++) {
			getSlowdata()[i] = buffer.get();
		}

		audio.clear();

		return parseVoiceField(buffer);
	}

	public boolean isEndSequence() {
		return ((getShortSequence() & 0x40) == 0x40);
	}

	public void setEndSequence(final boolean isEnd) {
		if(isEnd)
			setShortSequence(getShortSequence() | 0x40);
		else
			setShortSequence(getShortSequence() & ~0x40);
	}

	protected abstract boolean assembleVoiceField(@NonNull final ByteBuffer buffer);
	protected abstract int getVoiceFieldLength();
	protected abstract boolean parseVoiceField(@NonNull final ByteBuffer buffer);

	@Override
	public abstract NoraVRCodecType getCodecType();

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
		sb.append(String.format("0x%08X", getClientCode()));
		sb.append("/");
		sb.append("FrameID:");
		sb.append(String.format("0x%04X", getFrameID()));
		sb.append("/");
		sb.append("LongSequence:");
		sb.append(String.format("0x%04X", getLongSequence()));
		sb.append("/");
		sb.append("ShortSequence:");
		sb.append(String.format("0x%02X", getShortSequence()));
		sb.append("/");
		sb.append("Flags:");
		sb.append(String.format("%02X %02X %02X", getFlags()[0], getFlags()[1], getFlags()[2]));
		sb.append("/");
		sb.append("RPT2:");
		sb.append(getRepeater2Callsign());
		sb.append("/");
		sb.append("RPT1:");
		sb.append(getRepeater1Callsign());
		sb.append("/");
		sb.append("UR:");
		sb.append(getYourCallsign());
		sb.append("/");
		sb.append("MY:");
		sb.append(getMyCallsignLong());
		sb.append("_");
		sb.append(getMyCallsignShort());
		sb.append("/");
		sb.append("Slowdata:");
		sb.append(String.format("%02X %02X %02X", getSlowdata()[0], getSlowdata()[1], getSlowdata()[2]));

		return sb.toString();
	}
}
