/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;


/**
 * @author AHND
 *
 */
public abstract class DvDongleCommandForHost extends DvDongleCommandBase {


	public static enum DvDongleCommandTypeForHost{
		Unknown(-1),
		SetControlItem(0x0),
		RequestCurrentControlItem(0x1),
		RequestControlItemRange(0x2),
		DataItemAckFromHostToTarget(0x3),
		HostDataItem0(0x4),
		HostDataItem1(0x5),
		HostDataItem2(0x6),
		HostDataItem3(0x7),
		;

		private final int val;

		private DvDongleCommandTypeForHost(final int val) {
			this.val = val;
		}

		public int getInt() {
			return this.val;
		}

		public static DvDongleCommandTypeForHost getType(final int val) {
			for(DvDongleCommandTypeForHost type : DvDongleCommandTypeForHost.values()) {
				if(type.getInt() == val) {return type;}
			}
			return DvDongleCommandTypeForHost.Unknown;
		}
	}


	private int messageLength;

	private DvDongleCommandTypeForHost messageType;

	/**
	 * @return messageLength
	 */
	public int getMessageLength() {
		return messageLength;
	}

	/**
	 * @param messageLength セットする messageLength
	 */
	public void setMessageLength(int messageLength) {
		this.messageLength = messageLength;
	}

	/**
	 * @return messageType
	 */
	public DvDongleCommandTypeForHost getMessageType() {
		return messageType;
	}

	/**
	 * @param messageType セットする messageType
	 */
	public void setMessageType(DvDongleCommandTypeForHost messageType) {
		this.messageType = messageType;
	}


	protected short getHeader() {
		int header =
			((this.getMessageLength() << 8) & 0xFF00) |
			((this.getMessageType().getInt() << 5) & 0x00E0) |
			((this.getMessageLength() >>> 8) & 0x001F);

		return (short)header;
	}

	/**
	 *
	 */
	public DvDongleCommandForHost() {
		super();

		this.messageLength = 0;
		this.messageType = DvDongleCommandTypeForHost.Unknown;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer) {
		throw new UnsupportedOperationException();
	}

}
