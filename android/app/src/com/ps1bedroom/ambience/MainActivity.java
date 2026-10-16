package com.ps1bedroom.ambience;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Full-screen WebView that runs the bundled PS1 bedroom page (assets/index.html) offline.
 * The page talks to Android through the "PS1Android" bridge: playback state for the background
 * service, notification and widgets, and saving photos to the gallery.
 */
public class MainActivity extends Activity {
    public static final String ACTION_PLAY = "com.ps1bedroom.ambience.PLAY";
    public static final String ACTION_SLEEP30 = "com.ps1bedroom.ambience.SLEEP30";

    static MainActivity current;
    private WebView webView;
    private boolean pageReady = false;
    private String pendingAction = null;
    private boolean audioPlaying = false;
    private boolean askedNotifications = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);

        webView.addJavascriptInterface(new Bridge(), "PS1Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingAction != null) {
                    runLaunchAction(pendingAction);
                    pendingAction = null;
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        hideSystemBars();
        webView.loadUrl("file:///android_asset/index.html");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        String action = ACTION_PLAY.equals(a) ? "play" : (ACTION_SLEEP30.equals(a) ? "sleep30" : null);
        if (action == null) return;
        if (pageReady) runLaunchAction(action);
        else pendingAction = action;
    }

    private void runLaunchAction(final String action) {
        // Give the page a moment to finish its own start-up before acting on the widget tap
        webView.postDelayed(new Runnable() {
            @Override public void run() { callJs("window.ps1App && ps1App.launchAction('" + action + "')"); }
        }, 600);
    }

    void callJs(final String js) {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (webView != null) webView.evaluateJavascript(js, null); }
        });
    }

    /** Methods the page can call. They run on a background thread. */
    private class Bridge {
        @JavascriptInterface
        public void updateState(String json) {
            AppState.save(MainActivity.this, json);
            final boolean playing = AppState.isPlaying(json);
            final boolean changed = playing != audioPlaying;
            audioPlaying = playing;
            runOnUiThread(new Runnable() { @Override public void run() {
                if (playing || changed) AmbienceService.sync(MainActivity.this, json, playing);
                if (playing && !askedNotifications) {
                    askedNotifications = true;
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                    }
                }
                WindowWidget.updateAll(MainActivity.this);
                ControlWidget.updateAll(MainActivity.this);
            } });
        }

        @JavascriptInterface
        public boolean saveImage(String base64Png, String fileName) {
            try {
                byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PS1 Bedroom");
                    v.put(MediaStore.Images.Media.IS_PENDING, 1);
                    ContentResolver cr = getContentResolver();
                    Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return false;
                    try (OutputStream out = cr.openOutputStream(uri)) {
                        if (out == null) return false;
                        out.write(bytes);
                    }
                    v.clear();
                    v.put(MediaStore.Images.Media.IS_PENDING, 0);
                    cr.update(uri, v, null, null);
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PS1 Bedroom");
                    if (!dir.exists() && !dir.mkdirs()) return false;
                    File f = new File(dir, fileName);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(bytes);
                    }
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } });
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Let the page close menus / leave photo mode first; otherwise go to the background
        // (keeping the sound playing) instead of quitting
        webView.evaluateJavascript("window.ps1App ? ps1App.back() : false", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                if (!"true".equals(value)) {
                    if (audioPlaying) moveTaskToBack(true);
                    else MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        // With the ambience playing, keep the page (and its sound) running in the background.
        // Otherwise pause everything to save battery.
        if (!audioPlaying) {
            webView.evaluateJavascript("try{window.Tone&&Tone.getContext().rawContext.suspend();}catch(e){}", null);
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
        webView.evaluateJavascript("try{window.Tone&&Tone.getContext().rawContext.resume();}catch(e){}", null);
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        AmbienceService.stop(this);
        AppState.setPlaying(this, false);
        WindowWidget.updateAll(this);
        ControlWidget.updateAll(this);
        if (current == this) current = null;
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
