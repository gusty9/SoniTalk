package edu.osu.cse.aturf.crypto;

import android.content.Context;
import android.util.Base64;

import java.util.Random;

import edu.osu.cse.aturf.crypto.utilities.PersistentUtility;

public class SeedManager {
    static String TAG = SeedManager.class.getName();

    Context context;
    static String FNAME = "seed";
    byte[] seed = null;

    public SeedManager(Context context) {
        this.context = context;
        initialize();
    }

    private void initialize() {
        loadSeedFile();
        if (seed == null) {
            seed = new byte[32];
            new Random().nextBytes(seed);
            PersistentUtility.save2File(context, Base64.encodeToString(seed, Base64.DEFAULT), FNAME);
            MYLog.i(TAG, "[+] seed file created");
        }
    }

    private void loadSeedFile() {
        if (seed != null) return;
        String seedStr = PersistentUtility.readFromFile(context, FNAME);
        if (seedStr != null) {
            MYLog.i(TAG, "[+] seed file loaded");
            seed = Base64.decode(seedStr, Base64.DEFAULT);
        }else{
            MYLog.i(TAG, "[*] seed file not exists");
        }
    }


    public byte[] getSeed() {
        loadSeedFile();
        return seed;
    }
}
