package org.jp.illg.dstar.service.remotecontrol.model;

import java.util.ArrayList;
import java.util.List;

import org.jp.illg.dstar.model.defines.ReconnectType;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class RemoteControlRepeater implements Cloneable{

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private ReconnectType reconnectType;

	@Getter
	@Setter
	private String startupReflectorCallsign;

	private List<ReflectorLinkInformation> links;

	public RemoteControlRepeater() {
		super();

		links = new ArrayList<>();
	}

	@Override
	public RemoteControlRepeater clone() {
		RemoteControlRepeater copy = null;
		try {
			copy = (RemoteControlRepeater)super.clone();

			copy.repeaterCallsign = repeaterCallsign;

			copy.reconnectType = reconnectType;

			copy.startupReflectorCallsign = startupReflectorCallsign;

			copy.links = new ArrayList<>();
			if(links != null)
				for(ReflectorLinkInformation link : links) {copy.links.add(link.clone());}

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

}
