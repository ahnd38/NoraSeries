/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.util.Arrays;

/**
 * @author AHND
 *
 */
public class UnCompressedAudioDataToDongle extends DvDongleCommandForHost {

	private final short[] audioData = new short[160];

	/**
	 * @return audioData
	 */
	public short[] getAudioData() {
		return audioData;
	}

	/**
	 *
	 */
	public UnCompressedAudioDataToDongle() {
		super();

		super.setMessageLength(322);
		super.setMessageType(DvDongleCommandTypeForHost.HostDataItem0);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[super.getMessageLength()];
		Arrays.fill(data, (byte)0x00);

		int header = super.getHeader();

		data[0] = (byte)((header >>> 8) & 0xFF);
		data[1] = (byte)(header & 0xFF);

		for(int si = 0,bi = 2;si < this.getAudioData().length;si++) {
			byte lsb = (byte)(this.getAudioData()[si] & 0xff);
			byte msb = (byte)((this.getAudioData()[si] >>> 8) & 0xff);

			data[bi++] = lsb;
			data[bi++] = msb;
		}

		return data;
	}
}
