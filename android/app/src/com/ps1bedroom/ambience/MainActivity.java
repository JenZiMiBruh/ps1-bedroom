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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.database.Cursor;
import android.provider.DocumentsContract;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

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
    private String pendingJs = null;
    private boolean audioPlaying = false;
    private boolean askedNotifications = false;
    private static final int PICK_AUDIO = 42;
    private static final int PICK_FOLDER = 43;
    private android.webkit.ValueCallback<Uri[]> fileCallback;

    /**
     * The page is served from this https address (out of the APK's assets) rather than file://, because
     * YouTube's player refuses to play inside pages that have no web address.
     */
    static final String HOST = "appassets.androidplatform.net";
    static final String START_URL = "https://" + HOST + "/assets/index.html";
    /** Files from a music folder the user picked, served to the page at /music/<index> while the app runs. */
    private final ArrayList<Uri> musicDocs = new ArrayList<Uri>();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) readMusicFolder(data.getData());
            return;
        }
        if (requestCode != PICK_AUDIO || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveLocal(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The page itself stays in the app; any other link (YouTube search, etc.) opens outside it
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                if (!request.isForMainFrame()) return false;
                openExternal(u.toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingAction != null) {
                    runLaunchAction(pendingAction);
                    pendingAction = null;
                }
                if (pendingJs != null) {
                    final String js = pendingJs;
                    pendingJs = null;
                    webView.postDelayed(new Runnable() { @Override public void run() { callJs(js); } }, 900);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                // "Add songs" in My Music: let the user pick audio files from their phone
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(pick, "Add songs"), PICK_AUDIO);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(webView);
        hideSystemBars();
        webView.loadUrl(START_URL);
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
        // A YouTube link shared to the app (Share -> PS1 Bedroom) plays on the bedroom TV
        if (Intent.ACTION_SEND.equals(a) && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            final String js = "window.ps1App && ps1App.youtube(" + JSONObject.quote(intent.getStringExtra(Intent.EXTRA_TEXT)) + ")";
            if (pageReady) webView.postDelayed(new Runnable() { @Override public void run() { callJs(js); } }, 300);
            else pendingJs = js;
            return;
        }
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

        /** "+ Album folder": the system folder picker, then every song and picture inside is handed to the page. */
        @JavascriptInterface
        public void pickMusicFolder() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
                } catch (Exception e) {
                    callJs("window.showToast && showToast('This phone has no folder picker. Use + Songs instead.')");
                }
            } });
        }

        /** Links that should leave the app, like a YouTube search (opens in the YouTube app or the browser). */
        @JavascriptInterface
        public void openExternal(String url) {
            MainActivity.this.openExternal(url);
        }

        /**
         * What the boombox is playing, for the notification and lock screen. art: base64 JPEG of the cover,
         * "" to keep the previous cover, "-" for none.
         */
        @JavascriptInterface
        public void nowPlaying(String json, String art) {
            AmbienceService.setNowPlaying(json, art);
            AmbienceService.sync(MainActivity.this, AppState.get(MainActivity.this).toString(), audioPlaying);
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } });
        }
    }

    void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // No app can open it
        }
    }

    private static final Map<String, String> MIME = new HashMap<String, String>();
    static {
        MIME.put("html", "text/html"); MIME.put("js", "application/javascript"); MIME.put("css", "text/css");
        MIME.put("png", "image/png"); MIME.put("jpg", "image/jpeg"); MIME.put("json", "application/json");
    }

    /** Serves https://appassets.androidplatform.net/assets/... from the APK and /music/<n> from a picked folder. */
    private WebResourceResponse serveLocal(Uri url) {
        if (url == null || !HOST.equals(url.getHost()) || url.getPath() == null) return null;
        String path = url.getPath();
        try {
            if (path.startsWith("/assets/")) {
                String name = path.substring("/assets/".length());
                String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
                InputStream in = getAssets().open(name);
                return withHeaders(new WebResourceResponse(mime, "UTF-8", in));
            }
            if (path.startsWith("/music/")) {
                int i = Integer.parseInt(path.substring("/music/".length()));
                Uri doc;
                synchronized (musicDocs) { doc = i >= 0 && i < musicDocs.size() ? musicDocs.get(i) : null; }
                if (doc == null) return notFound();
                String mime = getContentResolver().getType(doc);
                InputStream in = getContentResolver().openInputStream(doc);
                return withHeaders(new WebResourceResponse(mime != null ? mime : "application/octet-stream", null, in));
            }
        } catch (Exception e) {
            return notFound();
        }
        return notFound();
    }

    private WebResourceResponse withHeaders(WebResourceResponse r) {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-cache");
        r.setResponseHeaders(h);
        return r;
    }

    private WebResourceResponse notFound() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
    }

    /** Lists the songs and pictures in a picked folder (and its sub-folders) and hands the list to the page. */
    private void readMusicFolder(final Uri tree) {
        new Thread(new Runnable() { @Override public void run() {
            JSONArray list = new JSONArray();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                String rootName = queryName(tree, rootId);
                walk(tree, rootId, rootName, 0, list);
            } catch (Exception ignored) {
                // Unreadable folder: the page gets whatever was listed
            }
            callJs("window.ps1App && ps1App.folderPicked(" + list.toString() + ")");
        } }).start();
    }

    private String queryName(Uri tree, String docId) {
        Cursor c = null;
        try {
            c = getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return "Music";
    }

    private void walk(Uri tree, String parentId, String path, int depth, JSONArray out) throws Exception {
        if (depth > 3 || out.length() > 2000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(tree, id, path + "/" + name, depth + 1, out);
                } else if (mime != null && (mime.startsWith("audio/") || mime.startsWith("image/") || name.matches("(?i).*\\.(mp3|m4a|flac|ogg|opus|wav|aac|jpg|jpeg|png)$"))) {
                    int index;
                    synchronized (musicDocs) {
                        musicDocs.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
                        index = musicDocs.size() - 1;
                    }
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("mime", mime);
                    o.put("path", path + "/" + name);
                    o.put("url", "https://" + HOST + "/music/" + index);
                    out.put(o);
                }
            }
        } finally {
            c.close();
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
        } else {
            // Sound only: stop drawing the room while nobody can see it
            webView.evaluateJavascript("window.ps1App&&ps1App.background(true)", null);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
        webView.evaluateJavascript("try{window.Tone&&Tone.getContext().rawContext.resume();}catch(e){}window.ps1App&&ps1App.background(false)", null);
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
