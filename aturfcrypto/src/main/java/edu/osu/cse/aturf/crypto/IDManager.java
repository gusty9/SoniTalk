package edu.osu.cse.aturf.crypto;

import android.content.Context;

import java.util.Calendar;
import java.util.List;

public class IDManager {

    private IDManager() {
    }

    static IDManager manager = new IDManager();
    static int MAXDAYS = 14;

    public static IDManager getManager() {
        return manager;
    }

    private Context context;
    private SeedManager seedManager;
    private DRIDDeriver dridDeriver;
    private RPIDDeriver rpidDeriver;

    public void setMAXDays(int len) {
        MAXDAYS = len;
    }

    public void setContext(Context context) {
        if (this.context == null) {
            this.context = context;
            seedManager = new SeedManager(context);
            dridDeriver = new DRIDDeriver(context, MAXDAYS, seedManager);
            rpidDeriver = new RPIDDeriver(dridDeriver);
        }
    }

    public RPID getCurrentRPID() {
        return rpidDeriver.fetchRPID(Calendar.getInstance().getTime());
    }

    public List<DRID> getDRIDs() {
        return dridDeriver.getDRIDs();
    }

    public List<RPID> deriveAllRPIDs(DRID drid) {
        return rpidDeriver.fetchAllRPIDs(drid);
    }
}
