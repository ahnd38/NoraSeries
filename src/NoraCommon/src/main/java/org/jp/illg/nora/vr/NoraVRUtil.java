package org.jp.illg.nora.vr;

import java.util.Random;

public class NoraVRUtil {

	private static final Random clientIDRandom =
		new Random(System.currentTimeMillis() ^ (long)0x65d849ed);

	private static final Random loginChallengeCodeRandom =
		new Random(System.currentTimeMillis() ^ (long)0x984d94fe);

	private NoraVRUtil() {
		super();
	}

	public static long createClientID() {
		synchronized(clientIDRandom) {
			return (clientIDRandom.nextInt() + 0x80000000L) & 0xFFFFFFFFL;
		}
	}

	public static long createLoginChallengeCode() {
		synchronized(loginChallengeCodeRandom) {
			return (loginChallengeCodeRandom.nextInt() + 0x80000000L) & 0xFFFFFFFFL;
		}
	}
}
