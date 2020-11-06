package org.jp.illg.dstar.reflector.protocol.dextra.model;

import org.jp.illg.dstar.model.ConnectInfo;

public class DExtraConnectInfo extends ConnectInfo implements Cloneable{

	private int revision;


	@Override
	public DExtraConnectInfo clone() {
		DExtraConnectInfo copy = null;

		copy = (DExtraConnectInfo)super.clone();

		copy.revision = this.revision;

		return copy;
	}


	/**
	 * @return revision
	 */
	public int getRevision() {
		return revision;
	}


	/**
	 * @param revision セットする revision
	 */
	public void setRevision(int revision) {
		this.revision = revision;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb = new StringBuilder();

		sb.append(super.toString(indentLevel));

		sb.append("/[Revision]:");
		sb.append(getRevision());

		return sb.toString();
	}
}
