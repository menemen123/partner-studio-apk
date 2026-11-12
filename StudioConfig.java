package com.partnerstudio.runtime;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;
final class StudioConfig {
    private static JSONObject cached;
    static synchronized JSONObject get(Context context) {
        if (cached == null) {
            try (InputStream input=context.getAssets().open("studio.json")) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                while((n=input.read(b))!=-1)bytes.write(b,0,n);
                cached=new JSONObject(bytes.toString("UTF-8"));
            } catch(Exception e) {cached=new JSONObject();}
        }
        return cached;
    }
}
