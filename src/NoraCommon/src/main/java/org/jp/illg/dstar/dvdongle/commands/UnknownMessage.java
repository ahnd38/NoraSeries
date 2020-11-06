/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

/**
 * @author AHND
 *
 */
@Slf4j
public class UnknownMessage extends DvDongleCommandForTarget {

	private byte[] unknownMessage;

	/**
	 * @return unknownMessage
	 */
	public byte[] getUnknownMessage() {
		return unknownMessage;
	}

	/**
	 *
	 */
	public UnknownMessage() {
		super();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
				buffer.limit() >= 2 &&
				super.analyzeHeader(buffer) &&
				super.getMessageLength() >= 3 &&
				super.getMessageLength() <= buffer.limit()
		){
			this.unknownMessage = new byte[super.getMessageLength()];

			buffer.rewind();
			this.unknownMessage[0] = (byte)buffer.get();
			this.unknownMessage[1] = (byte)buffer.get();

			for(int index = 2;index < super.getMessageLength();index++) {
				this.unknownMessage[index] = buffer.get();
			}

			if(buffer.position() != super.getMessageLength() && log.isDebugEnabled())
				log.debug("mismatch buffer read position and message length!");

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
