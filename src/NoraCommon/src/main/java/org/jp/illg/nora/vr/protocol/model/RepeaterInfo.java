package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.jp.illg.dstar.DSTARDefines;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterInfo extends NoraVRPacketBase {

	private static final String logTag =
		RepeaterInfo.class.getSimpleName() + " : ";

	public static class RepeaterInformation implements Cloneable{

		@Getter
		@Setter
		private String callsign;

		@Getter
		@Setter
		private String name;

		@Getter
		@Setter
		private String location;

		@Getter
		@Setter
		private double frequency;

		@Getter
		@Setter
		private double frequency_offset;

		@Getter
		@Setter
		private double service_range;

		@Getter
		@Setter
		private double agl;

		@Getter
		@Setter
		private String url;

		@Getter
		@Setter
		private String description1;

		@Getter
		@Setter
		private String description2;


		public RepeaterInformation() {
			super();

			clear();
		}

		@Override
		public RepeaterInformation clone() {
			RepeaterInformation copy = null;

			try {
				copy = (RepeaterInformation)super.clone();

				copy.callsign = this.callsign;
				copy.name = this.name;
				copy.location = this.location;
				copy.frequency = this.frequency;
				copy.frequency_offset = this.frequency_offset;
				copy.service_range = this.service_range;
				copy.agl = this.agl;
				copy.url = this.url;
				copy.description1 = this.description1;
				copy.description2 = this.description2;

			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}

			return copy;
		}

		public void clear() {
			callsign = DSTARDefines.EmptyLongCallsign;
			name = "";
			location = "";
			frequency = 0.0d;
			frequency_offset = 0.0d;
			service_range = 0.0d;
			agl = 0.0d;
			url = "";
			description1 = "";
			description2 = "";
		}
	}

	@Getter
	@Setter
	private long clientCode;

	private RepeaterInformation repeaterInformation;

	private byte[] repeaterInformationJson;

	public RepeaterInfo() {
		super(NoraVRCommandType.RINFO);

		clientCode = 0x0;
		repeaterInformation = new RepeaterInformation();

		repeaterInformationJson = null;
	}

	@Override
	public RepeaterInfo clone() {
		RepeaterInfo copy = (RepeaterInfo)super.clone();

		copy.clientCode = this.clientCode;
		copy.repeaterInformation = this.repeaterInformation.clone();

		return copy;
	}

	public void setCallsign(final String callsign) {
		if(callsign.length() > DSTARDefines.CallsignFullLength)
			repeaterInformation.setCallsign(callsign.substring(0,DSTARDefines.CallsignFullLength));
		else
			repeaterInformation.setCallsign(callsign);
	}

	public String getCallsign() {
		return repeaterInformation.getCallsign();
	}

	public void setName(final String name) {
		if(name.length() > 32)
			repeaterInformation.setName(name.substring(0,32));
		else
			repeaterInformation.setName(name);
	}

	public String getName() {
		return repeaterInformation.getName();
	}

	public void setLocation(final String location) {
		if(location.length() > 32)
			repeaterInformation.setLocation(location.substring(0,32));
		else
			repeaterInformation.setLocation(location);
	}

	public String getLocation() {
		return repeaterInformation.getLocation();
	}

	public void setFrequencyMHz(final double frequencyMHz) {
		repeaterInformation.setFrequency(frequencyMHz);
	}

	public double getFrequencyMHz() {
		return repeaterInformation.getFrequency();
	}

	public void setFrequencyOffsetMHz(final double frequencyOffsetMHz) {
		repeaterInformation.setFrequency_offset(frequencyOffsetMHz);
	}

	public double getFrequencyOffsetMHz() {
		return repeaterInformation.getFrequency_offset();
	}

	public void setServiceRangeKm(final double serviceRangeKm) {
		repeaterInformation.setService_range(serviceRangeKm);
	}

	public double getServiceRangeKm() {
		return repeaterInformation.getService_range();
	}

	public void setAgl(final double agl) {
		repeaterInformation.setAgl(agl);
	}

	public double getAgl() {
		return repeaterInformation.getAgl();
	}

	public void setUrl(final String url) {
		if(url.length() > 64)
			repeaterInformation.setUrl(url.substring(0, 64));
		else
			repeaterInformation.setUrl(url);
	}

	public String getUrl() {
		return repeaterInformation.getUrl();
	}

	public void setDescription1(final String description1) {
		if(description1.length() > 32)
			repeaterInformation.setDescription1(description1.substring(0, 32));
		else
			repeaterInformation.setDescription1(description1);
	}

	public String getDescription1() {
		return repeaterInformation.getDescription1();
	}

	public void setDescription2(final String description2) {
		if(description2.length() > 32)
			repeaterInformation.setDescription2(description2.substring(0, 32));
		else
			repeaterInformation.setDescription2(description2);
	}

	public String getDescription2() {
		return repeaterInformation.getDescription2();
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength()) {return false;}

		//Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		//Repeater Information
		for(int i = 0; i < repeaterInformationJson.length && buffer.hasRemaining(); i++)
			buffer.put(repeaterInformationJson[i]);

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		if(repeaterInformationJson == null)
			repeaterInformationJson = createRepeaterInformation();

		return 4 + repeaterInformationJson.length;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 4) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		// Repeater Information
		repeaterInformation.clear();
		if(buffer.hasRemaining()) {
			byte[] rbuf = new byte[buffer.remaining()];
			for(int i = 0; i < rbuf.length && buffer.hasRemaining(); i++) {
				final byte b = buffer.get();

				rbuf[i] = b;
			}

			final String json = new String(rbuf, StandardCharsets.UTF_8);

			final Gson gson = new Gson();
			try {
				repeaterInformation =
					gson.fromJson(json, RepeaterInformation.class);
			}catch(JsonSyntaxException ex) {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Illegal repeater information json data = \n" +
						"    " + json
					);
				}

				return false;
			}
		}

		return true;
	}

	private byte[] createRepeaterInformation() {
		final Gson gson = new Gson();

		return gson.toJson(repeaterInformation).getBytes(StandardCharsets.UTF_8);
	}

}
