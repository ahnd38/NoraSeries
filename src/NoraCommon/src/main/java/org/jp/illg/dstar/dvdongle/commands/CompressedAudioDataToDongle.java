/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.util.Arrays;

/**
 * @author AHND
 *
 */
public class CompressedAudioDataToDongle extends DvDongleCommandForHost {


	private final short[] channelData = new short[12];


	/**
	 * @return channelData
	 */
	public short[] getChannelData() {
		return channelData;
	}


	/**
	 *
	 */
	public CompressedAudioDataToDongle() {
		super();

		super.setMessageLength(50);
		super.setMessageType(DvDongleCommandTypeForHost.HostDataItem1);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[super.getMessageLength()];
		Arrays.fill(data, (byte)0x00);

		int header = super.getHeader();

		data[0] = (byte)(header & 0xFF);
		data[1] = (byte)((header >>> 8) & 0xFF);

		//[0] AMBE Header
		data[2] = (byte)0xEC;
		data[3] = (byte)0x13;

		//[1] Control Word
		data[4] = (byte)0x00;
		//[1] Power Control
		data[5] = (byte)0x00;

		//[2]Rate Info
		data[6] = (byte)0x30;
		data[7] = (byte)0x10;
		//[3]
		data[8] = (byte)0x00;
		data[9] = (byte)0x40;
		//[4]
		data[10] = (byte)0x00;
		data[11] = (byte)0x00;
		//[5]
		data[12] = (byte)0x00;
		data[13] = (byte)0x00;
		//[6]
		data[14] = (byte)0x48;
		data[15] = (byte)0x00;

		//[7] unused
		//[8] unused
		//[9] unused

		///[10] DTMF Control
		data[22] = (byte)0xFF;
		data[23] = (byte)0x00;

		//[11] Control 2
		data[24] = (byte)0x20;
		data[25] = (byte)0x80;


		for(int si = 0,bi = 26;si < this.getChannelData().length;si++) {
			byte lsb = (byte)(this.getChannelData()[si] & 0xff);
			byte msb = (byte)((this.getChannelData()[si] >>> 8) & 0xff);

			data[bi++] = lsb;
			data[bi++] = msb;
		}

		return data;
	}

}
