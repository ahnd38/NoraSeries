package org.jp.illg.nora.vr.protocol.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import lombok.NonNull;

public interface NoraVRPacket {

	public NoraVRCommandType getCommandType();


	public ByteBuffer assemblePacket();
	public NoraVRPacket parsePacket(@NonNull final ByteBuffer buffer);

	public NoraVRPacket clone();

	public InetSocketAddress getRemoteHostAddress();
	public void setRemoteHostAddress(final InetSocketAddress remoteHostAddress);

	public InetSocketAddress getLocalHostAddress();
	public void setLocalHostAddress(final InetSocketAddress localHostAddress);

	public String toString();
	public String toString(int indentLevel);
}
