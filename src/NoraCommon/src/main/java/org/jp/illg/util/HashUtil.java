package org.jp.illg.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

	private HashUtil() {
		super();
	}

	public static byte[] calcSHA256(byte[] input) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		}catch(NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}

		if(input != null)
			md.update(input);

		return md.digest();
	}

}
