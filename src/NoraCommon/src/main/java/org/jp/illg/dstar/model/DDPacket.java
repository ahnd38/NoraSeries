package org.jp.illg.dstar.model;

import org.jp.illg.util.ToStringWithIndent;

import lombok.Getter;
import lombok.Setter;

public class DDPacket implements Cloneable, ToStringWithIndent{

	/**
	 * RF Header
	 */
	@Getter
	@Setter
	private Header rfHeader;

	/**
	 * BackBone
	 */
	@Getter
	@Setter
	private BackBoneHeader backBone;

	/**
	 * Data
	 */
	@Getter
	@Setter
	private byte[] data;


	public DDPacket(
		final Header header,
		final BackBoneHeader backbone,
		final byte[] data
	) {
		super();

		setRfHeader(header);
		setBackBone(backbone);
		setData(data);
	}

	@Override
	public DDPacket clone() {
		DDPacket copy = null;

		try {
			copy = (DDPacket)super.clone();

			clone(copy, this);

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException();
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(final int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuffer sb = new StringBuffer();

		for(int c = 0; c < indent; c++) {sb.append(' ');}

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append("\n");
		sb.append(getBackBone().toString(indent + 4));

		sb.append("\n");
		sb.append(this.getRfHeader().toString(indent + 4));

		return sb.toString();
	}

	private static void clone(final DDPacket dst, final DDPacket src) {

		final Header header = src.getRfHeader();
		if(header != null)
			dst.setRfHeader(header.clone());

		final BackBoneHeader backboneHeader = src.getBackBone();
		if(backboneHeader != null)
			dst.setBackBone(backboneHeader.clone());
	}
}
