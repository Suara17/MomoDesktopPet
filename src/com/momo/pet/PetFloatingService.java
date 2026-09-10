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
    private TextView timerView;
    private LinearLayout menuLayout;
    private WindowManager.LayoutParams params;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // 动作帧数据
    public static class FrameItem {
        Bitmap bitmap; int durationMs;
        FrameItem(Bitmap b, int d) { bitmap = b; durationMs = d; }
    }
    public static class ActionItem {
        boolean loop; String nextAction;
        List<FrameItem> frames = new ArrayList<>();
    }
    private final Map<String, ActionItem> actionMap = new HashMap<>();

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
    private long lastTapTime = 0;
    private boolean hasTriggeredLongPress = false;

    // 尺寸档位
    private final int[] SIZES_DP = {120, 160, 200};
    private int sizeIdx = 1;

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

            Map<String, Bitmap> cache = new HashMap<>();

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

                    Bitmap bmp = cache.get(file);
                    if (bmp == null) {
                        try {
                            InputStream imgIs = am.open(file);
BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            opts.inSampleSize = 1; // 保留原始分辨率，避免人物边缘和像素细节变糊
                            opts.inScaled = false;   // 禁止按设备密度再次缩放，保持素材清晰度
                            bmp = BitmapFactory.decodeStream(imgIs, null, opts);
                            imgIs.close();
                            if (bmp != null) cache.put(file, bmp);
                        } catch (Exception ignored) {}
                    }
                    if (bmp != null) item.frames.add(new FrameItem(bmp, dur));
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

        // 2. 头顶气泡 (Bubble View)
        bubbleView = new TextView(this);
        bubbleView.setBackgroundResource(R.drawable.bg_bubble);
        bubbleView.setTextColor(0xFFFFFFFF);
        bubbleView.setTextSize(13);
        bubbleView.setMaxWidth(dp(210));
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setLineSpacing(dp(2), 1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bubbleView.setElevation(dp(6));
        }
        bubbleView.setVisibility(View.GONE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        bp.bottomMargin = dp(4);
        bubbleView.setLayoutParams(bp);
        container.addView(bubbleView);

        // 3. 角色动画主图
        petImageView = new ImageView(this);
        int sz = dp(SIZES_DP[sizeIdx]);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(sz, sz);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        petImageView.setLayoutParams(ip);
        petImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        container.addView(petImageView);

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
                        play("drag_smooth", true, null);
                        showBubble("诶诶？！怎么又拎我领子……放我下来！", 1600);
                    }
                    if (dragging) {
                        long nowDrag = System.currentTimeMillis();
                        if (nowDrag - lastDragUpdateMs >= 16) {
                            params.x = initX + (int) dx;
                            params.y = initY + (int) dy;
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
                        int edgeThreshold = dp(24);
                        boolean nearLeft = params.x <= edgeThreshold;
                        boolean nearRight = params.x + petContainer.getWidth() >= displayMetrics.widthPixels - edgeThreshold;
                        if ((nearLeft || nearRight) && actionMap.containsKey("edge_left") && actionMap.containsKey("edge_right")) {
                            edgeMode = true;
                            edgeSide = nearLeft ? -1 : 1;
                            snapToEdge();
                            play(edgeSide < 0 ? "edge_left" : "edge_right", true, null);
                            showBubble(edgeSide < 0 ? "贴到左边啦。这里也能陪你。" : "贴到右边啦。团子说这里视野不错。", 2600);
                        } else {
                            edgeMode = false;
                            edgeSide = 0;
                            playOnce("curious", new Runnable() {
                            @Override public void run() { resumeCurrentMode(); }
                            });
                        }
                    } else if (dur < 300) {
                        // 如果菜单正开着，点击墨墨则收起菜单
                        if (menuLayout.getVisibility() == View.VISIBLE) {
                            hideMenu();
                            return true;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 400) {
                            onDoubleTap(); lastTapTime = 0;
                        } else {
                            lastTapTime = now;
                            handler.postDelayed(new Runnable() {
                                @Override public void run() {
                                    if (lastTapTime != 0) { onSingleTap(); lastTapTime = 0; }
                                }
                            }, 400);
                        }
                    }
                    return true;
            }
            return false;
        }
    };

    private void onSingleTap() {
        lastInteractMs = System.currentTimeMillis();
        // 贴边后点击只触发互动，不退出贴边；必须拖离边缘才解除
        if (edgeMode) {
            showBubble(edgeSide < 0 ? "嗯？贴着边也能陪你。" : "团子说，右边的位置不错。", 2400);
            play(edgeSide < 0 ? "edge_left" : "edge_right", true, null);
            snapToEdge();
            return;
        }
        resumeCurrentMode();
        if (currentMode == Mode.STUDY) {
            String quote = QUOTES_STUDY[random.nextInt(QUOTES_STUDY.length)];
            showBubble(quote, 2800);
            playOnce("curious", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        } else if (currentMode == Mode.LEISURE) {
            String quote = QUOTES_LEISURE[random.nextInt(QUOTES_LEISURE.length)];
            showBubble(quote, 2800);
            playOnce("happy", new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        } else {
            String[] tapPool = {"click","happy","hug","drink","cat","angry"};
            String act = tapPool[random.nextInt(tapPool.length)];
            showBubble(QUOTES_NORMAL[random.nextInt(QUOTES_NORMAL.length)], 2800);
            playOnce(act, new Runnable() {
                @Override public void run() { resumeCurrentMode(); }
            });
        }
    }

    private void onDoubleTap() {
        lastInteractMs = System.currentTimeMillis();
        sizeIdx = (sizeIdx + 1) % SIZES_DP.length;
        int sz = dp(SIZES_DP[sizeIdx]);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) petImageView.getLayoutParams();
        lp.width = sz; lp.height = sz;
        petImageView.setLayoutParams(lp);
        windowManager.updateViewLayout(petContainer, params);

        String desc = sizeIdx == 0 ? "小巧模式" : sizeIdx == 1 ? "标准模式" : "大只墨墨";
        showBubble("体型切换: " + desc + " 🐱", 2000);
        playOnce("happy", new Runnable() {
            @Override public void run() { resumeCurrentMode(); }
        });
    }

    private void onLongPress() {
        lastInteractMs = System.currentTimeMillis();
        if (menuLayout.getVisibility() == View.VISIBLE) {
            hideMenu();
        } else {
            showMainMenu();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  模式切换菜单系统
    // ═══════════════════════════════════════════════════════════════

    private void showMainMenu() {
        menuLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText("模式切换");
        title.setTextSize(12);
        title.setTextColor(0xFF888899);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        menuLayout.addView(title);

        // 学习模式按钮
        Button studyBtn = createMenuButton("🎓 学习模式", 0xFF2D323E);
        studyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTimerSelection(Mode.STUDY); }
        });
        menuLayout.addView(studyBtn);

        // 休闲模式按钮
        Button leisureBtn = createMenuButton("☕ 休闲模式", 0xFF2D323E);
        leisureBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTimerSelection(Mode.LEISURE); }
        });
        menuLayout.addView(leisureBtn);

        // 若当前处于特殊模式或正在计时，显示退出按钮
        if (currentMode != Mode.NORMAL || timerRunning) {
            Button normalBtn = createMenuButton("⏹️ 结束计时 / 自由待机", 0xFF442D2D);
            normalBtn.setTextColor(0xFFFF8888);
            normalBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    switchToNormalMode();
                    hideMenu();
                }
            });
            menuLayout.addView(normalBtn);
        }

        menuLayout.setVisibility(View.VISIBLE);
    }

    private void showTimerSelection(final Mode targetMode) {
        menuLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText(targetMode == Mode.STUDY ? "🎓 选择学习计时方式" : "☕ 选择休闲计时方式");
        title.setTextSize(12);
        title.setTextColor(0xFF888899);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        menuLayout.addView(title);

        // 正计时
        Button countUpBtn = createMenuButton("⏱️ 正向累计计时 (从 00:00 开始)", 0xFF23352B);
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
        if (menuLayout != null) {
            menuLayout.setVisibility(View.GONE);
            menuLayout.removeAllViews();
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
        currentMode = Mode.NORMAL;
        stopTimer();
        timerView.setVisibility(View.GONE);
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
        timerView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(timerTickRunnable);
        handler.removeCallbacks(modeBubbleTickRunnable);
        updateTimerDisplay();
        handler.postDelayed(timerTickRunnable, 1000);
        handler.postDelayed(modeBubbleTickRunnable, 45000);
    }
    private void stopTimer() {
        timerRunning = false;
        handler.removeCallbacks(timerTickRunnable);
        handler.removeCallbacks(modeBubbleTickRunnable);
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
        timerView.setText(String.format(Locale.getDefault(), "%s%02d:%02d", prefix, m, s));
        if (isCountDown && sec <= 60) {
            timerView.setTextColor(0xFFFF6666); // 最后1分钟变红微警示
        } else {
            timerView.setTextColor(currentMode == Mode.STUDY ? 0xFF00FFA6 : 0xFFFFD166);
        }
    }

    private void onCountdownFinished() {
        stopTimer();
        timerView.setVisibility(View.GONE);
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

    // ── 气泡 ─────────────────────────────────────────────────────
    private void showBubble(String text, int ms) {
        bubbleView.setText(text);
        bubbleView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideBubble);
        handler.postDelayed(hideBubble, ms);
    }
    private final Runnable hideBubble = new Runnable() {
        @Override public void run() {
            if (bubbleView != null) bubbleView.setVisibility(View.GONE);
            // 气泡隐藏会改变 WRAP_CONTENT 容器宽度，重新校正贴边位置
            if (edgeMode) handler.postDelayed(new Runnable() {
                @Override public void run() { snapToEdge(); }
            }, 40);
        }
    };
    private void snapToEdge() {
        if (!edgeMode || petContainer == null || windowManager == null) return;
        petContainer.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = petContainer.getMeasuredWidth();
        if (w <= 0) w = petContainer.getWidth();
        params.x = edgeSide < 0 ? 0 : Math.max(0, displayMetrics.widthPixels - w);
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
                    petImageView.setImageBitmap(fi.bitmap);
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

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleRestart();
    }
}