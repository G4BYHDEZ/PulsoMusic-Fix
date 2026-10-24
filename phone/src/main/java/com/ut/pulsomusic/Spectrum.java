package com.ut.pulsomusic;

/** Sixteen logarithmically spaced bands. No microphone data or synthetic levels. */
public final class Spectrum {
    public static final int SIZE=1024, RATE=16000, BANDS=16;
    public static float[] analyze(short[] pcm, int count) {
        float[] out=new float[BANDS];
        count=Math.min(count,pcm.length);
        if(count<2) return out;
        for(int b=0;b<BANDS;b++) {
            double frequency=80*Math.pow(7000.0/80,b/(double)(BANDS-1));
            double coefficient=2*Math.cos(2*Math.PI*frequency/RATE), q1=0,q2=0;
            for(int i=0;i<count;i++) {
                double sample=pcm[i]/32768.0*(0.5-0.5*Math.cos(2*Math.PI*i/(count-1.0)));
                double q=sample+coefficient*q1-q2; q2=q1; q1=q;
            }
            double power=Math.max(0,q1*q1+q2*q2-coefficient*q1*q2);
            double amplitude=4*Math.sqrt(power)/count;
            double db=20*Math.log10(Math.max(1e-6,amplitude));
            out[b]=(float)Math.max(0,Math.min(1,(db+65)/65));
        }
        return out;
    }
    private Spectrum() {}
}
