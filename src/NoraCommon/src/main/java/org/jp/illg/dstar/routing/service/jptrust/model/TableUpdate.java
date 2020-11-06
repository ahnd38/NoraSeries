/**
 *
 */
package org.jp.illg.dstar.routing.service.jptrust.model;

/**
 * @author AHND
 *
 */
public abstract class TableUpdate extends JpTrustCommandBase implements Cloneable {

	private static final int commandIDLocal;

	static{
		commandIDLocal = JpTrustCommandBase.generateCommandID();
	}

	/**
	 *
	 */
	public TableUpdate() {
//		super.getCommandID()[0] = (byte)0xf9;
//		super.getCommandID()[1] = (byte)0xa5;
		super.getCommandID()[1] = (byte)(commandIDLocal & 0xFF);
		super.getCommandID()[0] = (byte)((commandIDLocal >> 8) & 0xFF);

		super.setCommandType(CommandType.RequestPositionUpdate);
	}

}
