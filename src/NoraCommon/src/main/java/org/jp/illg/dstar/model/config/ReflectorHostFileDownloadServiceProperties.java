package org.jp.illg.dstar.model.config;

import java.util.LinkedList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class ReflectorHostFileDownloadServiceProperties {
	
	private boolean enable;
	
	@Setter(AccessLevel.PRIVATE)
	private List<ReflectorHostFileDownloadURLEntry> urlEntries;
	
	public ReflectorHostFileDownloadServiceProperties() {
		super();
		
		enable = false;
		urlEntries = new LinkedList<>();
	}
	
}
