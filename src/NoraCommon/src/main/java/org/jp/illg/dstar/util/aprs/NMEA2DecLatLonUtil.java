package org.jp.illg.dstar.util.aprs;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NMEA2DecLatLonUtil {
	
	private double latitude;
	private char latitudePos;
	
	private double longitude;
	private char longitudePos;
	
	public NMEA2DecLatLonUtil(
			final double latitude, final char latitudePos,
			final double longitude, final char longitudePos
	) {
		this.latitude = latitude;
		this.latitudePos = latitudePos;
		this.longitude = longitude;
		this.longitudePos = longitudePos;
	}
	
	public double getDecimalLatitude() {
		return toDecimal(latitude, latitudePos);
	}
	
	private BigDecimal getDegrees(double d) {
		BigDecimal bd = new BigDecimal(d);
		bd = bd.movePointLeft(2);
		
		return new BigDecimal(bd.intValue());
	}
	
	private BigDecimal getMinutes(double d) {
		BigDecimal bd = new BigDecimal(d);
		bd = bd.movePointLeft(2);
		
		BigDecimal minutesBd = bd.subtract(new BigDecimal(bd.intValue()));
		minutesBd = minutesBd.movePointRight(2);
		
		final BigDecimal minutes = new BigDecimal(
				(minutesBd.doubleValue() * 100) / 60).movePointLeft(2);
		
		return minutes;
	}
	
	private double toDecimal(double d, char c) {
		BigDecimal bd = new BigDecimal(d);
		bd = bd.movePointLeft(2);
		
		BigDecimal degrees = getDegrees(d);
		
		BigDecimal minutesAndSeconds = getMinutes(d);
		
		BigDecimal decimal = degrees.add(minutesAndSeconds).setScale(4,
				RoundingMode.HALF_EVEN);
		
		return decimal.doubleValue();
	}
	
	public double getDecimalLongitude() {
		return toDecimal(longitude, longitudePos);
	}
	
	public double getSignedDecimalLongitude() {
		double l = getDecimalLongitude();
		if (l > 0 && longitudePos == 'W' || longitudePos == 'S') {
			return l * -1;
		} else {
			return l;
		}
		
	}
	
	public double getSignedDecimalLatitude() {
		double l = getDecimalLatitude();
		if (l > 0 && latitudePos == 'W' || latitudePos == 'S') {
			return l * -1;
		} else {
			return l;
		}
		
	}
	
	@Override
	public String toString() {
		double lat = getDecimalLatitude();
		double lon = getDecimalLongitude();
		StringBuilder b = new StringBuilder();
		b.append(lat);
		b.append(" ");
		b.append(latitudePos);
		b.append(", ");
		b.append(lon);
		b.append(" ");
		b.append(longitudePos);
		return b.toString();
	}
}
