package org.jp.illg.dstar.reflector.protocol.dplus.model;

import java.net.InetSocketAddress;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

public interface DPlusPacket extends DSTARPacket, Cloneable {

	public DPlusPacketType getDPlusPacketType();

	public DPlusPoll getPoll();

	public DPlusConnect getConnect();

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

	public DPlusPacket clone();

	public String toString(int indentLevel);
}
