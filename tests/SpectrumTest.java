import com.ut.pulsomusic.Spectrum;

public class SpectrumTest {
    public static void main(String[] args) {
        short[] samples=new short[Spectrum.SIZE];
        for(float v:Spectrum.analyze(samples,samples.length)) check(v==0,"Silence must remain zero");
        for(int band:new int[]{0,5,10,15}) {
            double frequency=80*Math.pow(7000.0/80,band/(double)(Spectrum.BANDS-1));
            for(int i=0;i<samples.length;i++) samples[i]=(short)(12000*Math.sin(2*Math.PI*frequency*i/Spectrum.RATE));
            float[] levels=Spectrum.analyze(samples,samples.length);
            int peak=0;
            for(int i=0;i<levels.length;i++) {
                check(Float.isFinite(levels[i]) && levels[i]>=0 && levels[i]<=1,"Levels in range");
                if(levels[i]>levels[peak]) peak=i;
            }
            check(peak==band,"Expected frequency band "+band+", got "+peak);
            check(levels[peak]>.5,"Tone must be visible");
        }
        check(Spectrum.analyze(samples,0)[0]==0,"Empty buffer");
        check(Spectrum.analyze(samples,1)[0]==0,"Single sample has no spectrum");
        System.out.println("PASS: silence, four reference frequencies, bounds and empty buffer");
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
