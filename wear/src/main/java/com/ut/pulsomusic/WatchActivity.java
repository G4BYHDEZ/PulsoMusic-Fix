package com.ut.pulsomusic;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.session.PlaybackState;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import com.ut.pulsomusic.shared.Link;
import org.json.*;
import java.util.ArrayList;

public class WatchActivity extends Activity implements MessageClient.OnMessageReceivedListener {
    private final Handler main=new Handler(Looper.getMainLooper());
    private String phone;
    private long lastState;
    private JSONObject state=new JSONObject();
    private BarsView bars;
    private TextView title,artist,status,times,volume,audioStatus;
    private Button previous,toggle,next,back,forward,stop,more,players,customs,queue,minus,plus,mute,shuffle,repeat;
    private SeekBar seek;
    private Switch showBars;
    private LinearLayout extras;
    private boolean dragging;
    private String lastNotice="";
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            send("sync",null);
            if(SystemClock.elapsedRealtime()-lastState>12000) {
                status.setText("Sin respuesta · abre Pulso en el teléfono");
                bars.playing=false; bars.invalidate(); enable(false);
            }
            main.postDelayed(this,4000);
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root=new FrameLayout(this); bars=new BarsView(this); root.addView(bars,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER_HORIZONTAL); body.setPadding(dp(26),dp(24),dp(26),dp(38)); scroll.addView(body);
        TextView brand=text(body,"P U L S O",11,0xff65e6c4);
        title=text(body,"Tu música, aquí",18,Color.WHITE); title.setMaxLines(2);
        artist=text(body,"Inicia una canción en el teléfono",12,0xffbecbde); artist.setMaxLines(2);
        status=text(body,"Conectando…",10,0xff65e6c4);
        LinearLayout transport=row(body);
        previous=button(transport,"|◀","Canción anterior",()->send("previous",null));
        toggle=button(transport,"▶","Reproducir o pausar",()->send("toggle",null));
        next=button(transport,"▶|","Canción siguiente",()->send("next",null));
        seek=new SeekBar(this); seek.setMax(1000); seek.setProgressTintList(ColorStateList.valueOf(0xff65e6c4)); body.addView(seek,new LinearLayout.LayoutParams(-1,dp(32))); seek.setContentDescription("Posición de la canción");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean user) { if(user) times.setText(time(state.optLong("duration")*p/1000)+" / "+time(state.optLong("duration"))); }
            public void onStartTrackingTouch(SeekBar s) { dragging=true; }
            public void onStopTrackingTouch(SeekBar s) { dragging=false; send("seek",Link.object("position",state.optLong("duration")*s.getProgress()/1000)); }
        });
        times=text(body,"0:00 / 0:00",10,0xffbecbde);
        LinearLayout volumes=row(body);
        minus=button(volumes,"−","Bajar volumen",()->send("volumeDown",null));
        volume=text(volumes,"Volumen",11,0xffbecbde); volume.setLayoutParams(new LinearLayout.LayoutParams(0,dp(42),1)); volume.setGravity(Gravity.CENTER);
        plus=button(volumes,"+","Subir volumen",()->send("volumeUp",null));
        showBars=new Switch(this); showBars.setText("Barras de audio"); showBars.setTextSize(12); showBars.setTextColor(Color.WHITE); showBars.setMinHeight(dp(48));
        showBars.setChecked(getPreferences(0).getBoolean("bars",true)); bars.enabled=showBars.isChecked(); body.addView(showBars,new LinearLayout.LayoutParams(-1,dp(48)));
        showBars.setOnCheckedChangeListener((v,checked)->{
            boolean spotify = state.optBoolean("spotify", false);
            bars.enabled=spotify || checked; bars.invalidate(); getPreferences(0).edit().putBoolean("bars",checked).apply(); send("sync",null); updateAudio();
        });
        audioStatus=text(body,"Activa el visualizador en el teléfono",10,0xffbecbde);
        more=wide(body,"Más controles",()->{ extras.setVisibility(extras.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE); });
        extras=new LinearLayout(this); extras.setOrientation(LinearLayout.VERTICAL); extras.setVisibility(View.GONE); body.addView(extras,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout skips=row(extras);
        back=button(skips,"−10 s","Retroceder diez segundos",()->send("back",null));
        forward=button(skips,"+10 s","Avanzar diez segundos",()->send("forward",null));
        mute=wide(extras,"Silenciar / restaurar",()->send("mute",null));
        stop=wide(extras,"Detener",()->send("stop",null));
        shuffle=wide(extras,"Aleatorio",()->send("shuffle",null));
        repeat=wide(extras,"Repetición",()->send("repeat",null));
        players=wide(extras,"Elegir reproductor",this::choosePlayer);
        queue=wide(extras,"Cola de reproducción",this::chooseQueue);
        customs=wide(extras,"Opciones del reproductor",this::chooseCustom);
        text(extras,"Aleatorio, repetición y otras opciones aparecen si el reproductor las ofrece. Gira la corona o desliza para ver más.",10,0xffbecbde);
        scroll.setOnGenericMotionListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_SCROLL) { scroll.smoothScrollBy(0,(int)(-event.getAxisValue(MotionEvent.AXIS_SCROLL)*dp(35))); return true; } return false;
        });
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1)); setContentView(root); enable(false);
    }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(LinearLayout parent,String label,int size,int color) {
        TextView v=new TextView(this); v.setText(label); v.setTextColor(color); v.setTextSize(size); v.setGravity(Gravity.CENTER); v.setPadding(0,dp(3),0,dp(3)); parent.addView(v,new LinearLayout.LayoutParams(-1,-2)); return v;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER); parent.addView(row,new LinearLayout.LayoutParams(-1,-2)); return row;
    }
    private Button button(LinearLayout row,String label,String description,Runnable action) {
        Button v=new Button(this); v.setText(label); v.setAllCaps(false); v.setTextSize(15); v.setPadding(0,0,0,0); v.setMinWidth(0); v.setMinimumWidth(0);
        v.setTextColor(Color.WHITE); v.setBackgroundTintList(ColorStateList.valueOf(0xff23384a)); v.setContentDescription(description);
        v.setOnClickListener(w->action.run()); row.addView(v,new LinearLayout.LayoutParams(0,dp(48),1)); return v;
    }
    private Button wide(LinearLayout parent,String label,Runnable action) {
        Button v=new Button(this); v.setText(label); v.setAllCaps(false); v.setTextSize(12); v.setTextColor(Color.WHITE); v.setBackgroundTintList(ColorStateList.valueOf(0xff23384a));
        v.setOnClickListener(w->action.run()); parent.addView(v,new LinearLayout.LayoutParams(-1,dp(48))); return v;
    }
    private void enable(boolean connected) {
        long a=state.optLong("actions"); boolean ready=connected && state.optBoolean("ready");
        previous.setEnabled(ready && (a&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);
        next.setEnabled(ready && (a&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);
        toggle.setEnabled(ready && (a&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0);
        boolean seeking=ready && (a&PlaybackState.ACTION_SEEK_TO)!=0;
        seek.setEnabled(seeking && state.optLong("duration")>0); back.setEnabled(seeking); forward.setEnabled(seeking);
        stop.setEnabled(ready && (a&PlaybackState.ACTION_STOP)!=0);
        minus.setEnabled(ready); plus.setEnabled(ready); mute.setEnabled(ready && !state.optBoolean("remote"));
        players.setEnabled(ready && array("players").length()>0);
        queue.setEnabled(ready && array("queue").length()>0);
        customs.setEnabled(ready && array("customs").length()>0);
        shuffle.setEnabled(ready && state.optBoolean("canShuffle")); repeat.setEnabled(ready && state.optBoolean("canRepeat"));
    }
    private JSONArray array(String key) { JSONArray a=state.optJSONArray(key); return a==null?new JSONArray():a; }
    private void send(String action,JSONObject args) {
        JSONObject command=args==null?new JSONObject():args;
        try { command.put("action",action); command.put("bars",showBars.isChecked()); } catch(Exception ignored) {}
        if(phone==null) {
            Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{
                if(nodes.isEmpty()) { status.setText("Sin teléfono conectado"); enable(false); }
                else { phone=nodes.get(0).getId(); transmit(command); }
            }).addOnFailureListener(e->{ status.setText("Revisa Google Play Services"); enable(false); });
        } else transmit(command);
    }
    private void transmit(JSONObject command) {
        Wearable.getMessageClient(this).sendMessage(phone,Link.COMMAND,Link.bytes(command)).addOnFailureListener(e->{ phone=null; status.setText("Conexión interrumpida"); enable(false); });
    }
    @Override public void onMessageReceived(MessageEvent event) {
        if(phone!=null && !phone.equals(event.getSourceNodeId())) return;
        JSONObject incoming=Link.json(event.getData());
        if(Link.STATE.equals(event.getPath())) main.post(()->{
            state=incoming; lastState=SystemClock.elapsedRealtime();
            title.setText(state.optString("title","Sin canción")); artist.setText(state.optString("artist"));
            status.setText(state.optBoolean("ready")?"Teléfono conectado":"Falta permiso en el teléfono");
            boolean playing=state.optBoolean("playing"); toggle.setText(playing?"Ⅱ":"▶"); bars.playing=playing; bars.invalidate();
            long duration=state.optLong("duration"),position=state.optLong("position");
            if(!dragging) { seek.setProgress(duration>0?(int)(position*1000/duration):0); times.setText(time(position)+" / "+time(duration)); }
            volume.setText(state.optBoolean("muted")?"Silencio":state.optInt("volume")+" / "+state.optInt("maxVolume"));
            shuffle.setText(state.optInt("shuffle")==1?"Aleatorio: activado":"Aleatorio: desactivado");
            int mode=state.optInt("repeat"); repeat.setText(mode==1?"Repetir: una":mode==2?"Repetir: todas":"Repetir: desactivado");
            String notice=state.optString("notice"); if(!notice.isEmpty() && !notice.equals(lastNotice)) Toast.makeText(this,notice,Toast.LENGTH_LONG).show(); lastNotice=notice;
            bars.enabled = showBars.isChecked();
            updateAudio(); enable(true);
        });
        else if(Link.AUDIO.equals(event.getPath())) main.post(()->{
            bars.levels(incoming.optJSONArray("bands"));
            if(showBars.isChecked()) audioStatus.setText(incoming.optString("status","Audio real"));
        });
    }
    private void updateAudio() {
        audioStatus.setText(!showBars.isChecked()?"Barras desactivadas":state.optBoolean("capture")?state.optString("captureStatus","Esperando audio"):"Activa el visualizador en el teléfono");
    }
    private String time(long ms) { long seconds=Math.max(0,ms/1000); return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60); }
    private void choosePlayer() { choose("Reproductor",array("players"),"name",item->send("player",Link.object("package",item.optString("package")))); }
    private void chooseCustom() { choose("Opciones disponibles",array("customs"),"name",item->send("custom",Link.object("id",item.optString("id")))); }
    private void chooseQueue() { choose("Cola",array("queue"),"title",item->send("queue",Link.object("id",item.optLong("id")))); }
    private interface Choice { void picked(JSONObject item); }
    private void choose(String title,JSONArray items,String field,Choice choice) {
        String[] labels=new String[items.length()]; for(int i=0;i<labels.length;i++) labels[i]=items.optJSONObject(i).optString(field);
        new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,i)->choice.picked(items.optJSONObject(i))).setNegativeButton("Cerrar",null).show();
    }
    @Override protected void onResume() {
        super.onResume(); bars.foreground=true; bars.invalidate();
        Wearable.getMessageClient(this).addListener(this); main.post(poll);
    }
    @Override protected void onPause() {
        main.removeCallbacks(poll);
        if(phone!=null) Link.send(this,phone,Link.COMMAND,Link.object("action","bye"));
        Wearable.getMessageClient(this).removeListener(this); bars.foreground=false; bars.invalidate(); super.onPause();
    }
}
