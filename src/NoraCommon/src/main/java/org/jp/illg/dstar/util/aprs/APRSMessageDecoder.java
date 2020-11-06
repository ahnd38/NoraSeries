package org.jp.illg.dstar.util.aprs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.util.DSTARCRCCalculator;

import lombok.Getter;
import lombok.Setter;

public class APRSMessageDecoder {

	public static class APRSMessageDecoderResult{
		@Getter
		@Setter
		private double latitude;

		@Getter
		@Setter
		private double longitude;

		public APRSMessageDecoderResult(final double latitude, final double longitude) {
			super();

			this.latitude = latitude;
			this.longitude = longitude;
		}
	}

	private static final Pattern dprsPattern =
		Pattern.compile("([0-9]+([.][0-9]+){0,1})([N]|[S])[/]([0-9]+([.][0-9]+){0,1})([E]|[W])");

	private static final Pattern nmeaPattern =
		Pattern.compile("[,]([0-9]+([.][0-9]+){0,1})[,]([N]|[S])[,]([0-9]+([.][0-9]+){0,1})[,]([E]|[W])[,]");

	private APRSMessageDecoder() {
		super();
	}

	public static APRSMessageDecoderResult decodeDPRS(final String aprsMessage) {
		APRSMessageDecoderResult result = null;

		if((result = decodeDPRSMessage(aprsMessage)) == null) {
			result = decodeNMEAMessage(aprsMessage);
		}

		return result;
	}

	private static APRSMessageDecoderResult decodeDPRSMessage(final String aprsMessage) {
		//ex
		//$$CRCABF4,JH1RDA-A>API51,DSTAR*:!3552.43N/14000.85E>/

		final String message = aprsMessage.replaceAll("[\r\n]", "");

		if(
			!message.startsWith("$$CRC") ||
			!checksumDPRSMessage(aprsMessage)
		){return null;}

		final Matcher matcher = dprsPattern.matcher(message);
		if(!matcher.find() || matcher.groupCount() != 6)
			return null;

		final String latitudeString = matcher.group(1);
		double latitude = 0;
		try{
			latitude = Double.valueOf(latitudeString);
		}catch(NumberFormatException ex){
			return null;
		}
		final String latitudePos = matcher.group(3);
		if(
			latitudePos.length() != 1 ||
			(latitudePos.charAt(0) != 'N' && latitudePos.charAt(0) != 'S')
		){return null;}

		final String longitudeString = matcher.group(4);
		double longitude = 0;
		try{
			longitude = Double.valueOf(longitudeString);
		}catch(NumberFormatException ex){
			return null;
		}

		final String longitudePos = matcher.group(6);
		if(
			longitudePos.length() != 1 ||
			(longitudePos.charAt(0) != 'E' && longitudePos.charAt(0) != 'W')
		){return null;}

		final NMEA2DecLatLonUtil converter =
				new NMEA2DecLatLonUtil(
						latitude, latitudePos.charAt(0),
						longitude, longitudePos.charAt(0)
				);

		return new APRSMessageDecoderResult(
			converter.getSignedDecimalLatitude(), converter.getSignedDecimalLongitude()
		);
	}

	private static APRSMessageDecoderResult decodeNMEAMessage(final String aprsMessage) {
		//ex
		//$GPRMC,131921.00,A,3552.4427,N,14000.8616,E,0.000,-0.000,050419,,E,A*3E

		final String message = aprsMessage.replaceAll("[\r\n]", "");

		if(
			(!message.startsWith("$GPRMC") && !message.startsWith("$GPGGA")) ||
			!checksumNMEAMessage(aprsMessage)
		){
			return null;
		}

		final Matcher matcher = nmeaPattern.matcher(message);
		if(!matcher.find() || matcher.groupCount() != 6)
			return null;

		final String latitudeString = matcher.group(1);
		double latitude = 0;
		try{
			latitude = Double.valueOf(latitudeString);
		}catch(NumberFormatException ex){
			return null;
		}
		final String latitudePos = matcher.group(3);
		if(
			latitudePos.length() != 1 ||
			(latitudePos.charAt(0) != 'N' && latitudePos.charAt(0) != 'S')
		){return null;}

		final String longitudeString = matcher.group(4);
		double longitude = 0;
		try{
			longitude = Double.valueOf(longitudeString);
		}catch(NumberFormatException ex){
			return null;
		}

		final String longitudePos = matcher.group(6);
		if(
			longitudePos.length() != 1 ||
			(longitudePos.charAt(0) != 'E' && longitudePos.charAt(0) != 'W')
		){return null;}

		final NMEA2DecLatLonUtil converter =
				new NMEA2DecLatLonUtil(
						latitude, latitudePos.charAt(0),
						longitude, longitudePos.charAt(0)
				);

		return new APRSMessageDecoderResult(
				converter.getSignedDecimalLatitude(), converter.getSignedDecimalLongitude()
		);
	}

	private static boolean checksumNMEAMessage(final String aprsMessage) {
		if(!aprsMessage.startsWith("$GP"))
			return false;

		final int splitterIndex = aprsMessage.indexOf("*");
		if(
			splitterIndex < 0 ||
			splitterIndex + 2 > aprsMessage.length() - 1
		) {return false;}

		final String checksumHex = aprsMessage.substring(splitterIndex + 1).replaceAll("[^A-Z0-9]", "");
		int checksum = -1;
		try {
			checksum = Integer.parseInt(checksumHex, 16);
		}catch(NumberFormatException ex) {
			return false;
		}

		int calcChecksum = 0x0;
		for(int i = 1; i < splitterIndex; i++) {
			calcChecksum ^= aprsMessage.charAt(i);
		}

		return checksum == calcChecksum;
	}

	private static boolean checksumDPRSMessage(final String aprsMessage) {
		//ex
		//$$CRCABF4,JH1RDA-A>API51,DSTAR*:!3552.43N/14000.85E>/

		if(!aprsMessage.startsWith("$$CRC"))
			return false;

		final int crcSplitterIndex = aprsMessage.indexOf(",");
		if(crcSplitterIndex < 6 || crcSplitterIndex > 9)
			return false;

		final String crcHex = aprsMessage.substring(5, crcSplitterIndex);

		int crc = -1;
		try {
			crc = Integer.parseInt(crcHex, 16);
		}catch(NumberFormatException ex) {
			return false;
		}

		final DSTARCRCCalculator crcTool = new DSTARCRCCalculator();
		for(int i = 10; i < aprsMessage.length(); i++) {
			final byte in = (byte)aprsMessage.charAt(i);

			crcTool.updateCRC(in);
		}
		final int calcCrc = crcTool.getResultCRC();
//		final int calcCrc = calcDPRSCRC(aprsMessage, 10, aprsMessage.length() - 10);

		return crc == calcCrc;
	}
/*
	private static int calcDPRSCRC(String buffer, int startpos, int length)
	{
		int icomcrc = 0xffff;

		for (int j = startpos; j < startpos + length; j++){
			int ch = buffer.charAt(j) & 0xff;
			for (int i = 0; i < 8; i++){
				boolean xorflag = (((icomcrc ^ ch) & 0x01) == 0x01);
				icomcrc >>>= 1;
				if (xorflag) {icomcrc ^= 0x8408;}
				ch >>>= 1;
			}
		}

		return (~icomcrc) & 0xffff;
	}
*/
}
