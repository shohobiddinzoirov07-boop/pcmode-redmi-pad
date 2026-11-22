package com.android.pcmode.desktop;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class DesktopPreviewUi {
    private static final String TAG = "PcDesktopPreview";
    // Non-SDK ActivityOptions bundle field read by the Android window manager.
    private static final String LAUNCH_WINDOWING_MODE = "android.activity.windowingMode";
    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final int BAR_HEIGHT = 0x7f07053a;
    private static final int START_ICON = 0x7f0801ae;
    private static final int WIFI_ICON = 0x7f080199;
    private static final int BLUETOOTH_ICON = 0x7f08014e;
    private static final int PANEL_COLOR = 0xff21252c;

    private static boolean placeNextWindowRight;

    private DesktopPreviewUi() {}

    public static List<ResolveInfo> visibleLaunchers(PackageManager packages) {
        Intent filter = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(packages.queryIntentActivities(filter, 0));
        apps.sort(new ResolveInfo.DisplayNameComparator(packages));
        return apps;
    }

    public static Intent launcherIntent(ResolveInfo source) {
        ActivityInfo activity = source.activityInfo;
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(activity.packageName, activity.name));
    }

    public static void bindShortcut(View item, Intent intent, String label) {
        item.setOnClickListener(view -> open(view.getContext(), intent, label));
    }

    private static void open(Context context, Intent intent, CharSequence label) {
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (!(context instanceof Activity)) {
            Log.e(TAG, "Missing Activity for " + intent.toUri(0));
            return;
        }
        try {
            ComponentName component = intent.getComponent();
            if (component == null) {
                context.startActivity(intent);
            } else {
                Activity activity = (Activity) context;
                boolean external = !activity.getPackageName().equals(component.getPackageName());
                float offset = external ? (placeNextWindowRight ? 0.08f : -0.08f) : 0f;
                Rect bounds = desktopLaunchBounds(activity, offset);
                Bundle options = ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle();
                if (external) {
                    options.putInt(LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM);
                }
                Intent launch = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.i(TAG, "Requested launch package=" + component.getPackageName()
                        + " component=" + component.flattenToShortString()
                        + " bounds=" + bounds.toShortString()
                        + " requestedMode=" + (external ? "freeform" : "default"));
                activity.startActivity(launch, options);
                if (external) {
                    placeNextWindowRight = !placeNextWindowRight;
                }
            }
        } catch (ActivityNotFoundException exception) {
            report(context, intent, label, "ilova topilmadi", exception);
        } catch (SecurityException exception) {
            report(context, intent, label, "ochishga ruxsat berilmadi", exception);
        }
    }

    private static Rect desktopLaunchBounds(Activity activity, float horizontalOffset) {
        WindowMetrics metrics = activity.getWindowManager().getMaximumWindowMetrics();
        Rect workArea = new Rect(metrics.getBounds());
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        workArea.inset(insets.left, insets.top, insets.right, insets.bottom);
        if (workArea.isEmpty()) {
            workArea.set(metrics.getBounds());
        }

        int width = Math.max(1, Math.round(workArea.width() * 0.70f));
        int height = Math.max(1, Math.round(workArea.height() * 0.70f));
        int left = workArea.left + (workArea.width() - width) / 2
                + Math.round(workArea.width() * horizontalOffset);
        int top = workArea.top + (workArea.height() - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }

    private static void report(Context context, Intent intent, CharSequence label,
                               String reason, RuntimeException exception) {
        Log.w(TAG, "Could not open " + label + " using " + intent.toUri(0), exception);
        Toast.makeText(context, label + ": " + reason, Toast.LENGTH_SHORT).show();
    }

    public static void mount(Activity activity, View desktop) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        desktop.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        content.addView(desktop, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(PANEL_COLOR);
        int height = activity.getResources().getDimensionPixelSize(BAR_HEIGHT);
        content.addView(bar, new LinearLayout.LayoutParams(-1, height));

        ImageButton start = new ImageButton(activity);
        start.setImageResource(START_ICON);
        start.setColorFilter(Color.WHITE);
        start.setContentDescription("Start");
        start.setBackgroundResource(android.R.drawable.list_selector_background);
        bar.addView(start, new LinearLayout.LayoutParams(height, -1));
        start.setOnClickListener(view -> showApps(activity, bar));

        TextView pin = new TextView(activity);
        pin.setText("Yorliq qo‘shish");
        pin.setTextColor(Color.WHITE);
        pin.setGravity(Gravity.CENTER);
        pin.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        pin.setBackgroundResource(android.R.drawable.list_selector_background);
        bar.addView(pin, new LinearLayout.LayoutParams(-2, -1));
        pin.setOnClickListener(view -> DesktopEntry.pin(activity));

        bar.addView(new View(activity), new LinearLayout.LayoutParams(0, -1, 1));

        TextClock status = new TextClock(activity);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.WHITE);
        status.setPadding(dp(activity, 20), 0, dp(activity, 20), 0);
        status.setBackgroundResource(android.R.drawable.list_selector_background);
        status.setContentDescription("Tezkor sozlamalar");
        bar.addView(status, new LinearLayout.LayoutParams(-2, -1));
        status.setOnClickListener(view -> showSettings(activity, bar));

        activity.setContentView(content);
    }

    private static void showApps(Activity activity, View bar) {
        PackageManager packages = activity.getPackageManager();
        List<ResolveInfo> apps = visibleLaunchers(packages);
        ListView list = new ListView(activity);
        ArrayAdapter<ResolveInfo> adapter = new ArrayAdapter<ResolveInfo>(activity,
                android.R.layout.simple_list_item_1, apps) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                ResolveInfo app = getItem(position);
                row.setText(app.loadLabel(packages));
                row.setTextColor(Color.WHITE);
                setIcon(row, app.loadIcon(packages));
                return row;
            }
        };
        list.setAdapter(adapter);
        Log.i(TAG, "Start launchers queried=" + apps.size() + " menuItems=" + adapter.getCount());
        int height = Math.min(dp(activity, 420), ((View) bar.getParent()).getHeight() / 2);
        PopupWindow menu = popup(list, dp(activity, 320), height);
        list.setOnItemClickListener((parent, row, position, id) -> {
            ResolveInfo app = apps.get(position);
            menu.dismiss();
            open(activity, launcherIntent(app), app.loadLabel(packages));
        });
        menu.showAtLocation(bar, Gravity.BOTTOM | Gravity.LEFT, 0, bar.getHeight());
    }

    private static void showSettings(Activity activity, View bar) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        PopupWindow menu = popup(panel, dp(activity, 260), -2);
        addSetting(activity, panel, menu, "Wi-Fi", WIFI_ICON, Settings.ACTION_WIFI_SETTINGS);
        addSetting(activity, panel, menu, "Bluetooth", BLUETOOTH_ICON,
                Settings.ACTION_BLUETOOTH_SETTINGS);
        menu.showAtLocation(bar, Gravity.BOTTOM | Gravity.RIGHT, 0, bar.getHeight());
    }

    private static void addSetting(Activity activity, LinearLayout panel, PopupWindow menu,
                                   String label, int icon, String action) {
        TextView row = new TextView(activity);
        row.setText(label);
        row.setTextColor(Color.WHITE);
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        setIcon(row, activity.getDrawable(icon));
        panel.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 56)));
        row.setOnClickListener(view -> {
            menu.dismiss();
            open(activity, new Intent(action), label);
        });
    }

    private static PopupWindow popup(View view, int width, int height) {
        PopupWindow window = new PopupWindow(view, width, height, true);
        window.setBackgroundDrawable(new ColorDrawable(PANEL_COLOR));
        window.setOutsideTouchable(true);
        return window;
    }

    private static void setIcon(TextView row, Drawable icon) {
        int size = dp(row.getContext(), 24);
        if (icon != null) icon.setBounds(0, 0, size, size);
        row.setCompoundDrawables(icon, null, null, null);
        row.setCompoundDrawablePadding(dp(row.getContext(), 12));
    }

    private static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
