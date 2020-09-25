package edu.osu.cse.rs;
/* 
 * Reed-Solomon error-correcting code decoder demo (Java)
 * 
 * Copyright (c) 2019 Project Nayuki
 * All rights reserved. Contact Nayuki for licensing.
 * https://www.nayuki.io/page/reed-solomon-error-correcting-code-decoder
 */

import java.util.Arrays;
import java.util.Random;

/*
 * Reed-Solomon error-correcting code decoder demo (Java)
 *
 * Copyright (c) 2019 Project Nayuki
 * All rights reserved. Contact Nayuki for licensing.
 * https://www.nayuki.io/page/reed-solomon-error-correcting-code-decoder
 */

import java.util.Arrays;
import java.util.Random;


public final class MyReedSolomon {

	// Runs a bunch of demos and tests, printing information to standard error.
	public static void main(String[] args) {
		byte[] data = new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
		byte[] encoded = encode(data);

		int index = new Random().nextInt(18);
		byte[] randomBytes = new byte[1];
		new Random().nextBytes(randomBytes);
		System.err.println(": " + index);
		System.err.println(": " + randomBytes[0]);
		encoded[index] = (byte) (randomBytes[0]>0?randomBytes[0]:-randomBytes[0]);
		System.err.println(": " + Arrays.toString(encoded));


		byte[] decoded = decode(encoded);
		System.err.println(": " + Arrays.toString(decoded));
	}

	static int msgLen = 16;
	public static byte[] encode(byte[] data) {

		// Generate random message
		Integer[] message = new Integer[msgLen];
		for (int i = 0; i < message.length; i++)
			message[i] = (int) data[i];

		// Encode message to produce codeword
		Integer[] codeword = encode(message);
		byte[] retData = new byte[codeword.length];
		for (int i = 0; i < retData.length; i++)
			retData[i] = codeword[i].byteValue();

		return retData;
	}

	public static Integer[] encode(Integer[] message) {
		BinaryField field = new BinaryField(0x11D);
		Integer generator = 0x02;
		int eccLen = 2;
		ReedSolomon<Integer> rs = new ReedSolomon<>(field, generator, Integer.class, msgLen, eccLen);
		// Encode message to produce codeword
		return rs.encode(message);
	}

	// Shows an example of encoding a binary message, and decoding a codeword containing errors.
	public static byte[] decode(byte[] data) {
		// Configurable parameters

		// Generate random message
		Integer[] message = new Integer[data.length];
		for (int i = 0; i < data.length; i++)
			message[i] = (int) data[i];

		Integer[] decoded = decode(message);

		byte[] retData = new byte[decoded.length];
		for (int i = 0; i < retData.length; i++)
			retData[i] = decoded[i].byteValue();
		return retData;
	}
	public static Integer[] decode(Integer[] message) {
		BinaryField field = new BinaryField(0x11D);
		Integer generator = 0x02;
		int msgLen = 16;
		int eccLen = 2;
		ReedSolomon<Integer> rs = new ReedSolomon<>(field, generator, Integer.class, msgLen, eccLen);
		return rs.decode(message);
	}
}


