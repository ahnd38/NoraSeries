package org.jp.illg.dstar.model;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ToStringWithIndent;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class BackBoneHeader implements Cloneable, ToStringWithIndent{

	@Getter
	private static final byte minSequenceNumber = DSTARDefines.MinSequenceNumber;

	@Getter
	private static final byte maxSequenceNumber = DSTARDefines.MaxSequenceNumber;

	/**
	 * Locker
	 */
	private Lock locker;

	/**
	 * ID
	 */
	@Getter
	@Setter
	private byte id;

	/**
	 * Destination Repeater ID
	 */
	@Getter
	@Setter
	private byte destinationRepeaterID;

	/**
	 * Send Repeater ID
	 */
	@Getter
	@Setter
	private byte sendRepeaterID;

	/**
	 * Send Terminal ID
	 */
	@Getter
	@Setter
	private byte sendTerminalID;

	/**
	 * Frame ID
	 */
	@Getter
	private byte[] frameID = new byte[2];

	/**
	 * Management Information
	 */
	@Getter
	private byte managementInformation;

	/**
	 * FrameIDModCodes
	 */
	private Stack<Integer> frameIDModCodes;


	public BackBoneHeader(
		@NonNull final BackBoneHeaderType type,
		@NonNull final BackBoneHeaderFrameType frameType,
		final int frameID,
		final byte sequenceNumber
	) {
		super();

		locker = new ReentrantLock();

		frameIDModCodes = new Stack<>();

		clear();

		setType(type);
		setFrameType(frameType);
		setFrameIDNumber(frameID);
		setSequenceNumber(sequenceNumber);
	}

	public BackBoneHeader(
		@NonNull final BackBoneHeaderType type,
		@NonNull final BackBoneHeaderFrameType frameType,
		final int frameID
	) {
		this(type, frameType, frameID, (byte)0x0);
	}

	public BackBoneHeader(
		@NonNull final BackBoneHeaderType type,
		@NonNull final BackBoneHeaderFrameType frameType
	) {
		this(type, frameType, 0x0);
	}

	@Override
	public BackBoneHeader clone() {
		BackBoneHeader copy = null;
		try {
			copy = (BackBoneHeader)super.clone();

			clone(copy, this);

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public void clear() {
		locker.lock();
		try {
			this.setId((byte)0x00);
			this.setDestinationRepeaterID((byte)0x00);
			this.setSendRepeaterID((byte)0x00);
			this.setSendTerminalID((byte)0x00);
			Arrays.fill(this.getFrameID(), (byte)0x00);
			this.setManagementInformation((byte)0x00);

			frameIDModCodes.clear();

		}finally {
			locker.unlock();
		}
	}

	public void setType(final BackBoneHeaderType type) {
		id = (byte)((id & ~BackBoneHeaderType.getMask()) | type.getValue());
	}

	public BackBoneHeaderType getType() {
		return BackBoneHeaderType.getTypeByValue(id);
	}

	public void setManagementInformation(final byte managementInformation) {
		this.managementInformation = managementInformation;
	}

	public boolean isEndSequence() {
		return (managementInformation & BackBoneHeaderFrameType.getMask()) ==
			BackBoneHeaderFrameType.VoiceDataLastFrame.getValue();
	}

	public void setEndSequence() {
		managementInformation =
			((byte)(managementInformation | BackBoneHeaderFrameType.VoiceDataLastFrame.getValue()));
	}

	public BackBoneHeaderFrameType getFrameType() {
		return BackBoneHeaderFrameType.valueOf(managementInformation);
	}

	public void setFrameType(final BackBoneHeaderFrameType frameType) {
		if(frameType == null) {return;}

		managementInformation =
			(byte)(((byte)managementInformation & ~(byte)BackBoneHeaderFrameType.getMask()) | (byte)frameType.getValue());
	}

	public boolean isHeaderError() {
		return (managementInformation & 0x20) != 0x0;
	}

	public void setHeaderError(final boolean isError) {
		managementInformation = (byte)(isError ? managementInformation | 0x20 : managementInformation & ~0x20);
	}

	@Deprecated
	public void setSequence(final byte managementInformation) {
		final BackBoneHeaderFrameType frameType = BackBoneHeaderFrameType.valueOf(managementInformation);

		this.managementInformation =
			(byte)(
				(managementInformation & 0x1F) |
				(
					frameType != null ?
						frameType.getValue() : (this.managementInformation & BackBoneHeaderFrameType.getMask())
				)
			);
	}

	public byte getSequenceNumber() {
		return (byte)(managementInformation & 0x1F);
	}

	public void setSequenceNumber(final byte sequence) {
		this.managementInformation = (byte)((this.managementInformation & ~0x1F) | (sequence & 0x1F));
	}

	public boolean isMaxSequence() {
		return (managementInformation & 0x1F) == getMaxSequenceNumber();
	}

	public boolean isMinSequence() {
		return (managementInformation & 0x1F) == getMinSequenceNumber();
	}

	public void setFrameID(byte[] frameID) {
		if(frameID == null || frameID.length <= 0) {return;}

		ArrayUtil.copyOf(this.frameID, frameID);
	}

	public int getFrameIDNumber() {
		return (int)(((this.frameID[0] << 8) & 0xFF00) | (this.frameID[1] & 0xFF));
	}

	public void setFrameIDNumber(int frameID) {
		this.frameID[0] = (byte)((frameID >> 8) & 0xFF);
		this.frameID[1] = (byte)(frameID & 0xFF);
	}

	public void modFrameID(final int modCode) {
		locker.lock();
		try {
			frameIDModCodes.add(modCode);

			setFrameIDNumber(getFrameIDNumber() ^ modCode);
		}finally {
			locker.unlock();
		}
	}

	public int undoModFrameID(final boolean isSetFrameID) {
		locker.lock();
		try {
			int frameID = getFrameIDNumber();

			for(final Iterator<Integer> it = frameIDModCodes.iterator(); it.hasNext();) {
				final int modCode = it.next();

				frameID = frameID ^ modCode;

				if(isSetFrameID) {it.remove();}
			}

			if(isSetFrameID) {setFrameIDNumber(frameID);}

			return frameID;
		}finally {
			locker.unlock();
		}
	}

	public int undoModFrameID() {
		return undoModFrameID(true);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indent) {
		if(indent < 0) {indent = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]");

		sb.append('\n');
		FormatUtil.addIndent(sb, indent + 4);

		sb.append("ID=0x");
		sb.append(String.format("%02X", getId()));
		sb.append('(');
		sb.append("Type=");
		sb.append(getType());
		sb.append(')');

		sb.append("/");

		sb.append("DestinationRepeaterID=0x");
		sb.append(String.format("%02X", getDestinationRepeaterID()));

		sb.append("/");

		sb.append("SendRepeaterID=0x");
		sb.append(String.format("%02X", getSendRepeaterID()));

		sb.append("/");

		sb.append("SendTerminalID=0x");
		sb.append(String.format("%02X", getSendTerminalID()));

		sb.append("/");

		sb.append("FrameID=0x");
		sb.append(String.format("%04X", getFrameIDNumber()));
		sb.append("(0x");
		sb.append(String.format("%04X", undoModFrameID(false)));
		sb.append(")");

		sb.append('\n');
		FormatUtil.addIndent(sb, indent + 4);

		sb.append("ManagementInformation=0x");
		sb.append(String.format("%02X", getManagementInformation()));

		sb.append('(');
		sb.append("Type=");
		sb.append(getType());

		sb.append("/");

		sb.append("FrameType=");
		sb.append(getFrameType());

		sb.append("/");

		sb.append("SequenceNumber=0x");
		sb.append(String.format("%02X", getSequenceNumber()));
		if(isEndSequence()) {sb.append("[END]");}

		if(isHeaderError()) {sb.append("[HEADER ERROR]");}
		sb.append(')');

		return sb.toString();
	}

	private static void clone(final BackBoneHeader dst, final BackBoneHeader src) {
		dst.locker = new ReentrantLock();

		dst.id = src.id;
		dst.destinationRepeaterID = src.destinationRepeaterID;
		dst.sendRepeaterID = src.sendRepeaterID;
		dst.sendTerminalID = src.sendTerminalID;
		dst.frameID = Arrays.copyOf(src.frameID, src.frameID.length);
		dst.managementInformation = src.managementInformation;

		src.locker.lock();
		try {
			dst.frameIDModCodes = new Stack<>();
			dst.frameIDModCodes.addAll(src.frameIDModCodes);
		}finally {
			src.locker.unlock();
		}
	}
}
