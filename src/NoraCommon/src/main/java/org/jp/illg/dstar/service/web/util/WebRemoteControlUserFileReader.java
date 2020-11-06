package org.jp.illg.dstar.service.web.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.jp.illg.dstar.service.web.model.UserEntry;
import org.jp.illg.dstar.service.web.model.WebRemoteUserGroup;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebRemoteControlUserFileReader {

	private static final String logTag = WebRemoteControlUserFileReader.class.getSimpleName() + " : ";

	private WebRemoteControlUserFileReader() {}

	public static List<UserEntry> readUserFile(@NonNull String filePath) {
		final File file = new File(filePath);
		if(!file.exists()) {return Collections.emptyList();}
		if(!file.canRead()) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read user list " + filePath);

			return null;
		}

		final List<UserEntry> result = new ArrayList<>();
		boolean isSuccess = false;

		final FileBasedConfigurationBuilder<XMLConfiguration> builder =
			new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
					.configure(new Parameters().fileBased().setFile(file));

		builder.setAutoSave(false);

		try {
			final XMLConfiguration xml = builder.getConfiguration();

			isSuccess = readUsers("", xml, result);
		}catch(ConfigurationException ex) {
			if(log.isErrorEnabled()) {
				log.error(logTag + "Could not read Configuration file = " + filePath);
			}

			return null;
		}

		return isSuccess ? result : null;
	}

	private static boolean readUsers(
		final String parentKey,
		final XMLConfiguration xml,
		final List<UserEntry> result
	) {
		final String key = "Users";
		final String allKey =
			(parentKey != null && !"".equals(parentKey)) ? parentKey + "." + key : key;

		boolean isSuccess = true;

		final List<HierarchicalConfiguration<ImmutableNode>> userNodes =
			xml.configurationsAt(allKey + "." + "UserEntry");
		for(final HierarchicalConfiguration<ImmutableNode> userNode : userNodes) {
			final UserEntry userEntry = new UserEntry();

			final boolean readOk = readUser(allKey, userNode, userEntry);
			if(readOk) {result.add(userEntry);}

			isSuccess &= readOk;
		}

		return isSuccess;
	}

	private static boolean readUser(
		final String parentKey,
		final HierarchicalConfiguration<ImmutableNode> node,
		final UserEntry result
	) {
		String attr;

		attr = "[@username]";
		result.setUsername(node.getString(attr, UserEntry.usernameDefault));

		attr = "[@password]";
		result.setPassword(node.getString(attr, UserEntry.passwordDefault));
		if(result.getPassword().length() < 4) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Illegal " + result.getUsername() +  "'s password length < 4," + parentKey + attr);

			return false;
		}

		attr = "[@group]";
		final String groupString = node.getString(attr, UserEntry.groupDefault.getTypeName());
		final WebRemoteUserGroup group = WebRemoteUserGroup.getTypeByNameIgnoreCase(groupString);
		if(group == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Illegal " + result.getUsername() +  "'s group " + groupString + "/" + parentKey + attr);

			return false;
		}
		result.setGroup(group);

		return true;
	}
}
