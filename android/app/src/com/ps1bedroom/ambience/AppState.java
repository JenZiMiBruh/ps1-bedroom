package com.ps1bedroom.ambience;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Calendar;

/** Last state the page reported (weather, time, playing...), shared with the service and widgets. */
final class AppState {
    private static final String PREFS = "ps1bedroom";

    private AppState() {}

    static void save(Context c, String json) {
        prefs(c).edit().putString("state", json).apply();
    }

    static void setPlaying(Context c, boolean playing) {
        JSONObject o = get(c);
        try { o.put("playing", playing); } catch (Exception ignored) { }
        save(c, o.toString());
    }

    static JSONObject get(Context c) {
        try {
            return new JSONObject(prefs(c).getString("state", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static boolean isPlaying(String json) {
        try { return new JSONObject(json).optBoolean("playing", false); } catch (Exception e) { return false; }
    }

    /** Hour of day right now (widgets follow the real clock even when the app is closed). */
    static double hourNow() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0;
    }

    static String statusLine(JSONObject s) {
        StringBuilder b = new StringBuilder();
        String w = s.optString("weather", "Clear");
        b.append(w);
        String tempText = s.optString("tempText", "");
        if (tempText.length() > 0) {
            b.append(" · ").append(tempText);
        } else if (!s.isNull("tempC") && s.has("tempC")) {
            double t = s.optDouble("tempC", Double.NaN);
            if (!Double.isNaN(t)) b.append(" · ").append(Math.round(t * 9 / 5 + 32)).append("°F");
        }
        String season = s.optString("season", "");
        if (season.length() > 0) b.append(" · ").append(Character.toUpperCase(season.charAt(0))).append(season.substring(1));
        return b.toString();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
