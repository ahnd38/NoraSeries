package org.jp.illg.dstar.routing.service.jptrust.model;

public abstract class PositionQueryExtend extends JpTrustCommandBase implements Cloneable {

	private static final int commandIDLocal;

	static {
		commandIDLocal = JpTrustCommandBase.generateCommandID();
	}

	public PositionQueryExtend() {
		super();

		super.getCommandID()[1] = (byte)(commandIDLocal & 0xFF);
		super.getCommandID()[0] = (byte)((commandIDLocal >> 8) & 0xFF);

		super.setCommandType(CommandType.PositionQueryExtend);
	}
}
