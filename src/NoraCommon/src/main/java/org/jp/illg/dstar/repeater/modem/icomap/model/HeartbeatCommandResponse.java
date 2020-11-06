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
public class HeartbeatCommandResponse extends AccessPointCommandBase
implements Cloneable{

	@Override
	public HeartbeatCommandResponse clone() {
		HeartbeatCommandResponse copy = null;

		copy = (HeartbeatCommandResponse)super.clone();

		return copy;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		throw new UnsupportedOperationException();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.icom.ap.commands.AccessPointCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
				buffer.limit() >= 4 &&
				buffer.get() == (byte)0x03 &&
				buffer.get() == (byte)0x03 &&
				(buffer.get() & 0x0) == 0x0 &&
				buffer.get() == (byte)0xff
		){

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}else {
			buffer.rewind();
			return null;
		}
	}

}
