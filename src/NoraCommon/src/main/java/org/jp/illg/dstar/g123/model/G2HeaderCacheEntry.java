package org.jp.illg.dstar.g123.model;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class G2HeaderCacheEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createTime;

	@Getter
	private final Timer activityTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int frameID;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARPacket headerPacket;


	private G2HeaderCacheEntry() {
		super();

		setCreateTime(System.currentTimeMillis());

		activityTime = new Timer();
	}

	public G2HeaderCacheEntry(int frameID, DSTARPacket headerPacket) {
		this();

		setFrameID(frameID);
		setHeaderPacket(headerPacket);
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

		sb.append(String.format("[FrameID]:0x%04X", getFrameID()));

		sb.append("/");

		sb.append("[CreateTime]:");
		sb.append(FormatUtil.dateFormat(getCreateTime()));

		sb.append("/");

		sb.append("[ActivityTime]:");
		sb.append(FormatUtil.dateFormat(getActivityTime().getTimestampMilis()));

		if(getHeaderPacket() != null) {
			sb.append("\n");
			sb.append(getHeaderPacket().toString(indentLevel + 4));
		}

		return sb.toString();
	}

}
