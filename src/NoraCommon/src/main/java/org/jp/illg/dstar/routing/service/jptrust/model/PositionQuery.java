/**
 *
 */
package org.jp.illg.dstar.routing.service.jptrust.model;

/**
 * @author AHND
 *
 */
public abstract class PositionQuery
	extends JpTrustCommandBase
	implements Cloneable {

	private static final int commandIDLocal;

	static{
		commandIDLocal = JpTrustCommandBase.generateCommandID();
	}

	/**
	 *
	 */
	public PositionQuery() {
		super();

//		super.getCommandID()[0] = (byte)0x56;
//		super.getCommandID()[1] = (byte)0x5c;
		super.getCommandID()[1] = (byte)(commandIDLocal & 0xFF);
		super.getCommandID()[0] = (byte)((commandIDLocal >> 8) & 0xFF);

		super.setCommandType(CommandType.RequestPositionInformation);
	}

}
