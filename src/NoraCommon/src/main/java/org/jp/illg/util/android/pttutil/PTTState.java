package org.jp.illg.util.android.pttutil;

import lombok.NonNull;

public enum PTTState {
	UP,
	DOWN,
	;
	
	public String getTypeName(){
		return this.toString();
	}
	
	public static PTTState getTypeByName(final String typeName) {
		for(final PTTState state : values()){
			if(state.getTypeName().equals(typeName))
				return state;
		}
		
		return null;
	}
}
