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
public class CompressedAudioDataFromDongle extends DvDongleCommandForTarget {



	private final short[] channelData = new short[12];


	/**
	 * @return channelData
	 */
	public short[] getChannelData() {
		return channelData;
	}



	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
				buffer.limit() >= 50 &&
				super.analyzeHeader(buffer) &&
				super.getMessageLength() == 50 &&
				super.getMessageType() == DvDongleCommandTypeForTarget.TargetDataItem1
		){
			Arrays.fill(this.getChannelData(), (short)0x0000);

			for(int index = 0;index < 24;index++)
				buffer.get();	//コントロールは読み捨て

			for(int index =0;index < this.getChannelData().length;index++) {
				byte lsb = buffer.get();
				byte msb = buffer.get();

				this.getChannelData()[index] =
						(short)(
								(((msb << 8) & 0xFF00) | (lsb & 0x00FF)) & 0xffff
						);
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
