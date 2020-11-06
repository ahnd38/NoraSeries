/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;

/**
 * @author AHND
 *
 */
public abstract class DvDongleCommandBase implements DvDongleCommand {






	/**
	 * 送信コマンドデータを取得する
	 * @return
	 */
	public abstract byte[] assembleCommandData();

	/**
	 * バッファを解析してコマンドデータを作る
	 * @param buffer
	 * @return
	 */
	public abstract DvDongleCommand analyzeCommandData(ByteBuffer buffer);
}
