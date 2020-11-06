/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
@Deprecated
public class InitializeCommandRequest extends AccessPointCommandBase
implements Cloneable{

	@Override
	public InitializeCommandRequest clone() {
		InitializeCommandRequest copy = null;

		copy = (InitializeCommandRequest)super.clone();

		return copy;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.icom.ap.cmd.AccessPointCommand#getCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		return new byte[]{
			(byte)0xff,
			(byte)0xff,
			(byte)0xff
		};
	}

	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

}
