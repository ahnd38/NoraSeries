package org.jp.illg.dstar.model;

import org.jp.illg.util.FormatUtil;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class BackBoneManagementData implements Cloneable{

	@Getter
	@Setter
	private int magicNumber;

	@Getter
	@Setter
	private BackBonePacketDirectionType directionType;

	@Getter
	@Setter
	private BackBonePacketType type;

	@Getter
	@Setter
	private int length;


	public BackBoneManagementData(
		final int magicNumber,
		final BackBonePacketDirectionType directionType,
		final BackBonePacketType type,
		final int length
	) {
		this.magicNumber = magicNumber;
		this.directionType = directionType;
		this.type = type;
		this.length = length;
	}

	@Override
	public BackBoneManagementData clone() {
		BackBoneManagementData cloneInstance = null;
		try {
			cloneInstance = (BackBoneManagementData)super.clone();

			cloneInstance.magicNumber = magicNumber;
			cloneInstance.directionType = directionType;
			cloneInstance.type = type;
			cloneInstance.length = length;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException();
		}

		return cloneInstance;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		FormatUtil.addIndent(sb, indent);

		sb.append(String.format("MNumber=0x%04X", getMagicNumber()));
		sb.append('/');
		sb.append("Dir=");
		sb.append(getDirectionType());
		sb.append('/');
		sb.append("Type=");
		sb.append(getType());
		sb.append('/');
		sb.append("Length=");
		sb.append(getLength());

		return sb.toString();
	}
}
