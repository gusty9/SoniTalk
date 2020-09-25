package edu.osu.cse.aturf.crypto;

import android.content.Context;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.Date;
import java.util.List;

import edu.osu.cse.aturf.crypto.utilities.HashUtility;
import edu.osu.cse.aturf.crypto.utilities.PersistentUtility;

public class DRIDDeriver {
    static String TAG = DRIDDeriver.class.getName();

    Context context;
    SeedManager sm;

    int MAXDAYS;
    static String FNAME = "drids";
    static String SPLITTER = "XXXXXXX";

    LinkedList<DRID> queue = new LinkedList<DRID>();
    public DRIDDeriver(Context context, int maxd, SeedManager sm) {
        this.sm = sm;
        this.context = context;
        MAXDAYS = maxd;
        loadQueue();
    }

    public void add(DRID ddid){
        queue.add(ddid);
        MYLog.i(TAG, "[+] drid added:" + ddid.toString());
        while(queue.size() > MAXDAYS)
            queue.remove();
        MYLog.i(TAG, "[+] drid queue len:" + queue.size());
    }

    private void loadQueue() {
        String dridsStr = PersistentUtility.readFromFile(context, FNAME);
        if (dridsStr != null){
            for (String str : dridsStr.split(SPLITTER)) {
                if (str.trim().length() <= 0) continue;
                add(DRID.parse(str.trim()));
            }
            MYLog.i(TAG, "[+] drid queue file loaded:" + queue.size());
        }
    }

    private void saveQueue(){
        StringBuilder sb = new StringBuilder();
        for(DRID drid :queue){
            sb.append(drid.toString());
            sb.append(SPLITTER);
        }
        PersistentUtility.save2File(context, sb.toString(),FNAME);
        MYLog.i(TAG, "[+] drid file created");
    }

    private DRID newDRID(byte[] lastDRIDK, Date newDate){
        byte[] tarray = HashUtility.mergeArray(lastDRIDK, sm.getSeed());
        DRID drid = new DRID();
        drid.setDate(newDate);
        drid.setKey(HashUtility.SHA256(tarray));

        MYLog.i(TAG, "[+] new DRID drived:");
        MYLog.i(TAG, "[+]                :oldk:" + HashUtility.toHexString(lastDRIDK));
        MYLog.i(TAG, "[+]                :seed:" + HashUtility.toHexString(sm.getSeed()));
        MYLog.i(TAG, "[+]                :merg:" + HashUtility.toHexString(tarray));
        MYLog.i(TAG, "[+]                :s256:" + HashUtility.toHexString(HashUtility.SHA256(tarray)));
        MYLog.i(TAG, "[+]                :new::" + drid.toString());

        return drid;
    }

    private void makeItLatest(Date date){
        DRID tmpDRID;
        DRID newDRID;
        Calendar calendar = Calendar.getInstance();
        while(queue.getLast().getDate().before(date)){
            tmpDRID = queue.getLast();
            calendar.setTime(tmpDRID.getDate());
            calendar.add(Calendar.DATE, 1);

            newDRID = newDRID(tmpDRID.getID(), calendar.getTime());
            add(newDRID);
        }
    }

    public DRID fetchDRID(Date date){
        date = DRID.formatDate(date);
        DRID drid;
        if(queue.isEmpty()){
            drid = newDRID(HashUtility.allZeroArray(32), date);
            add(drid);
        }else{
            makeItLatest(date);
            drid = queue.getLast();
        }
        saveQueue();
        return drid;

    }

    public List<DRID> getDRIDs(){
        return (List<DRID>) queue.clone();
    }

}
