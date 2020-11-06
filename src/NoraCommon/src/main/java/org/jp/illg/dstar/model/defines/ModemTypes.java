package org.jp.illg.dstar.model.defines;

import org.jp.illg.dstar.repeater.modem.icomap.AccessPointInterface;
import org.jp.illg.dstar.repeater.modem.icomap.NewAccessPointInterface;
import org.jp.illg.dstar.repeater.modem.mmdvm.MMDVMInterface;
import org.jp.illg.dstar.repeater.modem.noravr.NoraVR;

public enum ModemTypes{
	Unknown(""),
	AccessPoint(AccessPointInterface.class.getName()),
	NewAccessPoint(NewAccessPointInterface.class.getName()),
	NewAccessPointBluetooth(NewAccessPointInterface.class.getName()),
	MMDVM(MMDVMInterface.class.getName()),
	MMDVMBluetooth(MMDVMInterface.class.getName()),
	NoraVR(NoraVR.class.getName()),
	AnalogModemPiGPIO("org.jp.illg.dstar.repeater.modem.analog.AnalogModemPiGPIO")
	;
	
	private final String className;

	ModemTypes(final String className) {
		this.className = className;
	}

	public String getClassName() {
		return this.className;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static ModemTypes getTypeByClassName(String className) {
		for(ModemTypes v : values()) {
			if(v.getClassName().equals(className)) {return v;}
		}
		return ModemTypes.Unknown;
	}

	public static String getClassNameByType(ModemTypes type) {
		for(ModemTypes v : values()) {
			if(v == type) {return v.getClassName();}
		}
		return ModemTypes.Unknown.getClassName();
	}

	public static ModemTypes getTypeByTypeName(String typeName){
		for(ModemTypes v : values()) {
			if(v.getTypeName().equals(typeName)) {return v;}
		}
		return ModemTypes.Unknown;
	}
	
	/*
	private static Class<?> readAnalogModemPiGPIOClass() {
		final String className = "org.jp.illg.dstar.repeater.modem.analog.AnalogModemPiGPIO";
		try{
			return Class.forName(className);
		}catch(ClassNotFoundException ex) {
			return null;
		}
	}
	*/
}
