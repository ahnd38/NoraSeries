package org.jp.illg.util.uart;

public enum UartInterfaceType {
	Serial,
	BluetoothSPP,
	;
	
	public String getTypeName(){
		return this.toString();
	}
	
	public static UartInterfaceType getTypeByName(final String typeName){
		for(final UartInterfaceType type : values()){
			if(type.getTypeName().equals(typeName)){return type;}
		}
		
		return null;
	}
}
