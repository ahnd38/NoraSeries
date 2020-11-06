package org.jp.illg.util.io.fum;

import java.io.InputStream;

public interface FileUpdateMonitoringFunction {

	public String getTargetFilePath();

	public int getMonitoringIntervalTimeSeconds();

	/**
	 * 初回読み込みファンクション
	 * @param targetFile ターゲットファイルストリーム
	 * @return 処理成功ならtrue
	 */
	public boolean initialize(InputStream targetFile);

	/**
	 * ファイル更新ファンクション
	 * @param targetFile ターゲットファイルストリーム
	 * @return 処理成功ならtrue
	 */
	public boolean fileUpdate(InputStream targetFile);

	/**
	 * ロールバックファンクション
	 *
	 * @see {@link #fileUpdate(InputStream) fileUpdate}にてfalseを返した場合に、
	 * 後に成功を返したターゲットファイルのデータがあればそのデータを用いてコールされる
	 *
	 * @param targetFile ターゲットファイルストリーム
	 * @return 処理成功ならtrue
	 */
	public boolean rollback(InputStream targetFile);
}
