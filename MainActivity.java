package com.aitrading.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView w;

    class Bridge {
        @JavascriptInterface
        public String hmac(String secret, String msg) {
            try {
                Mac m = Mac.getInstance("HmacSHA256");
                m.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
                StringBuilder sb = new StringBuilder();
                for (byte b : m.doFinal(msg.getBytes("UTF-8"))) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public void http(final int id, final String method, final String url, final String hdrs, final String body) {
            new Thread(() -> {
                int st = 0; String out;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(15000); c.setReadTimeout(20000);
                    JSONObject h = new JSONObject(hdrs);
                    Iterator<String> it = h.keys();
                    while (it.hasNext()) { String k = it.next(); c.setRequestProperty(k, h.getString(k)); }
                    if (!method.equals("GET")) {
                        c.setDoOutput(true);
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8")); os.close();
                    }
                    st = c.getResponseCode();
                    InputStream in = st >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int n;
                    while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    out = bo.toString("UTF-8");
                } catch (Exception e) { out = String.valueOf(e); }
                final String js = "window.__cb(" + id + "," + st + "," + JSONObject.quote(out) + ")";
                w.post(() -> w.evaluateJavascript(js, null));
            }).start();
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#0b1020"));
        getWindow().setNavigationBarColor(Color.parseColor("#0b1020"));
        w = new WebView(this);
        w.setBackgroundColor(Color.parseColor("#0b1020"));
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.addJavascriptInterface(new Bridge(), "Android");
        setContentView(w);
        w.loadUrl("file:///android_asset/index.html");
    }
}
