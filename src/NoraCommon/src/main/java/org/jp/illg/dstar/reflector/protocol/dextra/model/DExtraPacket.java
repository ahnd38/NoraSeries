package org.jp.illg.dstar.reflector.protocol.dextra.model;

import java.net.InetSocketAddress;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacketImpl.DExtraPacketType;

public interface DExtraPacket extends DSTARPacket, Cloneable {

	public DExtraPacketType getDExtraPacketType();

	public DExtraPoll getPoll();

	public void setPoll(DExtraPoll poll);

	public DExtraConnectInfo getConnectInfo();

	public void setConnectInfo(DExtraConnectInfo connectInfo);

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


	public DExtraPacket clone();

	public String toString();
	public String toString(int indentLevel);

}
