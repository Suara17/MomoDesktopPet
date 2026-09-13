package com.momo.pet;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PetGrowthManager {
    private static final String PREF_NAME = "momo_pet_growth";
    private static final String KEY_MOOD = "pet_mood";                   // 心情值 (0 ~ 100)
    private static final String KEY_EXP = "pet_bond_exp";               // 羁绊总经验
    private static final String KEY_LAST_DATE = "last_record_date";     // 上次记录日期 (YYYY-MM-DD)
    private static final String KEY_IGNORE_COUNT = "ignore_guard_count";// 当日连续无视防沉迷次数
    private static final String KEY_SNACKS = "fish_snacks_count";       // 小鱼干库存
    private static final String KEY_TOTAL_COMPANION_SECONDS = "total_companion_seconds"; // 累计陪伴总秒数

    // 各等级所需累计经验阈值 (Lv.1 初遇 ~ Lv.8 永恒契约)
    public static final int[] LEVEL_THRESHOLDS = {
        0,      // Lv.1 初遇
        100,    // Lv.2 熟悉
        300,    // Lv.3 默契
        600,    // Lv.4 依赖
        1000,   // Lv.5 偏爱
        1500,   // Lv.6 眷恋
        2200,   // Lv.7 心灵相通
        3000    // Lv.8 永恒契约
    };

    public static final String[] LEVEL_TITLES = {
        "Lv.1 初遇",
        "Lv.2 熟悉",
        "Lv.3 默契",
        "Lv.4 依赖",
        "Lv.5 偏爱",
        "Lv.6 眷恋",
        "Lv.7 心灵相通",
        "Lv.8 永恒契约"
    };

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static void checkAndResetDaily(Context context) {
        if (context == null) return;
        SharedPreferences sp = getPrefs(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastDate = sp.getString(KEY_LAST_DATE, "");
        if (!today.equals(lastDate)) {
            // 新的一天，重置当日无视提醒计数，心情值自然回稳至 75 (微慵懒)
            int currentMood = sp.getInt(KEY_MOOD, 75);
            int newMood = Math.max(50, Math.min(85, currentMood)); // 柔和回归基准线
            sp.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_IGNORE_COUNT, 0)
                .putInt(KEY_MOOD, newMood)
                .apply();
        }
    }

    // ─── 心情值 (0 ~ 100) ────────────────────────────────────
    public static int getMood(Context context) {
        if (context == null) return 75;
        checkAndResetDaily(context);
        return getPrefs(context).getInt(KEY_MOOD, 75);
    }

    public static void changeMood(Context context, int delta) {
        if (context == null) return;
        checkAndResetDaily(context);
        SharedPreferences sp = getPrefs(context);
        int current = sp.getInt(KEY_MOOD, 75);
        int target = Math.max(0, Math.min(100, current + delta));
        sp.edit().putInt(KEY_MOOD, target).apply();
    }

    // 心情状态文字描述
    public static String getMoodStatusDesc(int mood) {
        if (mood >= 85) return "🥰 超级开心";
        if (mood >= 65) return "😊 惬意慵懒";
        if (mood >= 40) return "😐 稍显无聊";
        if (mood >= 20) return "🥺 有点委屈";
        return "😤 气鼓鼓中";
    }

    // ─── 羁绊与等级 ──────────────────────────────────────────
    public static int getTotalExp(Context context) {
        if (context == null) return 0;
        return getPrefs(context).getInt(KEY_EXP, 0);
    }

    public static void addExp(Context context, int amount) {
        if (context == null || amount <= 0) return;
        SharedPreferences sp = getPrefs(context);
        int cur = sp.getInt(KEY_EXP, 0);
        sp.edit().putInt(KEY_EXP, cur + amount).apply();
    }

    public static int getLevel(Context context) {
        int exp = getTotalExp(context);
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (exp >= LEVEL_THRESHOLDS[i]) {
                return i + 1;
            }
        }
        return 1;
    }

    public static String getLevelTitle(Context context) {
        int lvl = getLevel(context);
        if (lvl <= LEVEL_TITLES.length) {
            return LEVEL_TITLES[lvl - 1];
        }
        return "Lv." + lvl + " 灵魂羁绊";
    }

    // 获取当前等级升级进度百分比与所需经验文本，如 "80/200 (40%)"
    public static class LevelProgress {
        public int currentLevelExp;
        public int nextLevelNeedExp;
        public float progressFraction; // 0.0f ~ 1.0f
    }

    public static LevelProgress getLevelProgress(Context context) {
        LevelProgress lp = new LevelProgress();
        int totalExp = getTotalExp(context);
        int lvl = getLevel(context);
        int currentBase = LEVEL_THRESHOLDS[Math.min(lvl - 1, LEVEL_THRESHOLDS.length - 1)];
        if (lvl >= LEVEL_THRESHOLDS.length) {
            // 已满级
            lp.currentLevelExp = totalExp - currentBase;
            lp.nextLevelNeedExp = 0;
            lp.progressFraction = 1.0f;
            return lp;
        }
        int nextBase = LEVEL_THRESHOLDS[lvl];
        int need = nextBase - currentBase;
        int have = Math.max(0, totalExp - currentBase);
        lp.currentLevelExp = have;
        lp.nextLevelNeedExp = need;
        lp.progressFraction = Math.min(1.0f, (float) have / (float) need);
        return lp;
    }

    // ─── 小鱼干零食 ──────────────────────────────────────────
    public static int getSnacks(Context context) {
        if (context == null) return 5;
        return getPrefs(context).getInt(KEY_SNACKS, 5); // 初始送 5 条
    }

    public static void addSnacks(Context context, int count) {
        if (context == null) return;
        SharedPreferences sp = getPrefs(context);
        int cur = sp.getInt(KEY_SNACKS, 5);
        sp.edit().putInt(KEY_SNACKS, Math.max(0, cur + count)).apply();
    }

    public static boolean feedSnack(Context context) {
        if (context == null) return false;
        SharedPreferences sp = getPrefs(context);
        int cur = sp.getInt(KEY_SNACKS, 5);
        if (cur <= 0) return false;
        sp.edit().putInt(KEY_SNACKS, cur - 1).apply();
        changeMood(context, 15); // 喂小鱼干心情立刻 +15
        addExp(context, 10);     // 羁绊 +10
        return true;
    }

    // ─── 互动与行为触发 ───────────────────────────────────────

    // 1. 完成专注 (达到预定时长)
    public static void onFocusCompleted(Context context, int durationMins) {
        if (context == null) return;
        int moodBonus = Math.min(25, 10 + durationMins / 3);
        int expBonus = Math.min(60, 15 + durationMins);
        changeMood(context, moodBonus);
        addExp(context, expBonus);
        addSnacks(context, 1); // 奖励小鱼干 1 条
    }

    // 2. 专注中途强行放弃
    public static void onFocusAbandoned(Context context) {
        if (context == null) return;
        changeMood(context, -12); // 心情小降
    }

    // 3. 收到防沉迷提醒后听劝退出 (2分钟内切走)
    public static void onHeededGuardAdvice(Context context) {
        if (context == null) return;
        SharedPreferences sp = getPrefs(context);
        sp.edit().putInt(KEY_IGNORE_COUNT, 0).apply();
        changeMood(context, 8);
        addExp(context, 10);
    }

    // 4. 防沉迷连续无视提醒
    public static int onIgnoredGuardAdvice(Context context) {
        if (context == null) return 1;
        checkAndResetDaily(context);
        SharedPreferences sp = getPrefs(context);
        int count = sp.getInt(KEY_IGNORE_COUNT, 0) + 1;
        sp.edit().putInt(KEY_IGNORE_COUNT, count).apply();

        if (count == 1) {
            changeMood(context, -5);
        } else if (count >= 2) {
            // 连续无视 2 次或以上，心情大跌进入生气状态！
            changeMood(context, -20);
        }
        return count;
    }
    // ─── 累计陪伴时长 ───────────────────────────────────────
    public static long getTotalCompanionSeconds(Context context) {
        if (context == null) return 0;
        return getPrefs(context).getLong(KEY_TOTAL_COMPANION_SECONDS, 0);
    }

    public static void addCompanionSeconds(Context context, long seconds) {
        if (context == null || seconds <= 0) return;
        SharedPreferences sp = getPrefs(context);
        long cur = sp.getLong(KEY_TOTAL_COMPANION_SECONDS, 0);
        sp.edit().putLong(KEY_TOTAL_COMPANION_SECONDS, cur + seconds).apply();
    }

    public static String formatCompanionDuration(long totalSec) {
        if (totalSec < 60) {
            return totalSec + " 秒";
        }
        long mins = totalSec / 60;
        if (mins < 60) {
            return mins + " 分钟";
        }
        long hours = mins / 60;
        long remainMins = mins % 60;
        if (hours < 24) {
            return hours + " 小时 " + remainMins + " 分钟";
        }
        long days = hours / 24;
        long remainHours = hours % 24;
        return days + " 天 " + remainHours + " 小时";
    }

}
