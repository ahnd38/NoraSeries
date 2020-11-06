package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.net.InetSocketAddress;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

public interface DCSPacket extends DSTARPacket, Cloneable {

	public DCSPacketType getDCSPacketType();
//	public void setDCSPacketType(DCSPacketType packetType);

	public DCSPoll getPoll();
//	public void setPoll(DCSPoll poll);

	public DCSConnect getConnect();
//	public void setConnect(DCSConnect connect);

	public InetSocketAddress getRemoteAddress();
	public void setRemoteAddress(InetSocketAddress remoteAddress);

	public InetSocketAddress getLocalAddress();
	public void setLocalAddress(InetSocketAddress localAddress);

	public DVPacket getDvPacket();

	public BackBoneHeader getBackBone();

	public Header getRfHeader();

	public VoiceData getVoiceData();

	public char[] getRepeater2Callsign();

	public char[] getRepeater1Callsign();

	public char[] getYourCallsign();

	public char[] getMyCallsign();

	public char[] getMyCallsignAdd();

	public void setConnectionDirection(ConnectionDirectionType dir);
	public ConnectionDirectionType getConnectionDirection();

	public DCSPacket clone();

	public int getLongSequence();
	public void setLongSequence(int longSequence);

	public String getText();
	public void setText(String text);

	public String toString(int indentLevel);
}
