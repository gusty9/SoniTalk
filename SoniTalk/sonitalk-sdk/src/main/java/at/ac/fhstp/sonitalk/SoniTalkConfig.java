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

import at.ac.fhstp.sonitalk.utils.DecoderUtils;

/**
 * Configuration, or profile, used to transmit data. The emitter and receiver of a message must use
 * the same configuration. A crucial use case will be transmitting with several profiles simultaneously.
 * This will allow for faster communication within one app and simultaneous communication of several apps.
 * To get preset configurations, the utility class ConfigFactory has the function loadFromJson().
 */
public class SoniTalkConfig {
    private int frequencyZero;// = 18000; (Hz)
    private int bitperiod;// = 100; (ms)
    private int pauseperiod;// = 0; (ms)
    private int nMaxCharacters;// = 18; // Remove ? Not used anymore. or Rename to payload ? or nBytesMessage ? nBytesContent ?
    private int nParityBytes; //  = 2 Parity bytes (actually 16 bits)
    private int nMessageBlocks;
    private int nFrequencies;// = 16;
    private int frequencySpace;// = 100; (Hz)

    public SoniTalkConfig(int frequencyZero, int bitperiod, int pauseperiod, int nMessageBlocks, int nFrequencies, int frequencySpace) {
        this.frequencyZero = frequencyZero;
        this.bitperiod = bitperiod;
        this.pauseperiod = pauseperiod;
        this.nMessageBlocks = nMessageBlocks;
        this.nFrequencies = nFrequencies;
        this.frequencySpace = frequencySpace;
    }

    public int getFrequencyZero() {
        return frequencyZero;
    }

    public void setFrequencyZero(int frequencyZero) {
        this.frequencyZero = frequencyZero;
    }

    public int getBitperiod() {
        return bitperiod;
    }

    public int getAnalysisWinLen(int sampleRate) {
        int bitperiodInSamples = (int)Math.round(bitperiod * (float)sampleRate/1000);
        return  (int)Math.round((float) bitperiodInSamples );
    }

    public int getBandpassWidth() {
        return DecoderUtils.getBandpassWidth(nFrequencies, frequencySpace);
    }

    public void setBitperiod(int bitperiod) {
        this.bitperiod = bitperiod;
    }

    public int getPauseperiod() {
        return pauseperiod;
    }

    public void setPauseperiod(int pauseperiod) {
        this.pauseperiod = pauseperiod;
    }

    public int getnMessageBlocks() {
        return nMessageBlocks;
    }

    public void setnMessageBlocks(int nMessageBlocks) {
        this.nMessageBlocks = nMessageBlocks;
    }

    public int getnFrequencies() {
        return nFrequencies;
    }

    public void setnFrequencies(int nFrequencies) {
        this.nFrequencies = nFrequencies;
    }

    public int getFrequencySpace() {
        return frequencySpace;
    }

    public void setFrequencySpace(int frequencySpace) {
        this.frequencySpace = frequencySpace;
    }

    /**
     * return the required size of the circular array buffer
     * to fit the entire message from this configuration in it
     * @param sampleRate
     *          The sample rate the microphone buffer is being accessed at
     * @return
     *          The size of the history buffer array
     */
    public int getHistoryBufferSize(int sampleRate) {
        int bitperiodInSamples = (int) Math.round(bitperiod * sampleRate/1000.0);
        int pauseperiodInSamples = (int) Math.round(pauseperiod * sampleRate/1000.0);
        int  nBlocks = (int) Math.ceil(getnMessageBlocks() *2 ) + 2;
        return bitperiodInSamples * nBlocks;
    }

    /**
     * @return
     *      How long a message takes to play in ms
     *      for some reason it is x2??
     */
    public int getMessageDurationMS() {
        //todo figure out why its * 2 and not what i think it is
        return (int) (((getnMessageBlocks() + 2) * getBitperiod()) * (2));
    }

}
