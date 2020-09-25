package edu.osu.cse.aturf.crypto.utilities;

import android.content.Context;
import android.content.SharedPreferences;

import static android.content.Context.MODE_PRIVATE;

public class PersistentUtility {
    public static void save2File(Context context, String str, String fname){
        SharedPreferences preferences = context.getSharedPreferences(fname, MODE_PRIVATE);
        preferences.edit().putString("data", str).apply();

    }

    public static String readFromFile(Context context, String fname){
        return context.getSharedPreferences(fname, MODE_PRIVATE).getString("data", null);
    }
}
