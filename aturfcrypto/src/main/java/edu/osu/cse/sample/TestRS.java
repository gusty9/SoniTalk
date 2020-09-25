package edu.osu.cse.sample;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import edu.osu.cse.rs.MyReedSolomon;

public class TestRS {
    public static void main(String[] args) throws Exception {// 16CE
        //String sdata = "1EDC83DF19C07674BD06F828C5651548";
        //String strA = "8062E36D79B3967FB54E0431C2847078D01F";
        // String strA = encode(sdata );
        // System.out.println(": " + strA);
        // String strB = decode(strA);
        // System.out.println(": " + strB);
        //System.out.println(": " + deWdata(strA));


        String fname = "/home/cszuo/Dropbox/workspace/javaworkspace/dexTest/src/aturf/s.txt";
        List<String> contents = Files.readAllLines(Paths.get(fname), StandardCharsets.UTF_8);

        int all = 0, cor = 0, fulldata = 0, c100 = 0;
        for (String content : contents) {
            String[] sps = content.split("\t");
            List<String> samples = SampleDecoder.getSample(sps[0]);
            for(String gg: samples){
                String ttt = TestRS.deWdata(gg);
                if(ttt!=null){
                    cor++;
                    if(sps[2].subSequence(0, sps[2].length()-4).equals(ttt))c100++;else{
                        System.out.println(ttt+"\t"+sps[2]);
                    }
                    break;
                }
            }
            //String res = SampleDecoder.getCorrectSample(samples);

            all++;
            //if(res!=null)cor++;
            if(sps[0].length() >= 1728/2)fulldata++;
        }
        System.out.println(all + "\t" + fulldata + "\t" + cor + "\t" + c100);

    }

    public static String deWdata(String str) {
        String tmp = str.substring(str.length() - 4) + str.substring(0, str.length() - 4);
        //System.out.println(tmp);
        return decode(tmp);
    }

    public static String encode(String sdata) {
        String[] adata = SampleDecoder.split(sdata, 2);

        Integer[] data = new Integer[adata.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = Integer.parseInt(adata[i], 16);
        }
        // System.out.println(": " + Arrays.toString(data));

        Integer[] encoded = MyReedSolomon.encode(data);

        // System.out.println(": " + Arrays.toString(encoded));

        String str = "";
        String tmp;
        for (int i : encoded) {
            tmp = Integer.toString(i, 16).toUpperCase();
            str += tmp.length() == 2 ? tmp : "0" + tmp;
        }
        //System.out.println(": " + str);
        return str;
    }

    public static String decode(String sdata) {
        String[] adata = SampleDecoder.split(sdata, 2);
        //Log.e("DECODE", sdata + " " + Arrays.toString(adata));

        Integer[] data = new Integer[adata.length];
        for (int i = 0; i < data.length; i++) {
            try {
                data[i] = Integer.parseInt(adata[i], 16);
            } catch (Exception e) {
                data[i] = 0;
                //e.printStackTrace();
            }
        }
        // System.out.println(": " + Arrays.toString(data));

        Integer[] encoded = MyReedSolomon.decode(data);
        if (encoded == null)
            return null;
        //System.out.println(": " + Arrays.toString(encoded));

        String str = "";
        String tmp;
        for (int i : encoded) {
            tmp = Integer.toString(i, 16).toUpperCase();
            str += tmp.length() == 2 ? tmp : "0" + tmp;
        }
        //System.out.println(": " + str);
        return str;
    }

}
