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


public final class ReedSolomonDemo {
	
	// Runs a bunch of demos and tests, printing information to standard error.
	public static void main(String[] args) {
		showBinaryExample();
		//showPrimeExample();
		//testCorrectness();
	}
	
	
	// Shows an example of encoding a binary message, and decoding a codeword containing errors.
	public static void showBinaryExample() {
		// Configurable parameters
		BinaryField field = new BinaryField(0x11D);
		Integer generator = 0x02;
		int msgLen = 16;
		int eccLen = 2;
		ReedSolomon<Integer> rs = new ReedSolomon<>(field, generator, Integer.class, msgLen, eccLen);
		
		// Generate random message
		Integer[] message = new Integer[msgLen];
		for (int i = 0; i < message.length; i++)
			message[i] = rand.nextInt(field.size);
		System.err.println("Original message\t\t: " + Arrays.toString(message));
		
		// Encode message to produce codeword
		Integer[] codeword = rs.encode(message);
		System.err.println("Encoded codeword\t\t: " + Arrays.toString(codeword));
		
		// Perturb some values in the codeword
		double probability = (double)(eccLen / 2) / (msgLen + eccLen);
		int perturbed = 0;
		for (int i = 0; i < codeword.length; i++) {
			if (rand.nextDouble() < probability) {
				codeword[i] = field.add(codeword[i], rand.nextInt(field.size - 1) + 1);
				perturbed++;
			}
		}
		System.err.println("Number of values perturbed: " + perturbed);
		System.err.println("Perturbed codeword\t\t: " + Arrays.toString(codeword));
		
		// Try to decode the codeword
		Integer[] decoded = rs.decode(codeword);
		System.err.println("Decoded message\t\t\t: " + (decoded != null ? Arrays.toString(decoded) : "Failure"));
		System.err.println();
	}
	
	
	private static final Random rand = new Random();
	
}

