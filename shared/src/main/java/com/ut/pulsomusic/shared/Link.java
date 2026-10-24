package com.ut.pulsomusic.shared;

import android.content.Context;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public final class Link {
    public static final String COMMAND = "/pulso/command", STATE = "/pulso/state", AUDIO = "/pulso/audio";
    public static byte[] bytes(JSONObject data) { return data.toString().getBytes(StandardCharsets.UTF_8); }
    public static JSONObject json(byte[] data) {
        try { return new JSONObject(new String(data, StandardCharsets.UTF_8)); }
        catch (Exception e) { return new JSONObject(); }
    }
    public static JSONObject object(Object... pairs) {
        JSONObject data = new JSONObject();
        try { for (int i=0; i<pairs.length; i+=2) data.put((String)pairs[i], pairs[i+1]); }
        catch (Exception ignored) { }
        return data;
    }
    public static void send(Context context, String node, String path, JSONObject data) {
        Wearable.getMessageClient(context).sendMessage(node, path, bytes(data));
    }
    private Link() {}
}
