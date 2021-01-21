package at.ac.fhstp.sonitalk;

import android.util.Log;

import java.util.List;

import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

public class ChannelAnalyzer {


    public static int selectChannel(float[] historyBuffer, List<SoniTalkConfig> configList, int analysisWindowLength) {
        //loop through all of the configs and see if they are free
        for (int i = 0; i < configList.size(); i++) {
            float[] frontWindow = new float[analysisWindowLength];
            float[] frontWindowFlag = new float[analysisWindowLength];
            System.arraycopy(historyBuffer, 0, frontWindow, 0, analysisWindowLength);
            System.arraycopy(historyBuffer, 0, frontWindowFlag, 0, analysisWindowLength);
            int channelWidth = DecoderUtils.getBandpassWidth(configList.get(i).getnFrequencies(), configList.get(i).getFrequencySpace());
            int centerFrequencyChannel = configList.get(i).getFrequencyZero() + (channelWidth/2);
            Butterworth butterworthChannel = new Butterworth();
            Butterworth butterworthFlag = new Butterworth();
            butterworthChannel.bandPass(8, DynamicConfigProtocol.SAMPLE_RATE, centerFrequencyChannel, channelWidth);
            butterworthFlag.bandPass(8, DynamicConfigProtocol.SAMPLE_RATE, configList.get(i).getFlagFrequency(), 100);
            int nextPowerTwo = DecoderUtils.nextPowerOfTwo(analysisWindowLength);
            double[] channelResponse = new double[nextPowerTwo];
            double[] flagResponse = new double[nextPowerTwo];

            for (int j = 0; j <  frontWindow.length; j ++) {
                channelResponse[j] = butterworthChannel.filter(frontWindow[j]);
                flagResponse[j] = butterworthFlag.filter(frontWindowFlag[j]);
            }

            ComplexArray complexArrayChannel = Hilbert.transform(channelResponse);
            ComplexArray complexArrayFlag = Hilbert.transform(flagResponse);

            double sumChannel = 0.0;
            double sumFlag = 0.0;

            for (int j = 0; j  <complexArrayFlag.real.length; j++) {
                sumChannel += DecoderUtils.getComplexAbsolute(complexArrayChannel.real[j], complexArrayChannel.imag[j]);
                sumFlag += DecoderUtils.getComplexAbsolute(complexArrayFlag.real[j], complexArrayFlag.imag[j]);
            }
            if (sumFlag > sumChannel * 2.5) {
                //this channel is occupied
                Log.e("Channel " + i, "occupied");
                Log.e("flag", "" + sumFlag);
                Log.e("channel", "" + sumChannel);
            } else {
                //Log.e("Channel " + i, "free");

            }
        }
        return -1;//todo
    }
}
