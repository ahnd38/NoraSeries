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
public class InitializeCommandResponse extends AccessPointCommandBase
implements Cloneable{

	@Override
	public InitializeCommandResponse clone() {
		InitializeCommandResponse copy = null;

		copy = (InitializeCommandResponse)super.clone();

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
				buffer.limit() >= 3 &&
				buffer.get() == (byte)0xff &&
				buffer.get() == (byte)0xff &&
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
