package org.jp.illg.dstar.model;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.Setter;

public class BackBonePacket extends DSTARPacketBase{

	@Getter
	@Setter
	private byte[] header;

	@Getter
	@Setter
	private BackBoneManagementData managementData;

	@Getter
	@Setter
	private byte[] errorData;

	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	@Getter
	@Setter
	private InetSocketAddress localAddress;


	public BackBonePacket(
		final UUID loopBlockID,
		final byte[] header,
		final BackBoneManagementData managementData,
		final byte[] errorData,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket dvPacket
	) {
		super(
			DSTARProtocol.ICOM,
			loopBlockID,
			ConnectionDirectionType.Unknown,
			dvPacket
		);

		this.header = header;
		this.managementData = managementData;
		this.errorData = errorData;
		this.remoteAddress = remoteAddress;
		this.localAddress = localAddress;
	}

	public BackBonePacket(
		final UUID loopBlockID,
		final byte[] header,
		final BackBoneManagementData managementData,
		final byte[] errorData,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DDPacket ddPacket
	) {
		super(
			DSTARProtocol.ICOM,
			loopBlockID,
			ConnectionDirectionType.Unknown,
			ddPacket
		);

		this.header = header;
		this.managementData = managementData;
		this.errorData = errorData;
		this.remoteAddress = remoteAddress;
		this.localAddress = localAddress;
	}

	public BackBonePacket(
		final UUID loopBlockID,
		final byte[] header,
		final BackBoneManagementData managementData,
		final byte[] errorData,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final HeardPacket heardPacket
	) {
		super(
			DSTARProtocol.ICOM,
			loopBlockID,
			ConnectionDirectionType.Unknown,
			heardPacket
		);

		this.header = header;
		this.managementData = managementData;
		this.errorData = errorData;
		this.remoteAddress = remoteAddress;
		this.localAddress = localAddress;
	}

	public BackBonePacket(
		final byte[] header,
		final BackBoneManagementData managementData
	) {
		super(
			DSTARPacketType.Other,
			DSTARProtocol.ICOM,
			null,
			ConnectionDirectionType.Unknown
		);

		this.header = header;
		this.managementData = managementData;
	}

	public BackBonePacket(
		final BackBoneManagementData managementData
	) {
		super(
			DSTARPacketType.Other,
			DSTARProtocol.ICOM,
			null,
			ConnectionDirectionType.Unknown
		);

		this.managementData = managementData;
	}

	@Override
	public BackBonePacket clone() {
		BackBonePacket cloneInstance = (BackBonePacket)super.clone();

		final byte[] header = this.header;
		if(header != null)
			cloneInstance.header = Arrays.copyOf(header, header.length);

		final BackBoneManagementData managementData = this.managementData;
		if(managementData != null)
			cloneInstance.managementData = managementData.clone();

		final byte[] errorData = this.errorData;
		if(errorData != null)
			cloneInstance.errorData = Arrays.copyOf(errorData, errorData.length);

		return cloneInstance;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(final int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		FormatUtil.addIndent(sb, indent);

		sb.append("[ICOMPacket]\n");

		FormatUtil.addIndent(sb, indent);
		sb.append("RemoteAddress=");
		sb.append(getRemoteAddress());

		sb.append('/');

		sb.append("LocalAddress=");
		sb.append(getLocalAddress());

		sb.append("\n");

		FormatUtil.addIndent(sb, indent);
		sb.append("Header=\n");
		sb.append(FormatUtil.bytesToHexDump(header, indent + 4));

		sb.append("\n");

		FormatUtil.addIndent(sb, indent);
		sb.append("ManagementData=\n");
		sb.append(getManagementData().toString(indent + 4));

		if(errorData != null) {
			sb.append("\n");
			FormatUtil.addIndent(sb, indent);
			sb.append("ErrorData=\n");
			sb.append(FormatUtil.bytesToHexDump(getErrorData(), indent + 4));
		}

		sb.append('\n');
		sb.append(super.toString(indent));

		return sb.toString();
	}
}
