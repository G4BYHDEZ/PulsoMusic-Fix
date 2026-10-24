package com.ut.pulsomusic;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.view.*;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONObject;

public class PhoneActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView permission,nowPlaying,capture,connection;
    private final Runnable refresh=new Runnable() {
        @Override public void run() { update(); main.postDelayed(this,1500); }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); getWindow().setStatusBarColor(Color.rgb(11,17,31)); getWindow().setNavigationBarColor(Color.rgb(11,17,31));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(11,17,31));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom); return insets;
        });
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(24),dp(24),dp(28)); scroll.addView(body);
        text(body,"P U L S O",15,0xff65e6c4); text(body,"Tu música.\nEn tu muñeca.",34,Color.WHITE);
        text(body,"Conecta tu teléfono Android con Wear OS para controlar el reproductor que ya utilizas.",16,0xffb7c4da);
        section(body,"01  ·  ACCESO AL REPRODUCTOR");
        permission=text(body,"",16,Color.WHITE);
        button(body,"Permitir acceso a notificaciones",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        text(body,"Este permiso permite leer la canción y enviar controles. Pulso no guarda ni envía tus notificaciones a servidores.",14,0xffb7c4da);
        section(body,"02  ·  TELÉFONO Y RELOJ");
        connection=text(body,"Buscando reloj…",16,Color.WHITE);
        text(body,"Instala Pulso en ambos dispositivos y empareja Wear OS con este teléfono. Abre un reproductor e inicia una canción; después abre Pulso en el reloj.",15,0xffb7c4da);
        nowPlaying=text(body,"",19,0xff65e6c4);
        section(body,"03  ·  BARRAS DE AUDIO REAL");
        capture=text(body,"",16,Color.WHITE);
        button(body,"Activar visualizador real",this::startCapture);
        button(body,"Detener visualizador",()->stopService(new Intent(this,AudioCaptureService.class)));
        text(body,"Android solicitará permiso para capturar la reproducción. No usamos el micrófono. Algunas apps bloquean el audio: en ese caso las barras quedan en silencio. Puedes ocultarlas desde el reloj sin detener la captura; para ahorrar batería, detén la captura aquí.",14,0xffb7c4da);
        section(body,"CONTROLES DISPONIBLES");
        text(body,"Reproducir/pausar · anterior/siguiente · volumen · silencio · avanzar/retroceder 10 s · posición · detener · reproductor · cola y acciones adicionales cuando la app de música las ofrezca.\n\nAleatorio, repetición y otras acciones dependen de cada reproductor. Las opciones no compatibles aparecen desactivadas o no se muestran.",15,0xffb7c4da);
        setContentView(scroll);
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void section(LinearLayout body,String s) { TextView v=text(body,s,13,0xff65e6c4); v.setPadding(0,dp(22),0,dp(6)); }
    private TextView text(LinearLayout body,String s,int size,int color) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setPadding(0,dp(5),0,dp(7)); body.addView(v); return v;
    }
    private void button(LinearLayout body,String label,Runnable action) {
        Button v=new Button(this); v.setAllCaps(false); v.setText(label); v.setTextColor(0xff07131b); v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff65e6c4));
        v.setOnClickListener(w->action.run()); body.addView(v,new LinearLayout.LayoutParams(-1,56*getResources().getDisplayMetrics().densityDpi/160));
    }
    private boolean access() { return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(this,MediaAccessService.class)); }
    private void update() {
        permission.setText(access()?"✓ Permiso concedido":"Falta conceder el permiso");
        capture.setText(AudioCaptureService.status);
        if(MediaAccessService.instance!=null) {
            JSONObject s=MediaAccessService.instance.state(); nowPlaying.setText(s.optString("title")+"\n"+s.optString("artist"));
        } else nowPlaying.setText("Esperando acceso al reproductor");
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{
            StringBuilder names=new StringBuilder(); for(var node:nodes) { if(names.length()>0) names.append(", "); names.append(node.getDisplayName()); }
            connection.setText(nodes.isEmpty()?"Sin reloj conectado": "Conectado: "+names);
        }).addOnFailureListener(e->connection.setText("Configura la app de vinculación de tu reloj y actualiza Google Play Services"));
    }
    private void startCapture() {
        if(AudioCaptureService.active) { Toast.makeText(this,"El visualizador ya está activo",Toast.LENGTH_SHORT).show(); return; }
        if(!access()) { Toast.makeText(this,"Primero permite acceso al reproductor",Toast.LENGTH_LONG).show(); return; }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.RECORD_AUDIO},10); return;
        }
        startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),20);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==10 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),20);
        } else Toast.makeText(this,"Permiso de audio necesario para analizar la reproducción",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==20 && result==RESULT_OK && data!=null) startForegroundService(new Intent(this,AudioCaptureService.class).putExtra("token",data));
    }
    @Override protected void onResume() { super.onResume(); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
}
