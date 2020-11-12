package at.ac.fhstp.sonitalk.utils;

import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;

public class TypeUtils {

    /**
     * Converts an input array from short to [-1.0;1.0] float, result is put into the (pre-allocated) output array
     * @param input
     * @param output Should be allocated beforehand
     * @param arrayLength
     */
    public static void convertShortToFloat(short[] input, float[] output, int arrayLength) {
        for (int i = 0; i < arrayLength; i++) {
            // Do we actually need float anywhere ? Switch to double ?
            output[i] = ((float) input[i]) / Short.MAX_VALUE;
        }
    }

    /**
     *
     * Converts an input array from [-1.0;1.0] float to short full range and returns it
     * @param input
     * @return a short array containing short values with a distribution similar to the input one
     */
    public static short [] convertFloatToShort(float[] input) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            // Do we actually need float anywhere ? Switch to double ?
            output[i] = (short) (input[i] * Short.MAX_VALUE);
        }
        return output;
    }

    /**
     * temp wrapper function to create a thread safe version of the
     * hilbert transform. Just uses synchronous access to the normal
     * hilbert transform
     * todo find an actual thread safe hilbert transform function
     */
    public static synchronized ComplexArray threadSafeHilbertTransform(double[] x) {
        return Hilbert.transform(x);
    }

}
