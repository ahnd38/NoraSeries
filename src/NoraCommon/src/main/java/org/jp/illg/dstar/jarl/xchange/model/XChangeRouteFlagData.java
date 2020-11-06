package org.jp.illg.dstar.jarl.xchange.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.NonNull;

public class XChangeRouteFlagData implements Cloneable{

	private List<XChangeRouteFlag> flags;

	public XChangeRouteFlagData() {
		super();

		flags = new ArrayList<>();
	}

	public XChangeRouteFlagData(final XChangeRouteFlag... flags) {
		this();

		if(flags != null) {
			synchronized(this.flags) {
				for(final XChangeRouteFlag flag : flags)
					addRouteFlag(flag);
			}
		}
	}

	public boolean addRouteFlag(@NonNull final XChangeRouteFlag flag) {
		synchronized(this.flags) {
			if(!hasRouteFlag(flag)) {return this.flags.add(flag);}
		}

		return false;
	}

	public boolean removeRouteFlag(@NonNull final XChangeRouteFlag flag) {
		synchronized(flags) {
			for(final Iterator<XChangeRouteFlag> it = flags.iterator(); it.hasNext();) {
				if(flag == it.next()) {
					it.remove();

					return true;
				}
			}
		}

		return false;
	}

	public boolean hasRouteFlag(@NonNull final XChangeRouteFlag flag) {
		synchronized(flags) {
			for(final XChangeRouteFlag f : flags)
				if(f == flag) {return true;}
		}

		return false;
	}

	public byte getValue() {
		byte value = 0x0;
		synchronized(flags) {
			for(final XChangeRouteFlag f : flags) {value |= f.getValue();}
		}

		return value;
	}

	public void clear() {
		synchronized (flags) {
			flags.clear();
		}
	}

	@Override
	public XChangeRouteFlagData clone() {
		XChangeRouteFlagData copy = null;
		try {
			copy = (XChangeRouteFlagData)super.clone();

			synchronized(flags) {
				copy.flags = new ArrayList<>(flags);
			}

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < lvl; c++) {sb.append(' ');}

		synchronized(flags) {
			for(Iterator<XChangeRouteFlag> it = flags.iterator(); it.hasNext();) {
				sb.append(it.next());
				if(it.hasNext()) {sb.append('/');}
			}
		}

		return sb.toString();
	}
}
