package org.jp.illg.dstar.model;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.FormatUtil;

public class InternalPacket extends DSTARPacketBase {

	public InternalPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket dvPacket
	) {
		super(DSTARProtocol.Internal, loopBlockID, connectionDirection, dvPacket);
	}

	public InternalPacket(
		final UUID loopBlockID,
		final DVPacket dvPacket
	) {
		this(loopBlockID, ConnectionDirectionType.Unknown, dvPacket);
	}

	public InternalPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DDPacket ddPacket
	) {
		super(DSTARProtocol.Internal, loopBlockID, connectionDirection, ddPacket);
	}

	public InternalPacket(
		final UUID loopBlockID,
		final DDPacket ddPacket
	) {
		this(loopBlockID, ConnectionDirectionType.Unknown, ddPacket);
	}

	public InternalPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final HeardPacket heardPacket
	) {
		super(DSTARProtocol.Internal, loopBlockID, connectionDirection, heardPacket);
	}

	public InternalPacket(
		final UUID loopBlockID,
		final HeardPacket heardPacket
	) {
		this(loopBlockID, ConnectionDirectionType.Unknown, heardPacket);
	}

	@Override
	public String toString(final int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		FormatUtil.addIndent(sb, indent);

		sb.append('[');
		sb.append(getClass().getSimpleName());
		sb.append("]");
		sb.append("\n");

		sb.append(super.toString(indent + 4));

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}

}
