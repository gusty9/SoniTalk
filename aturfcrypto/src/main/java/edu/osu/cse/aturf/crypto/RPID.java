package edu.osu.cse.aturf.crypto;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import edu.osu.cse.aturf.crypto.utilities.HashUtility;

public class RPID {
    public final static SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd:HH:mm", Locale.getDefault());
    static String SPLITER = "|||||";

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

    public static RPID parse(String data) {
        String[] tmps = data.split(SPLITER);
        RPID drid = new RPID();
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

}
