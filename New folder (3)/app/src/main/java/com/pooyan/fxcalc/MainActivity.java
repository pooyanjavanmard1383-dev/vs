package com.pooyan.fxcalc;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView web;

    public class Bridge {
        @JavascriptInterface
        public void fetchText(final String cb, final String url) {
            new Thread(new Runnable() {
                public void run() {
                    String out = null;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) FxDesk");
                        if (c.getResponseCode() == 200) {
                            InputStream in = c.getInputStream();
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                            out = bo.toString("UTF-8");
                        }
                        c.disconnect();
                    } catch (Exception e) { out = null; }
                    final String js = "window.__nf(" + JSONObject.quote(cb) + "," + (out != null) + "," + (out != null ? JSONObject.quote(out) : "null") + ")";
                    runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
                }
            }).start();
        }

        @JavascriptInterface
        public void schedule(double id, double millis, String title, String text) {
            Context c = MainActivity.this;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, AlarmReceiver.class);
            i.putExtra("id", (int) id).putExtra("t", title).putExtra("x", text);
            PendingIntent pi = PendingIntent.getBroadcast(c, (int) id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = (long) millis;
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }

        @JavascriptInterface
        public void cancel(double id) {
            Context c = MainActivity.this;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, (int) id, new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }

        @JavascriptInterface
        public void notifyNow(String title, String text) {
            AlarmReceiver.post(MainActivity.this, (int) (System.currentTimeMillis() % 100000), title, text);
        }

        @JavascriptInterface
        public void askNotify() {
            runOnUiThread(new Runnable() { public void run() { askPerm(); } });
        }

        @JavascriptInterface
        public void vibrate(double ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot((long) ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate((long) ms);
        }
    }

    private void askPerm() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#07080C"));
        getWindow().setNavigationBarColor(Color.parseColor("#07080C"));
        AlarmReceiver.ensureChannel(this);
        askPerm();
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#07080C"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
