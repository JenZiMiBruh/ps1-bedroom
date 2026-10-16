package com.ps1bedroom.ambience;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.json.JSONObject;

import java.util.Random;

/**
 * Draws the view out of the bedroom window as chunky pixel art for the widgets, the notification
 * and the lock-screen media card: sky for the time of day, sun or moon (with tonight's phase),
 * clouds, the skyline with its lit windows, rain or snow, and the window frame.
 */
final class WidgetArt {
    private WidgetArt() {}

    private static int mix(int a, int b, double k) {
        k = Math.max(0, Math.min(1, k));
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * k),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * k),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
    }

    private static double smooth(double a, double b, double v) {
        double k = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return k * k * (3 - 2 * k);
    }

    /** Renders at w x h logical pixels, then scales up by `scale` with hard pixel edges. */
    static Bitmap render(Context c, JSONObject s, double hour, int w, int h, int scale) {
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(small);
        Paint p = new Paint();
        p.setAntiAlias(false);

        double sunrise = s.optDouble("sunrise", 6.5);
        double sunset = s.optDouble("sunset", 18.5);
        if (Double.isNaN(sunrise)) sunrise = 6.5;
        if (Double.isNaN(sunset)) sunset = 18.5;
        // Daylight from the real clock: 0 at night, 1 in the middle of the day
        double dl = Math.min(smooth(sunrise - 0.6, sunrise + 1.0, hour), 1 - smooth(sunset - 1.0, sunset + 0.6, hour));
        double twilight = Math.max(1 - Math.abs(hour - sunrise) / 1.1, 1 - Math.abs(hour - sunset) / 1.1);
        twilight = Math.max(0, twilight);
        double cloud = s.optDouble("cloud", 0.05);
        double rain = s.optDouble("rain", 0);
        double snow = s.optDouble("snow", 0);
        String sky = s.optString("sky", "natural");
        double phase = s.optDouble("moonPhase", 0.5);

        int top = mix(Color.rgb(6, 9, 26), Color.rgb(40, 100, 220), dl);
        int bottom = mix(Color.rgb(24, 32, 70), Color.rgb(170, 214, 255), dl);
        bottom = mix(bottom, Color.rgb(255, 140, 80), twilight * 0.8);
        top = mix(top, Color.rgb(70, 50, 120), twilight * 0.4);
        if ("vapor".equals(sky)) { top = mix(Color.rgb(30, 6, 60), Color.rgb(120, 60, 190), dl); bottom = mix(Color.rgb(200, 60, 150), Color.rgb(255, 180, 220), dl); }
        if ("blood".equals(sky) && dl < 0.5) { top = mix(top, Color.rgb(60, 5, 10), 0.5); bottom = mix(bottom, Color.rgb(140, 30, 30), 0.5); }
        int grey = mix(Color.rgb(30, 30, 40), Color.rgb(150, 156, 168), dl);
        top = mix(top, grey, cloud * 0.8);
        bottom = mix(bottom, grey, cloud * 0.8);
        for (int y = 0; y < h; y++) {
            p.setColor(mix(top, bottom, (double) y / h));
            cv.drawRect(0, y, w, y + 1, p);
        }

        Random r = new Random(77);
        // Stars and aurora on clear nights
        if (dl < 0.3 && cloud < 0.6) {
            p.setColor(Color.argb((int) (200 * (1 - dl / 0.3) * (1 - cloud)), 230, 236, 255));
            for (int i = 0; i < w / 2; i++) cv.drawPoint(r.nextInt(w), r.nextInt((int) (h * 0.55)), p);
            if ("aurora".equals(sky)) {
                p.setColor(Color.argb(70, 80, 255, 170));
                for (int x = 0; x < w; x++) cv.drawRect(x, (float) (h * 0.18 + Math.sin(x * 0.2) * 3), x + 1, (float) (h * 0.32 + Math.sin(x * 0.15) * 3), p);
            }
        }
        // Sun or moon
        if (dl > 0.35) {
            float sx = (float) (w * (0.15 + 0.7 * smooth(sunrise, sunset, hour)));
            float sy = (float) (h * (0.5 - 0.3 * Math.sin(Math.PI * smooth(sunrise, sunset, hour))));
            p.setColor(Color.argb((int) (255 * (1 - cloud * 0.8)), 255, 236, 180));
            cv.drawCircle(sx, sy, Math.max(3, w / 14f), p);
        } else {
            float mx = w * 0.68f, my = h * 0.26f, mr = Math.max(3, w / 13f);
            p.setColor(Color.argb((int) (230 * (1 - cloud * 0.8)), "blood".equals(sky) ? 230 : 232, "blood".equals(sky) ? 80 : 238, "blood".equals(sky) ? 50 : 252));
            cv.drawCircle(mx, my, mr, p);
            // Shadow side of the moon for its phase
            if (!"blood".equals(sky)) {
                double k = Math.cos(phase * 2 * Math.PI); // 1 new, -1 full
                Paint shadow = new Paint();
                shadow.setColor(mix(top, bottom, my / h));
                float off = (float) ((phase < 0.5 ? -1 : 1) * mr * 2 * (1 - (1 - k) / 2));
                if (k > -0.95) cv.drawCircle(mx + off, my, mr, shadow);
            }
        }
        // Clouds
        if (cloud > 0.25) {
            p.setColor(mix(Color.rgb(70, 72, 90), Color.rgb(235, 238, 245), dl));
            int n = (int) (cloud * 6);
            for (int i = 0; i < n; i++) {
                float cx = r.nextInt(w), cy = (float) (h * (0.1 + r.nextDouble() * 0.3));
                cv.drawOval(new RectF(cx - w / 7f, cy - 2, cx + w / 7f, cy + 3), p);
                cv.drawOval(new RectF(cx - w / 12f, cy - 4, cx + w / 12f, cy + 2), p);
            }
        }
        // Skyline with lit windows at night
        int bld = mix(Color.rgb(8, 11, 24), Color.rgb(60, 72, 96), dl);
        int x = 0;
        Random br = new Random(12);
        while (x < w) {
            int bw = 4 + br.nextInt(6), bh = (int) (h * (0.22 + br.nextDouble() * 0.35));
            p.setColor(bld);
            cv.drawRect(x, h - bh, x + bw, h, p);
            if (dl < 0.5) {
                for (int wy = h - bh + 2; wy < h - 1; wy += 3) for (int wx = x + 1; wx < x + bw - 1; wx += 2) {
                    if (br.nextDouble() < 0.35) { p.setColor(br.nextDouble() < 0.2 ? Color.rgb(159, 216, 255) : Color.rgb(255, 214, 140)); cv.drawPoint(wx, wy, p); }
                }
            }
            x += bw + 1;
        }
        // Rain / snow
        if (rain > 0) {
            p.setColor(Color.argb(150, 190, 215, 250));
            for (int i = 0; i < (int) (w * rain); i++) { int rx = r.nextInt(w), ry = r.nextInt(h); cv.drawLine(rx, ry, rx - 1, ry + 3, p); }
        }
        if (snow > 0) {
            p.setColor(Color.argb(220, 250, 252, 255));
            for (int i = 0; i < (int) (w * snow * 0.8); i++) cv.drawPoint(r.nextInt(w), r.nextInt(h), p);
        }
        // Window frame: mullion and sill
        p.setColor(Color.rgb(40, 46, 66));
        cv.drawRect(w / 2f - 1, 0, w / 2f + 1, h, p);
        cv.drawRect(0, 0, w, 2, p);
        cv.drawRect(0, h - 2, w, h, p);
        cv.drawRect(0, 0, 2, h, p);
        cv.drawRect(w - 2, 0, w, h, p);

        if (scale <= 1) return small;
        return Bitmap.createScaledBitmap(small, w * scale, h * scale, false);
    }
}
