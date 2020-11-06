package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class ConfigurationSet extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private NoraVRConfiguration serverConfiguration;

	public ConfigurationSet() {
		super(NoraVRCommandType.CONFSET);

		setClientCode((long)0x0);
		setServerConfiguration(new NoraVRConfiguration());
	}

	@Override
	public ConfigurationSet clone() {
		ConfigurationSet copy = null;

		copy = (ConfigurationSet)super.clone();

		copy.clientCode = this.clientCode;
		copy.serverConfiguration = this.serverConfiguration.clone();

		return copy;
	}

	@Override
	protected boolean assembleField(ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		//Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		//Server Configuration
		buffer.put((byte)((getServerConfiguration().getValue() >> 8) & 0xFF));
		buffer.put((byte)(getServerConfiguration().getValue() & 0xFF));

		//Reserved
		buffer.put((byte)0x00);
		buffer.put((byte)0x00);

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 8;
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 8) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		//Server Configuration
		int scVal = (buffer.get() & 0xFF);
		scVal = (scVal << 8) | (buffer.get() & 0xFF);
		getServerConfiguration().setValue(scVal);

		//Reserved
		buffer.get();
		buffer.get();

		return true;
	}

}
