/**
 *
 */
package org.jp.illg.util;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;

import lombok.NonNull;

/**
 * @author AHND
 *
 */
public class ArrayUtil {

//--------------------------------------------------------------------------------

	public static void copyOf(char[] dst,char[] src) {
		if(dst == null || src == null) {return;}

		for(int i = 0;dst.length > i && src.length > i;i++)
			dst[i] = src[i];

		return;
	}

	public static void copyOf(byte[] dst,byte[] src) {
		if(dst == null || src == null) {return;}

		for(int i = 0;dst.length > i && src.length > i;i++)
			dst[i] = src[i];

		return;
	}

	public static void copyOf(short[] dst,short[] src) {
		if(dst == null || src == null) {return;}

		for(int i = 0;dst.length > i && src.length > i;i++)
			dst[i] = src[i];

		return;
	}

//--------------------------------------------------------------------------------

	public static void copyOfRange(char[] dst, char[] src, int srcFrom, int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom
		) {return;}

		int f = srcFrom;
		for(int i = 0;dst.length > i && src.length > f && f < srcTo;i++) {
			dst[i] = src[f];
			f++;
		}

		return;
	}

	public static void copyOfRange(byte[] dst, char[] src, int srcFrom, int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom
		) {return;}

		int f = srcFrom;
		for(int i = 0;dst.length > i && src.length > f && f < srcTo;i++) {
			dst[i] = (byte)src[f];
			f++;
		}

		return;
	}

	public static void copyOfRange(char[] dst, byte[] src, int srcFrom, int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom
		) {return;}

		int f = srcFrom;
		for(int i = 0;dst.length > i && src.length > f && f < srcTo;i++) {
			dst[i] = (char)src[f];
			f++;
		}

		return;
	}

//--------------------------------------------------------------------------------

	public static void copyOfRange(char[] dst, int dstOffset, char[] src) {
		if(
			dst == null || src == null ||
			(dst.length - dstOffset) < src.length
		) {return;}

		for(int i = 0; i < src.length && (i + dstOffset) < dst.length; i++)
			dst[i + dstOffset] = src[i];

		return;
	}

	public static void copyOfRange(byte[] dst, int dstOffset, byte[] src) {
		if(
			dst == null || src == null ||
			(dst.length - dstOffset) < src.length
		) {return;}

		for(int i = 0; i < src.length && (i + dstOffset) < dst.length; i++)
			dst[i + dstOffset] = src[i];

		return;
	}

	public static void copyOfRange(byte[] dst, int dstOffset, char[] src) {
		if(
			dst == null || src == null ||
			(dst.length - dstOffset) < src.length
		) {return;}

		for(int i = 0; i < src.length && (i + dstOffset) < dst.length; i++)
			dst[i + dstOffset] = (byte)src[i];

		return;
	}

	public static void copyOfRange(char[] dst, int dstOffset, byte[] src) {
		if(
			dst == null || src == null ||
			(dst.length - dstOffset) < src.length
		) {return;}

		for(int i = 0; i < src.length && (i + dstOffset) < dst.length; i++)
			dst[i + dstOffset] = (char)src[i];

		return;
	}

//--------------------------------------------------------------------------------

	public static void copyOfRange(byte[] dst, byte[] src,int srcFrom,int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom
		) {return;}

		int f = srcFrom;
		for(int i = 0;dst.length > i && src.length > f && f < srcTo;i++) {
			dst[i] = src[f];
			f++;
		}

		return;
	}

//--------------------------------------------------------------------------------

	public static void copyOfRange(byte[] dst, byte[] src, int dstFrom, int dstTo, int srcFrom, int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom ||
			dstFrom > dstTo ||  dst.length < dstFrom ||
			((dstTo - dstFrom) != (srcTo - srcFrom))
		) {return;}

		int dstPtr = dstFrom;
		int srcPtr = srcFrom;

		while(dstPtr < dstTo && srcPtr < srcTo) {
			dst[dstPtr++] = src[srcPtr++];
		}

		return;
	}

	public static void copyOfRange(byte[] dst, char[] src, int dstFrom, int dstTo, int srcFrom, int srcTo) {
		if(
			dst == null || src == null ||
			srcFrom > srcTo || src.length < srcFrom ||
			dstFrom > dstTo ||  dst.length < dstFrom ||
			((dstTo - dstFrom) != (srcTo - srcFrom))
		) {return;}

		int dstPtr = dstFrom;
		int srcPtr = srcFrom;

		while(dstPtr < dstTo && srcPtr < srcTo) {
			dst[dstPtr++] = (byte)src[srcPtr++];
		}

		return;
	}

//--------------------------------------------------------------------------------

	public static byte[] convertByteBufferToByteArray(ByteBuffer buffer) {
		if(buffer == null) {return null;}

		byte[] array = new byte[buffer.remaining()];
		for(int i = 0; i < array.length && buffer.hasRemaining(); i++)
			array[i] = buffer.get();

		return array;
	}

//--------------------------------------------------------------------------------

	@SafeVarargs
	public static <T> T[] concat(final T[] array1, final T... array2) {
		final Class<?> type1 = array1.getClass().getComponentType();

		@SuppressWarnings("unchecked")
		final T[] joinedArray = (T[]) Array.newInstance(type1, array1.length + array2.length);

		System.arraycopy(array1, 0, joinedArray, 0, array1.length);
		System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);

		return joinedArray;
	}

//--------------------------------------------------------------------------------

	public static final byte[] concatByteArray(@NonNull final byte[] array1, @NonNull final byte[] array2) {

		final byte[] joinedArray = new byte[array1.length + array2.length];

		System.arraycopy(array1, 0, joinedArray, 0, array1.length);
		System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);

		return joinedArray;
	}
}
