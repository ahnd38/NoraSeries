package org.jp.illg.dstar.model.defines;

import org.jp.illg.dstar.repeater.ecdummy.ECDummyRepeater;
import org.jp.illg.dstar.repeater.echo.EchoAutoReplyRepeater;
import org.jp.illg.dstar.repeater.homeblew.HomeblewRepeater;
import org.jp.illg.dstar.repeater.icom.ExternalICOMRepeater;
import org.jp.illg.dstar.repeater.internal.InternalRepeater;
import org.jp.illg.dstar.repeater.reflectorecho.ReflectorEchoAutoReplyRepeater;
import org.jp.illg.dstar.repeater.voiceroid.VoiceroidAutoReplyRepeater;

public enum RepeaterTypes{
	Unknown(""),
	Internal(InternalRepeater.class.getName()),
	ExternalHomebrew(HomeblewRepeater.class.getName()),
	VoiceroidAutoReply(VoiceroidAutoReplyRepeater.class.getName()),
	EchoAutoReply(EchoAutoReplyRepeater.class.getName()),
	ReflectorEchoAutoReply(ReflectorEchoAutoReplyRepeater.class.getName()),
	ExternalConnectorDummy(ECDummyRepeater.class.getName()),
	ExternalICOMRepeater(ExternalICOMRepeater.class.getName()),
	;

	private final String className;

	RepeaterTypes(final String className) {
		this.className = className;
	}

	public String getClassName() {
		return this.className;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static RepeaterTypes getTypeByTypeName(String typeName) {
		return getTypeByTypeName(typeName, false);
	}

	public static RepeaterTypes getTypeByTypeNameIgnoreCase(String typeName) {
		return getTypeByTypeName(typeName, true);
	}

	public static RepeaterTypes getTypeByClassName(String className) {
		for(RepeaterTypes v : values()) {
			if(v.getClassName().equals(className)) {return v;}
		}
		return RepeaterTypes.Unknown;
	}

	public static String getClassNameByType(RepeaterTypes type) {
		for(RepeaterTypes v : values()) {
			if(v == type) {return v.getClassName();}
		}
		return RepeaterTypes.Unknown.getClassName();
	}

	private static RepeaterTypes getTypeByTypeName(String typeName, boolean ignoreCase) {
		for(RepeaterTypes v : values()) {
			if(
				(!ignoreCase && v.getTypeName().equals(typeName)) ||
				(ignoreCase && v.getTypeName().equalsIgnoreCase(typeName))
			) {return v;}
		}
		return RepeaterTypes.Unknown;
	}
}
