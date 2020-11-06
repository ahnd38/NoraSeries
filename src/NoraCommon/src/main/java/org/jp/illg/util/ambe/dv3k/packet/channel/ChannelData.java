package org.jp.illg.util.ambe.dv3k.packet.channel;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ChannelData extends DV3KChannelPacketBase {

	private static final int bytesMax = 24;
	private static final int bytesMin = 5;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Queue<Byte> channelData;

	public ChannelData() {
		super(DV3KChannelPacketType.ChannelData);

		setChannelData(new LinkedList<Byte>());
	}

	@Override
	public ChannelData clone() {
		ChannelData copy = null;

		copy = (ChannelData)super.clone();

		copy.channelData = new LinkedList<>(getChannelData());

		return copy;
	}

	@Override
	protected int getRequestChannelFieldDataLength() {
		return 1 + getBytes();
	}

	@Override
	protected boolean assembleChannelFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < getRequestChannelFieldDataLength()) {return false;}

		final int bytes = getBytes();

		buffer.put((byte)(bytes * 8));

		for(
			int i = 0; i < bytes && !getChannelData().isEmpty() && buffer.hasRemaining(); i++
		) {
			final byte ambe = getChannelData().poll();

			buffer.put(ambe);
		}

		return true;
	}

	@Override
	protected boolean parseChannelFieldData(ByteBuffer buffer, int fieldLength) {
		if(
			buffer.remaining() < fieldLength ||
			buffer.remaining() < (1 + bytesMin)
		) {return false;}

		getChannelData().clear();

		final int bits = buffer.get();
		final int samples = bits % 8 > 0 ? bits / 8 + 1 : bits / 8;

		for(int i = 0; i < samples && buffer.hasRemaining(); i++) {
			final byte ambe = buffer.get();

			getChannelData().add(ambe);
		}

		return true;
	}

	private int getBytes() {
		return getChannelData().size() > bytesMax ? bytesMax : getChannelData().size();
	}
}
