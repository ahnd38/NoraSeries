package org.jp.illg.dstar.jarl.xchange.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.jp.illg.dstar.model.DSTARPacketBase;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class XChangePacketBase extends DSTARPacketBase implements XChangePacket {

	private static final byte[] headerStatic = "DSTR".getBytes(StandardCharsets.US_ASCII);

	private static final String logTag = XChangePacketBase.class.getSimpleName() + " : ";

	private static final int controlFieldLength = 10;

	/**
	 * パケット番号
	 */
	@Getter
	@Setter
	private int packetNo;

	/**
	 * 送受信方向
	 */
	@Getter
	@Setter
	private XChangePacketDirection direction;

	/**
	 * ルートフラグ
	 */
	@Getter
	@Setter
	private XChangeRouteFlagData routeFlags;

	/**
	 * データ長
	 */
	@Getter
	@Setter
	private int length;

	/**
	 * リモートホストアドレス
	 */
	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	/**
	 * デフォルトコンストラクタ
	 */
	public XChangePacketBase() {
		super();

		packetNo = 0;
		direction = null;
		routeFlags = null;
		length = 0;
		remoteAddress = null;

		setHeader(headerStatic);
	}

	@Override
	public final XChangePacket parsePacket(@NonNull final ByteBuffer buffer) {
		if(!buffer.hasRemaining()) {return null;}

		final int bufferPos = buffer.position();

		final int dataFieldLength = getDataFieldReceiveDataLength();

		if(
			buffer.remaining() < controlFieldLength
		) {
			onReceiveIllegalData(logTag, buffer, "Too short data length");

			buffer.position(bufferPos);
			return null;
		}

		if(
			buffer.get() != 'D' || buffer.get() != 'S' || buffer.get() != 'T' || buffer.get() != 'R'
		) {
			onReceiveIllegalData(logTag, buffer, "Illegal Header");

			buffer.position(bufferPos);
			return null;
		}

		setPacketNo(((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF));
		setDirection(XChangePacketDirection.valueOf((char)buffer.get()));
		if(getDirection() == null) {
			buffer.position(bufferPos);

			onReceiveIllegalData(logTag, buffer, "Illegal direction");

			return null;
		}

		final byte flags = buffer.get();
		final XChangePacketType type = XChangePacketType.valueOf(flags);
		if(type == null) {
			buffer.position(bufferPos);

			onReceiveIllegalData(logTag, buffer, "Unsupported packet type");

			return null;
		}
		else if(type != getType()) {
			buffer.position(bufferPos);
			return null;
		}
		setRouteFlags(XChangeRouteFlag.valueOf(flags));

		setLength(((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF));
		if(getLength() != buffer.remaining()) {
			buffer.position(bufferPos);

			onReceiveIllegalData(logTag, buffer, "Illegal receive data length");

			return null;
		}

		if(dataFieldLength >= 1 && getLength() != dataFieldLength){
			buffer.position(bufferPos);
			return null;
		}

		if(parseDataField(buffer))
			return clone();
		else {
			buffer.position(bufferPos);
			return null;
		}
	}

	@Override
	public final ByteBuffer assemblePacket() {
		final int dataFieldLength = getDataFieldTransmitDataLength();
		if(dataFieldLength < 0) {throw new RuntimeException();}

		final ByteBuffer buffer =
			ByteBuffer.allocate(controlFieldLength + dataFieldLength);

		for(int c = 0; c < getHeader().length && buffer.hasRemaining(); c++) {
			buffer.put((byte)getHeader()[c]);
		}

		buffer.put((byte)((packetNo >> 8) & 0xFF));
		buffer.put((byte)(packetNo & 0xFF));

		if(getDirection() == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Direction is must not null.");

			return null;
		}
		buffer.put((byte)getDirection().getValue());

		if(getType() == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Type is must not null.");

			return null;
		}
		byte flags = getType().getValue();

		if(getRouteFlags() == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "RouteFlags is must not null.");

			return null;
		}

		flags |= getRouteFlags().getValue();
		buffer.put(flags);

		buffer.put((byte)((dataFieldLength >> 8) & 0xFF));
		buffer.put((byte)(dataFieldLength & 0xFF));

		if(dataFieldLength == 0 || (buffer.hasRemaining() && assembleDataField(buffer))) {
			buffer.flip();

			return buffer;
		}
		else {
			return null;
		}
	}

	@Override
	public XChangePacketBase clone() {
		final XChangePacketBase copy = (XChangePacketBase)super.clone();

		copy.packetNo = packetNo;
		if(direction != null) {copy.direction = direction;}
		if(routeFlags != null) {copy.routeFlags = routeFlags.clone();}
		copy.length = length;

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	protected static void onReceiveIllegalData(
		final String logTag, final ByteBuffer buffer, final String reason
	) {
		if(log.isDebugEnabled()) {
			log.debug(
				logTag +
				"Illegal data received, " + reason + ".\n" + FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < lvl; c++) {sb.append(' ');}

		sb.append("[Type]:");
		sb.append(getType());

		sb.append('/');

		sb.append("[PacketNo]:");
		sb.append(String.format("0x%04X", getPacketNo()));

		sb.append('/');

		sb.append("[Dir]:");
		sb.append(getDirection());

		sb.append('/');

		sb.append("[RouteFlags]:");
		sb.append('{');
		sb.append(getRouteFlags());
		sb.append('}');

		sb.append('/');

		sb.append("[Length]:");
		sb.append(getLength());

		sb.append('/');

		sb.append("[RemoteAddress]");
		sb.append(getRemoteAddress());

		return sb.toString();
	}

	/**
	 * パケットタイプ
	 */
	public abstract XChangePacketType getType();

	protected abstract int getDataFieldTransmitDataLength();
	protected abstract boolean parseDataField(final ByteBuffer buffer);

	protected abstract int getDataFieldReceiveDataLength();
	protected abstract boolean assembleDataField(final ByteBuffer buffer);

	protected static void copyFromBuffer(final char[] dst, final ByteBuffer src, final int length) {
		for(int c = 0; c < length && c < dst.length && src.hasRemaining(); c++) {
			dst[c] = (char)src.get();
		}
	}

	protected static void copyFromBuffer(final byte[] dst, final ByteBuffer src, final int length) {
		for(int c = 0; c < length && c < dst.length && src.hasRemaining(); c++) {
			dst[c] = (byte)src.get();
		}
	}

	protected static void copyToBuffer(final ByteBuffer dst, final byte[] src, final int length) {
		for(int c = 0; c < length && dst.hasRemaining(); c++) {
			if(c < src.length)
				dst.put((byte)src[c]);
			else
				dst.put((byte)0x00);
		}
	}

	protected static void copyToBuffer(final ByteBuffer dst, final char[] src, final int length) {
		for(int c = 0; c < length && dst.hasRemaining(); c++) {
			if(c < src.length)
				dst.put((byte)src[c]);
			else
				dst.put((byte)0x00);
		}
	}
}
