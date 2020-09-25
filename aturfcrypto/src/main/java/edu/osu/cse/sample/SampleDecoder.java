package edu.osu.cse.sample;


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

public class SampleDecoder {

    public static int GSIZE = 24;
    static int RSIZE = 18;

    public static void main(String[] args) throws Exception {

        String fname = "/home/cszuo/Dropbox/workspace/javaworkspace/dexTest/src/aturf/s.txt";
        List<String> contents = Files.readAllLines(Paths.get(fname), StandardCharsets.UTF_8);

        int all = 0, cor = 0, fulldata = 0, ind = 0;

        Hashtable<Integer, Integer> errs = new Hashtable<Integer, Integer>();
        for (String content : contents) {
            ind++;
            String[] sps = content.split("\t");
            String[] resA = sample(sps[0], false);
            String[] resB = sample(sps[0], true);

            String resStrA = String.join("", resA);
            String resStrB = String.join("", resB);

            all++;
            int errCount = Math.min(comp(resStrA, sps[2]), comp(resStrB, sps[2]));
            if(!errs.containsKey(errCount))
                errs.put(errCount, 0);
            errs.put(errCount, errs.get(errCount)+1);

            boolean cr = errCount <= 1;
            System.out.println(ind + "\t" + resStrA + "\t" + resStrB + "\t" + cr + "\t" + sps[0].length());
            if (cr)
                cor++;
            if(sps[0].length()>=1728/2)
                fulldata++;
            // System.out.println(resStr);
            // System.out.println(sps[2]);
            // byte[] resBytes =hexStringToByteArray(resStr);
            // System.out.println(resBytes.length);
            // System.err.println(": " + Arrays.toString(resBytes));
            if(cr){
                if(TestRS.deWdata(resStrA) == null && TestRS.deWdata(resStrB) == null){
                    System.err.println("MD!");
                    System.exit(0);
                }
            }
            // System.out.println(toHexString(MyReedSolomon.decode(resBytes)));
        }
        System.out.println(all + "\t" + fulldata + "\t" + cor);

        for(int ii:errs.keySet()){
            System.out.println(ii +"\t" + errs.get(ii));
        }
    }

    public static int comp(String stra, String strb) {
        String[] spa = split(stra, 2);
        String[] spb = split(strb, 2);
        int dif = 0;
        for (int i = 0; i < spb.length; i++) {
            if (!spa[i].equals(spb[i]))
                dif++;
        }
        // System.out.println(dif);
        return dif ;
    }

    public static List<String> getSample(String str){
        String[] resA = sample(str, false);
        String[] resB = sample(str, true);
        String resStrA = String.join("", resA);
        String resStrB = String.join("", resB);

        ArrayList<String> ret = new ArrayList<String>();
        ret.add(resStrA);
        ret.add(resStrB);

        return ret;
    }

    public static String getCorrectSample(List<String> strs){
        String tmp;
        for(String s :strs){
            tmp = TestRS.decode(s);
            if(tmp!=null)
                return tmp;
        }
        return null;
    }

    public static String[] sample(String content, boolean rev) {
        String[] rdata = split(content, 2);
        //Log.e("SAMPLE106",content+" "+Arrays.toString(rdata));

        // System.out.print(Arrays.toString(rdata)); 3 6 7
        List<String> restData = new ArrayList<>(Arrays.asList(rdata));

        String[] res = new String[RSIZE];

        if (rev) {
            for (int i = 0; i < RSIZE / 2; i++) {
                if (!restData.isEmpty()) {
                    res[i] = getOneStr(restData);
                    // System.out.println(res[i]);
                } else {
                    res[i] = "00";
                }
            }

            Collections.reverse(restData);
            for (int i = 0; i < RSIZE / 2; i++) {
                if (!restData.isEmpty()) {
                    res[res.length - i - 1] = getOneStr(restData);
                } else {
                    res[res.length - i - 1] = "00";
                }
            }
        } else {

            for (int i = 0; i < RSIZE; i++) {
                if (!restData.isEmpty()) {
                    res[i] = getOneStr(restData);
                    // System.out.println(res[i]);
                } else {
                    res[i] = "00";
                }
            }
        }

        // Collections.reverse(restData);
        // for (int i = 0; i < RSIZE / 2; i++) {
        // if (!restData.isEmpty()) {
        // res[res.length - i - 1] = getOneStr(restData);
        // } else {
        // res[i] = "00";
        // }
        // }

        return res;
    }

    public static String getOneStr(List<String> restData) {
        List<String> sample = new ArrayList<String>();
        Hashtable<String, Double> tj = new Hashtable<String, Double>();
        String tmp;
        String lastTmp;
        int index;
        while (!restData.isEmpty() && sample.size() < GSIZE) {
            tmp = restData.remove(0);
            index = sample.size();
            sample.add(tmp);

            if (!tmp.contains("-")) {
                if (!tj.containsKey(tmp))
                    tj.put(tmp, 0.0);
                tj.put(tmp, tj.get(tmp) + Math.pow(Math.min(index, GSIZE-index), 4));
            }
        }

        tmp = null;
        double max = -1;

        for (String d : tj.keySet()) {
            if (tj.get(d) > max) {
                max = tj.get(d);
                tmp = d;
            }
        }
        // System.out.println("[*] 1: " + tmp);
        // System.out.println("[*] 2: " + max);

        int next = sample.indexOf(tmp);
        // System.out.println("[*] 3: " + next);
        int found = -1;
        for (int i = next; i >= 0; i--) {
            if (restData.size() > i && restData.get(i).equals(tmp)) {
                found = i;
                break;
            }
        }
        if (found != -1) {
            for (int i = 0; i <= found; i++)
                restData.remove(0);
        }else{
            int toback = Math.max(sample.lastIndexOf(tmp), sample.size()-5);
            for(int i = sample.size() -1;i>toback;i--){
                restData.add(0, sample.get(i));
            }
        }
        //System.out.println(String.join("", sample) + "\t" + tmp);
        return tmp != null ? tmp : "00";

    }

    public static String[] split(String src, int len) {
        String[] result = new String[(int) Math.ceil((double) src.length() / (double) len)];
        for (int i = 0; i < result.length; i++)
            result[i] = src.substring(i * len, Math.min(src.length(), (i + 1) * len));
        return result;
    }

    public static String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    public static byte hexToByte(String hexString) {
        int firstDigit = toDigit(hexString.charAt(0));
        int secondDigit = toDigit(hexString.charAt(1));
        return (byte) ((firstDigit << 4) + secondDigit);
    }

    private static int toDigit(char hexChar) {
        int digit = Character.digit(hexChar, 16);
        if (digit == -1) {
            throw new IllegalArgumentException("Invalid Hexadecimal Character: " + hexChar);
        }
        return digit;
    }

    public static String toHexString(byte[] byteArray) {
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < byteArray.length; i++) {
            hexStringBuffer.append(byteToHex(byteArray[i]));
        }
        return hexStringBuffer.toString();
    }

    public static byte[] toByteArray(String hexString) {
        if (hexString.length() % 2 == 1) {
            throw new IllegalArgumentException("Invalid hexadecimal String supplied.");
        }

        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length(); i += 2) {
            bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
        }
        return bytes;
    }

    public static byte[] hexStringToByteArray(String str) {
        byte[] arrayOfValues = new byte[str.length() / 2];
        int counter = 0;
        for (int i = 0; i < str.length(); i += 2) {
            String s = str.substring(i, i + 2);
            arrayOfValues[counter] = (byte) Integer.parseInt(s, 16);
            counter++;
        }
        return arrayOfValues;
    }
}
