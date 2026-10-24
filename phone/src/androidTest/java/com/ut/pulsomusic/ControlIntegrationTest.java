package com.ut.pulsomusic;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.ComponentName;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.*;
import com.ut.pulsomusic.shared.Link;

/** Run only on a test phone/emulator with Pulso's notification access enabled. */
public class ControlIntegrationTest extends Instrumentation {
    private MediaSessionCompat session;
    private volatile String called="";
    private volatile long position;
    private final long actions=PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PAUSE|
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS|
            PlaybackStateCompat.ACTION_SEEK_TO|PlaybackStateCompat.ACTION_STOP|
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE|PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            runOnMainSync(()->{
                session=new MediaSessionCompat(getTargetContext(),"Pulso integration test");
                session.setMetadata(new MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE,"Prueba Pulso")
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"Prueba automática").putLong(MediaMetadataCompat.METADATA_KEY_DURATION,60000).build());
                session.setCallback(new MediaSessionCompat.Callback() {
                    @Override public void onPlay() { called="play"; playback(PlaybackStateCompat.STATE_PLAYING); }
                    @Override public void onPause() { called="pause"; playback(PlaybackStateCompat.STATE_PAUSED); }
                    @Override public void onSkipToNext() { called="next"; }
                    @Override public void onSkipToPrevious() { called="previous"; }
                    @Override public void onSeekTo(long p) { position=p; called="seek"; playback(PlaybackStateCompat.STATE_PAUSED); }
                    @Override public void onStop() { called="stop"; playback(PlaybackStateCompat.STATE_STOPPED); }
                    @Override public void onSetShuffleMode(int mode) { called="shuffle"; session.setShuffleMode(mode); }
                    @Override public void onSetRepeatMode(int mode) { called="repeat"; session.setRepeatMode(mode); }
                },new Handler(Looper.getMainLooper()));
                session.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE); session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
                playback(PlaybackStateCompat.STATE_PAUSED); session.setActive(true);
                NotificationListenerService.requestRebind(new ComponentName(getTargetContext(),MediaAccessService.class));
            });
            for(int i=0;i<150 && MediaAccessService.instance==null;i++) SystemClock.sleep(200);
            check(MediaAccessService.instance!=null,"Permite acceso a notificaciones antes de ejecutar la prueba");
            command("player",Link.object("package",getTargetContext().getPackageName()));
            check("Prueba Pulso".equals(MediaAccessService.instance.state().optString("title")),"Metadata");
            command("toggle",null); check(called.equals("play"),"Play");
            command("toggle",null); check(called.equals("pause"),"Pause");
            command("next",null); check(called.equals("next"),"Next");
            command("previous",null); check(called.equals("previous"),"Previous");
            command("seek",Link.object("position",999999)); check(position==60000,"Seek upper clamp");
            command("seek",Link.object("position",-200)); check(position==0,"Seek lower clamp");
            command("forward",null); check(position==10000,"Forward 10s");
            command("back",null); check(position==0,"Back 10s");
            command("shuffle",null); check(called.equals("shuffle"),"Shuffle");
            check(MediaAccessService.instance.state().optInt("shuffle")==PlaybackStateCompat.SHUFFLE_MODE_ALL,"Shuffle state");
            command("repeat",null); check(called.equals("repeat"),"Repeat");
            command("stop",null); check(called.equals("stop"),"Stop");
            result.putString("stream","PASS: metadata, play, pause, next, previous, seek limits, +10s, -10s, shuffle, repeat, stop.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) { result.putString("stream","FAIL: "+e+"\n"); finish(Activity.RESULT_CANCELED,result); }
        finally { if(session!=null) runOnMainSync(()->{session.setActive(false);session.release();}); }
    }
    private void playback(int state) { session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state,position,state==PlaybackStateCompat.STATE_PLAYING?1:0).build()); }
    private void command(String action,org.json.JSONObject data) {
        org.json.JSONObject command=data==null?Link.object("action",action):data;
        try {command.put("action",action);} catch(Exception ignored) {}
        MediaAccessService.instance.receive("integration-test",command); SystemClock.sleep(600);
    }
    private void check(boolean success,String description) { if(!success) throw new AssertionError(description); }
}
