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
public class HeartbeatCommandRequest extends AccessPointCommandBase
implements Cloneable{

	@Override
	public HeartbeatCommandRequest clone() {
		HeartbeatCommandRequest copy = null;

		copy = (HeartbeatCommandRequest)super.clone();

		return copy;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.icom.ap.cmd.AccessPointCommand#getSendCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		return new byte[] {
			(byte)0x02,
			(byte)0x02,
			(byte)0xff
		};
	}

	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

}
