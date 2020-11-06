/**
 *
 */
package org.jp.illg.dvr;

import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.BCDUtil;
import org.jp.illg.util.FormatUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * @author AHND
 *
 */
@Slf4j
public class TestCRC {

	private static final byte[] data = new byte[] {
			(byte)0x00,(byte)0x00,(byte)0x00,
			(byte)0x4a,(byte)0x4b,(byte)0x33,(byte)0x5a,(byte)0x4e,(byte)0x42,(byte)0x20,(byte)0x41,
			(byte)0x4a,(byte)0x4b,(byte)0x33,(byte)0x5a,(byte)0x4e,(byte)0x42,(byte)0x20,(byte)0x47,
			(byte)0x4a,(byte)0x4b,(byte)0x33,(byte)0x5a,(byte)0x4e,(byte)0x42,(byte)0x20,(byte)0x46,
			(byte)0x4a,(byte)0x49,(byte)0x31,(byte)0x52,(byte)0x4f,(byte)0x4a,(byte)0x20,(byte)0x20,
			(byte)0x48,(byte)0x4f,(byte)0x4d,(byte)0x45
	};

	private static final byte[] dataFromRIG = new byte[] {	//0x24DF?
//			(byte)0x2C,(byte)0x10,
			(byte)0x40,(byte)0x00,(byte)0x00,
			(byte)0x4A,(byte)0x4A,(byte)0x30,(byte)0x54,(byte)0x50,(byte)0x58,(byte)0x20,(byte)0x45,
			(byte)0x4A,(byte)0x4A,(byte)0x30,(byte)0x54,(byte)0x50,(byte)0x58,(byte)0x20,(byte)0x45,
			(byte)0x43,(byte)0x51,(byte)0x43,(byte)0x51,(byte)0x43,(byte)0x51,(byte)0x20,(byte)0x20,
			(byte)0x4A,(byte)0x49,(byte)0x31,(byte)0x52,(byte)0x4F,(byte)0x4A,(byte)0x20,(byte)0x20,
			(byte)0x48,(byte)0x4F,(byte)0x4D,(byte)0x45
//			(byte)0x24,(byte)0xDF,(byte)0x00,(byte)0xFF
	};


	/**
	 * @param args
	 */
	public static void main(String[] args) {

		int crcResult = DSTARUtils.calcCRC(data, data.length);

		log.info("Result(fromInet):0x" + String.format("%04x", crcResult & 0xffff));

		crcResult = DSTARUtils.calcCRC(dataFromRIG, dataFromRIG.length);

		log.info("Result(fromRIG):0x" + String.format("%04x", crcResult & 0xffff));


		log.info("BCD:" + FormatUtil.bytesToHex(BCDUtil.DecToBCDArray(30)));
	}

}
