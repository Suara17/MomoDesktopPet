package com.momo.pet;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static final String PREF_NAME = "momo_sound_prefs";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_SOUND_VOLUME = "sound_volume"; // 0 ~ 100

    private static SoundManager instance;
    private SoundPool soundPool;
    private final Map<String, Integer> soundMap = new HashMap<>();
    private final java.util.Set<Integer> loadedSoundIds = new java.util.HashSet<>();
    private boolean isLoaded = false;
    private Context appContext;

    private SoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        initSoundPool();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    private void initSoundPool() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                    .build();
                soundPool = new SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(attributes)
                    .build();
            } else {
                soundPool = new SoundPool(8, android.media.AudioManager.STREAM_MUSIC, 0);
            }

            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    if (status == 0) {
                        loadedSoundIds.add(sampleId);
                    }
                }
            });

            // 加载 res/raw 下的全部 9 种交互音效
            loadSound("tap", R.raw.sfx_tap);
            loadSound("cat", R.raw.sfx_cat);
            loadSound("size", R.raw.sfx_size);
            loadSound("drag", R.raw.sfx_drag);
            loadSound("snap", R.raw.sfx_snap);
            loadSound("menu", R.raw.sfx_menu);
            loadSound("pause", R.raw.sfx_pause);
            loadSound("finish", R.raw.sfx_finish);
            loadSound("alert", R.raw.sfx_alert);

            isLoaded = true;
        } catch (Exception e) {
            isLoaded = false;
        }
    }

    private void loadSound(String key, int resId) {
        try {
            int soundId = soundPool.load(appContext, resId, 1);
            soundMap.put(key, soundId);
        } catch (Exception ignored) {}
    }

    public void play(String key) {
        if (!isSoundEnabled(appContext)) return;
        if (soundPool == null || !soundMap.containsKey(key)) return;

        try {
            int soundId = soundMap.get(key);
            // 确保该音频已经加载解码就绪
            if (!loadedSoundIds.contains(soundId)) {
                // 如果还没收到回调，尝试直接播放一次
                float vol = getVolume(appContext);
                soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
                return;
            }
            float vol = getVolume(appContext);
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        } catch (Exception ignored) {}
    }

    public static boolean isSoundEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SOUND_ENABLED, enabled).commit();
    }

    public static float getVolume(Context context) {
        if (context == null) return 0.7f;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int volInt = sp.getInt(KEY_SOUND_VOLUME, 70);
        return Math.max(0.1f, Math.min(1.0f, volInt / 100.0f));
    }

    public static void setVolumePercent(Context context, int percent) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SOUND_VOLUME, Math.max(0, Math.min(100, percent))).commit();
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundMap.clear();
        isLoaded = false;
    }
}