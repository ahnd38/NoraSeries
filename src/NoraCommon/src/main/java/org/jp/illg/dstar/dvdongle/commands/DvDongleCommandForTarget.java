/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
public abstract class DvDongleCommandForTarget extends DvDongleCommandBase {

	public static enum DvDongleCommandTypeForTarget{
		Unknown(-1),
		ResponseToSetOrRequestCurrentControlItem(0x0),
		UnsolicitedControlItem(0x1),
		ResponseToRequestControlItemRange(0x2),
		DataItemAckFromTargetToHost(0x3),
		TargetDataItem0(0x4),
		TargetDataItem1(0x5),
		TargetDataItem2(0x6),
		TargetDataItem3(0x7),
		;

		private final int val;

		private DvDongleCommandTypeForTarget(final int val) {
			this.val = val;
		}

		public int getInt() {
			return this.val;
		}

		public static DvDongleCommandTypeForTarget getType(final int val) {
			for(DvDongleCommandTypeForTarget type : DvDongleCommandTypeForTarget.values()) {
				if(type.getInt() == val) {return type;}
			}
			return DvDongleCommandTypeForTarget.Unknown;
		}
	}

	private int messageLength;

	private DvDongleCommandTypeForTarget messageType;

	/**
	 * @return messageLength
	 */
	public int getMessageLength() {
		return messageLength;
	}

	/**
	 * @return messageType
	 */
	public DvDongleCommandTypeForTarget getMessageType() {
		return messageType;
	}

	/**
	 *
	 */
	public DvDongleCommandForTarget() {
		super();

		this.messageLength = 0;
		this.messageType = DvDongleCommandTypeForTarget.Unknown;
	}


	protected boolean analyzeHeader(ByteBuffer buffer) {

		if(buffer.limit() < 2) {return false;}
		buffer.rewind();

		int header = (buffer.get() << 8) & 0xFF00;
		header |= (buffer.get() & 0xFF);

		this.messageType = DvDongleCommandTypeForTarget.getType((header >>> 5) & 0x7);

		this.messageLength = ((header << 8) & 0x1F00) | (header >>> 8 & 0xFF);

		return true;
	}


	/**
	 * 送信コマンドデータを取得する
	 * @return
	 */
	public byte[] assembleCommandData() {
		throw new UnsupportedOperationException();
	}

}
