package org.jp.illg.nora.vr.protocol.model;

import java.util.Queue;

import org.jp.illg.nora.vr.model.NoraVRCodecType;

public interface NoraVRVoicePacket<T> extends NoraVRPacket {

	public NoraVRVoicePacket<?> clone();

	public long getClientCode();
	public void setClientCode(long clientCode);

	public int getFrameID();
	public void setFrameID(int frameID);

	public int getLongSequence();
	public void setLongSequence(int longSequence);

	public int getShortSequence();
	public void setShortSequence(int shortSequence);

	public byte[] getFlags();

	public String getRepeater2Callsign();
	public void setRepeater2Callsign(String repeater2Callsign);

	public String getRepeater1Callsign();
	public void setRepeater1Callsign(String repeater1Callsign);

	public String getYourCallsign();
	public void setYourCallsign(String yourCallsign);

	public String getMyCallsignLong();
	public void setMyCallsignLong(String myCallsignLong);

	public String getMyCallsignShort();
	public void setMyCallsignShort(String myCallsignShort);

	public byte[] getSlowdata();

	public Queue<T> getAudio();

	public boolean isEndSequence();
	public void setEndSequence(boolean isEnd);

	public NoraVRCodecType getCodecType();
}
