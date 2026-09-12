package com.momo.pet;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AppMonitorManager {
    private static final String PREF_NAME = "momo_app_monitor";
    private static final String KEY_ENABLED = "monitor_enabled";
    private static final String KEY_LIMIT_MINS = "limit_mins";
    private static final String KEY_MONITOR_ALL = "monitor_all";
    private static final String KEY_PACKAGE_SET = "package_set";

    // 默认关照应用清单（常见视频、社交、娱乐、资讯类）
    public static final String[] DEFAULT_PACKAGES = {
        "tv.danmaku.bili",              // 哔哩哔哩
        "com.ss.android.ugc.aweme",     // 抖音
        "com.smile.gifmaker",           // 快手
        "com.xingin.xhs",               // 小红书
        "com.tencent.mm",               // 微信
        "com.tencent.mobileqq",         // QQ
        "com.sina.weibo",               // 微博
        "com.zhihu.android",            // 知乎
        "com.taobao.taobao",            // 淘宝
        "com.jingdong.app.mall",        // 京东
        "com.xunmeng.pinduoduo",        // 拼多多
        "com.tencent.tmgp.sgame",       // 王者荣耀
        "com.miHoYo.Yuanshen",          // 原神
        "com.netease.hyxd"              // 网易游戏等
    };

    // 检查是否获得了查看使用情况的权限
    public static boolean hasUsageStatsPermission(Context context) {
        if (context == null) return false;
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMonitorEnabled(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true);
    }

    public static void setMonitorEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    // 单次连续使用时长阈值（分钟，默认 30 分钟）
    public static int getLimitMinutes(Context context) {
        if (context == null) return 30;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LIMIT_MINS, 30);
    }

    public static void setLimitMinutes(Context context, int mins) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LIMIT_MINS, Math.max(5, mins)).commit();
    }

    // 是否对所有第三方应用生效（true: 任何App超过阈值都提醒；false: 仅监控列表内的App）
    public static boolean isMonitorAll(Context context) {
        if (context == null) return true;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITOR_ALL, true);
    }

    public static void setMonitorAll(Context context, boolean monitorAll) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MONITOR_ALL, monitorAll).commit();
    }

    public static Set<String> getMonitoredPackages(Context context) {
        if (context == null) return new HashSet<>(Arrays.asList(DEFAULT_PACKAGES));
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> set = sp.getStringSet(KEY_PACKAGE_SET, null);
        if (set == null || set.isEmpty()) {
            Set<String> def = new HashSet<>(Arrays.asList(DEFAULT_PACKAGES));
            sp.edit().putStringSet(KEY_PACKAGE_SET, def).commit();
            return def;
        }
        return new HashSet<>(set);
    }

    public static void addPackage(Context context, String pkg) {
        if (context == null || pkg == null) return;
        Set<String> set = getMonitoredPackages(context);
        set.add(pkg.trim());
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PACKAGE_SET, set).commit();
    }

    public static void removePackage(Context context, String pkg) {
        if (context == null || pkg == null) return;
        Set<String> set = getMonitoredPackages(context);
        set.remove(pkg.trim());
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PACKAGE_SET, set).commit();
    }

    // 判断某个包名是否应该被监控
    public static boolean shouldMonitor(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        String myPkg = context.getPackageName();
        if (pkg.equals(myPkg)) return false; // 不监控桌宠自己
        // 排除桌面启动器与系统常见组件
        if (pkg.contains("launcher") || pkg.contains("home") || pkg.contains("systemui") || pkg.contains("settings")) {
            return false;
        }
        if (isMonitorAll(context)) {
            return true;
        }
        return getMonitoredPackages(context).contains(pkg);
    }
}
