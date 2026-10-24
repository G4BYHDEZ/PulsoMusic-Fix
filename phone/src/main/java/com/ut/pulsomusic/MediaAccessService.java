package com.ut.pulsomusic;

import android.content.ComponentName;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.ut.pulsomusic.shared.Link;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MediaAccessService extends NotificationListenerService {
    public static volatile MediaAccessService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Long> watchers = new HashMap<>();
    private final Map<String, Boolean> audioWanted = new HashMap<>();
    private MediaSessionManager manager;
    private MediaController selected;
    private MediaControllerCompat compat;
    private final MediaControllerCompat.Callback compatCallback=new MediaControllerCompat.Callback() {
        @Override public void onSessionReady() { publish(); }
        @Override public void onShuffleModeChanged(int mode) { publish(); }
        @Override public void onRepeatModeChanged(int mode) { publish(); }
    };
    private String preferredPackage;
    private String notice = "";
    private List<MediaController> sessions = java.util.Collections.emptyList();
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { publish(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onAudioInfoChanged(MediaController.PlaybackInfo i) { publish(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = list -> refresh();
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            watchers.entrySet().removeIf(e -> SystemClock.elapsedRealtime()-e.getValue()>15000);
            audioWanted.keySet().retainAll(watchers.keySet());
            if (!watchers.isEmpty()) refresh();
            main.postDelayed(this, 2000);
        }
    };
    @Override public void onListenerConnected() {
        instance = this;
        manager = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        manager.addOnActiveSessionsChangedListener(changed, new ComponentName(this, MediaAccessService.class), main);
        refresh(); main.removeCallbacks(pulse); main.post(pulse);
    }
    @Override public void onListenerDisconnected() { cleanup(); }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    private void cleanup() {
        if(instance==this) instance=null;
        main.removeCallbacksAndMessages(null);
        if(manager!=null) manager.removeOnActiveSessionsChangedListener(changed);
        if(selected!=null) selected.unregisterCallback(callback);
        if(compat!=null) compat.unregisterCallback(compatCallback);
        compat=null;
        selected=null;
    }
    private void refresh() {
        if(manager==null) return;
        try {
            sessions=manager.getActiveSessions(new ComponentName(this, MediaAccessService.class));
            MediaController best=null;
            for(MediaController c:sessions) {
                if(preferredPackage!=null && preferredPackage.equals(c.getPackageName())) { best=c; break; }
            }
            if(best==null) for(MediaController c:sessions) {
                if(best==null) best=c;
                PlaybackState s=c.getPlaybackState();
                if(s!=null && s.getState()==PlaybackState.STATE_PLAYING) { best=c; break; }
            }
            if(best==null || selected==null || !best.getSessionToken().equals(selected.getSessionToken())) {
                if(selected!=null) selected.unregisterCallback(callback);
                if(compat!=null) compat.unregisterCallback(compatCallback);
                compat=null;
                selected=best;
                if(selected!=null) {
                    selected.registerCallback(callback, main);
                    compat=new MediaControllerCompat(this,MediaSessionCompat.Token.fromToken(selected.getSessionToken()));
                    compat.registerCallback(compatCallback,main);
                }
            }
            publish();
        } catch(SecurityException e) { notice="Vuelve a permitir acceso a notificaciones"; publish(); }
    }
    public void receive(String node, JSONObject data) {
        main.post(() -> {
            watchers.put(node,SystemClock.elapsedRealtime());
            audioWanted.put(node,data.optBoolean("bars",true));
            String action=data.optString("action","sync");
            if(action.equals("bye")) { watchers.remove(node); audioWanted.remove(node); return; }
            notice="";
            refresh();
            try { execute(action, data); }
            catch(Exception e) { notice="El reproductor rechazó la orden"; }
            publish();
        });
    }
    private void execute(String action, JSONObject data) {
        if(action.equals("sync")) return;
        if(action.equals("player")) {
            preferredPackage=data.optString("package",null); refresh(); return;
        }
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(action.equals("volumeUp") || action.equals("volumeDown")) {
            int dir=action.equals("volumeUp") ? AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER;
            if(selected!=null) selected.adjustVolume(dir,0);
            else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,dir,0);
            return;
        }
        if(action.equals("mute")) {
            if(selected!=null && selected.getPlaybackInfo()!=null && selected.getPlaybackInfo().getPlaybackType()==MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE)
                notice="Silencio disponible para reproducción local en el teléfono";
            else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_TOGGLE_MUTE,0);
            return;
        }
        if(selected==null) { notice="Inicia música en el teléfono"; return; }
        MediaController.TransportControls t=selected.getTransportControls();
        PlaybackState s=selected.getPlaybackState();
        long supported=s==null?0:s.getActions();
        long required=0;
        switch(action) {
            case "toggle": required=(s!=null && s.getState()==PlaybackState.STATE_PLAYING)?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY; break;
            case "next": required=PlaybackState.ACTION_SKIP_TO_NEXT; break;
            case "previous": required=PlaybackState.ACTION_SKIP_TO_PREVIOUS; break;
            case "stop": required=PlaybackState.ACTION_STOP; break;
            case "seek": case "forward": case "back": required=PlaybackState.ACTION_SEEK_TO; break;
            case "shuffle": required=PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE; break;
            case "repeat": required=PlaybackStateCompat.ACTION_SET_REPEAT_MODE; break;
        }
        if(required!=0 && (supported & required)==0 && !(action.equals("toggle") && (supported & PlaybackState.ACTION_PLAY_PAUSE)!=0)) {
            notice="Función no disponible en este reproductor"; return;
        }
        switch(action) {
            case "toggle": if(s!=null && s.getState()==PlaybackState.STATE_PLAYING) t.pause(); else t.play(); break;
            case "next": t.skipToNext(); break;
            case "previous": t.skipToPrevious(); break;
            case "stop": t.stop(); break;
            case "seek": t.seekTo(clamp(data.optLong("position"))); break;
            case "forward": t.seekTo(clamp(position()+10000)); break;
            case "back": t.seekTo(clamp(position()-10000)); break;
            case "shuffle":
                if(compat!=null && compat.isSessionReady()) compat.getTransportControls().setShuffleMode(compat.getShuffleMode()==PlaybackStateCompat.SHUFFLE_MODE_NONE?PlaybackStateCompat.SHUFFLE_MODE_ALL:PlaybackStateCompat.SHUFFLE_MODE_NONE);
                else notice="Este reproductor no expone el modo aleatorio";
                break;
            case "repeat":
                if(compat!=null && compat.isSessionReady()) {
                    int mode=compat.getRepeatMode();
                    int next=mode==PlaybackStateCompat.REPEAT_MODE_NONE?PlaybackStateCompat.REPEAT_MODE_ALL:mode==PlaybackStateCompat.REPEAT_MODE_ALL?PlaybackStateCompat.REPEAT_MODE_ONE:PlaybackStateCompat.REPEAT_MODE_NONE;
                    compat.getTransportControls().setRepeatMode(next);
                } else notice="Este reproductor no expone la repetición";
                break;
            case "queue":
                long id=data.optLong("id",Long.MIN_VALUE);
                if(selected.getQueue()!=null && selected.getQueue().stream().anyMatch(q->q.getQueueId()==id)) {
                    t.skipToQueueItem(id);
                    if(s!=null && s.getState()!=PlaybackState.STATE_PLAYING) t.play();
                } else if((supported & PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)!=0) {
                    notice="La cola no permite seleccionar canciones";
                } else {
                    notice="La cola no permite seleccionar canciones";
                }
                break;
            case "custom":
                boolean found=false;
                if(s!=null) for(PlaybackState.CustomAction custom:s.getCustomActions()) {
                    if(custom.getAction().equals(data.optString("id"))) { t.sendCustomAction(custom.getAction(),custom.getExtras()); found=true; break; }
                }
                if(!found) notice="Opción no disponible";
                break;
        }
    }
    private long clamp(long p) {
        MediaMetadata m=selected==null?null:selected.getMetadata();
        long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return Math.max(0,duration>0?Math.min(p,duration):p);
    }
    private long position() {
        PlaybackState s=selected==null?null:selected.getPlaybackState();
        if(s==null) return 0;
        long p=s.getPosition();
        if(s.getState()==PlaybackState.STATE_PLAYING) p+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());
        return clamp(p);
    }
    public JSONObject state() {
        JSONObject data=Link.object("ready",true,"title","Sin reproducción","artist","Abre tu reproductor en el teléfono","playing",false,
                "notice",notice,"capture",AudioCaptureService.active,"captureStatus",AudioCaptureService.status);
        try {
            AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            data.put("volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            data.put("maxVolume",audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            data.put("muted",audio.isStreamMute(AudioManager.STREAM_MUSIC));
            if(selected!=null && selected.getPlaybackInfo()!=null && selected.getPlaybackInfo().getPlaybackType()==MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                data.put("volume",selected.getPlaybackInfo().getCurrentVolume());
                data.put("maxVolume",selected.getPlaybackInfo().getMaxVolume());
                data.put("remote",true);
            }
            JSONArray players=new JSONArray();
            for(MediaController c:sessions) {
                String name=c.getPackageName();
                try { name=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(name,0)).toString(); } catch(Exception ignored) {}
                players.put(Link.object("name",name,"package",c.getPackageName()));
            }
            data.put("players",players);
            if(selected!=null) {
                MediaMetadata m=selected.getMetadata(); PlaybackState s=selected.getPlaybackState();
                data.put("package",selected.getPackageName());
                if(m!=null) {
                    CharSequence title=m.getDescription().getTitle(); CharSequence artist=m.getDescription().getSubtitle();
                    data.put("title",title==null?"Sin título":title.toString()); data.put("artist",artist==null?"":artist.toString());
                    data.put("duration",m.getLong(MediaMetadata.METADATA_KEY_DURATION));
                }
                data.put("position",position());
                data.put("playing",s!=null && s.getState()==PlaybackState.STATE_PLAYING);
                data.put("actions",s==null?0:s.getActions());
                data.put("canShuffle",compat!=null && compat.isSessionReady() && s!=null && (s.getActions()&PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)!=0);
                data.put("canRepeat",compat!=null && compat.isSessionReady() && s!=null && (s.getActions()&PlaybackStateCompat.ACTION_SET_REPEAT_MODE)!=0);
                data.put("shuffle",compat==null?-1:compat.getShuffleMode());
                data.put("repeat",compat==null?-1:compat.getRepeatMode());
                JSONArray customs=new JSONArray();
                if(s!=null) for(PlaybackState.CustomAction a:s.getCustomActions()) customs.put(Link.object("id",a.getAction(),"name",a.getName()));
                data.put("customs",customs);
                JSONArray queue=new JSONArray();
                if(selected.getQueue()!=null) for(android.media.session.MediaSession.QueueItem q:selected.getQueue()) {
                    if(queue.length()>=30) break;
                    queue.put(Link.object("id",q.getQueueId(),"title",String.valueOf(q.getDescription().getTitle())));
                }
                data.put("queue",queue);
            }
        } catch(Exception ignored) {}
        return data;
    }
    public void publish() {
        JSONObject data=state();
        for(String node:watchers.keySet()) Link.send(this,node,Link.STATE,data);
    }
    public void sendAudio(JSONObject levels) {
        main.post(() -> {
            for(String node:watchers.keySet()) if(Boolean.TRUE.equals(audioWanted.get(node)) && SystemClock.elapsedRealtime()-watchers.get(node)<15000) Link.send(this,node,Link.AUDIO,levels);
        });
    }
}
