package edu.osu.cse.aturf.crypto;


import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.util.Random;

import edu.osu.cse.aturf.crypto.utilities.HashUtility;

public class DRID {
    public final static SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    static String SPLITER = ":::::";

    Date date;
    byte[] key;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public byte[] getID() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public String toString() {
        return DATEFORMAT.format(getDate()) + SPLITER + HashUtility.toHexString(getID());
    }

    public static DRID parse(String data) {
        String[] tmps = data.split(SPLITER);
        DRID drid = new DRID();
        try {
            drid.setDate(DATEFORMAT.parse(tmps[0]));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        drid.setKey(HashUtility.toByteArray(tmps[1]));

        return drid;
    }

    public static Date formatDate(Date date){
        try {
            return DATEFORMAT.parse(DATEFORMAT.format(date));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] arg) throws ParseException {
        byte[] seed = new byte[32];
        new Random().nextBytes(seed);
        System.out.println(new BigInteger(1, seed).toString(16));
        String sdate = "2019-02-12";
        DRID dd = new DRID();
        dd.setDate(DATEFORMAT.parse(sdate));
        dd.setKey(seed);

        System.out.println(dd.toString());

        dd = DRID.parse(dd.toString());

        System.out.println(dd.getDate());
        System.out.println(new BigInteger(1, dd.getID()).toString(16));
    }
}
