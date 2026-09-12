package com.momo.pet;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StatsManager {
    private static final String PREF_NAME = "momo_pet_stats";
    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat SDF_WEEK = new SimpleDateFormat("yyyy-'W'ww", Locale.getDefault());

    public static synchronized void recordDuration(Context context, boolean isStudy, int seconds) {
        if (seconds <= 0 || context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Date now = new Date();
        String dayKey = SDF_DAY.format(now);
        String weekKey = SDF_WEEK.format(now);

        String type = isStudy ? "study" : "leisure";

        String dKey = "sec_" + type + "_day_" + dayKey;
        String wKey = "sec_" + type + "_week_" + weekKey;
        String totalKey = "sec_" + type + "_total";

        sp.edit()
            .putLong(dKey, sp.getLong(dKey, 0L) + seconds)
            .putLong(wKey, sp.getLong(wKey, 0L) + seconds)
            .putLong(totalKey, sp.getLong(totalKey, 0L) + seconds)
            .commit(); // 同步强制写入磁盘，避免退出时内存数据丢失
    }

    public static long getTodayStudySec(Context context) {
        return getDaySec(context, true, new Date());
    }

    public static long getTodayLeisureSec(Context context) {
        return getDaySec(context, false, new Date());
    }

    public static long getWeekStudySec(Context context) {
        return getWeekSec(context, true, new Date());
    }

    public static long getWeekLeisureSec(Context context) {
        return getWeekSec(context, false, new Date());
    }

    public static long getDaySec(Context context, boolean isStudy, Date date) {
        if (context == null || date == null) return 0L;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String dayKey = SDF_DAY.format(date);
        String type = isStudy ? "study" : "leisure";
        return sp.getLong("sec_" + type + "_day_" + dayKey, 0L);
    }

    public static long getWeekSec(Context context, boolean isStudy, Date date) {
        if (context == null || date == null) return 0L;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String weekKey = SDF_WEEK.format(date);
        String type = isStudy ? "study" : "leisure";
        return sp.getLong("sec_" + type + "_week_" + weekKey, 0L);
    }

    public static String formatDuration(long totalSec) {
        if (totalSec <= 0) return "0 分钟";
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;

        if (h > 0) {
            return h + " 小时 " + m + " 分";
        } else if (m > 0) {
            return m + " 分 " + s + " 秒";
        } else {
            return s + " 秒";
        }
    }

    public static String formatShortDuration(long totalSec) {
        if (totalSec <= 0) return "0m";
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        if (h > 0) {
            return h + "h" + (m > 0 ? m + "m" : "");
        } else {
            return Math.max(1, m) + "m";
        }
    }

    public static String getDayLabel(Date date) {
        if (date == null) return "";
        return SDF_DAY.format(date);
    }
}