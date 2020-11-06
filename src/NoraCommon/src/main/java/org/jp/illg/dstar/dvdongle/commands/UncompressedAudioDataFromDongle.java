/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

/**
 * @author AHND
 *
 */
@Slf4j
public class UncompressedAudioDataFromDongle extends DvDongleCommandForTarget {


	private final short[] audioData = new short[160];

	/**
	 * @return audioData
	 */
	public short[] getAudioData() {
		return audioData;
	}

	public UncompressedAudioDataFromDongle() {
		super();
	}


	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
				buffer.limit() >= 322 &&
				super.analyzeHeader(buffer) &&
				super.getMessageLength() == 322 &&
				super.getMessageType() == DvDongleCommandTypeForTarget.TargetDataItem0
		){
			Arrays.fill(this.getAudioData(), (short)0x0000);

			for(int index = 0;index < this.getAudioData().length;index++) {
				byte lsbData = buffer.get();
				byte msbData = buffer.get();

				this.getAudioData()[index] = (short)((msbData << 8) | lsbData);
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
