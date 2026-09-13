package com.momo.pet;

import android.content.Context;
import android.content.SharedPreferences;

public class ModuleConfigManager {
    private static final String PREF_NAME = "momo_module_config";
    private static final String KEY_FOCUS = "module_focus_enabled";
    private static final String KEY_BILL = "module_bill_enabled";
    private static final String KEY_MONITOR = "module_monitor_enabled";
    private static final String KEY_BG_PATH = "custom_bg_path";
    private static final String KEY_BG_ALPHA = "custom_bg_alpha"; // 0 ~ 100

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isFocusEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_FOCUS, false); // 默认收敛，保持纯粹桌宠
    }

    public static void setFocusEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_FOCUS, enabled).apply();
    }

    public static boolean isBillEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_BILL, false);
    }

    public static void setBillEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_BILL, enabled).apply();
    }

    public static boolean isMonitorEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_MONITOR, false);
    }

    public static void setMonitorEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_MONITOR, enabled).apply();
    }

    public static String getCustomBgPath(Context context) {
        return getPrefs(context).getString(KEY_BG_PATH, null);
    }

    public static void setCustomBgPath(Context context, String path) {
        getPrefs(context).edit().putString(KEY_BG_PATH, path).apply();
    }

    public static int getBgAlpha(Context context) {
        return getPrefs(context).getInt(KEY_BG_ALPHA, 75); // 默认 75% 柔和透光
    }

    public static void setBgAlpha(Context context, int alphaPercent) {
        getPrefs(context).edit().putInt(KEY_BG_ALPHA, Math.max(0, Math.min(100, alphaPercent))).apply();
    }

    private static final String KEY_CARD_ALPHA = "custom_card_alpha"; // 0 ~ 100

    public static int getCardAlpha(Context context) {
        return getPrefs(context).getInt(KEY_CARD_ALPHA, 85); // 默认 85% 优雅半透磨砂
    }

    public static void setCardAlpha(Context context, int alphaPercent) {
        getPrefs(context).edit().putInt(KEY_CARD_ALPHA, Math.max(10, Math.min(100, alphaPercent))).apply();
    }

}
