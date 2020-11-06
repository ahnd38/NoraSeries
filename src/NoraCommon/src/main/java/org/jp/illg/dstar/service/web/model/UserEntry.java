package org.jp.illg.dstar.service.web.model;

import lombok.Data;

@Data
public class UserEntry {

	private String username;
	public static final String usernameDefault = "";

	private String password;
	public static final String passwordDefault = "";

	private WebRemoteUserGroup group;
	public static final WebRemoteUserGroup groupDefault = WebRemoteUserGroup.Guests;
}
