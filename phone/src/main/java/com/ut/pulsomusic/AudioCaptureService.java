package com.ut.pulsomusic;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import com.ut.pulsomusic.shared.Link;
import org.json.JSONArray;

public class AudioCaptureService extends Service {
    public static volatile boolean active=false;
    public static volatile String status="Visualizador desactivado";
    private volatile boolean running;
    private MediaProjection projection;
    private AudioRecord recorder;
    private Thread worker;
    private final MediaProjection.Callback revoked=new MediaProjection.Callback() {
        @Override public void onStop() { status="Captura finalizada: vuelve a autorizar en el teléfono"; stopSelf(); }
    };
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(active) return START_NOT_STICKY;
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("audio","Visualizador de audio",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,PhoneActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AudioCaptureService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"audio").setSmallIcon(com.ut.pulsomusic.R.drawable.ic_pulso)
                .setContentTitle("Pulso · Visualizador activo").setContentText("Analizando audio permitido. Puedes detenerlo aquí.")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Detener",stop).build()).build();
        try {
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Audio permission revoked");
            startForeground(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Intent token=intent.getParcelableExtra("token");
            if(token==null) throw new IllegalArgumentException("Missing consent");
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK,token);
            projection.registerCallback(revoked,new Handler(Looper.getMainLooper()));
            AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
            AudioFormat format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Spectrum.RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            int min=AudioRecord.getMinBufferSize(Spectrum.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            recorder=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(min,Spectrum.SIZE*8))
                    .setAudioPlaybackCaptureConfig(config).build();
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Audio initialization");
            recorder.startRecording(); running=true; active=true; status="Esperando audio permitido";
            worker=new Thread(this::read,"Pulso-spectrum"); worker.start();
        } catch(Exception e) {
            status="No se pudo iniciar la captura. Revisa permisos y vuelve a intentarlo."; stopSelf();
        }
        publish(); return START_NOT_STICKY;
    }
    private void read() {
        short[] samples=new short[Spectrum.SIZE]; long last=0;
        try {
            while(running) {
                int count=recorder.read(samples,0,samples.length);
                if(count<=0) { if(running) status="Error al leer el audio; reinicia el visualizador"; break; }
                if(SystemClock.elapsedRealtime()-last<100) continue;
                last=SystemClock.elapsedRealtime();
                float[] values=Spectrum.analyze(samples,count); JSONArray bands=new JSONArray(); float max=0;
                for(float value:values) { bands.put((double)value); max=Math.max(max,value); }
                status=max>0.05f?"Audio real activo":"Silencio o reproductor que bloquea la captura";
                MediaAccessService service=MediaAccessService.instance;
                if(service!=null) service.sendAudio(Link.object("bands",bands,"status",status));
            }
        } catch(Exception e) { if(running) status="Captura interrumpida. Vuelve a activarla."; }
        finally { if(running) new Handler(Looper.getMainLooper()).post(this::stopSelf); }
    }
    private void publish() { if(MediaAccessService.instance!=null) MediaAccessService.instance.publish(); }
    @Override public void onDestroy() {
        running=false; active=false;
        if(recorder!=null) {
            try { recorder.stop(); } catch(Exception ignored) {}
            if(worker!=null) try { worker.join(1500); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            recorder.release(); recorder=null;
        }
        if(projection!=null) { projection.unregisterCallback(revoked); projection.stop(); projection=null; }
        if(!status.startsWith("Captura") && !status.startsWith("No se") && !status.startsWith("Error")) status="Visualizador desactivado";
        stopForeground(STOP_FOREGROUND_REMOVE); publish(); super.onDestroy();
    }
}
