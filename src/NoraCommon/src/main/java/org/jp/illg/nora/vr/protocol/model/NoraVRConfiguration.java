package org.jp.illg.nora.vr.protocol.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class NoraVRConfiguration implements Cloneable{

//	private static final int supportedCodecMask = 0x00FF;
	private static final int supportedCodecPCM = 0x0001;
	private static final int supportedCodecOpus64k = 0x0002;
	private static final int supportedCodecOpus24k = 0x0004;
	private static final int supportedCodecOpus8k = 0x0008;
	private static final int supportedCodecAMBE = 0x0080;

	private static final int localUserNotify = 0x0400;
	private static final int remoteUserNotify = 0x0200;
	private static final int accessLogNotify = 0x0100;

	private static final int echoback = 0x4000;
	private static final int rfNode = 0x8000;

	@Getter
	@Setter
	private int value;

	public NoraVRConfiguration() {
		super();

		setValue(0x0);
	}

	public NoraVRConfiguration(
		@NonNull final NoraVRConfiguration instance
	) {
		this.value |= instance.value;
	}

	@Override
	public NoraVRConfiguration clone() {
		NoraVRConfiguration copy = null;

		try {
			copy = (NoraVRConfiguration)super.clone();

			copy.value = this.value;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public boolean isSupportedCodecPCM() {
		return (getValue() & supportedCodecPCM) != 0x0;
	}

	public void setSupportedCodecPCM(final boolean enable) {
		if(enable)
			setValue(getValue() | supportedCodecPCM);
		else
			setValue(getValue() & ~supportedCodecPCM);
	}

	public boolean isSupportedCodecOpus24k() {
		return (getValue() & supportedCodecOpus24k) != 0x0;
	}

	public void setSupportedCodecOpus24k(final boolean enable) {
		if(enable)
			setValue(getValue() | supportedCodecOpus24k);
		else
			setValue(getValue() & ~supportedCodecOpus24k);
	}

	public boolean isSupportedCodecOpus64k() {
		return (getValue() & supportedCodecOpus64k) != 0x0;
	}

	public void setSupportedCodecOpus64k(final boolean enable) {
		if(enable)
			setValue(getValue() | supportedCodecOpus64k);
		else
			setValue(getValue() & ~supportedCodecOpus64k);
	}

	public boolean isSupportedCodecOpus8k() {
		return (getValue() & supportedCodecOpus8k) != 0x0;
	}

	public void setSupportedCodecOpus8k(final boolean enable) {
		if(enable)
			setValue(getValue() | supportedCodecOpus8k);
		else
			setValue(getValue() & ~supportedCodecOpus8k);
	}

	public boolean isSupportedCodecAMBE() {
		return (getValue() & supportedCodecAMBE) != 0x0;
	}

	public void setSupportedCodecAMBE(final boolean enable) {
		if(enable)
			setValue(getValue() | supportedCodecAMBE);
		else
			setValue(getValue() & ~supportedCodecAMBE);
	}

	public boolean isLocalUserNotify() {
		return (getValue() & localUserNotify) != 0x0;
	}

	public void setLocalUserNotify(final boolean enable) {
		if(enable)
			setValue(getValue() | localUserNotify);
		else
			setValue(getValue() & ~localUserNotify);
	}

	public boolean isRemoteUserNotify() {
		return (getValue() & remoteUserNotify) != 0x0;
	}

	public void setRemoteUserNotify(final boolean enable) {
		if(enable)
			setValue(getValue() | remoteUserNotify);
		else
			setValue(getValue() & ~remoteUserNotify);
	}

	public boolean isAccessLogNotify() {
		return (getValue() & accessLogNotify) != 0x0;
	}

	public void setAccessLogNotify(final boolean enable) {
		if(enable)
			setValue(getValue() | accessLogNotify);
		else
			setValue(getValue() & ~accessLogNotify);
	}

	public boolean isEchoback() {
		return (getValue() & echoback) != 0x0;
	}

	public void setEchoback(final boolean enable) {
		if(enable)
			setValue(getValue() | echoback);
		else
			setValue(getValue() & ~echoback);
	}

	public boolean isRfNode() {
		return (getValue() & rfNode) != 0x0;
	}

	public void setRfNode(final boolean enable) {
		if(enable)
			setValue(getValue() | rfNode);
		else
			setValue(getValue() & ~rfNode);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("] ");

		sb.append("Value:");
		sb.append(String.format("0x%04X", getValue()));

		return sb.toString();
	}
}
