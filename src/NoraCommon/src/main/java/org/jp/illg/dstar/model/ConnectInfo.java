package org.jp.illg.dstar.model;

import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;

public class ConnectInfo implements Cloneable {

	private ReflectorConnectTypes type;

	private String callsign;

	private char callsignModule;

	private char reflectorModule;


	public ConnectInfo() {
		super();
	}

	@Override
	public ConnectInfo clone() {
		ConnectInfo copy = null;

		try {
			copy = (ConnectInfo)super.clone();

			copy.type = this.type;

			copy.callsign = new String(this.callsign);

			copy.callsignModule = this.callsignModule;

			copy.reflectorModule = this.reflectorModule;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	/**
	 * @return type
	 */
	public ReflectorConnectTypes getType() {
		return type;
	}

	/**
	 * @param type セットする type
	 */
	public void setType(ReflectorConnectTypes type) {
		this.type = type;
	}

	/**
	 * @return repeaterCallsign
	 */
	public String getCallsign() {
		return callsign;
	}

	/**
	 * @param callsign セットする callsign
	 */
	public void setCallsign(String callsign) {
		this.callsign = callsign;
	}

	/**
	 * @return callsignModule
	 */
	public char getCallsignModule() {
		return callsignModule;
	}

	/**
	 * @param callsignModule セットする callsignModule
	 */
	public void setCallsignModule(char callsignModule) {
		this.callsignModule = callsignModule;
	}

	/**
	 * @return reflectorModule
	 */
	public char getReflectorModule() {
		return reflectorModule;
	}

	/**
	 * @param reflectorModule セットする reflectorModule
	 */
	public void setReflectorModule(char reflectorModule) {
		this.reflectorModule = reflectorModule;
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
		sb.append("]:");

		sb.append("[Type]:");
		sb.append(getType().toString());

		sb.append("/");

		sb.append("[Callsign]:");
		sb.append(getCallsign());
		sb.append("_");
		sb.append(getCallsignModule());

		sb.append("/");

		sb.append("[ReflectorModule]:");
		sb.append(getReflectorModule());


		return sb.toString();
	}
}
