package com.momo.pet;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppMonitorManager {
    private static final String PREF_NAME = "momo_app_monitor";
    private static final String KEY_ENABLED = "monitor_enabled";
    private static final String KEY_LIMIT_MINS = "limit_mins"; // 全局单次连续限制 (分钟)
    private static final String KEY_DAILY_LIMIT_MINS = "daily_limit_mins"; // 全局今日累计限制 (分钟，默认 120 分钟)
    private static final String KEY_MONITOR_ALL = "monitor_all";
    private static final String KEY_PACKAGE_SET = "package_set";
    private static final String PREFIX_APP_DAILY_LIMIT = "daily_limit_pkg_"; // 单个 App 独立配置今日累计限制

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

    // 单次连续使用时长全局默认阈值（分钟，默认 30 分钟）
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

    // 全局今日累计使用时长默认阈值（分钟，默认 120 分钟）
    public static int getGlobalDailyLimitMinutes(Context context) {
        if (context == null) return 120;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_LIMIT_MINS, 120);
    }

    public static void setGlobalDailyLimitMinutes(Context context, int mins) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAILY_LIMIT_MINS, Math.max(10, mins)).commit();
    }

    // 获取某个特定应用的今日累计使用阈值（分钟；若未单独设置，则返回全局阈值）
    public static int getAppDailyLimitMinutes(Context context, String pkg) {
        if (context == null || pkg == null) return getGlobalDailyLimitMinutes(context);
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int val = sp.getInt(PREFIX_APP_DAILY_LIMIT + pkg, -1);
        if (val > 0) return val;
        return getGlobalDailyLimitMinutes(context);
    }

    // 检查某个应用是否单独配置了累计时长
    public static boolean hasCustomDailyLimit(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getInt(PREFIX_APP_DAILY_LIMIT + pkg, -1) > 0;
    }

    // 设置某个特定应用的今日累计使用阈值（若 <= 0 表示使用全局默认值）
    public static void setAppDailyLimitMinutes(Context context, String pkg, int mins) {
        if (context == null || pkg == null) return;
        SharedPreferences.Editor ed = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        if (mins <= 0) {
            ed.remove(PREFIX_APP_DAILY_LIMIT + pkg);
        } else {
            ed.putInt(PREFIX_APP_DAILY_LIMIT + pkg, mins);
        }
        ed.commit();
    }

    // 查询该应用今天从 00:00 到当前时间的原生前台累计总使用时长 (分钟)
    public static int getAppTodayUsedMinutes(Context context, String pkg) {
        if (context == null || pkg == null) return 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return 0;
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startToday = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            List<UsageStats> statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startToday, now);
            if (statsList == null || statsList.isEmpty()) return 0;

            long totalMs = 0;
            for (UsageStats us : statsList) {
                if (pkg.equals(us.getPackageName())) {
                    totalMs += us.getTotalTimeInForeground();
                }
            }
            return (int) (totalMs / 60000);
        } catch (Exception e) {
            return 0;
        }
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
        addPackage(context, pkg, 0);
    }

    public static void addPackage(Context context, String pkg, int customDailyLimitMins) {
        if (context == null || pkg == null) return;
        Set<String> set = getMonitoredPackages(context);
        set.add(pkg.trim());
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PACKAGE_SET, set).commit();
        if (customDailyLimitMins > 0) {
            setAppDailyLimitMinutes(context, pkg, customDailyLimitMins);
        }
    }

    public static void removePackage(Context context, String pkg) {
        if (context == null || pkg == null) return;
        Set<String> set = getMonitoredPackages(context);
        set.remove(pkg.trim());
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGE_SET, set)
            .remove(PREFIX_APP_DAILY_LIMIT + pkg)
            .commit();
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