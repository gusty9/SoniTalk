/*
 * Copyright (c) 2019. Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniTalk Android SDK.
 *
 * SoniTalk Android SDK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SoniTalk Android SDK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SoniTalk Android SDK.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonitalk;

import android.util.Log;

import java.util.Arrays;

import at.ac.fhstp.sonitalk.utils.EncoderUtils;
import at.ac.fhstp.sonitalk.utils.RSUtils;

/**
 * Wrapper class for messages (received or to be sent).
 * crcIsCorrect, decodingTimeNanosecond are mostly used for debugging purpose and should not be
 * accessed during normal usage of the library.
 * rawAudio is optional for received messages (also a debugging support)
 */
public class SoniTalkMessage {
    /**
     * Actual message, received or to be sent. DecoderUtils contains functions to transform it
     * to/from UTF8 String if that is the kind of data you use.
     */
    private byte[] message;
    /**
     * crcIsCorrect is true by default (if not provided, we consider the message to be useful)
     */
    private boolean crcIsCorrect;
    /**
     * Time elapsed between reading the last piece of data and returning the decoded message.
     */
    private long decodingTimeNanosecond;
    /**
     * Received historyBuffer or generated buffer to be sent
     */
    private short[] rawAudio;

    /**
     * Constructor used to send unique sequence of hex characters
     * appends the reed solomon error correction code
     * @param message
     *          The hex string to send
     */
    SoniTalkMessage(String message) {
        message = RSUtils.getEDC(message) + message;
        this.message = EncoderUtils.hexStringToByteArray(message);
        Log.e(GaltonChat.TAG, "Sending message " + message);
        crcIsCorrect = true;
        decodingTimeNanosecond = 0;
    }

    // Add optional spectrum array ?
    /*package-private*/SoniTalkMessage(byte[] message) {
        this.message = message;
        crcIsCorrect = true;
        decodingTimeNanosecond = 0;
        rawAudio = null;
    }

    /*package-private*/SoniTalkMessage(byte[] message, boolean crcIsCorrect, long decodingTimeNanosecond) {
        this.message = message;
        this.crcIsCorrect = crcIsCorrect;
        this.decodingTimeNanosecond = decodingTimeNanosecond;
        this.rawAudio = null;
    }

    /*package-private*/SoniTalkMessage(byte[] message, boolean crcIsCorrect, long decodingTimeNanosecond, short[] rawAudio) {
        this.message = message;
        this.crcIsCorrect = crcIsCorrect;
        this.decodingTimeNanosecond = decodingTimeNanosecond;
        this.rawAudio = rawAudio;
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }

    public boolean isCrcCorrect() {
        return crcIsCorrect;
    }

    public void setCrcIsCorrect(boolean crcIsCorrect) {
        this.crcIsCorrect = crcIsCorrect;
    }

    public long getDecodingTimeNanosecond() {
        return decodingTimeNanosecond;
    }

    public void setDecodingTimeNanosecond(long decodingTimeNanosecond) {
        this.decodingTimeNanosecond = decodingTimeNanosecond;
    }

    /**
     * returns the data array as a hex string representation
     * @return
     *      The data in the byte array message represented as a hex string
     */
    public String getHexString() {
        StringBuilder sb = new StringBuilder();
        int mask = 0xFF; // used to convert the bytes to unsigned bytes (-128,128) -> (0,256)
        for (int i = 0; i < message.length; i++) {
            String temp = Integer.toString(message[i] & mask, 16);
            if (temp.length() == 1) {
                temp = "0" + temp;
            }
            sb.append(temp);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * Returns hex message after the reed solomon code has been decoded
     * @return
     */
    public String getDecodedMessage() throws NullPointerException {
        String hex = getHexString();
        String msg = hex.substring(4);
        String verify = hex.substring(0,4);
        return RSUtils.Decode(msg, verify);
    }

    /**
     * returns the raw audio signal (after generation for sending or after receiving)
     * PLEASE DO NOT MAKE PUBLIC
     * @return the audio signal corresponding to this message
     */
    /*package-private*/short[] getRawAudio() {
        return rawAudio;
    }

    /**
     * returns the raw audio signal (after generation for sending or after receiving)
     * PLEASE DO NOT MAKE PUBLIC
     * @param rawAudio the audio signal generated to be sent, or captured while receiving
     */
    /*package-private*/void setRawAudio(short[] rawAudio) {
        this.rawAudio = rawAudio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoniTalkMessage that = (SoniTalkMessage) o;
        return Arrays.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(message);
    }
}
