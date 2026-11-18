package com.mercuryong.gesturenavigation;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * System bars that follow the screen. Reports gesture vs button navigation and,
 * once auto style is on, samples the app's own pixels under the status bar and
 * just above the navigation bar to pick light or dark icons for each. The bars
 * are transparent (edge-to-edge); the web layer draws the button backing in the
 * reported bottom colour.
 */
@CapacitorPlugin(name = "GestureNavigation")
public class GestureNavigationPlugin extends Plugin {

    private static final String MODE_GESTURE = "gesture";
    private static final String MODE_BUTTONS = "buttons";
    private static final int SAMPLE_WIDTH = 48;
    private static final int SAMPLE_HEIGHT = 208;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable sampler = this::sample;
    private HandlerThread copyThread;
    private Handler copyHandler;

    private String lastMode;
    private boolean autoStyle = false;
    private boolean paused = false;
    private long intervalMs = 250;
    private Boolean topLight;
    private Boolean bottomLight;
    private int bottomColor = Color.WHITE;

    @Override
    public void load() {
        super.load();
        lastMode = readMode();
    }

    @PluginMethod
    public void getMode(PluginCall call) {
        getBridge().executeOnMainThread(() -> {
            lastMode = readMode();
            JSObject result = new JSObject();
            result.put("mode", lastMode);
            call.resolve(result);
        });
    }

    @PluginMethod
    public void setStyle(PluginCall call) {
        boolean darkButtons = !"light".equals(call.getString("buttons", "dark"));
        getBridge().executeOnMainThread(() -> {
            controller().setAppearanceLightNavigationBars(darkButtons);
            disableContrastScrim();
            call.resolve();
        });
    }

    /** Samples the screen edges and styles both bars until stopAutoStyle. */
    @PluginMethod
    public void startAutoStyle(PluginCall call) {
        intervalMs = Math.max(100, call.getInt("interval", 250));
        getBridge().executeOnMainThread(() -> {
            if (copyThread == null) {
                copyThread = new HandlerThread("SystemBarsSampler");
                copyThread.start();
                copyHandler = new Handler(copyThread.getLooper());
            }
            disableContrastScrim();
            autoStyle = true;
            schedule(0);
            call.resolve();
        });
    }

    @PluginMethod
    public void stopAutoStyle(PluginCall call) {
        autoStyle = false;
        main.removeCallbacks(sampler);
        call.resolve();
    }

    // The user can switch navigation mode in system settings while the app is away.
    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        paused = false;
        getBridge().executeOnMainThread(() -> {
            String mode = readMode();
            if (!mode.equals(lastMode)) {
                lastMode = mode;
                JSObject event = new JSObject();
                event.put("mode", mode);
                notifyListeners("modeChange", event);
            }
            schedule(0);
        });
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        paused = true;
        main.removeCallbacks(sampler);
    }

    @Override
    protected void handleOnDestroy() {
        main.removeCallbacks(sampler);
        if (copyThread != null) copyThread.quitSafely();
        super.handleOnDestroy();
    }

    private void schedule(long delayMs) {
        main.removeCallbacks(sampler);
        if (autoStyle && !paused) main.postDelayed(sampler, delayMs);
    }

    private void sample() {
        Activity activity = getActivity();
        if (activity == null || copyHandler == null) return;
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (insets == null || width == 0 || height == 0) {
            schedule(intervalMs);
            return;
        }
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        float scale = (float) SAMPLE_HEIGHT / height;
        // Rows under the status bar, and an 8dp strip just above the navigation bar.
        int topRows = Math.max(1, Math.round(bars.top * scale));
        int band = Math.max(1, Math.round(8 * decor.getResources().getDisplayMetrics().density * scale));
        int navTopRow = Math.min(SAMPLE_HEIGHT - 1, Math.round((height - bars.bottom) * scale));

        Bitmap bitmap = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(window, bitmap, result -> {
                if (result == PixelCopy.SUCCESS) {
                    int top = dominantColor(bitmap, 0, Math.min(topRows, SAMPLE_HEIGHT));
                    int bottom = dominantColor(bitmap, Math.max(0, navTopRow - band), navTopRow);
                    main.post(() -> apply(top, bottom));
                }
                bitmap.recycle();
                main.post(() -> schedule(intervalMs));
            }, copyHandler);
        } catch (IllegalArgumentException notReady) {
            // The window has no surface yet (starting up or in the background).
            bitmap.recycle();
            schedule(intervalMs);
        }
    }

    private void apply(int topColor, int bottomColorNow) {
        boolean top = isLight(topColor, topLight);
        boolean bottom = isLight(bottomColorNow, bottomLight);
        WindowInsetsControllerCompat controller = controller();
        // Re-applied every pass: other plugins may restyle the bars in between.
        controller.setAppearanceLightStatusBars(top);
        controller.setAppearanceLightNavigationBars(bottom);

        boolean changed = topLight == null || topLight != top || bottomLight != bottom || bottomColor != bottomColorNow;
        topLight = top;
        bottomLight = bottom;
        bottomColor = bottomColorNow;
        if (changed) {
            JSObject event = new JSObject();
            event.put("top", top ? "light" : "dark");
            event.put("bottom", bottom ? "light" : "dark");
            event.put("bottomColor", String.format("#%06X", 0xFFFFFF & bottomColorNow));
            notifyListeners("toneChange", event);
        }
    }

    // Light enough for dark icons. Hysteresis keeps scrolling content from flickering them.
    private static boolean isLight(int color, Boolean wasLight) {
        double luminance = luminance(color);
        if (wasLight == null) return luminance > 0.4;
        return wasLight ? luminance > 0.3 : luminance > 0.5;
    }

    private static double luminance(int color) {
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // Most common colour in the rows (4 bits per channel): the background, not the text on it.
    private static int dominantColor(Bitmap bitmap, int fromRow, int toRow) {
        Map<Integer, Integer> counts = new HashMap<>();
        Map<Integer, Integer> sample = new HashMap<>();
        int best = 0;
        int bestCount = -1;
        for (int y = fromRow; y < Math.max(fromRow + 1, toRow); y++) {
            for (int x = 0; x < SAMPLE_WIDTH; x++) {
                int pixel = bitmap.getPixel(x, y);
                int key = ((Color.red(pixel) >> 4) << 8) | ((Color.green(pixel) >> 4) << 4) | (Color.blue(pixel) >> 4);
                int count = counts.merge(key, 1, Integer::sum);
                sample.putIfAbsent(key, pixel);
                if (count > bestCount) {
                    bestCount = count;
                    best = key;
                }
            }
        }
        return sample.getOrDefault(best, Color.WHITE);
    }

    private WindowInsetsControllerCompat controller() {
        Window window = getActivity().getWindow();
        return WindowCompat.getInsetsController(window, window.getDecorView());
    }

    // The web layer paints the backing; a system scrim would double it.
    private void disableContrastScrim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getActivity().getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private String readMode() {
        // navigation_mode: 0 = three buttons, 1 = two buttons, 2 = gestures.
        int setting = Settings.Secure.getInt(getContext().getContentResolver(), "navigation_mode", -1);
        if (setting == 2) return MODE_GESTURE;
        if (setting == 0 || setting == 1) return MODE_BUTTONS;

        // Not reported on this device: buttons take tappable space, gestures don't.
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getActivity().getWindow().getDecorView());
        if (insets == null) return MODE_BUTTONS;
        return insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom > 0 ? MODE_BUTTONS : MODE_GESTURE;
    }
}
