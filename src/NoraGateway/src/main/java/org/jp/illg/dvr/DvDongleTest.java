/**
 *
 */
package org.jp.illg.dvr;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jp.illg.dstar.dvdongle.DvDongleInterface;
import org.jp.illg.dstar.jptrust.DstarNetworkInterface;
import org.jp.illg.util.AudioInputWorker;
import org.jp.illg.util.log4j.Log4jUtil;

/**
 * @author AHND
 *
 */
public class DvDongleTest {

	private static final Log log = LogFactory.getLog(DvDongleTest.class);

	private static final String log4jConfigurationFilePath =
			System.getProperty("user.dir") + File.separator + "config" + File.separator + "log4j2.xml";

	private static final String applicationConfigurationFilePath =
			System.getProperty("user.dir") + File.separator +
			"config" + File.separator +
			"config.properties";

	private static DvGatewayProperties appProperties = new DvGatewayProperties();

	/**
	 * @param args
	 */
	public static void main(String[] args) {


		if(
				Log4jUtil.initializeLogger(new File(log4jConfigurationFilePath)) &&
				appProperties.readConfiguration(new File(applicationConfigurationFilePath)) &&
				process()
		) {
			System.exit(0);
		}else {
			System.exit(-1);
		}
	}



	private static boolean process() {
		try {
			DstarNetworkInterface dni = null;
			DvDongleInterface ddi = null;
			AudioInputWorker aiw = null;
			DvDongleTestWorker ddtw = null;

			try {
				dni = new DstarNetworkInterface();
				dni.setPortNumber(50000);

				aiw = new AudioInputWorker();

				ddi = new DvDongleInterface();
				ddi.setDonglePortName("AI03BXLY");

				ddtw = new DvDongleTestWorker(dni, ddi, aiw);
				ddtw.start();

				if(log.isInfoEnabled())
					log.info("DV dongle TEST Online...");

				BufferedReader br = null;
				try {
					br = new BufferedReader(new InputStreamReader(System.in));
					br.readLine();
				}finally {
					if(br != null) {br.close();}
				}

			}finally {
				if(ddtw != null) {ddtw.stop();}
				if(dni != null) {dni.stop();}
				if(aiw != null) {aiw.stop();}
				if(ddi != null) {ddi.stop();}
			}

			return true;
		}catch(Exception ex) {
			log.fatal(ex);
		}
		return false;
	}
}
