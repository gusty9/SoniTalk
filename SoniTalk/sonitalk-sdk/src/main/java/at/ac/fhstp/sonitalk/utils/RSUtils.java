package at.ac.fhstp.sonitalk.utils;

import android.util.Log;

import java.util.Arrays;

import edu.osu.cse.rs.BinaryField;
import edu.osu.cse.rs.ReedSolomon;

public class RSUtils {

    static final Integer GENERATOR = 0x02;
    static final int msgLength = 16;
    static final int edcLength = 2;

    static final BinaryField field = new BinaryField(0x11D);

    /**
     * @param hex
     * @param verify
     * @return
     */
    public static String Decode(String hex, String verify) throws NullPointerException {

        String x = verify + hex;
        //System.err.println("Original message\t\t: " + x);

        ReedSolomon<Integer> rs = new ReedSolomon<>(field, GENERATOR, Integer.class, msgLength, edcLength);

        Integer[] message = new Integer[x.length() / 2];
        for (int i = 0; i < message.length; i++) {
            try {
                message[i] = Integer.parseInt(x.substring(2 * i, 2 * (i + 1)), 16);
            } catch (Exception e) {
                message[i] = Integer.parseInt("FF", 16);
            }
        }
        //System.err.println("Original message\t\t: " + Arrays.toString(message));

        //Log.e("CRC_ENCODE_STRING_TO_BYTE", total.length + " ");

        Integer[] raw = rs.decode(message);

        //System.err.println("Encoded codeword\t\t: " + Arrays.toString(raw));

        StringBuilder result = new StringBuilder();

        for (Integer k : raw) {
            result.append(Integer.toHexString((k & 0xf0) >>> 4)).append(Integer.toHexString(k & 0x0f));
        }


        return result.toString().toUpperCase();
    }


    public static String getEDC(String x) {

        //Log.e("CRC_ENCODE_STRING_TO_BYTE", total.length + " ");


        ReedSolomon<Integer> rs = new ReedSolomon<>(field, GENERATOR, Integer.class, msgLength, edcLength);

        Integer[] message = new Integer[x.length() / 2];
        for (int i = 0; i < message.length; i++) {
            message[i] = Integer.parseInt(x.substring(2 * i, 2 * (i + 1)), 16);
        }
        //System.err.println("Original message\t\t: " + Arrays.toString(message));

        Integer[] edc = rs.encode(message);

        //System.err.println("Encoded codeword\t\t: " + Arrays.toString(edc));

        StringBuilder result = new StringBuilder();

        for (Integer k : edc) {
            result.append(Integer.toHexString((k & 0xf0) >>> 4)).append(Integer.toHexString(k & 0x0f));
        }
        //System.err.println("HEX Encoded codeword\t\t: " + result.toString().toUpperCase());

        return result.substring(0, 4).toUpperCase();
    }
}
