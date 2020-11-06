/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
public interface DvDongleCommand {


	/**
	 * 送信コマンドデータを取得する
	 * @return
	 */
	public byte[] assembleCommandData();

	/**
	 * バッファを解析してコマンドデータを作る
	 * @param buffer
	 * @return
	 */
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer);


}
