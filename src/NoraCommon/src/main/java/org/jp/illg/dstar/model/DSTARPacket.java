package org.jp.illg.dstar.model;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.ToStringWithIndent;

public interface DSTARPacket extends Cloneable, ToStringWithIndent{

	public long getCreateTimeNanos();
	public long getCreateTimeMillis();

	public DSTARPacketType getPacketType();
	public void setPacketType(DSTARPacketType packetType);

	public DSTARProtocol getProtocol();
	public void setProtocol(DSTARProtocol dstarProtocol);

	public UUID getLoopBlockID();
	public void setLoopBlockID(UUID loopBlockID);
	public UUID getLoopblockID();
	public void setLoopblockID(UUID loopblockID);

	public ConnectionDirectionType getConnectionDirection();
	public void setConnectionDirection(ConnectionDirectionType connectionDirectionType);

	public DVPacket getDVPacket();
	public void setDVPacket(DVPacket dvPacket);

	public DDPacket getDDPacket();
	public void setDDPacket(DDPacket ddPacket);

	public HeardPacket getHeardPacket();
	public void setHeardPacket(HeardPacket heardPacket);

	public Header getRFHeader();
	public Header getRfHeader();

	public BackBoneHeader getBackBoneHeader();
	public BackBoneHeader getBackBone();

	public int getFrameID();
	public int getSequenceNumber();

	public boolean isLastFrame();
	public boolean isEndVoicePacket();

	public VoiceData getDVData();
	public VoiceData getVoiceData();
	public byte[] getDDData();

	public DSTARPacket clone();

	public String toString();
	public String toString(int indentLevel);
}
