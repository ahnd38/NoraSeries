package org.jp.illg.nora.gateway.service.norausers.model;

import java.util.Map;

import lombok.Data;

@Data
public class Result {
	
	private String resultType;
	
	private String requestType;
	
	private Map<String, String> results;
	
	public Result() {
		super();
	}
	
}
