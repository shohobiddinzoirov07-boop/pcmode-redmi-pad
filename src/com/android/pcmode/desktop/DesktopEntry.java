package com.android.pcmode.desktop;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.view.MotionEvent;
import android.widget.Toast;
import java.util.WeakHashMap;

public final class DesktopEntry {
    private static final int START_ICON = 0x7f0801ae;
    private static final String LABEL = "PC ish stoli";
    private static final WeakHashMap<Activity, Swipe> SWIPES = new WeakHashMap<>();

    private DesktopEntry() {}

    public static Intent intent(Context context) {
        return new Intent(Intent.ACTION_VIEW)
                .setClassName(context.getPackageName(), "com.android.pcmode.DesktopEntryActivity");
    }

    public static void open(Activity activity) {
        activity.startActivity(intent(activity));
    }

    public static void pin(Activity activity) {
        ShortcutManager shortcuts = activity.getSystemService(ShortcutManager.class);
        if (shortcuts == null || !shortcuts.isRequestPinShortcutSupported()) {
            Toast.makeText(activity, "Launcher yorliq qo‘shishni qo‘llamaydi", Toast.LENGTH_SHORT).show();
            return;
        }
        ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "pc-desktop")
                .setShortLabel(LABEL)
                .setIcon(Icon.createWithResource(activity, START_ICON))
                .setIntent(intent(activity))
                .build();
        if (!shortcuts.requestPinShortcut(shortcut, null)) {
            Toast.makeText(activity, "Yorliq qo‘shish so‘rovi rad etildi", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean handleTouch(Activity activity, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            SWIPES.put(activity, new Swipe());
            return false;
        }
        Swipe swipe = SWIPES.get(activity);
        if (swipe == null) return false;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            SWIPES.remove(activity);
            return swipe.opened;
        }
        if (swipe.opened) return true;
        int count = event.getPointerCount();
        if (count > 5) swipe.invalid = true;
        if (swipe.invalid) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN && count == 5) {
            swipe.capture(event);
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            swipe.invalid = true;
            float threshold = 56 * activity.getResources().getDisplayMetrics().density;
            if (count == 5 && swipe.matches(event, threshold)) {
                swipe.opened = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                activity.getWindow().superDispatchTouchEvent(cancel);
                cancel.recycle();
                open(activity);
                return true;
            }
        }
        return false;
    }

    private static final class Swipe {
        private final int[] ids = new int[5];
        private final float[] x = new float[5];
        private final float[] y = new float[5];
        private boolean captured;
        private boolean invalid;
        private boolean opened;

        void capture(MotionEvent event) {
            for (int i = 0; i < 5; i++) {
                if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) {
                    invalid = true;
                    return;
                }
                ids[i] = event.getPointerId(i);
                x[i] = event.getX(i);
                y[i] = event.getY(i);
            }
            captured = true;
        }

        boolean matches(MotionEvent event, float threshold) {
            if (!captured) return false;
            for (int i = 0; i < 5; i++) {
                int index = event.findPointerIndex(ids[i]);
                if (index < 0) return false;
                float left = x[i] - event.getX(index);
                if (left < threshold || Math.abs(event.getY(index) - y[i]) > left * 0.65f) {
                    return false;
                }
            }
            return true;
        }
    }
}
