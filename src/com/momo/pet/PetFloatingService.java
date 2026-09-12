package com.momo.pet;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class PetFloatingService extends Service {
    private static final String CHANNEL_ID    = "momo_pet_channel";
    private static final int    NOTIF_ID      = 2026;
    private static final String ACTION_STOP   = "STOP";
    private static final String ACTION_REBOOT = "REBOOT_WATCH";

    private PowerManager.WakeLock wakeLock;

    // Window & Views
    private WindowManager windowManager;
    private View petContainer;
    private ImageView petImageView;
    private TextView bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private TextView timerView;
    private LinearLayout menuLayout;
    private android.widget.FrameLayout circleOverlay;
    private WindowManager.LayoutParams params;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // 动作帧数据
    public static class FrameItem {
        String filePath;
        int durationMs;
        FrameItem(String fp, int d) { filePath = fp; durationMs = d; }
    }
    public static class ActionItem {
        boolean loop; String nextAction;
        List<FrameItem> frames = new ArrayList<>();
    }
    private final Map<String, ActionItem> actionMap = new HashMap<>();

    // 基于 LRU 的超轻量图片缓存（只在内存保留最近用到的 60 帧，约 15MB 内存，彻底告别 OOM 闪退！）
    private final android.util.LruCache<String, Bitmap> bitmapCache = 
        new android.util.LruCache<String, Bitmap>(60) {
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                // 由 GC 自动回收
            }
        };

    private Bitmap getOrLoadBitmap(String file) {
        Bitmap bmp = bitmapCache.get(file);
        if (bmp != null && !bmp.isRecycled()) {
            return bmp;
        }
        try {
            InputStream imgIs = getAssets().open(file);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inSampleSize = 1;
            opts.inScaled = false;
            bmp = BitmapFactory.decodeStream(imgIs, null, opts);
            imgIs.close();
            if (bmp != null) {
                bitmapCache.put(file, bmp);
            }
        } catch (Exception ignored) {}
        return bmp;
    }

    // 普通随机动作池（排除拖动、睡觉、学习、休闲）
    private static final String[] POOL_ACTIONS = {
        "idle", "happy", "curious", "drink", "hug", "daze", "cat", "cat_smooth"
    };
    private final List<String> deck = new ArrayList<>();
    private int deckIdx = 0;

    // 运行模式与计时器状态
    public enum Mode {
        NORMAL,  // 普通自由模式（随机展示、可发呆、超时入睡）
        STUDY,   // 学习模式（锁定 study 动作）
        LEISURE  // 休闲模式（锁定 leisure 动作）
    }
    private Mode currentMode = Mode.NORMAL;
    private boolean isCountDown = false;
    private int timerRemainingSec = 0;
    private int timerElapsedSec = 0;
    private boolean timerRunning = false;
    private boolean timerPaused = false;

    // 当前动画播放状态
    private String currentAction = "idle";
    private int currentFrameIdx = 0;
    private boolean isOneShot = false;
    private Runnable oneShotCallback = null;
    private long lastInteractMs = System.currentTimeMillis();

    // 手势状态
    private int initX, initY;
    private float initTouchX, initTouchY;
    private boolean dragging = false;
    private long lastDragUpdateMs = 0;
    private final android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
    private boolean edgeMode = false;
    private int edgeSide = 0; // -1 左侧，1 右侧
    private long touchDownTime = 0;
    private int tapCount = 0;
    private final Runnable tapTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            int count = tapCount;
            tapCount = 0;
            if (count == 1) {
                onSingleTap();
            } else if (count == 2) {
                onDoubleTap();
            }
        }
    };
    private boolean hasTriggeredLongPress = false;

    // 尺寸档位：迷你(90dp)、小巧(125dp)、标准(160dp)、大只(200dp)
    private final int[] SIZES_DP = {90, 125, 160, 200};
    private int sizeIdx = 2; // 默认标准档位

    // 防沉迷监控状态跟踪
    private String lastForegroundPkg = null;
    private long currentPkgStartTime = 0;
    private long lastWarningTime = 0;
    private int warningStage = 0; // 0: 未提醒, 1: 初次提醒, 2: 严重超时提醒
    private boolean isWarningBubbleActive = false; // 是否正展示强调提醒气泡（常驻不消失，直到点击人物）

    // 分场景台词库：轻松、可爱、正向，但不强行灌鸡汤
    private final String[] QUOTES_NORMAL = {
        "嗯？你来啦。今天也一起慢慢来。",
        "不会就不会嘛……我又没说不陪你。",
        "先做眼前这一小步，剩下的等走到那里再想。",
        "团子说：今天也要好好吃饭、好好休息。喵。",
        "你已经坚持到这里了，别小看这段路。",
        "脑袋卡住的时候，换口气，不代表你不行。",
        "今天的进度哪怕只有一点点，也算向前走了。",
        "来，整理一下思路。乱糟糟的也可以慢慢理顺。",
        "不着急，墨墨和团子都在。",
        "诶，这次居然自己想到了。不错嘛。",
        "做错了就改，改完就是新线索。",
        "给今天的自己打个小勾：有在努力。",
        "先喝口水。人类不是靠意志力运行的机器。",
        "今天也不和别人比，和昨天的自己比一下就好。",
        "团子已经替你检查过了：还可以继续加油。喵。",
        "累了就休息，休息不是任务失败。",
        "小小进步也值得被发现，我看见了。",
        "别被一时的情绪骗了，你比现在想的更能坚持。"
    };
    private final String[] QUOTES_STUDY = {
        "先看最简单的部分，我们从这里开始。",
        "卡住了？很好，说明这里值得弄懂。",
        "不用一次学会，先把这一题拆开。",
        "专注十分钟也算专注，别给自己加奇怪的门槛。",
        "错题不是黑历史，是下次得分提示。",
        "先自己想三十秒，我在旁边等你。",
        "前半段思路是对的，只要修正这一小步。",
        "今天不追求完美，追求比刚才清楚一点。",
        "团子负责趴着，我负责陪你把难点拆掉。",
        "你不是学不会，只是还没找到合适的入口。",
        "再坚持这一小段，答案马上就要露头了。",
        "把大目标切成小格子，完成一格就很厉害。",
        "不会的地方做个记号，先往后走，不要被它绑架。",
        "诶，这个推导你自己走出来了。记住这种感觉。",
        "学习不是和时间打架，是一点点把陌生变熟悉。",
        "认真做完一道，比假装看完十页更有用。",
        "呼吸一下，肩膀放松。脑子也需要一点空间。",
        "最后这一题做完就休息，墨墨说到做到。"
    };
    private final String[] QUOTES_LEISURE = {
        "休闲模式启动。今天的任务：把自己充回电。",
        "什么都不做五分钟，也是一种合理安排。",
        "团子已经进入柔软模式，你也可以放松啦。喵。",
        "喝口水，伸个懒腰，别把自己折叠成问号。",
        "休息不是偷懒，是给明天的自己留电量。",
        "今天已经做得够多了，现在轮到舒服一下。",
        "允许自己发呆，灵感有时就是从发呆里蹦出来的。",
        "把烦恼暂时放在门外，等有力气了再处理。",
        "来听点喜欢的东西，心情也要晒晒太阳。",
        "不用一直高效，人类偶尔低功耗很正常。",
        "好好休息，等会儿再出发也完全来得及。",
        "团子说：今天的你值得一罐小鱼干。喵喵。",
        "眼睛离开屏幕一下，看看窗外。世界还在正常运转。",
        "休息到舒服为止，不用为放松感到内疚。",
        "这不是暂停人生，这是保存进度。",
        "慢一点没关系，舒服地走也能走很远。"
    };
    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        loadAnimations();
        initDeck();
        buildWindow();
        startAnimLoop();
        startBehaviorLoop();
        startAppMonitorLoop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_REBOOT.equals(action)) {
                if (petContainer == null || petContainer.getWindowToken() == null) {
                    if (windowManager != null && petContainer != null) {
                        try { windowManager.removeView(petContainer); } catch (Exception ignored) {}
                    }
                    petContainer = null;
                    buildWindow();
                    startAnimLoop();
                    startBehaviorLoop();
                }
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
        handler.removeCallbacksAndMessages(null);
        if (petContainer != null && windowManager != null) {
            try { windowManager.removeView(petContainer); } catch (Exception ignored) {}
        }
        if (bubbleView != null && windowManager != null && bubbleView.isAttachedToWindow()) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        }
        SoundManager.getInstance(this).release();
        releaseWakeLock();
        scheduleRestart();
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MomoPet:KeepAlive");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void scheduleRestart() {
        Intent i = new Intent(this, PetFloatingService.class);
        i.setAction(ACTION_REBOOT);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getService(this, 9527, i, flags);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long wake = System.currentTimeMillis() + 1500;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wake, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, wake, pi);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "墨墨桌宠服务", NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("保持墨墨桌宠悬浮窗在前台活跃运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        return b.setContentTitle("墨墨与团子正在桌面上陪伴你")
                .setContentText("点击返回主界面管理")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .build();
    }

    private void loadAnimations() {
        try {
            AssetManager am = getAssets();
            InputStream is = am.open("animation.json");
            byte[] bytes = new byte[is.available()];
            is.read(bytes); is.close();
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            JSONObject actions = root.getJSONObject("actions");

            for (java.util.Iterator<String> it = actions.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONObject ao = actions.getJSONObject(name);
                ActionItem item = new ActionItem();
                item.loop = ao.optBoolean("loop", true);
                item.nextAction = ao.optString("next_action", null);
                JSONArray fa = ao.getJSONArray("frames");
                for (int i = 0; i < fa.length(); i++) {
                    JSONObject fr = fa.getJSONObject(i);
                    String file = fr.getString("file");
                    int dur = fr.optInt("duration_ms", 40);
                    item.frames.add(new FrameItem(file, dur));
                }
                if (!item.frames.isEmpty()) actionMap.put(name, item);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initDeck() {
        deck.clear();
        for (String a : POOL_ACTIONS) {
            if (actionMap.containsKey(a)) deck.add(a);
        }
        Collections.shuffle(deck);
        deckIdx = 0;
    }

    private String nextDeckAction() {
        if (deck.isEmpty()) return "idle";
        if (deckIdx >= deck.size()) { Collections.shuffle(deck); deckIdx = 0; }
        return deck.get(deckIdx++);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 120; params.y = 350;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        // 1. 头顶悬浮时间数字 (Timer Pill)
        timerView = new TextView(this);
        timerView.setBackgroundResource(R.drawable.bg_timer_pill);
        timerView.setTextColor(0xFF00FFA6); // 专注薄荷青色
        timerView.setTextSize(13);
        timerView.setTypeface(android.graphics.Typeface.MONOSPACE);
        timerView.setGravity(Gravity.CENTER);
        timerView.setVisibility(View.GONE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.bottomMargin = dp(2);
        timerView.setLayoutParams(tp);
        container.addView(timerView);

        // 2. 独立智能定位气泡 (Bubble View)
        bubbleView = new TextView(this);
        bubbleView.setBackgroundResource(R.drawable.bg_bubble);
        bubbleView.setTextColor(0xFFFFFFFF);
        bubbleView.setTextSize(13);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setLineSpacing(dp(2), 1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bubbleView.setElevation(dp(8));
        }
        bubbleView.setVisibility(View.GONE);
        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        // 3. 角色动画主图
        petImageView = new ImageView(this);
        int sz = dp(SIZES_DP[sizeIdx]);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(sz, sz);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        petImageView.setLayoutParams(ip);
        petImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // 人物与环形按钮重叠容器
        android.widget.FrameLayout petLayer = new android.widget.FrameLayout(this);
        petLayer.setClipChildren(false);
        petLayer.setClipToPadding(false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        petLayer.setLayoutParams(plp);
        petLayer.addView(petImageView);

        // 围绕人物的环形按键容器 (绝对坐标排布)
        circleOverlay = new android.widget.FrameLayout(this);
        circleOverlay.setClipChildren(false);
        circleOverlay.setClipToPadding(false);
        circleOverlay.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        );
        circleOverlay.setLayoutParams(clp);
        petLayer.addView(circleOverlay);

        container.addView(petLayer);

        // 4. 模式切换悬浮菜单 (Menu Card)
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setBackgroundResource(R.drawable.bg_menu_card);
        menuLayout.setVisibility(View.GONE);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        mp.gravity = Gravity.CENTER_HORIZONTAL;
        mp.topMargin = dp(4);
        menuLayout.setLayoutParams(mp);
        container.addView(menuLayout);

        petContainer = container;
        petContainer.setOnTouchListener(touchListener);
        windowManager.addView(petContainer, params);

        play("idle", true, null);
    }

    // 长按检测 Runnable
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging && Math.abs(System.currentTimeMillis() - touchDownTime) >= 550) {
                hasTriggeredLongPress = true;
                onLongPress();
            }
        }
    };

    private final View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = params.x; initY = params.y;
                    initTouchX = e.getRawX(); initTouchY = e.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    dragging = false;
                    hasTriggeredLongPress = false;
                    handler.postDelayed(longPressRunnable, 600);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - initTouchX;
                    float dy = e.getRawY() - initTouchY;
                    if (!dragging && (Math.abs(dx) > 15 || Math.abs(dy) > 15)) {
                        dragging = true;
                        edgeMode = false; edgeSide = 0;
                        handler.removeCallbacks(longPressRunnable); // 移动则取消长按
                        hideMenu();
                        hideBubbleImmediately(); // 拖拽时收起气泡，防止向上遮挡阻碍视线
                        SoundManager.getInstance(PetFloatingService.this).play("drag");
                        play("drag_smooth", true, null);
                    }
                    if (dragging) {
                        long nowDrag = System.currentTimeMillis();
                        if (nowDrag - lastDragUpdateMs >= 16) {
                            params.x = initX + (int) dx;
                            int newY = initY + (int) dy;
                            // 允许人物一直推到屏幕顶端（但保证墨墨头顶留在屏幕内，不滑入屏幕外部消失）
                            int minY = -dp(15);
                            int maxY = displayMetrics.heightPixels - dp(60);
                            params.y = Math.max(minY, Math.min(newY, maxY));
                            windowManager.updateViewLayout(petContainer, params);
                            lastDragUpdateMs = nowDrag;
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    long dur = System.currentTimeMillis() - touchDownTime;

                    if (hasTriggeredLongPress) {
                        return true; // 长按事件已消费
                    }

                    if (dragging) {
                        dragging = false;
                        lastInteractMs = System.currentTimeMillis();
                        int edgeThreshold = dp(28);
                        boolean nearLeft = params.x <= edgeThreshold;
                        boolean nearRight = params.x + petContainer.getWidth() >= displayMetrics.widthPixels - edgeThreshold;
                        if ((nearLeft || nearRight) && actionMap.containsKey("edge_left") && actionMap.containsKey("edge_right")) {
                            edgeMode = true;
                            edgeSide = nearLeft ? -1 : 1;
                            snapToEdge();
                            SoundManager.getInstance(PetFloatingService.this).play("snap");
                            play(edgeSide < 0 ? "edge_left" : "edge_right", true, null);
                            showBubble(edgeSide < 0 ? "贴到左边啦。这里也能陪你。" : "贴到右边啦。团子说这里视野不错。", 2600);
                        } else {
                            edgeMode = false;
                            edgeSide = 0;
                            SoundManager.getInstance(PetFloatingService.this).play("tap");
                            playOnce("curious", new Runnable() {
                            @Override public void run() { resumeCurrentMode(); }
                            });
                        }
                    } else if (dur < 300) {
                        // 如果菜单或环形按钮正开着，点击墨墨则收起菜单
                        if (menuLayout.getVisibility() == View.VISIBLE || circleOverlay.getVisibility() == View.VISIBLE) {
                            hideMenu();
                            tapCount = 0;
                            handler.removeCallbacks(tapTimeoutRunnable);
                            return true;
                        }

                        handler.removeCallbacks(tapTimeoutRunnable);
                        tapCount++;

                        // 只有在计时运行/暂停状态下，三击才触发暂停/继续
                        if (tapCount == 3) {
                            tapCount = 0;
                            if (timerRunning) {
                                toggleTimerPause();
                            } else {
                                // 非计时状态下连击3下当成双击处理
                                onDoubleTap();
                            }
                        } else {
                            handler.postDelayed(tapTimeoutRunnable, 320);
                        }
                    }
                    return true;
            }
            return false;
        }
    };

    private void onSingleTap() {
        lastInteractMs = System.currentTimeMillis();

        // 如果当前正弹着防沉迷超时提醒气泡，点击墨墨立刻收起该提醒
        if (isWarningBubbleActive) {
            isWarningBubbleActive = false;
            hideBubbleImmediately();
            SoundManager.getInstance(this).play("tap");
            showBubble("好啦，知道你看到提醒了。那快休息下吧~ 🐱", 2200);
            return;
        }

        // 贴边后点击只触发互动，不退出贴边；必须拖离边缘才解除
        if (edgeMode) {
            SoundManager.getInstance(this).play("tap");
            showBubble(edgeSide < 0 ? "嗯？贴着边也能陪你。" : "团子说，右边的位置不错。", 2400);
            play(edgeSide < 0 ? "edge_left" : "edge_right", true, null);
            snapToEdge();
            return;
        }
        resumeCurrentMode();
        if (currentMode == Mode.STUDY) {
            SoundManager.getInstance(this).play("tap");
            String quote = QUOTES_STUDY[random.nextInt(QUOTES_STUDY.length)];
            showBubble(quote, 2800);
            playOnce("curious", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        } else if (currentMode == Mode.LEISURE) {
            SoundManager.getInstance(this).play("cat");
            String quote = QUOTES_LEISURE[random.nextInt(QUOTES_LEISURE.length)];
            showBubble(quote, 2800);
            playOnce("happy", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        } else {
            String[] tapPool = {"click","happy","hug","drink","cat","angry"};
            String act = tapPool[random.nextInt(tapPool.length)];
            if ("cat".equals(act) || "hug".equals(act)) {
                SoundManager.getInstance(this).play("cat");
            } else {
                SoundManager.getInstance(this).play("tap");
            }
            showBubble(QUOTES_NORMAL[random.nextInt(QUOTES_NORMAL.length)], 2800);
            playOnce(act, new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        }
    }

    private void onDoubleTap() {
        lastInteractMs = System.currentTimeMillis();
        SoundManager.getInstance(this).play("size");
        sizeIdx = (sizeIdx + 1) % SIZES_DP.length;
        int sz = dp(SIZES_DP[sizeIdx]);
        android.view.ViewGroup.LayoutParams lp = petImageView.getLayoutParams();
        if (lp != null) {
            lp.width = sz;
            lp.height = sz;
            petImageView.setLayoutParams(lp);
        }
        if (bubbleView != null) {
            // bubbleView 不受体型缩放直接限制，独立按屏幕计算自适应宽度
        }
        if (edgeMode) {
            snapToEdge();
        } else {
            windowManager.updateViewLayout(petContainer, params);
        }

        String desc;
        switch (sizeIdx) {
            case 0: desc = "迷你模式"; break;
            case 1: desc = "小巧模式"; break;
            case 2: desc = "标准模式"; break;
            default: desc = "大只墨墨"; break;
        }
        showBubble("体型切换: " + desc + " 🐱", 2000);
        if (edgeMode) {
            play(edgeSide < 0 ? "edge_left" : "edge_right", true, null);
        } else {
            playOnce("happy", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        }
    }

    private void onLongPress() {
        lastInteractMs = System.currentTimeMillis();
        if (circleOverlay.getVisibility() == View.VISIBLE || menuLayout.getVisibility() == View.VISIBLE) {
            hideMenu();
        } else {
            SoundManager.getInstance(this).play("menu");
            showMainMenu();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  模式切换菜单系统（环形主菜单 + 卡片子菜单）
    // ═══════════════════════════════════════════════════════════════

    private void showMainMenu() {
        if (circleOverlay == null) return;
        circleOverlay.removeAllViews();
        menuLayout.setVisibility(View.GONE);

        int sz = dp(SIZES_DP[sizeIdx]);
        // 针对不同体型自适应按键尺寸与边距，确保 100% 完整落在人物图层四角内侧
        int btnSz = (sizeIdx == 0) ? dp(30) : (sizeIdx == 1 ? dp(36) : dp(40));
        int pad = (sizeIdx == 0) ? dp(2) : dp(4);

        // 紧凑四角环形配置（左上、右上、右下、左下），完全在容器尺寸内部，不受父级边界裁切
        class CornerBtnConfig {
            String label;
            int left;
            int top;
            int bgColor;
            int textColor;
            View.OnClickListener listener;
            CornerBtnConfig(String l, int lf, int tp, int bg, int tc, View.OnClickListener lis) {
                label = l; left = lf; top = tp; bgColor = bg; textColor = tc; listener = lis;
            }
        }

        List<CornerBtnConfig> list = new ArrayList<>();
        // 1. 专注（左上）
        list.add(new CornerBtnConfig("专注", pad, pad, 0xEE1E293B, 0xFF6EE7B7, new View.OnClickListener() {
            @Override public void onClick(View v) {
                circleOverlay.setVisibility(View.GONE);
                showTimerSelection(Mode.STUDY);
            }
        }));

        // 2. 休闲（右上）
        list.add(new CornerBtnConfig("休闲", sz - pad - btnSz, pad, 0xEE1E293B, 0xFFFCD34D, new View.OnClickListener() {
            @Override public void onClick(View v) {
                circleOverlay.setVisibility(View.GONE);
                showTimerSelection(Mode.LEISURE);
            }
        }));

        // 3. 音乐（右下）
        list.add(new CornerBtnConfig("音乐", sz - pad - btnSz, sz - pad - btnSz, 0xEE1E293B, 0xFFC084FC, new View.OnClickListener() {
            @Override public void onClick(View v) {
                circleOverlay.setVisibility(View.GONE);
                showMusicMenu();
            }
        }));

        // 4. 统计（左下）
        list.add(new CornerBtnConfig("统计", pad, sz - pad - btnSz, 0xEE1E293B, 0xFF38BDF8, new View.OnClickListener() {
            @Override public void onClick(View v) {
                hideMenu();
                Intent intent = new Intent(PetFloatingService.this, StatsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }));

        // 5. 守护设置（正上方）
        list.add(new CornerBtnConfig("守护", sz / 2 - btnSz / 2, pad, 0xEE1E293B, 0xFFF472B6, new View.OnClickListener() {
            @Override public void onClick(View v) {
                hideMenu();
                Intent intent = new Intent(PetFloatingService.this, MonitorSettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }));

        // 6. 若正在计时或在特殊模式，增加【暂停/继续】与【结束】按钮
        if (currentMode != Mode.NORMAL || timerRunning) {
            String pauseLabel = timerPaused ? "继续" : "暂停";
            int pauseColor = timerPaused ? 0xFF6EE7B7 : 0xFFFCD34D;
            list.add(new CornerBtnConfig(pauseLabel, sz / 2 - btnSz - dp(3), sz - pad - btnSz, 0xEE1E293B, pauseColor, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleTimerPause();
                    hideMenu();
                }
            }));

            list.add(new CornerBtnConfig("结束", sz / 2 + dp(3), sz - pad - btnSz, 0xEE450A0A, 0xFFFCA5A5, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    switchToNormalMode();
                    hideMenu();
                }
            }));
        }

        for (CornerBtnConfig cfg : list) {
            Button b = new Button(this);
            b.setText(cfg.label);
            b.setTextSize(sizeIdx == 0 ? 9 : (sizeIdx == 1 ? 11 : 12));
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setTextColor(cfg.textColor);
            b.setPadding(0, 0, 0, 0);
            b.setGravity(Gravity.CENTER);

            // 纯净极简圆形背景
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(cfg.bgColor);
            gd.setStroke(dp(1), 0x55FFFFFF);
            b.setBackground(gd);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setElevation(dp(6));
            }

            b.setOnClickListener(cfg.listener);

            android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(btnSz, btnSz);
            blp.leftMargin = cfg.left;
            blp.topMargin = cfg.top;
            circleOverlay.addView(b, blp);
        }

        circleOverlay.setVisibility(View.VISIBLE);
    }

    private void showTimerSelection(final Mode targetMode) {
        menuLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText(targetMode == Mode.STUDY ? "专注计时方式" : "休闲计时方式");
        title.setTextSize(12);
        title.setTextColor(0xFF888899);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        menuLayout.addView(title);

        // 正计时
        Button countUpBtn = createMenuButton("正向计时", 0xFF23352B);
        countUpBtn.setTextColor(0xFF88FFB0);
        countUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startModeWithCountUp(targetMode);
                hideMenu();
            }
        });
        menuLayout.addView(countUpBtn);

        // 倒计时快捷选项横排
        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER);
        countRow.setPadding(0, dp(4), 0, dp(4));

        int[] mins = {15, 25, 45, 60};
        for (final int m : mins) {
            Button btn = new Button(this);
            btn.setText(m + "分");
            btn.setTextSize(11);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF2E3342);
            btn.setPadding(dp(8), dp(4), dp(8), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            );
            lp.setMargins(dp(2), 0, dp(2), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startModeWithCountDown(targetMode, m * 60);
                    hideMenu();
                }
            });
            countRow.addView(btn);
        }
        menuLayout.addView(countRow);

        // 不计时直接进入模式
        Button noTimerBtn = createMenuButton("不开启计时 (仅切换动作)", 0xFF282830);
        noTimerBtn.setTextSize(11);
        noTimerBtn.setTextColor(0xFFAAAAAA);
        noTimerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startModeWithoutTimer(targetMode);
                hideMenu();
            }
        });
        menuLayout.addView(noTimerBtn);

        menuLayout.setVisibility(View.VISIBLE);
        ensureMenuInsideScreen();
    }

    // 确保展开子菜单后整块悬浮窗不超出屏幕左右边缘
    private void ensureMenuInsideScreen() {
        if (petContainer == null || windowManager == null) return;
        petContainer.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int w = petContainer.getMeasuredWidth();
        if (w <= 0) w = petContainer.getWidth();
        if (w <= 0) w = dp(220); // 兜底按最大卡片宽估算

        int screenW = displayMetrics.widthPixels;
        int minMargin = dp(8);
        boolean changed = false;

        // 若右侧超出屏幕边界
        if (params.x + w > screenW - minMargin) {
            params.x = Math.max(minMargin, screenW - w - minMargin);
            changed = true;
        }
        // 若左侧跑出屏幕外（比如贴在左边缘时）
        if (params.x < minMargin) {
            params.x = minMargin;
            changed = true;
        }

        if (changed) {
            try {
                windowManager.updateViewLayout(petContainer, params);
            } catch (Exception ignored) {}
        }
    }

    private void showMusicMenu() {
        menuLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText("音乐播放控制");
        title.setTextSize(12);
        title.setTextColor(0xFFD8B4FE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        menuLayout.addView(title);

        // 1. 打开 TuneFreeNext App
        Button launchAppBtn = createMenuButton("打开 TuneFreeNext", 0xFF352B42);
        launchAppBtn.setTextColor(0xFFE9D5FF);
        launchAppBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                hideMenu();
                launchTuneFreeNext();
            }
        });
        menuLayout.addView(launchAppBtn);

        // 2. 媒体快捷控制横排 (上一首 | 播放/暂停 | 下一首)
        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER);
        ctrlRow.setPadding(0, dp(4), 0, dp(4));

        Button prevBtn = new Button(this);
        prevBtn.setText("上一首");
        prevBtn.setTextSize(11);
        prevBtn.setTextColor(0xFFFFFFFF);
        prevBtn.setBackgroundColor(0xFF2E3342);
        prevBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lpPrev = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lpPrev.setMargins(dp(2), 0, dp(2), 0);
        prevBtn.setLayoutParams(lpPrev);
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                showBubble("切到上一首啦 🎶", 1800);
            }
        });
        ctrlRow.addView(prevBtn);

        Button toggleBtn = new Button(this);
        toggleBtn.setText("播放/暂停");
        toggleBtn.setTextSize(11);
        toggleBtn.setTextColor(0xFF88FFB0);
        toggleBtn.setBackgroundColor(0xFF23352B);
        toggleBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lpToggle = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
        lpToggle.setMargins(dp(2), 0, dp(2), 0);
        toggleBtn.setLayoutParams(lpToggle);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                showBubble("音乐播放 / 暂停 🎵", 1800);
            }
        });
        ctrlRow.addView(toggleBtn);

        Button nextBtn = new Button(this);
        nextBtn.setText("下一首");
        nextBtn.setTextSize(11);
        nextBtn.setTextColor(0xFFFFFFFF);
        nextBtn.setBackgroundColor(0xFF2E3342);
        nextBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lpNext = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lpNext.setMargins(dp(2), 0, dp(2), 0);
        nextBtn.setLayoutParams(lpNext);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
                showBubble("切到下一首啦 🎶", 1800);
            }
        });
        ctrlRow.addView(nextBtn);

        menuLayout.addView(ctrlRow);

        // 3. 返回主菜单
        Button backBtn = createMenuButton("返回", 0xFF282830);
        backBtn.setTextSize(11);
        backBtn.setTextColor(0xFFAAAAAA);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showMainMenu();
            }
        });
        menuLayout.addView(backBtn);

        menuLayout.setVisibility(View.VISIBLE);
        ensureMenuInsideScreen();
    }

    private void launchTuneFreeNext() {
        String pkg = "com.sayqz.tunefreenext.app";
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            showBubble("为你打开 TuneFreeNext 啦 🎧", 2200);
        } else {
            // 备用：尝试通过 intent filter 启动或提示未找到
            try {
                Intent custom = new Intent(Intent.ACTION_MAIN);
                custom.setClassName(pkg, pkg + ".MainActivity");
                custom.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(custom);
                showBubble("为你打开 TuneFreeNext 啦 🎧", 2200);
            } catch (Exception e) {
                showBubble("未找到 TuneFreeNext 播放器应用哦 🐱", 2500);
            }
        }
    }

    private void sendMediaKeyEvent(int keyCode) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            long now = android.os.SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0));
        }
    }

    private Button createMenuButton(String text, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(bgColor);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void hideMenu() {
        if (circleOverlay != null) {
            circleOverlay.setVisibility(View.GONE);
            circleOverlay.removeAllViews();
        }
        if (menuLayout != null) {
            menuLayout.setVisibility(View.GONE);
            menuLayout.removeAllViews();
        }
        if (edgeMode) {
            snapToEdge();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  模式执行与计时器核心
    // ═══════════════════════════════════════════════════════════════

    private void startModeWithCountUp(Mode mode) {
        currentMode = mode;
        isCountDown = false;
        timerElapsedSec = 0;
        startTimer();
        applyCurrentModeAction();
        showBubble(mode == Mode.STUDY ? "开启学习正计时！团子陪你一起专注。" : "开启悠闲时光~ 累了就好好歇歇吧。", 2500);
    }

    private void startModeWithCountDown(Mode mode, int totalSec) {
        currentMode = mode;
        isCountDown = true;
        timerRemainingSec = totalSec;
        startTimer();
        applyCurrentModeAction();
        int min = totalSec / 60;
        showBubble(mode == Mode.STUDY ? ("番茄专注开始！目标 " + min + " 分钟，冲呀~") : ("休闲倒计时 " + min + " 分钟，尽情放松！"), 2500);
    }

    private void startModeWithoutTimer(Mode mode) {
        currentMode = mode;
        stopTimer();
        timerView.setVisibility(View.GONE);
        applyCurrentModeAction();
        showBubble(mode == Mode.STUDY ? "进入学习模式（未开启计时）" : "进入休闲模式（未开启计时）", 2000);
    }

    private void switchToNormalMode() {
        boolean wasTiming = timerRunning || currentMode != Mode.NORMAL;
        currentMode = Mode.NORMAL;
        stopTimer();
        timerView.setVisibility(View.GONE);
        if (wasTiming) {
            SoundManager.getInstance(this).play("finish");
            // 手动结束计时：清晰两连震（300ms, 250ms）
            vibratePattern(
                new long[]{0, 300, 150, 250},
                new int[]{0, 255, 0, 255}
            );
        }
        play("idle", true, null);
        showBubble("已恢复自由待机模式~ (。•̀ᴗ-)✧", 2000);
    }

    private void applyCurrentModeAction() {
        if (currentMode == Mode.STUDY) {
            play("study", true, null);
        } else if (currentMode == Mode.LEISURE) {
            play("leisure", true, null);
        } else {
            play("idle", true, null);
        }
    }

    private void resumeCurrentMode() {
        applyCurrentModeAction();
    }

    private void startTimer() {
        timerRunning = true;
        timerPaused = false;
        timerView.setVisibility(View.VISIBLE);
        SoundManager.getInstance(this).play("start");
        handler.removeCallbacks(timerTickRunnable);
        handler.removeCallbacks(modeBubbleTickRunnable);
        updateTimerDisplay();
        handler.postDelayed(timerTickRunnable, 1000);
        handler.postDelayed(modeBubbleTickRunnable, 45000);
    }
    private void stopTimer() {
        timerRunning = false;
        timerPaused = false;
        handler.removeCallbacks(timerTickRunnable);
        handler.removeCallbacks(modeBubbleTickRunnable);
    }

    private void toggleTimerPause() {
        if (!timerRunning) return;
        timerPaused = !timerPaused;
        SoundManager.getInstance(this).play("pause");
        if (timerPaused) {
            handler.removeCallbacks(timerTickRunnable);
            handler.removeCallbacks(modeBubbleTickRunnable);
            updateTimerDisplay();
            play("daze", true, null);
            showBubble("⏸ 计时已暂停，稍后再继续吧~", 2200);
        } else {
            handler.removeCallbacks(timerTickRunnable);
            handler.removeCallbacks(modeBubbleTickRunnable);
            updateTimerDisplay();
            handler.postDelayed(timerTickRunnable, 1000);
            handler.postDelayed(modeBubbleTickRunnable, 45000);
            applyCurrentModeAction();
            showBubble("▶ 计时继续！墨墨陪着你。", 2000);
        }
    }
    // 学习/休闲计时期间每45秒陪伴一句，避免频繁打断
    private final Runnable modeBubbleTickRunnable = new Runnable() {
        @Override public void run() {
            if (!timerRunning || currentMode == Mode.NORMAL) return;
            String[] pool = currentMode == Mode.STUDY ? QUOTES_STUDY : QUOTES_LEISURE;
            showBubble(pool[random.nextInt(pool.length)], 3200);
            handler.postDelayed(this, 45000);
        }
    };

    private final Runnable timerTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;

            // 实时记录陪伴时长（按秒累计入库）
            if (!timerPaused && (currentMode == Mode.STUDY || currentMode == Mode.LEISURE)) {
                StatsManager.recordDuration(PetFloatingService.this, currentMode == Mode.STUDY, 1);
            }

            if (isCountDown) {
                if (timerRemainingSec > 0) {
                    timerRemainingSec--;
                    updateTimerDisplay();
                    handler.postDelayed(this, 1000);
                } else {
                    onCountdownFinished();
                }
            } else {
                timerElapsedSec++;
                updateTimerDisplay();
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void updateTimerDisplay() {
        int sec = isCountDown ? timerRemainingSec : timerElapsedSec;
        int m = sec / 60;
        int s = sec % 60;
        String prefix = currentMode == Mode.STUDY ? "🎓 " : "☕ ";
        if (timerPaused) {
            timerView.setText(String.format(Locale.getDefault(), "%s%02d:%02d ⏸", prefix, m, s));
            timerView.setTextColor(0xFFFFB703); // 暂停时显示柔和暖橙黄
        } else {
            timerView.setText(String.format(Locale.getDefault(), "%s%02d:%02d", prefix, m, s));
            if (isCountDown && sec <= 60) {
                timerView.setTextColor(0xFFFF6666); // 最后1分钟变红微警示
            } else {
                timerView.setTextColor(currentMode == Mode.STUDY ? 0xFF00FFA6 : 0xFFFFD166);
            }
        }
    }

    private void onCountdownFinished() {
        stopTimer();
        timerView.setVisibility(View.GONE);
        SoundManager.getInstance(this).play("finish");
        // 倒计时自然结束：明显饱满的强劲两连跳 (350ms, 400ms)
        vibratePattern(
            new long[]{0, 350, 150, 400},
            new int[]{0, 255, 0, 255}
        );
        String msg = currentMode == Mode.STUDY
            ? "叮！专注时间到啦！很棒哦，站起来喝口水伸个懒腰吧 🎉"
            : "叮！休闲时间结束啦，感觉电量充满了吗？(。•̀ᴗ-)✧";
        showBubble(msg, 4000);
        playOnce("happy", new Runnable() {
            @Override public void run() {
                switchToNormalMode();
            }
        });
    }

    // ── 气泡智能定位与展示 ─────────────────────────────────────────────────────
    private void showBubble(String text, int ms) {
        if (bubbleView == null || windowManager == null) return;
        isWarningBubbleActive = false;
        bubbleView.setBackgroundResource(R.drawable.bg_bubble);
        bubbleView.setTextColor(0xFFFFFFFF);
        bubbleView.setText(text);
        displayBubbleSmartly();
        handler.removeCallbacks(hideBubble);
        if (ms > 0) {
            handler.postDelayed(hideBubble, ms);
        }
    }

    // 专属防沉迷强调提醒气泡（强调边框与高亮底色，默认不自动消失，直到用户点击墨墨）
    private void showWarningBubble(String text, int stage) {
        if (bubbleView == null || windowManager == null) return;
        isWarningBubbleActive = true;
        handler.removeCallbacks(hideBubble); // 取消自动隐藏定时器，常驻显示

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(dp(14));

        if (stage == 1) {
            // 初次超时：优雅沉稳的琥珀橙渐变/强调边框
            gd.setColor(0xF02A241F); // 极深暖褐黑底
            gd.setStroke(dp(2), 0xFFF59E0B); // 亮暖橙边框
            bubbleView.setTextColor(0xFFFEF3C7);
        } else {
            // 严重超时：警戒珊瑚赤红强调色
            gd.setColor(0xF02D1E22); // 极深墨红底
            gd.setStroke(dp(2), 0xFFF43F5E); // 警戒霓虹玫红边框
            bubbleView.setTextColor(0xFFFFE4E6);
        }

        bubbleView.setBackground(gd);
        bubbleView.setText(text);
        displayBubbleSmartly();
    }

    private void displayBubbleSmartly() {
        if (bubbleView == null || windowManager == null || petContainer == null) return;

        int screenW = displayMetrics.widthPixels;
        int screenH = displayMetrics.heightPixels;
        int margin = dp(12);
        int maxW = Math.min(dp(260), screenW - margin * 2);
        bubbleView.setMaxWidth(maxW);

        // 测量气泡尺寸
        bubbleView.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int bubbleW = bubbleView.getMeasuredWidth();
        int bubbleH = bubbleView.getMeasuredHeight();
        int petSz = dp(SIZES_DP[sizeIdx]);

        // 水平方向：以人物中心为基准居中对齐，边缘不溢出屏幕
        int petCenterX = params.x + petSz / 2;
        int targetX = petCenterX - bubbleW / 2;
        targetX = Math.max(margin, Math.min(targetX, screenW - bubbleW - margin));

        // 垂直方向：优先放置于人物头顶（距离人物 8dp）
        // 若人物太靠近屏幕顶部（头顶放不下），智能翻转至人物脚下（下方展示）
        int targetY;
        int topCandidateY = params.y - bubbleH - dp(8);
        int safeTopY = dp(28); // 避开状态栏顶部区域
        if (topCandidateY >= safeTopY) {
            targetY = topCandidateY;
        } else {
            // 头顶放不下，智能放置在人物下方
            targetY = params.y + petSz + dp(8);
            // 确保底部不超出屏幕
            if (targetY + bubbleH > screenH - margin) {
                targetY = screenH - bubbleH - margin;
            }
        }

        bubbleParams.x = targetX;
        bubbleParams.y = targetY;
        bubbleView.setVisibility(View.VISIBLE);

        try {
            if (bubbleView.isAttachedToWindow()) {
                windowManager.updateViewLayout(bubbleView, bubbleParams);
            } else {
                windowManager.addView(bubbleView, bubbleParams);
            }
        } catch (Exception ignored) {}
    }

    private void hideBubbleImmediately() {
        isWarningBubbleActive = false;
        handler.removeCallbacks(hideBubble);
        if (bubbleView != null && windowManager != null && bubbleView.isAttachedToWindow()) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
        }
    }

    private final Runnable hideBubble = new Runnable() {
        @Override public void run() {
            hideBubbleImmediately();
        }
    };
    private void snapToEdge() {
        if (!edgeMode || petContainer == null || windowManager == null) return;
        int sz = dp(SIZES_DP[sizeIdx]);
        if (edgeSide < 0) {
            params.x = 0;
        } else {
            params.x = Math.max(0, displayMetrics.widthPixels - sz);
        }
        try { windowManager.updateViewLayout(petContainer, params); } catch (Exception ignored) {}
    }

    // 动作与台词适配
    private String quoteForAction(String action) {
        if (action == null) return null;
        if (action.contains("drink")) return "先喝口水，补充一点能量再继续。";
        if (action.contains("sleep")) return "团子都睡着了……你也可以安心休息一会儿。 zZz";
        if (action.contains("hug")) return "抱一下，今天也辛苦啦。";
        if (action.contains("happy")) return "诶，这次做得不错嘛。把这份开心收好。";
        if (action.contains("angry")) return "生气可以，别把自己困在生气里。深呼吸。";
        if (action.contains("daze")) return "发会儿呆，脑袋整理好再出发。";
        if (action.contains("curious")) return "让我看看……这里是不是有个小线索？";
        if (action.contains("cat")) return "团子今天也很可爱。嗯，比你乖一点点。";
        if (action.contains("blink")) return "眨眨眼，记得让眼睛也休息一下。";
        return null;
    }
    private void maybeShowActionBubble(String action) {
        String q = quoteForAction(action);
        if (q != null && currentMode == Mode.NORMAL) showBubble(q, 2600);
    }

    // ── 动画控制 ─────────────────────────────────────────────────────
    private void play(String action, boolean loop, Runnable callback) {
        if (!actionMap.containsKey(action)) {
            if (callback != null) callback.run();
            return;
        }
        currentAction = action;
        currentFrameIdx = 0;
        if (!loop && !"idle".equals(action)) maybeShowActionBubble(action);
        isOneShot = !loop;
        oneShotCallback = callback;
    }

    private void playOnce(String action, Runnable callback) {
        play(action, false, callback);
    }

    private boolean animLoopRunning = false;
    private void startAnimLoop() {
        if (animLoopRunning) return;
        animLoopRunning = true;
        handler.post(animTick);
    }

    private final Runnable animTick = new Runnable() {
        @Override public void run() {
            if (petImageView == null) { animLoopRunning = false; return; }

            ActionItem act = actionMap.get(currentAction);
            int delay = 40;

            if (act != null && !act.frames.isEmpty()) {
                if (currentFrameIdx >= act.frames.size()) {
                    if (isOneShot) {
                        isOneShot = false;
                        Runnable cb = oneShotCallback;
                        oneShotCallback = null;
                        if (cb != null) cb.run();
                        else if (act.nextAction != null && actionMap.containsKey(act.nextAction))
                            play(act.nextAction, true, null);
                        else
                            resumeCurrentMode();
                        act = actionMap.get(currentAction);
                        currentFrameIdx = 0;
                    } else {
                        currentFrameIdx = 0;
                    }
                }
                if (act != null && currentFrameIdx < act.frames.size()) {
                    FrameItem fi = act.frames.get(currentFrameIdx);
                    Bitmap bmp = getOrLoadBitmap(fi.filePath);
                    if (bmp != null) {
                        petImageView.setImageBitmap(bmp);
                    }
                    delay = Math.max(16, fi.durationMs);
                    currentFrameIdx++;
                }
            }
            handler.postDelayed(this, delay);
        }
    };

    // ── 闲置行为循环（每 30 秒）───────────────────────────────────
    private void startBehaviorLoop() {
        handler.postDelayed(behaviorTick, 30000);
    }

    private final Runnable behaviorTick = new Runnable() {
        @Override public void run() {
            // 学习和休闲模式下锁定动作，不进行 30 秒随机切换！
            if (currentMode == Mode.NORMAL && !edgeMode && !isOneShot && !dragging) {
                long idle = System.currentTimeMillis() - lastInteractMs;
                if (idle > 150000) {
                    if (!"sleep_smooth".equals(currentAction)) {
                        play("sleep_smooth", true, null);
                        showBubble("呼……困了，先趴会儿…… zZz", 2500);
                    }
                } else {
                    play(nextDeckAction(), true, null);
                }
            }
            handler.postDelayed(this, 30000);
        }
    };

    // ── 防沉迷应用监控循环（每 5 秒轮询前台应用）─────────────────────
    private void startAppMonitorLoop() {
        handler.postDelayed(appMonitorTick, 5000);
    }

    private final Runnable appMonitorTick = new Runnable() {
        @Override
        public void run() {
            checkForegroundAppUsage();
            handler.postDelayed(this, 5000);
        }
    };

    private void checkForegroundAppUsage() {
        // 如果未开启防沉迷，或者处于学习/休闲计时模式，不打扰
        if (!AppMonitorManager.isMonitorEnabled(this)) return;
        if (!AppMonitorManager.hasUsageStatsPermission(this)) return;

        try {
            android.app.usage.UsageStatsManager usm = 
                (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;

            long now = System.currentTimeMillis();
            // 查询最近 15 秒内的前台切换事件
            android.app.usage.UsageEvents events = usm.queryEvents(now - 15000, now);
            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            String currentForeground = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentForeground = event.getPackageName();
                }
            }

            // 如果最近 15 秒内无切前台事件，尝试从最近使用的任务列表兜底
            if (currentForeground == null && lastForegroundPkg != null) {
                currentForeground = lastForegroundPkg;
            }

            if (currentForeground == null) return;

            // 如果该应用不属于监控范围（如桌面启动器、系统UI、桌宠自己）
            if (!AppMonitorManager.shouldMonitor(this, currentForeground)) {
                // 如果切到了非监控应用，且超过 3 分钟，则重置上个应用的单次计时
                if (lastForegroundPkg != null && (now - currentPkgStartTime > 180000)) {
                    lastForegroundPkg = null;
                    warningStage = 0;
                }
                return;
            }

            // 如果切换了受监控的应用
            if (!currentForeground.equals(lastForegroundPkg)) {
                lastForegroundPkg = currentForeground;
                currentPkgStartTime = now;
                lastWarningTime = 0;
                warningStage = 0;
                return;
            }

            // 同一个受监控应用在前台连续运行的时长
            long continuousMs = now - currentPkgStartTime;
            int continuousMin = (int) (continuousMs / 60000);
            int limitMin = AppMonitorManager.getLimitMinutes(this);

            // 获取应用名称
            String appName = getAppName(currentForeground);

            // 阶段一：初次达到阈值提醒（例如 30 分钟）
            if (continuousMin >= limitMin && warningStage == 0) {
                warningStage = 1;
                lastWarningTime = now;
                triggerUsageWarning(appName, continuousMin, 1);
            }
            // 阶段二：严重超时提醒（初次提醒后继续使用 15 分钟）
            else if (warningStage == 1 && continuousMin >= limitMin + 15 && (now - lastWarningTime > 600000)) {
                warningStage = 2;
                lastWarningTime = now;
                triggerUsageWarning(appName, continuousMin, 2);
            }
        } catch (Exception ignored) {}
    }

    private String getAppName(String pkg) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return "这个 App";
        }
    }

    private void triggerUsageWarning(String appName, int minutes, int stage) {
        SoundManager.getInstance(this).play("alert");
        if (stage == 1) {
            // 初次提醒：饱满有力的双脉冲震动 (400ms, 400ms)
            vibratePattern(
                new long[]{0, 400, 200, 400},
                new int[]{0, 255, 0, 255}
            );
            // 轻度提醒：curious / daze
            playOnce("curious", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
            String[] quotes = {
                "⚠️「" + appName + "」你已经连着看了 " + minutes + " 分钟啦。眼睛不酸吗？",
                "⚠️「" + appName + "」玩挺久了哦。团子打了个哈欠，提醒你稍微揉揉眼~",
                "⚠️ 注意力在「" + appName + "」上停了 " + minutes + " 分钟了。放下手机喝口水吧？"
            };
            showWarningBubble(quotes[random.nextInt(quotes.length)], 1);
        } else {
            // 严重超时：强烈三连急促警示重震 (450ms, 450ms, 600ms)
            vibratePattern(
                new long[]{0, 450, 150, 450, 150, 600},
                new int[]{0, 255, 0, 255, 0, 255}
            );
            // 严重超时提醒：angry / daze
            playOnce("angry", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
            String[] severeQuotes = {
                "🚨 喂喂！「" + appName + "」都连续刷了 " + minutes + " 分钟了！脑子不晕吗？",
                "🚨 说好只玩一会儿的呢？团子都看不过去了，快退出来休息！🐱",
                "🚨 停一下啦！「" + appName + "」严重超长待机了。站起来活动肩颈！"
            };
            showWarningBubble(severeQuotes[random.nextInt(severeQuotes.length)], 2);
        }
    }

    private void vibratePattern(long[] pattern, int[] amplitudes) {
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            android.media.AudioAttributes audioAttributes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.os.VibrationEffect effect;
                if (amplitudes != null && vibrator.hasAmplitudeControl()) {
                    effect = android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1);
                } else {
                    effect = android.os.VibrationEffect.createWaveform(pattern, -1);
                }
                if (audioAttributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    vibrator.vibrate(effect, audioAttributes);
                } else {
                    vibrator.vibrate(effect);
                }
            } else {
                if (audioAttributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    vibrator.vibrate(pattern, -1, audioAttributes);
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleRestart();
    }
}