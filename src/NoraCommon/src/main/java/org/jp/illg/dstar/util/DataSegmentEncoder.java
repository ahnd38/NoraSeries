/**
 *
 */
package org.jp.illg.dstar.util;

import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BCDUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * @author AHND
 *
 */
public class DataSegmentEncoder extends DataSegmentProcessor{

//	private static final Log log = LogFactory.getLog(DataSegmentEncoder.class);


	@Getter
	@Setter
	private boolean enableCodeSquelch;

	@Getter
	@Setter
	private int codeSquelchCode;

	@Getter
	@Setter
	private boolean enableShortMessage;

	@Getter
	private final char[] shortMessage = new char[DSTARDefines.DvShortMessageLength];

	private final char[] shortMessageCache = new char[DSTARDefines.DvShortMessageLength];


	private static enum EncodeStates{
		SYNC,
		CSQL,
		SHORT_MESSAGE,
		NODATA,
	}

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private EncodeStates encodeState;

	private int packetSequence;

	private int shortMessageSequence;

	@Setter
	@Getter
	private boolean enableEncode;


	/**
	 * @param shortMessage セットする shortMessage
	 */
	public void setShortMessage(char[] shortMessage) {
		Arrays.fill(this.shortMessage, ' ');

		if(shortMessage != null) {
			for(int i = 0; i < shortMessage.length && i < DSTARDefines.DvShortMessageLength; i++)
				this.shortMessage[i] = shortMessage[i];
		}

//		ArrayUtil.copyOf(this.shortMessage, shortMessage);
	}

	/**
	 *
	 */
	public DataSegmentEncoder() {
		super();

		this.enableCodeSquelch = false;
		this.codeSquelchCode = 0;
		this.enableShortMessage = false;

		setEnableEncode(true);

		this.reset();

	}

	public boolean encode(byte[] dataSegment) {
		if(dataSegment == null || dataSegment.length != 3) {return false;}


		if(isEnableEncode()) {
			byte[] data = null;

			switch(this.encodeState) {
			case SYNC:
				for(int index = 0;index < dataSegment.length && index < syncCode.length;index++)
					dataSegment[index] = (byte)syncCode[index];

				this.shortMessageSequence = 0;

				ArrayUtil.copyOf(shortMessageCache, shortMessage);

				this.encodeState = this.selectNext();
				this.nextSequence();
				break;

			case CSQL:
				if((this.packetSequence % 2) == 1) {
					byte[] codeBCD = BCDUtil.DecToBCDArray(this.codeSquelchCode);

					data =
						new byte[] {
								(byte)0xC2,
								codeBCD.length >= 1 ? codeBCD[0] : (byte)0x0,
								codeBCD.length >= 1 ? codeBCD[0] : (byte)0x0,
						};
					for(int index = 0;index < dataSegment.length && index < data.length && index < magicCode.length;index++)
						dataSegment[index] = (byte)(data[index] ^ magicCode[index]);
				}else {
					for(int index = 0;index < dataSegment.length && index < magicCode.length;index++)
						dataSegment[index] = (byte)((byte)0x00 ^ magicCode[index]);

					this.encodeState = this.selectNext();
				}
				this.nextSequence();
				break;

			case SHORT_MESSAGE:
				if((this.packetSequence % 2) == 1) {
					data = new byte[] {
							(byte)(0x40 + this.shortMessageSequence),
							(byte)this.shortMessageCache[(this.shortMessageSequence * 5) + 0],
							(byte)this.shortMessageCache[(this.shortMessageSequence * 5) + 1]
					};
					for(int index = 0;index < dataSegment.length && index < data.length && index < magicCode.length;index++)
						dataSegment[index] = (byte)(data[index] ^ magicCode[index]);
				}else {
					data = new byte[] {
							(byte)this.shortMessageCache[(this.shortMessageSequence * 5) + 2],
							(byte)this.shortMessageCache[(this.shortMessageSequence * 5) + 3],
							(byte)this.shortMessageCache[(this.shortMessageSequence * 5) + 4]
					};
					for(int index = 0;index < dataSegment.length && index < data.length && index < magicCode.length;index++)
						dataSegment[index] = (byte)(data[index] ^ magicCode[index]);

					if(this.shortMessageSequence >= 3) {
						this.shortMessageSequence = 0;
						this.encodeState = this.selectNext();
					}else {
						this.shortMessageSequence++;
					}
				}
				this.nextSequence();
				break;

			case NODATA:
				for(int index = 0;index < dataSegment.length && index < magicCode.length;index++)
					dataSegment[index] = (byte)(0x66 ^ magicCode[index]);

				this.encodeState = this.selectNext();
				this.nextSequence();
				break;
			}
		}
		else {
			// disable encode
			for(int index = 0;index < dataSegment.length && index < magicCode.length;index++)
				dataSegment[index] = (byte)((byte)0x66 ^ magicCode[index]);
		}


		return true;
	}

	private EncodeStates selectNext() {
		EncodeStates result = EncodeStates.NODATA;

		if(this.packetSequence >= 0x14)
			result = EncodeStates.SYNC;
		else if(this.packetSequence == 0 && this.enableCodeSquelch)
			result = EncodeStates.CSQL;
		else if(
				(
						this.packetSequence == 0 ||
						this.packetSequence == 2 ||
						this.packetSequence == 10
				) && this.enableShortMessage
		)
			result = EncodeStates.SHORT_MESSAGE;
		else
			result = EncodeStates.NODATA;

		return result;
	}

	private void nextSequence() {
		if(this.packetSequence >= 0x14)
			this.packetSequence = 0x00;
		else
			this.packetSequence++;
	}

	public void reset() {
		this.encodeState = EncodeStates.SYNC;
		this.packetSequence = 0;

		Arrays.fill(this.shortMessage, ' ');
		this.shortMessageSequence = 0;
	}

}
