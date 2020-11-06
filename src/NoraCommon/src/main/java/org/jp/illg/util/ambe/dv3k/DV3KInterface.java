package org.jp.illg.util.ambe.dv3k;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Properties;

public interface DV3KInterface {

	public boolean open();
	public boolean isOpen();

	public void close();

	public String getPortName();

	public boolean setProperties(Properties properties);
	public Properties getProperties(Properties properties);

	public boolean writeDV3KPacket(final DV3KPacket packet);
	public boolean hasReadableDV3KPacket();
	public DV3KPacket readDV3KPacket();

	public boolean encodePCM2AMBEInput(final ShortBuffer pcmBuffer);
	public boolean encodePCM2AMBEOutput(final ByteBuffer ambeBuffer);

	public boolean decodeAMBE2PCMInput(final ByteBuffer ambeBuffer);
	public boolean decodeAMBE2PCMOutput(final ShortBuffer pcmBuffer);

}
