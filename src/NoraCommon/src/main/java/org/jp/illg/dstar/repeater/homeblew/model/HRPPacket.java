package org.jp.illg.dstar.repeater.homeblew.model;

import java.net.InetAddress;

import org.jp.illg.dstar.model.DSTARPacket;

public interface HRPPacket extends DSTARPacket{

	public long getCreatedTimestamp();

	public HRPPacketType getHrpPacketType();

	public HRPPollData getPollData();

	public HRPTextData getTextData();

	public HRPStatusData getStatusData();

	public HRPRegisterData getRegisterData();

	public int getErrors();
	public void setErrors(int error);

	public InetAddress getRemoteAddress();
	public void setRemoteAddress(InetAddress remoteAddress);

	public String toString(int indent);

	public int getRemotePort();
	public void setRemotePort(int remotePort);

	public HRPPacket clone();
}
