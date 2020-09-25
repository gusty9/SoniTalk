package edu.osu.cse.aturf.crypto.utilities;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtility {
    static SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
    public static boolean isSameDay(Date datea, Date dateb){
        return fmt.format(datea).equals(fmt.format(dateb));
    }
}
