package at.ac.fhstp.sonitalk.utils;

import android.util.Log;

import java.util.Random;

/**
 * Class used to represent a hex string ID to be send over acoustic protocol
 */
public class ID {

    final static public int ID_LENGTH = 32;
    static String CURRENTID = "5BF689A62F6A5257DC5EEE226B1B0821";
    //static Random random = new Random((int) System.currentTimeMillis());
    static Random random = new Random(System.currentTimeMillis());

    static public String generateRandomID() {
        int[] id = new int[ID_LENGTH];

        for (int i = 0; i < ID_LENGTH; i++)
        {
            id[i] = Math.abs(random.nextInt() % 16);
        }

        StringBuilder sid = new StringBuilder();


        for (int i = 0; i < ID_LENGTH; i++) {
            sid.append(Integer.toHexString(id[i] & 0xf));
        }
        return sid.toString().toUpperCase();
    }

    static public boolean isValidID(String id){
        if(id.length()!=ID_LENGTH)
            return false;

        for(int i = 1;i<ID_LENGTH;i++)
        {
            if(id.charAt(i)==id.charAt(i-1))
                return false;
        }

        Log.e("ID Checking",id);
        return true;
    }

}
