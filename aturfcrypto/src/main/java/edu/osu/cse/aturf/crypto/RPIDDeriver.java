package edu.osu.cse.aturf.crypto;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import edu.osu.cse.aturf.crypto.utilities.DateUtility;
import edu.osu.cse.aturf.crypto.utilities.HashUtility;

public class RPIDDeriver {
    static String TAG = RPIDDeriver.class.getName();
    DRIDDeriver dridDeriver;

    RPID lastRPID = null;
    Calendar calendar = Calendar.getInstance();

    public RPIDDeriver(DRIDDeriver dridDeriver) {

        this.dridDeriver = dridDeriver;
    }

    private RPID newRPID(byte[] rpidk, Date now, byte[] dridk) {
        // =  dridDeriver.fetchDRID(now).getID();
        byte[] tmp = HashUtility.mergeArray(rpidk, dridk);
        byte[] hash = HashUtility.SHA256(tmp);
        byte[] newrpidk = new byte[16];
        System.arraycopy(hash, 0, newrpidk, 0, newrpidk.length);

        RPID rpid = new RPID();
        rpid.setDate(now);
        rpid.setKey(newrpidk);


        MYLog.i(TAG, "[+] new RPID drived:");
        MYLog.i(TAG, "[+]                :oldk:" + HashUtility.toHexString(rpidk));
        MYLog.i(TAG, "[+]                :drid:" + HashUtility.toHexString(dridk));
        MYLog.i(TAG, "[+]                :merg:" + HashUtility.toHexString(tmp));
        MYLog.i(TAG, "[+]                :s256:" + HashUtility.toHexString(hash));
        MYLog.i(TAG, "[+]                :new::" + rpid.toString());

        return rpid;

    }

    private RPID nextRPID(RPID old) {

        calendar.setTime(old.getDate());
        calendar.add(Calendar.MINUTE, 1);

        return newRPID(old.getID(), calendar.getTime(), dridDeriver.fetchDRID(calendar.getTime()).getID());
    }

    private RPID getFirst(Date date) {
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date zd = calendar.getTime();

        MYLog.i(TAG, zd.toString());
        return newRPID(HashUtility.allZeroArray(16), zd, dridDeriver.fetchDRID(zd).getID());
    }

    public RPID fetchRPID(Date date) {
        date = RPID.formatDate(date);
        if (lastRPID == null || !DateUtility.isSameDay(date, lastRPID.getDate())) {
            lastRPID = getFirst(date);
        }

        while (lastRPID.getDate().before(date)) {
            lastRPID = nextRPID(lastRPID);
        }

        return lastRPID;
    }

    public List<RPID> fetchAllRPIDs(DRID drid) {
        List<RPID> rpids = new ArrayList<RPID>();
        RPID tmp = newRPID(HashUtility.allZeroArray(16), RPID.formatDate(drid.getDate()), drid.getID());
        rpids.add(tmp);

        calendar.setTime(tmp.getDate());
        for (int i = 0; i < 60 * 24 - 1; i++) {
            calendar.add(Calendar.MINUTE, 1);
            tmp = newRPID(tmp.getID(), calendar.getTime(), drid.getID());
            rpids.add(tmp);
        }

        return rpids;
    }


}
