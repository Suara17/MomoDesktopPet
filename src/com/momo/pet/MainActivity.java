package com.momo.pet;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY_CODE = 1001;

    // 当前选中的底部导航 Tab：
    // 0 = 我的 (桌宠主控制台、身份卡片、偏好设置、系统权限、使用指南)
    // 1 = 专注 (双数字打卡、近7/14天趋势柱状图、按日查询)
    // 2 = 记账 (微信/支付宝自动记账、今日/本周开销、流水明细)
    // 3 = 防沉迷 (应用限额监督、今日累计监控、配置管理)
    private int currentTab = 0;

    private LinearLayout mainContentContainer;
    private TextView tvTopBarTitle;
    private TextView tvPetRunningBadge;
    private LinearLayout bottomBarContainer;
    private ImageView ivCustomBg;
    private View vBgMask;
    private static final int REQUEST_PICK_BG = 1002;

    private static class TabDef {
        String key; // "MINE", "FOCUS", "BILL", "MONITOR"
        String title;
        MenuIconView.IconType icon;
        TabDef(String k, String t, MenuIconView.IconType i) {
            key = k; title = t; icon = i;
        }
    }
    private List<TabDef> activeTabs = new ArrayList<>();
    private String currentTabKey = "MINE";
    private boolean isDarkBgTheme = false;

    // 选定日期状态
    private final Calendar statsCalendar = Calendar.getInstance();
    private final Calendar billCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateDisplayFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());

    // 专注页图表状态: 0=7天, 1=14天
    private int chartPeriodMode = 0;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable makeRounded(int bgColor, int cornerDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(cornerDp));
        return gd;
    }

    private GradientDrawable makeBorderCard(int bgColor, int borderColor, int cornerDp, int borderWidthDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(cornerDp));
        gd.setStroke(dp(borderWidthDp), borderColor);
        return gd;
    }

    // 根据用户设置的图块卡片透明度，动态计算温润透光磨砂卡片背景 (无生硬实线边框，自带柔和光晕)
    private GradientDrawable getThemedCardBg(int cornerDp) {
        int percent = ModuleConfigManager.getCardAlpha(this);
        int alphaInt = (int) (percent * 2.55f);
        int cardColor;
        int glowBorder;
        if (isDarkBgTheme) {
            cardColor = (alphaInt << 24) | 0x001E1B2E; // 深暗夜透光磨砂
            glowBorder = ((int)(alphaInt * 0.5f) << 24) | 0x00F472B6; // 二次元霓虹微粉发光边
        } else {
            cardColor = (alphaInt << 24) | 0x00FFFFFF; // 纯白温润磨砂
            glowBorder = ((int)(alphaInt * 0.4f) << 24) | 0x00FBCFE8; // 柔粉微光边
        }
        return makeBorderCard(cardColor, glowBorder, cornerDp, 1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureNotificationListenerBound();

        // ════ 真正的全面屏延伸沉浸 (透到状态栏与导航栏后方) ════
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            window.getDecorView().setSystemUiVisibility(uiFlags);
        }

        // 二次元卡片视效架构：底层自定义背景壁纸 + 柔光遮罩 + 内容层
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(0xFFFFF7F9); // 浅粉温润基底

        ivCustomBg = new ImageView(this);
        ivCustomBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootLayout.addView(ivCustomBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        vBgMask = new View(this);
        rootLayout.addView(vBgMask, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyCustomBackground();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000); // 内容层透明，由背景与卡片承托
        rootLayout.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ════════════════════════════════════════════════════════════════
        // 1. 纯白大标题顶栏 TopBar
        // ════════════════════════════════════════════════════════════════
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0x00000000); // 彻底透明沉浸，无任何外框
        int statusBarH = getStatusBarHeight();
        topBar.setPadding(dp(22), statusBarH + dp(12), dp(22), dp(12));

        tvTopBarTitle = new TextView(this);
        tvTopBarTitle.setText("专注");
        tvTopBarTitle.setTextSize(22);
        tvTopBarTitle.setTypeface(null, Typeface.BOLD);
        tvTopBarTitle.setTextColor(0xFF1E1B18);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(tvTopBarTitle, tlp);

        tvPetRunningBadge = new TextView(this);
        tvPetRunningBadge.setTextSize(11);
        tvPetRunningBadge.setTypeface(null, Typeface.BOLD);
        tvPetRunningBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        topBar.addView(tvPetRunningBadge);

        root.addView(topBar);

        // ════════════════════════════════════════════════════════════════
        // 2. 中间动态内容区
        // ════════════════════════════════════════════════════════════════
        ScrollView contentScrollView = new ScrollView(this);
        contentScrollView.setFillViewport(true);
        mainContentContainer = new LinearLayout(this);
        mainContentContainer.setOrientation(LinearLayout.VERTICAL);
        mainContentContainer.setPadding(dp(18), dp(16), dp(18), dp(24));
        contentScrollView.addView(mainContentContainer);
        root.addView(contentScrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // ════════════════════════════════════════════════════════════════
        // 3. 悬浮胶囊风底部导航栏（去文字纯图标 + 优雅胶囊底座）
        // ════════════════════════════════════════════════════════════════
        bottomBarContainer = new LinearLayout(this);
        bottomBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        bottomBarContainer.setGravity(Gravity.CENTER_VERTICAL);
        bottomBarContainer.setBackgroundColor(0x00000000); // 彻底透明无框，纯悬浮图标
        int navBarH = getNavBarHeight();
        bottomBarContainer.setPadding(dp(20), dp(8), dp(20), Math.max(dp(16), navBarH + dp(8)));
        root.addView(bottomBarContainer);

        setContentView(rootLayout);

        String targetName = getIntent().getStringExtra("target_tab_name");
        refreshActiveTabs();
        switchTabByKey(targetName != null ? targetName : "MINE");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String targetName = intent.getStringExtra("target_tab_name");
        if (targetName != null) {
            switchTabByKey(targetName);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTopBarBadge();
        if (tvTopBarTitle != null) {
            tvTopBarTitle.setTextColor(isDarkBgTheme ? 0xFFFDF2F8 : 0xFF1F2937);
            tvTopBarTitle.setShadowLayer(dp(4), 0, dp(1), isDarkBgTheme ? 0xCC000000 : 0x50FFFFFF);
        }
        applyCustomBackground();
        refreshActiveTabs();
        String targetName = getIntent().getStringExtra("target_tab_name");
        if (targetName != null) {
            getIntent().removeExtra("target_tab_name");
            switchTabByKey(targetName);
        } else {
            switchTabByKey(currentTabKey);
        }
    }

    private void refreshActiveTabs() {
        activeTabs.clear();
        // 我的 永远在首位
        activeTabs.add(new TabDef("MINE", "我的", MenuIconView.IconType.NAV_MINE));
        if (ModuleConfigManager.isFocusEnabled(this)) {
            activeTabs.add(new TabDef("FOCUS", "专注", MenuIconView.IconType.NAV_FOCUS));
        }
        if (ModuleConfigManager.isBillEnabled(this)) {
            activeTabs.add(new TabDef("BILL", "记账", MenuIconView.IconType.NAV_BILL));
        }
        if (ModuleConfigManager.isMonitorEnabled(this)) {
            activeTabs.add(new TabDef("MONITOR", "防沉迷", MenuIconView.IconType.NAV_MONITOR));
        }
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(32);
    }

    private int getNavBarHeight() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(16);
    }

    // 智能分析图片上半部分的明暗度，决定是否启用深色背景文字模式
    private boolean analyzeIfDarkBackground(Bitmap bm, int alphaPercent) {
        if (bm == null || alphaPercent < 25) return false; // 透明度低时主要看底层浅粉遮罩，属于浅色底
        try {
            int w = bm.getWidth();
            int h = Math.min(bm.getHeight(), bm.getHeight() / 3); // 重点检测标题和顶栏所在的上部 1/3
            if (w <= 0 || h <= 0) return false;
            // 均匀采样 20x20 个点快速计算平均亮度
            long totalLuma = 0;
            int samples = 0;
            for (int y = 0; y < h; y += Math.max(1, h / 20)) {
                for (int x = 0; x < w; x += Math.max(1, w / 20)) {
                    int c = bm.getPixel(x, y);
                    int r = Color.red(c);
                    int g = Color.green(c);
                    int b = Color.blue(c);
                    // 业界标准加权发光感知公式
                    int luma = (r * 299 + g * 587 + b * 114) / 1000;
                    totalLuma += luma;
                    samples++;
                }
            }
            if (samples == 0) return false;
            int avgLuma = (int) (totalLuma / samples);
            // 结合遮罩混合后的综合亮度 (如果壁纸暗但透明度低，也会被白粉遮罩提亮)
            float bgWeight = alphaPercent / 100.0f;
            int compositeLuma = (int) (avgLuma * bgWeight + 248 * (1.0f - bgWeight));
            return compositeLuma < 138; // 综合亮度低于阈值时判定为深色背景
        } catch (Exception e) {
            return false;
        }
    }

    private void applyCustomBackground() {
        if (ivCustomBg == null || vBgMask == null) return;
        String bgPath = ModuleConfigManager.getCustomBgPath(this);
        int alphaPercent = ModuleConfigManager.getBgAlpha(this);
        Bitmap loadedBm = null;

        if (bgPath != null && new File(bgPath).exists()) {
            try {
                loadedBm = BitmapFactory.decodeFile(bgPath);
            } catch (Exception ignored) {}
        }
        // 若用户未自定义背景或已清除，则默认使用内置专属「02-雨夜阳台」二次元壁纸
        if (loadedBm == null) {
            try {
                loadedBm = BitmapFactory.decodeResource(getResources(), R.drawable.default_bg);
            } catch (Exception ignored) {}
        }

        if (loadedBm != null) {
            ivCustomBg.setImageBitmap(loadedBm);
            ivCustomBg.setVisibility(View.VISIBLE);
            isDarkBgTheme = analyzeIfDarkBackground(loadedBm, alphaPercent);

            int maskAlpha = (int) ((100 - alphaPercent) * 2.55f);
            if (isDarkBgTheme) {
                // 深色主题下柔光深紫微光遮罩
                vBgMask.setBackgroundColor(Color.argb(maskAlpha, 15, 10, 22));
            } else {
                // 浅色主题下温润浅粉白遮罩
                vBgMask.setBackgroundColor(Color.argb(maskAlpha, 255, 245, 247));
            }
        } else {
            ivCustomBg.setImageDrawable(null);
            ivCustomBg.setVisibility(View.GONE);
            vBgMask.setBackgroundColor(0xFFFFF7F9);
            isDarkBgTheme = false;
        }

        // 动态调整系统状态栏图标颜色 (深色背景配白图标，浅色背景配黑图标)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isDarkBgTheme) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // 白色图标
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // 黑色图标
            }
            decor.setSystemUiVisibility(flags);
        }

        // 动态刷新顶栏文字颜色
        if (tvTopBarTitle != null) {
            tvTopBarTitle.setTextColor(isDarkBgTheme ? 0xFFFDF2F8 : 0xFF1F2937);
            // 给大标题增加一层微妙的阴影，确保在复杂插画上无论黑白都能清晰立显
            tvTopBarTitle.setShadowLayer(dp(4), 0, dp(1), isDarkBgTheme ? 0xCC000000 : 0x50FFFFFF);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 渲染无文字纯图标微胶囊导航栏（我的居首位）
    // ════════════════════════════════════════════════════════════════
        private void renderBottomNav() {
        bottomBarContainer.removeAllViews();
        for (final TabDef tab : activeTabs) {
            final boolean selected = currentTabKey.equals(tab.key);
            final int activeColor = 0xFFFB7185; // 甜美二次元桃粉
            final int inactiveColor = 0xFF94A3B8; // 柔和灰蓝

            FrameLayout navBtn = new FrameLayout(this);
            navBtn.setClickable(true);
            navBtn.setFocusable(true);
            navBtn.setPadding(dp(4), dp(2), dp(4), dp(2));

            FrameLayout capsule = new FrameLayout(this);
            if (selected) {
                // 纯柔光悬浮水滴底座，无生硬方框或描边，极度自然
                capsule.setBackground(makeRounded(0x40FB7185, 24));
            } else {
                capsule.setBackgroundColor(0x00000000);
            }
            capsule.setPadding(dp(20), dp(10), dp(20), dp(10));

            MenuIconView iconView = new MenuIconView(this, tab.icon, selected ? activeColor : inactiveColor);
            capsule.addView(iconView, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            navBtn.addView(capsule, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            navBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!currentTabKey.equals(tab.key)) {
                        switchTabByKey(tab.key);
                    }
                }
            });
            bottomBarContainer.addView(navBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }
    }

    private void switchTabByKey(String tabKey) {
        refreshActiveTabs();
        boolean exists = false;
        for (TabDef td : activeTabs) {
            if (td.key.equals(tabKey)) {
                exists = true;
                break;
            }
        }
        if (!exists) tabKey = "MINE";
        currentTabKey = tabKey;
        renderBottomNav();
        mainContentContainer.removeAllViews();

        if ("MINE".equals(tabKey)) {
            tvTopBarTitle.setText("墨墨的小窝");
            renderMineTab();
        } else if ("FOCUS".equals(tabKey)) {
            tvTopBarTitle.setText("专注时光");
            renderFocusTab();
        } else if ("BILL".equals(tabKey)) {
            tvTopBarTitle.setText("小金库账本");
            renderBillTab();
        } else if ("MONITOR".equals(tabKey)) {
            tvTopBarTitle.setText("防沉迷守护");
            renderMonitorTab();
        }
        updateTopBarBadge();
        if (tvTopBarTitle != null) {
            tvTopBarTitle.setTextColor(isDarkBgTheme ? 0xFFFDF2F8 : 0xFF1F2937);
            tvTopBarTitle.setShadowLayer(dp(4), 0, dp(1), isDarkBgTheme ? 0xCC000000 : 0x50FFFFFF);
        }
    }

    private void switchTab(int tabIndex) {
        if (tabIndex == 1) switchTabByKey("FOCUS");
        else if (tabIndex == 2) switchTabByKey("BILL");
        else if (tabIndex == 3) switchTabByKey("MONITOR");
        else switchTabByKey("MINE");
    }

// ════════════════════════════════════════════════════════════════
    // Tab 0: 专注页面
    // ════════════════════════════════════════════════════════════════
    private void renderFocusTab() {
        // 双数字卡片（对齐参考图中的 220 听过 / 48小时收听）
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);

        long todayStudy = StatsManager.getTodayStudySec(this);
        long weekStudy = StatsManager.getWeekStudySec(this);

        statRow.addView(makeBigNumberCard(StatsManager.formatDuration(todayStudy), "今日专注时光", 0xFF0D9488), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        View ssp = new View(this);
        statRow.addView(ssp, new LinearLayout.LayoutParams(dp(12), 1));
        statRow.addView(makeBigNumberCard(StatsManager.formatDuration(weekStudy), "本周累计打卡", 0xFF0F766E), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mainContentContainer.addView(statRow);

        // 柱状图卡片
        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp1);

        mainContentContainer.addView(makeGroupHeader("陪伴趋势柱状图"));

        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setBackground(getThemedCardBg(18));
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) chartCard.setElevation(dp(2));

        LinearLayout chartHeader = new LinearLayout(this);
        chartHeader.setOrientation(LinearLayout.HORIZONTAL);
        chartHeader.setGravity(Gravity.CENTER_VERTICAL);
        chartHeader.setPadding(0, 0, 0, dp(14));

        final TextView btn7 = makeActionPill("近7天", 0xFF2D2825, 0xFFFFFFFF);
        final TextView btn14 = makeActionPill("近14天", 0xFFEDE7DF, 0xFF5A524A);

        View.OnClickListener tabLis = new View.OnClickListener() {
            @Override public void onClick(View v) {
                chartPeriodMode = (v == btn7) ? 0 : 1;
                renderChartButtons(btn7, btn14);
                renderFocusTab();
            }
        };
        btn7.setOnClickListener(tabLis);
        btn14.setOnClickListener(tabLis);
        renderChartButtons(btn7, btn14);

        chartHeader.addView(btn7);
        View tsp = new View(this);
        tsp.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
        chartHeader.addView(tsp);
        chartHeader.addView(btn14);

        View spring = new View(this);
        chartHeader.addView(spring, new LinearLayout.LayoutParams(0, 1, 1.0f));

        chartHeader.addView(makeLegendDot(0xFF14B8A6, "专注"));
        View lsp = new View(this);
        lsp.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        chartHeader.addView(lsp);
        chartHeader.addView(makeLegendDot(0xFFF59E0B, "休闲"));

        chartCard.addView(chartHeader);

        LinearLayout barsContainer = new LinearLayout(this);
        barsContainer.setOrientation(LinearLayout.HORIZONTAL);
        barsContainer.setGravity(Gravity.BOTTOM);
        barsContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
        renderBarChartBars(barsContainer, chartPeriodMode == 0 ? 7 : 14);
        chartCard.addView(barsContainer);

        mainContentContainer.addView(chartCard);

        // 选定单日详情卡
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp2);

        mainContentContainer.addView(makeGroupHeader("按日期查询专注"));

        LinearLayout pickerCard = new LinearLayout(this);
        pickerCard.setOrientation(LinearLayout.VERTICAL);
        pickerCard.setBackground(getThemedCardBg(18));
        pickerCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) pickerCard.setElevation(dp(2));

        LinearLayout pHeader = new LinearLayout(this);
        pHeader.setOrientation(LinearLayout.HORIZONTAL);
        pHeader.setGravity(Gravity.CENTER_VERTICAL);
        pHeader.setPadding(0, 0, 0, dp(10));

        Date curDate = statsCalendar.getTime();
        TextView tvCurDate = new TextView(this);
        tvCurDate.setText(dateDisplayFormat.format(curDate));
        tvCurDate.setTextSize(15);
        tvCurDate.setTypeface(null, Typeface.BOLD);
        tvCurDate.setTextColor(getTitleTextColor());
        pHeader.addView(tvCurDate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnChooseDate = makeActionPill("按日筛选 ▾", 0xFFF3EFEA, 0xFF4A443E);
        btnChooseDate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showDatePicker(statsCalendar, new Runnable() {
                    @Override public void run() { switchTab(1); }
                });
            }
        });
        pHeader.addView(btnChooseDate);
        pickerCard.addView(pHeader);

        long sSec = StatsManager.getDaySec(this, true, curDate);
        long lSec = StatsManager.getDaySec(this, false, curDate);
        pickerCard.addView(makeKeyValueRow("选定日专注时长", StatsManager.formatDuration(sSec), 0xFF0D9488));
        pickerCard.addView(makeKeyValueRow("选定日休闲时长", StatsManager.formatDuration(lSec), 0xFFD97706));

        mainContentContainer.addView(pickerCard);
    }

    private void renderChartButtons(TextView btn7, TextView btn14) {
        if (chartPeriodMode == 0) {
            btn7.setBackground(makeRounded(0xFF2D2825, 12));
            btn7.setTextColor(0xFFFFFFFF);
            btn14.setBackground(makeRounded(0xFFF0EBE3, 12));
            btn14.setTextColor(0xFF6B635A);
        } else {
            btn7.setBackground(makeRounded(0xFFF0EBE3, 12));
            btn7.setTextColor(0xFF6B635A);
            btn14.setBackground(makeRounded(0xFF2D2825, 12));
            btn14.setTextColor(0xFFFFFFFF);
        }
    }

    private void renderBarChartBars(LinearLayout container, int days) {
        container.removeAllViews();
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -(days - 1));

        long[] studySecs = new long[days];
        long[] leisureSecs = new long[days];
        String[] labels = new String[days];
        long maxSec = 1;

        SimpleDateFormat sdfLabel = new SimpleDateFormat(days <= 7 ? "MM/dd" : "d", Locale.getDefault());
        for (int i = 0; i < days; i++) {
            Date d = c.getTime();
            studySecs[i] = StatsManager.getDaySec(this, true, d);
            leisureSecs[i] = StatsManager.getDaySec(this, false, d);
            labels[i] = sdfLabel.format(d);
            if (studySecs[i] > maxSec) maxSec = studySecs[i];
            if (leisureSecs[i] > maxSec) maxSec = leisureSecs[i];
            c.add(Calendar.DAY_OF_YEAR, 1);
        }

        int maxBarH = dp(90);
        int barW = days <= 7 ? dp(10) : dp(5);

        for (int i = 0; i < days; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            LinearLayout barsPair = new LinearLayout(this);
            barsPair.setOrientation(LinearLayout.HORIZONTAL);
            barsPair.setGravity(Gravity.BOTTOM);

            int hStudy = Math.max(dp(3), (int) (studySecs[i] * maxBarH / maxSec));
            int hLeisure = Math.max(dp(3), (int) (leisureSecs[i] * maxBarH / maxSec));

            View barStudy = new View(this);
            barStudy.setBackground(makeRounded(0xFF14B8A6, 3));
            barsPair.addView(barStudy, new LinearLayout.LayoutParams(barW, hStudy));

            View pairSp = new View(this);
            pairSp.setLayoutParams(new LinearLayout.LayoutParams(dp(2), 1));
            barsPair.addView(pairSp);

            View barLeisure = new View(this);
            barLeisure.setBackground(makeRounded(0xFFF59E0B, 3));
            barsPair.addView(barLeisure, new LinearLayout.LayoutParams(barW, hLeisure));

            col.addView(barsPair);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(labels[i]);
            tvLabel.setTextSize(days <= 7 ? 10 : 8);
            tvLabel.setTextColor(getSubTextColor());
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setPadding(0, dp(4), 0, 0);
            col.addView(tvLabel);

            container.addView(col);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Tab 1: 记账页面
    // ════════════════════════════════════════════════════════════════
    private void renderBillTab() {
        BillDbHelper billDb = BillDbHelper.getInstance(this);

        // 双数字卡片
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);

        double todayExp = billDb.getTodayExpense();
        double weekExp = billDb.getWeekExpense();

        statRow.addView(makeBigNumberCard(String.format(Locale.getDefault(), "￥%.2f", todayExp), "今日自动入账", 0xFFE11D48), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        View ssp = new View(this);
        statRow.addView(ssp, new LinearLayout.LayoutParams(dp(12), 1));
        statRow.addView(makeBigNumberCard(String.format(Locale.getDefault(), "￥%.2f", weekExp), "本周累计支出", 0xFFBE123C), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mainContentContainer.addView(statRow);

        // 授权提示条
        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(16)));
        mainContentContainer.addView(sp1);

        boolean hasPerm = isNotificationListenerEnabled(this);
        LinearLayout permCard = new LinearLayout(this);
        permCard.setOrientation(LinearLayout.HORIZONTAL);
        permCard.setGravity(Gravity.CENTER_VERTICAL);
        permCard.setBackground(makeBorderCard(hasPerm ? 0xFFF0FDF4 : 0xFFFFFBEB, hasPerm ? 0xFFBBF7D0 : 0xFFFDE68A, 14, 1));
        permCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tvPerm = new TextView(this);
        tvPerm.setText(hasPerm ? "微信/支付宝通知监听生效中" : "未开启通知读取权限 (无法自动记账)");
        tvPerm.setTextSize(13);
        tvPerm.setTypeface(null, Typeface.BOLD);
        tvPerm.setTextColor(hasPerm ? 0xFF15803D : 0xFFB45309);
        permCard.addView(tvPerm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnPerm = makeActionPill(hasPerm ? "已开启 ✓" : "去授权 ➔", hasPerm ? 0xFF16A34A : 0xFFD97706, 0xFFFFFFFF);
        btnPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
                }
            }
        });
        permCard.addView(btnPerm);
        mainContentContainer.addView(permCard);

        // 流水明细列表
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp2);

        mainContentContainer.addView(makeGroupHeader("账单流水明细"));

        LinearLayout billCard = new LinearLayout(this);
        billCard.setOrientation(LinearLayout.VERTICAL);
        billCard.setBackground(getThemedCardBg(18));
        billCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) billCard.setElevation(dp(2));

        LinearLayout bHeader = new LinearLayout(this);
        bHeader.setOrientation(LinearLayout.HORIZONTAL);
        bHeader.setGravity(Gravity.CENTER_VERTICAL);
        bHeader.setPadding(0, 0, 0, dp(10));

        Date billDate = billCalendar.getTime();
        String billDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(billDate);
        double dayExp = billDb.getDayExpense(billDateStr);

        TextView tvBTitle = new TextView(this);
        tvBTitle.setText(dateDisplayFormat.format(billDate) + " (合计: ￥" + String.format(Locale.getDefault(), "%.2f", dayExp) + ")");
        tvBTitle.setTextSize(14);
        tvBTitle.setTypeface(null, Typeface.BOLD);
        tvBTitle.setTextColor(getTitleTextColor());
        bHeader.addView(tvBTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnChooseBillDate = makeActionPill("按日筛选 ▾", 0xFFF3EFEA, 0xFF4A443E);
        btnChooseBillDate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showDatePicker(billCalendar, new Runnable() {
                    @Override public void run() { switchTab(2); }
                });
            }
        });
        bHeader.addView(btnChooseBillDate);
        billCard.addView(bHeader);

        List<BillDbHelper.BillItem> items = billDb.getBillsByDate(billDateStr);
        if (items.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("当日暂无自动入账记录。微信或支付宝付款后将自动出现在这里~");
            emptyTv.setTextSize(12);
            emptyTv.setTextColor(0xFF9E958C);
            emptyTv.setPadding(0, dp(10), 0, dp(10));
            billCard.addView(emptyTv);
        } else {
            for (BillDbHelper.BillItem item : items) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));

                TextView tvChannel = new TextView(this);
                boolean isWx = "微信".equals(item.channel);
                tvChannel.setText(isWx ? "微信" : "支付宝");
                tvChannel.setTextSize(11);
                tvChannel.setTextColor(0xFFFFFFFF);
                tvChannel.setBackground(makeRounded(isWx ? 0xFF07C160 : 0xFF1677FF, 6));
                tvChannel.setPadding(dp(6), dp(2), dp(6), dp(2));
                row.addView(tvChannel);

                TextView tvShop = new TextView(this);
                tvShop.setText(" " + item.shop + " (" + item.getFormattedTime() + ")");
                tvShop.setTextSize(13);
                tvShop.setTextColor(0xFF3E3832);
                row.addView(tvShop, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView tvAmt = new TextView(this);
                tvAmt.setText(String.format(Locale.getDefault(), "-￥%.2f", item.amount));
                tvAmt.setTextSize(14);
                tvAmt.setTypeface(null, Typeface.BOLD);
                tvAmt.setTextColor(0xFFE11D48);
                row.addView(tvAmt);

                billCard.addView(row);
            }
        }
        mainContentContainer.addView(billCard);
    }

        // ════════════════════════════════════════════════════════════════
    // Tab 2: 防沉迷与健康使用守护页面 (直接内嵌配置与应用管理)
    // ════════════════════════════════════════════════════════════════
    private LinearLayout pkgListContainer;

    private void renderMonitorTab() {
        boolean hasUsagePerm = AppMonitorManager.hasUsageStatsPermission(this);
        boolean isEnabled = AppMonitorManager.isMonitorEnabled(this);
        int singleLimit = AppMonitorManager.getLimitMinutes(this);
        int dailyLimit = AppMonitorManager.getGlobalDailyLimitMinutes(this);
        int appCount = AppMonitorManager.getMonitoredPackages(this).size();

        // 1. 双数字核心概览卡片 (对齐音乐 App 风格)
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.addView(makeBigNumberCard(appCount + " 款", "重点监督应用", 0xFF0284C7), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        View ssp = new View(this);
        statRow.addView(ssp, new LinearLayout.LayoutParams(dp(12), 1));
        statRow.addView(makeBigNumberCard(singleLimit + " 分钟", "单次连续上限", 0xFFD97706), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mainContentContainer.addView(statRow);

        // 2. 权限自检横幅
        View sp0 = new View(this);
        sp0.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
        mainContentContainer.addView(sp0);

        LinearLayout permCard = new LinearLayout(this);
        permCard.setOrientation(LinearLayout.HORIZONTAL);
        permCard.setGravity(Gravity.CENTER_VERTICAL);
        permCard.setBackground(makeBorderCard(hasUsagePerm ? 0xFFF0FDF4 : 0xFFFFFBEB, hasUsagePerm ? 0xFFBBF7D0 : 0xFFFDE68A, 14, 1));
        permCard.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView tvPerm = new TextView(this);
        tvPerm.setText(hasUsagePerm ? "使用情况访问权限已获得，感知精准准时" : "未开启【使用情况访问】权限，提醒无法触发");
        tvPerm.setTextSize(12.5f);
        tvPerm.setTypeface(null, Typeface.BOLD);
        tvPerm.setTextColor(hasUsagePerm ? 0xFF15803D : 0xFFB45309);
        permCard.addView(tvPerm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (!hasUsagePerm) {
            TextView btnGrant = makeActionPill("去授权 ➔", 0xFFD97706, 0xFFFFFFFF);
            btnGrant.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
                    }
                }
            });
            permCard.addView(btnGrant);
        }
        mainContentContainer.addView(permCard);

        // 3. 守护控制与全局规则卡片
        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp1);
        mainContentContainer.addView(makeGroupHeader("守护控制与全局阈值"));

        LinearLayout rulesCard = new LinearLayout(this);
        rulesCard.setOrientation(LinearLayout.VERTICAL);
        rulesCard.setBackground(getThemedCardBg(18));
        rulesCard.setPadding(dp(16), dp(4), dp(16), dp(4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) rulesCard.setElevation(dp(2));

        // 1) 守护总开关行
        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(12), 0, dp(12));

        FrameLayout iconGuardBox = makeIconBox(0xFF10B981, MenuIconView.IconType.NAV_MONITOR);
        swRow.addView(iconGuardBox);

        LinearLayout guardCol = new LinearLayout(this);
        guardCol.setOrientation(LinearLayout.VERTICAL);
        guardCol.setPadding(dp(12), 0, 0, 0);
        TextView tvGTitle = new TextView(this);
        tvGTitle.setText("墨墨防沉迷守护总开关");
        tvGTitle.setTextSize(14.5f);
        tvGTitle.setTypeface(null, Typeface.BOLD);
        tvGTitle.setTextColor(getTitleTextColor());
        guardCol.addView(tvGTitle);
        TextView tvGSub = new TextView(this);
        tvGSub.setText("超时墨墨会弹窗或冒泡提醒你休息");
        tvGSub.setTextSize(11);
        tvGSub.setTextColor(getSubTextColor());
        guardCol.addView(tvGSub);
        swRow.addView(guardCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Switch swGuard = new Switch(this);
        swGuard.setChecked(isEnabled);
        swGuard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppMonitorManager.setMonitorEnabled(MainActivity.this, checked);
                Toast.makeText(MainActivity.this, checked ? "已开启应用守护" : "已暂停应用守护", Toast.LENGTH_SHORT).show();
            }
        });
        swRow.addView(swGuard);
        rulesCard.addView(swRow);

        View divR1 = new View(this);
        divR1.setBackgroundColor(getDividerColor());
        rulesCard.addView(divR1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 2) 全局单次上限修改行
        LinearLayout singleRow = new LinearLayout(this);
        singleRow.setOrientation(LinearLayout.HORIZONTAL);
        singleRow.setGravity(Gravity.CENTER_VERTICAL);
        singleRow.setPadding(0, dp(12), 0, dp(12));
        singleRow.setClickable(true);
        singleRow.setFocusable(true);

        LinearLayout sCol = new LinearLayout(this);
        sCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvSTitle = new TextView(this);
        tvSTitle.setText("全局单次连续时长上限");
        tvSTitle.setTextSize(14);
        tvSTitle.setTextColor(getTitleTextColor());
        sCol.addView(tvSTitle);
        TextView tvSSub = new TextView(this);
        tvSSub.setText("单次玩手机达到该时长墨墨即刻提示");
        tvSSub.setTextSize(11);
        tvSSub.setTextColor(getSubTextColor());
        sCol.addView(tvSSub);
        singleRow.addView(sCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvSVal = new TextView(this);
        tvSVal.setText(singleLimit + " 分钟 >");
        tvSVal.setTextSize(13);
        tvSVal.setTypeface(null, Typeface.BOLD);
        tvSVal.setTextColor(0xFFD97706);
        singleRow.addView(tvSVal);
        singleRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showLimitPickerDialog();
            }
        });
        rulesCard.addView(singleRow);

        View divR2 = new View(this);
        divR2.setBackgroundColor(getDividerColor());
        rulesCard.addView(divR2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 3) 全局今日累计上限修改行
        LinearLayout dailyRow = new LinearLayout(this);
        dailyRow.setOrientation(LinearLayout.HORIZONTAL);
        dailyRow.setGravity(Gravity.CENTER_VERTICAL);
        dailyRow.setPadding(0, dp(12), 0, dp(12));
        dailyRow.setClickable(true);
        dailyRow.setFocusable(true);

        LinearLayout dCol = new LinearLayout(this);
        dCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvDTitle = new TextView(this);
        tvDTitle.setText("全局今日累计时长上限");
        tvDTitle.setTextSize(14);
        tvDTitle.setTextColor(getTitleTextColor());
        dCol.addView(tvDTitle);
        TextView tvDSub = new TextView(this);
        tvDSub.setText("单日重点 App 累计用时预警线");
        tvDSub.setTextSize(11);
        tvDSub.setTextColor(getSubTextColor());
        dCol.addView(tvDSub);
        dailyRow.addView(dCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvDVal = new TextView(this);
        tvDVal.setText(formatMins(dailyLimit) + " >");
        tvDVal.setTextSize(13);
        tvDVal.setTypeface(null, Typeface.BOLD);
        tvDVal.setTextColor(0xFFD97706);
        dailyRow.addView(tvDVal);
        dailyRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showGlobalDailyLimitPickerDialog();
            }
        });
        rulesCard.addView(dailyRow);

        mainContentContainer.addView(rulesCard);

        // 4. 重点监督应用列表卡片 (包含内嵌的【+ 添加应用】与应用列表)
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp2);

        LinearLayout appHeaderRow = new LinearLayout(this);
        appHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        appHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        appHeaderRow.setPadding(dp(4), 0, dp(4), dp(8));

        TextView tvAppGroup = new TextView(this);
        tvAppGroup.setText("监督应用清单 (" + appCount + ")");
        tvAppGroup.setTextSize(13);
        tvAppGroup.setTextColor(getSubTextColor());
        tvAppGroup.setTypeface(null, Typeface.BOLD);
        appHeaderRow.addView(tvAppGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 精巧的【+ 添加应用】文字圆角微按钮
        TextView btnAddApp = makeActionPill("+ 添加应用", 0xFF0284C7, 0xFFFFFFFF);
        btnAddApp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showAddAppDialog();
            }
        });
        appHeaderRow.addView(btnAddApp);
        mainContentContainer.addView(appHeaderRow);

        LinearLayout appListCard = new LinearLayout(this);
        appListCard.setOrientation(LinearLayout.VERTICAL);
        appListCard.setBackground(getThemedCardBg(18));
        appListCard.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) appListCard.setElevation(dp(2));

        pkgListContainer = appListCard;
        renderPackageList();
        mainContentContainer.addView(appListCard);
    }

    private void renderPackageList() {
        if (pkgListContainer == null) return;
        pkgListContainer.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> set = AppMonitorManager.getMonitoredPackages(this);
        if (set.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("暂无受监督应用，点击右上角【+ 添加应用】添加小红书、抖音、游戏等软件吧~");
            emptyTv.setTextSize(12.5f);
            emptyTv.setTextColor(0xFF999999);
            emptyTv.setPadding(dp(8), dp(18), dp(8), dp(18));
            emptyTv.setGravity(Gravity.CENTER);
            pkgListContainer.addView(emptyTv);
            return;
        }

        int idx = 0;
        for (final String pkg : set) {
            String appName = pkg;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}
            final String finalAppName = appName;
            boolean hasCustom = AppMonitorManager.hasCustomDailyLimit(this, pkg);
            int appLimit = AppMonitorManager.getAppDailyLimitMinutes(this, pkg);
            int todayUsed = AppMonitorManager.getAppTodayUsedMinutes(this, pkg);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            TextView tvName = new TextView(this);
            tvName.setText(appName);
            tvName.setTextSize(14);
            tvName.setTextColor(getTitleTextColor());
            tvName.setTypeface(null, Typeface.BOLD);
            infoCol.addView(tvName);

            TextView tvStats = new TextView(this);
            String limitDesc = hasCustom
                ? ("今日限额: " + formatMins(appLimit) + " [独配]")
                : ("今日限额: " + formatMins(appLimit) + " [跟随全局]");
            tvStats.setText("今日已用 " + formatMins(todayUsed) + " · " + limitDesc);
            tvStats.setTextSize(11);
            tvStats.setTextColor(todayUsed >= appLimit ? 0xFFEF4444 : 0xFF8A827A);
            tvStats.setPadding(0, dp(2), 0, 0);
            infoCol.addView(tvStats);

            row.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            // 精致微胶囊按钮：修改限额
            TextView cfgBtn = makeActionPill(hasCustom ? "修改" : "限额", 0xFFF0F9FF, 0xFF0284C7);
            cfgBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showAppDailyLimitDialog(finalAppName, pkg);
                }
            });
            row.addView(cfgBtn);

            View spaceBtn = new View(this);
            spaceBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
            row.addView(spaceBtn);

            // 精致微胶囊按钮：移除
            TextView delBtn = makeActionPill("移除", 0xFFFEF2F2, 0xFFEF4444);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    AppMonitorManager.removePackage(MainActivity.this, pkg);
                    renderPackageList();
                }
            });
            row.addView(delBtn);

            pkgListContainer.addView(row);

            idx++;
            if (idx < set.size()) {
                View div = new View(this);
                div.setBackgroundColor(getDividerColor());
                pkgListContainer.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private void showLimitPickerDialog() {
        final int[] options = {10, 15, 20, 25, 30, 45, 60, 90};
        String[] items = new String[options.length];
        int current = AppMonitorManager.getLimitMinutes(this);
        int checkedItem = 4;
        for (int i = 0; i < options.length; i++) {
            items[i] = options[i] + " 分钟";
            if (options[i] == current) checkedItem = i;
        }

        new AlertDialog.Builder(this)
            .setTitle("选择单次连续使用触发阈值")
            .setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    AppMonitorManager.setLimitMinutes(MainActivity.this, options[which]);
                    dialog.dismiss();
                    switchTab(3); // 刷新防沉迷面板
                    Toast.makeText(MainActivity.this, "已设定为 " + options[which] + " 分钟", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showGlobalDailyLimitPickerDialog() {
        final int[] options = {30, 45, 60, 90, 120, 150, 180, 240};
        String[] items = new String[options.length];
        int current = AppMonitorManager.getGlobalDailyLimitMinutes(this);
        int checkedItem = 4;
        for (int i = 0; i < options.length; i++) {
            items[i] = formatMins(options[i]);
            if (options[i] == current) checkedItem = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择全局今日累计上限")
            .setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    AppMonitorManager.setGlobalDailyLimitMinutes(MainActivity.this, options[which]);
                    dialog.dismiss();
                    switchTab(3); // 刷新防沉迷面板
                    Toast.makeText(MainActivity.this, "全局今日累计已设定为 " + formatMins(options[which]), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showAppDailyLimitDialog(final String appName, final String pkg) {
        final int[] options = {0, 30, 45, 60, 90, 120, 150, 180, 240};
        String[] items = new String[options.length];
        int gVal = AppMonitorManager.getGlobalDailyLimitMinutes(this);
        items[0] = "跟随全局默认 (" + formatMins(gVal) + ")";
        int curVal = AppMonitorManager.hasCustomDailyLimit(this, pkg)
            ? AppMonitorManager.getAppDailyLimitMinutes(this, pkg)
            : 0;
        int checkedItem = 0;
        for (int i = 1; i < options.length; i++) {
            items[i] = formatMins(options[i]);
            if (options[i] == curVal) checkedItem = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("「" + appName + "」今日累计上限配置")
            .setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    int chosen = options[which];
                    AppMonitorManager.setAppDailyLimitMinutes(MainActivity.this, pkg, chosen);
                    renderPackageList();
                    dialog.dismiss();
                    String tip = chosen <= 0 ? "已恢复跟随全局默认" : ("已单独设定累计上限为 " + formatMins(chosen));
                    Toast.makeText(MainActivity.this, tip, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showAddAppDialog() {
        final PackageManager pm = getPackageManager();
        final List<AppItem> allApps = new ArrayList<>();
        final String myPkg = getPackageName();

        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(myPkg)) continue;
            boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSys = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            boolean hasLaunchIntent = pm.getLaunchIntentForPackage(ai.packageName) != null;
            if (!isSys || isUpdatedSys || hasLaunchIntent) {
                String label = pm.getApplicationLabel(ai).toString();
                allApps.add(new AppItem(label, ai.packageName));
            }
        }
        Collections.sort(allApps, new Comparator<AppItem>() {
            @Override public int compare(AppItem o1, AppItem o2) {
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setPadding(dp(18), dp(16), dp(18), dp(16));

        final EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 搜索应用名称或包名（如 微信、抖音）");
        etSearch.setTextSize(13);
        etSearch.setTextColor(0xFF33302C);
        etSearch.setBackground(makeBorderCard(0xFFF7F5F2, 0xFFDDD6CE, 10, 1));
        etSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        dialogRoot.addView(etSearch);

        ScrollView listScroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(320)
        );
        slp.topMargin = dp(12);
        listScroll.setLayoutParams(slp);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listContainer);
        dialogRoot.addView(listScroll);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("➕ 添加监督应用 (" + allApps.size() + " 个可用)")
            .setView(dialogRoot)
            .setNegativeButton("关闭", null)
            .create();

        final Runnable filterRunnable = new Runnable() {
            @Override public void run() {
                listContainer.removeAllViews();
                String query = etSearch.getText().toString().trim().toLowerCase();
                int count = 0;
                for (final AppItem item : allApps) {
                    if (query.isEmpty() || item.name.toLowerCase().contains(query) || item.pkg.toLowerCase().contains(query)) {
                        count++;
                        LinearLayout row = new LinearLayout(MainActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(4), dp(8), dp(4), dp(8));

                        LinearLayout textCol = new LinearLayout(MainActivity.this);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        TextView tvN = new TextView(MainActivity.this);
                        tvN.setText(item.name);
                        tvN.setTextSize(14);
                        tvN.setTextColor(0xFF33302C);
                        tvN.setTypeface(null, Typeface.BOLD);
                        textCol.addView(tvN);

                        TextView tvP = new TextView(MainActivity.this);
                        tvP.setText(item.pkg);
                        tvP.setTextSize(11);
                        tvP.setTextColor(0xFF8A827A);
                        textCol.addView(tvP);
                        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                        TextView addBtn = makeActionPill("添加", 0xFFF0F9FF, 0xFF0284C7);
                        addBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                dialog.dismiss();
                                showAddConfirmWithDailyLimit(item.name, item.pkg);
                            }
                        });
                        row.addView(addBtn);
                        listContainer.addView(row);

                        View div = new View(MainActivity.this);
                        div.setBackgroundColor(getDividerColor());
                        listContainer.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                    }
                }
                if (count == 0) {
                    TextView emptyTv = new TextView(MainActivity.this);
                    emptyTv.setText("未找到匹配的应用哦~");
                    emptyTv.setTextSize(12);
                    emptyTv.setTextColor(0xFF999999);
                    emptyTv.setPadding(dp(4), dp(16), dp(4), dp(16));
                    listContainer.addView(emptyTv);
                }
            }
        };

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRunnable.run();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        filterRunnable.run();
        dialog.show();
    }

    private void showAddConfirmWithDailyLimit(final String appName, final String pkg) {
        final int[] options = {0, 30, 45, 60, 90, 120, 150, 180, 240};
        String[] items = new String[options.length];
        int gVal = AppMonitorManager.getGlobalDailyLimitMinutes(this);
        items[0] = "跟随全局默认 (" + formatMins(gVal) + ")";
        for (int i = 1; i < options.length; i++) {
            items[i] = formatMins(options[i]);
        }
        final int[] selected = {0};
        new AlertDialog.Builder(this)
            .setTitle("为「" + appName + "」设定今日累计上限")
            .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    selected[0] = options[which];
                }
            })
            .setPositiveButton("确认添加", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    AppMonitorManager.addPackage(MainActivity.this, pkg, selected[0]);
                    switchTab(3);
                    String msg = selected[0] <= 0
                        ? ("已将「" + appName + "」加入守护清单 (累计上限跟随全局)")
                        : ("已将「" + appName + "」加入守护清单 (累计上限 " + formatMins(selected[0]) + ")");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private static class AppItem {
        String name;
        String pkg;
        AppItem(String n, String p) { name = n; pkg = p; }
    }

    private static String formatMins(int mins) {
        if (mins <= 0) return "0分钟";
        int h = mins / 60;
        int m = mins % 60;
        if (h > 0 && m > 0) return h + "小时" + m + "分";
        if (h > 0) return h + "小时";
        return m + "分钟";
    }
// ════════════════════════════════════════════════════════════════
    // Tab 3: 我的页面（对齐参考图的高颜值现代个人中心）
    // ════════════════════════════════════════════════════════════════
    private void renderMineTab() {
        // 1. 二次元小窝主理人名片（墨墨专属立绘 + 团子萌系状态）
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setBackground(getThemedCardBg(20));
        profileCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) profileCard.setElevation(dp(3));

        ImageView ivAvatar = new ImageView(this);
        Bitmap avatarBm = null;
        try {
            InputStream is = getAssets().open("idle/000.png");
            avatarBm = BitmapFactory.decodeStream(is);
            is.close();
        } catch (Exception ignored) {}
        if (avatarBm != null) {
            ivAvatar.setImageBitmap(avatarBm);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_launcher);
        }
        ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        profileCard.addView(ivAvatar, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout pCol = new LinearLayout(this);
        pCol.setOrientation(LinearLayout.VERTICAL);
        pCol.setPadding(dp(14), 0, 0, 0);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText("墨墨与团子");
        tvName.setTextSize(18.5f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(getTitleTextColor());
        nameRow.addView(tvName);

        TextView tvCat = new TextView(this);
        tvCat.setText(" 🐱 咕噜噜~");
        tvCat.setTextSize(11);
        tvCat.setTextColor(0xFFFB7185);
        nameRow.addView(tvCat);
        pCol.addView(nameRow);

        TextView tvSub = new TextView(this);
        tvSub.setText("「不会就不会嘛……我又没说不陪你。」");
        tvSub.setTextSize(12);
        tvSub.setTextColor(getSubTextColor());
        tvSub.setPadding(0, dp(3), 0, 0);
        pCol.addView(tvSub);

        TextView tvBadge = new TextView(this);
        tvBadge.setText("🌸 专属桌面伙伴 · 契约生效中");
        tvBadge.setTextSize(11);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setTextColor(0xFFE11D48);
        tvBadge.setBackground(makeRounded(0x25FB7185, 6));
        tvBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams bdlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bdlp.topMargin = dp(6);
        pCol.addView(tvBadge, bdlp);

        profileCard.addView(pCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        mainContentContainer.addView(profileCard);
        // 2. 陪伴状态卡片（完全按需：只有开启专注或记账模块才展示对应卡片，均未开启则彻底隐藏）
        boolean isFocusOn = ModuleConfigManager.isFocusEnabled(this);
        boolean isBillOn = ModuleConfigManager.isBillEnabled(this);
        if (isFocusOn || isBillOn) {
            View sp0 = new View(this);
            sp0.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
            mainContentContainer.addView(sp0);

            LinearLayout statRow = new LinearLayout(this);
            statRow.setOrientation(LinearLayout.HORIZONTAL);
            if (isFocusOn) {
                long todayStudy = StatsManager.getTodayStudySec(this);
                statRow.addView(makeBigNumberCard(StatsManager.formatDuration(todayStudy), "今日专注时光", 0xFF0D9488), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            }
            if (isFocusOn && isBillOn) {
                View ssp = new View(this);
                statRow.addView(ssp, new LinearLayout.LayoutParams(dp(12), 1));
            }
            if (isBillOn) {
                BillDbHelper billDb = BillDbHelper.getInstance(this);
                double todayExp = billDb.getTodayExpense();
                statRow.addView(makeBigNumberCard(String.format(Locale.getDefault(), "￥%.1f", todayExp), "今日记账支出", 0xFFE11D48), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            }
            mainContentContainer.addView(statRow);
        }
        // 3. 桌宠控制台分组
        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp1);

        mainContentContainer.addView(makeGroupHeader("控制台"));

        boolean isRunning = (PetFloatingService.getInstance() != null);
        LinearLayout controlCard = new LinearLayout(this);
        controlCard.setOrientation(LinearLayout.VERTICAL);
        controlCard.setBackground(getThemedCardBg(18));
        controlCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) controlCard.setElevation(dp(2));

        Button btnToggle = new Button(this);
        btnToggle.setText(isRunning ? "让墨墨回房间休息 (收起桌宠)" : "召唤墨墨与团子 (开启桌面陪伴)");
        btnToggle.setTextSize(15);
        btnToggle.setTypeface(null, Typeface.BOLD);
        btnToggle.setTextColor(0xFFFFFFFF);
        btnToggle.setBackground(makeRounded(isRunning ? 0xFF475569 : 0xFF1E293B, 14));
        btnToggle.setPadding(dp(18), dp(14), dp(18), dp(14));
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PetFloatingService.getInstance() != null) {
                    Intent stopIntent = new Intent(MainActivity.this, PetFloatingService.class);
                    stopIntent.setAction("STOP");
                    startService(stopIntent);
                    Toast.makeText(MainActivity.this, "墨墨抱着团子回去休息啦", Toast.LENGTH_SHORT).show();
                    v.postDelayed(new Runnable() {
                        @Override public void run() { switchTab(0); }
                    }, 250);
                } else {
                    checkAndStartPet();
                }
            }
        });
        controlCard.addView(btnToggle);
        mainContentContainer.addView(controlCard);

        // 4. 设置与权限分组（对齐参考图的分组圆角列表）
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp2);

        mainContentContainer.addView(makeGroupHeader("设置"));

        LinearLayout settingsGroupCard = new LinearLayout(this);
        settingsGroupCard.setOrientation(LinearLayout.VERTICAL);
        settingsGroupCard.setBackground(getThemedCardBg(18));
        settingsGroupCard.setPadding(dp(16), dp(4), dp(16), dp(4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) settingsGroupCard.setElevation(dp(2));

        // 1) 音效开关行（带彩色圆角图标）
        LinearLayout soundRow = new LinearLayout(this);
        soundRow.setOrientation(LinearLayout.HORIZONTAL);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundRow.setPadding(0, dp(12), 0, dp(12));

        FrameLayout iconSoundBox = makeIconBox(0xFF8B5CF6, MenuIconView.IconType.SOUND);
        soundRow.addView(iconSoundBox);

        TextView tvSound = new TextView(this);
        tvSound.setText("桌宠交互音效");
        tvSound.setTextSize(15);
        tvSound.setTypeface(null, Typeface.BOLD);
        tvSound.setTextColor(getTitleTextColor());
        tvSound.setPadding(dp(12), 0, 0, 0);
        soundRow.addView(tvSound, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Switch sw = new Switch(this);
        sw.setChecked(SoundManager.isSoundEnabled(this));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                SoundManager.setSoundEnabled(MainActivity.this, checked);
                if (checked) {
                    SoundManager.getInstance(MainActivity.this).play("cat");
                }
            }
        });
        soundRow.addView(sw);
        settingsGroupCard.addView(soundRow);

        View div1 = new View(this);
        div1.setBackgroundColor(getDividerColor());
        settingsGroupCard.addView(div1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 2) 通知记账权限行
        LinearLayout notifRow = new LinearLayout(this);
        notifRow.setOrientation(LinearLayout.HORIZONTAL);
        notifRow.setGravity(Gravity.CENTER_VERTICAL);
        notifRow.setPadding(0, dp(12), 0, dp(12));

        FrameLayout iconNotifBox = makeIconBox(0xFFEF4444, MenuIconView.IconType.NOTIF);
        notifRow.addView(iconNotifBox);

        TextView tvNotif = new TextView(this);
        tvNotif.setText("微信/支付宝通知记账");
        tvNotif.setTextSize(15);
        tvNotif.setTypeface(null, Typeface.BOLD);
        tvNotif.setTextColor(getTitleTextColor());
        tvNotif.setPadding(dp(12), 0, 0, 0);
        notifRow.addView(tvNotif, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        boolean hasPerm = isNotificationListenerEnabled(this);
        TextView tvPermState = new TextView(this);
        tvPermState.setText(hasPerm ? "已开启 >" : "未授权 >");
        tvPermState.setTextSize(13);
        tvPermState.setTextColor(hasPerm ? 0xFF10B981 : 0xFFF59E0B);
        notifRow.addView(tvPermState);

        notifRow.setClickable(true);
        notifRow.setFocusable(true);
        notifRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
                }
            }
        });
        settingsGroupCard.addView(notifRow);

        mainContentContainer.addView(settingsGroupCard);

        // ════════════════════════════════════════════════════════════════
        // 4.5. 二次元装扮与自定义背景卡片
        // ════════════════════════════════════════════════════════════════
        View spBg = new View(this);
        spBg.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(spBg);
        mainContentContainer.addView(makeGroupHeader("个性化装扮与背景壁纸"));

        LinearLayout themeCard = new LinearLayout(this);
        themeCard.setOrientation(LinearLayout.VERTICAL);
        themeCard.setBackground(getThemedCardBg(18));
        themeCard.setPadding(dp(16), dp(6), dp(16), dp(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) themeCard.setElevation(dp(2));

        // 选择背景行
        LinearLayout pickBgRow = new LinearLayout(this);
        pickBgRow.setOrientation(LinearLayout.HORIZONTAL);
        pickBgRow.setGravity(Gravity.CENTER_VERTICAL);
        pickBgRow.setPadding(0, dp(10), 0, dp(10));

        FrameLayout iconPaletteBox = makeIconBox(0xFFFB7185, MenuIconView.IconType.PALETTE);
        pickBgRow.addView(iconPaletteBox);

        LinearLayout pickCol = new LinearLayout(this);
        pickCol.setOrientation(LinearLayout.VERTICAL);
        pickCol.setPadding(dp(12), 0, 0, 0);
        TextView tvBgTitle = new TextView(this);
        tvBgTitle.setText("自定义二次元背景壁纸");
        tvBgTitle.setTextSize(14.5f);
        tvBgTitle.setTypeface(null, Typeface.BOLD);
        tvBgTitle.setTextColor(getTitleTextColor());
        pickCol.addView(tvBgTitle);
        TextView tvBgSub = new TextView(this);
        String currentPath = ModuleConfigManager.getCustomBgPath(this);
        tvBgSub.setText(currentPath != null ? "已启用自定义背景壁纸" : "从相册挑选喜欢的壁纸或插画");
        tvBgSub.setTextSize(11);
        tvBgSub.setTextColor(getSubTextColor());
        pickCol.addView(tvBgSub);
        pickBgRow.addView(pickCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnPick = makeActionPill(currentPath != null ? "更换" : "选择", 0x20FB7185, 0xFFE11D48);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_BG);
            }
        });
        pickBgRow.addView(btnPick);

        if (currentPath != null) {
            View spDel = new View(this);
            spDel.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
            pickBgRow.addView(spDel);

            TextView btnClear = makeActionPill("清除", 0x2094A3B8, 0xFF64748B);
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ModuleConfigManager.setCustomBgPath(MainActivity.this, null);
                    applyCustomBackground();
                    switchTabByKey("MINE");
                    Toast.makeText(MainActivity.this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
                }
            });
            pickBgRow.addView(btnClear);
        }
        themeCard.addView(pickBgRow);

        // 背景不透明度调节滑块
        if (currentPath != null) {
            View divB1 = new View(this);
            divB1.setBackgroundColor(getDividerColor());
            themeCard.addView(divB1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

            LinearLayout alphaRow = new LinearLayout(this);
            alphaRow.setOrientation(LinearLayout.VERTICAL);
            alphaRow.setPadding(0, dp(10), 0, dp(6));

            final TextView tvAlphaLabel = new TextView(this);
            final int curAlpha = ModuleConfigManager.getBgAlpha(this);
            tvAlphaLabel.setText("背景清晰度 / 不透明度: " + curAlpha + "%");
            tvAlphaLabel.setTextSize(12.5f);
            tvAlphaLabel.setTextColor(getSubTextColor());
            alphaRow.addView(tvAlphaLabel);

            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(100);
            seekBar.setProgress(curAlpha);
            seekBar.setPadding(dp(4), dp(8), dp(4), dp(8));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    tvAlphaLabel.setText("壁纸清晰度: " + progress + "%");
                    ModuleConfigManager.setBgAlpha(MainActivity.this, progress);
                    applyCustomBackground();
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            alphaRow.addView(seekBar);
            themeCard.addView(alphaRow);
        }

        // 统一无论是否有自定义壁纸，都支持调节图块卡片透明度
        View divCardAlpha = new View(this);
        divCardAlpha.setBackgroundColor(getDividerColor());
        themeCard.addView(divCardAlpha, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout cardAlphaRow = new LinearLayout(this);
        cardAlphaRow.setOrientation(LinearLayout.VERTICAL);
        cardAlphaRow.setPadding(0, dp(10), 0, dp(6));

        final TextView tvCardAlphaLabel = new TextView(this);
        final int curCardAlpha = ModuleConfigManager.getCardAlpha(this);
        tvCardAlphaLabel.setText("图块卡片不透明度: " + curCardAlpha + "% (透光磨砂)");
        tvCardAlphaLabel.setTextSize(12.5f);
        tvCardAlphaLabel.setTextColor(getSubTextColor());
        cardAlphaRow.addView(tvCardAlphaLabel);

        SeekBar sbCard = new SeekBar(this);
        sbCard.setMax(100);
        sbCard.setProgress(curCardAlpha);
        sbCard.setPadding(dp(4), dp(8), dp(4), dp(8));
        sbCard.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int safeVal = Math.max(15, progress);
                tvCardAlphaLabel.setText("图块卡片不透明度: " + safeVal + "% (透光磨砂)");
                ModuleConfigManager.setCardAlpha(MainActivity.this, safeVal);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                switchTabByKey("MINE"); // 松手即刻应用刷新整页卡片磨砂通透度
            }
        });
        cardAlphaRow.addView(sbCard);
        themeCard.addView(cardAlphaRow);
        mainContentContainer.addView(themeCard);

        // ════════════════════════════════════════════════════════════════
        // 4.6. 可选扩展模块开关卡片 (用户自主决定功能开启，绝不臃肿杂糅)
        // ════════════════════════════════════════════════════════════════
        View spMod = new View(this);
        spMod.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(spMod);
        mainContentContainer.addView(makeGroupHeader("扩展功能模块 (随心按需启用)"));

        LinearLayout modCard = new LinearLayout(this);
        modCard.setOrientation(LinearLayout.VERTICAL);
        modCard.setBackground(getThemedCardBg(18));
        modCard.setPadding(dp(16), dp(4), dp(16), dp(4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) modCard.setElevation(dp(2));

        // 1) 专注模块
        LinearLayout rowFocus = makeModuleSwitchRow("专注陪伴时光", "开启后底部出现专注打卡、趋势柱状图与明细",
                MenuIconView.IconType.NAV_FOCUS, 0xFF10B981, ModuleConfigManager.isFocusEnabled(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        ModuleConfigManager.setFocusEnabled(MainActivity.this, checked);
                        switchTabByKey("MINE");
                    }
                });
        modCard.addView(rowFocus);

        View divM1 = new View(this);
        divM1.setBackgroundColor(getDividerColor());
        modCard.addView(divM1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 2) 记账模块
        LinearLayout rowBill = makeModuleSwitchRow("自动记账小助手", "开启后监听微信/支付宝支付通知，并展示账本Tab",
                MenuIconView.IconType.NAV_BILL, 0xFFE11D48, ModuleConfigManager.isBillEnabled(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        ModuleConfigManager.setBillEnabled(MainActivity.this, checked);
                        switchTabByKey("MINE");
                    }
                });
        modCard.addView(rowBill);

        View divM2 = new View(this);
        divM2.setBackgroundColor(getDividerColor());
        modCard.addView(divM2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 3) 防沉迷模块
        LinearLayout rowMonitor = makeModuleSwitchRow("手机防沉迷守护", "开启后重点监督玩机时长，并展示健康守护Tab",
                MenuIconView.IconType.NAV_MONITOR, 0xFF0284C7, ModuleConfigManager.isMonitorEnabled(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        ModuleConfigManager.setMonitorEnabled(MainActivity.this, checked);
                        switchTabByKey("MINE");
                    }
                });
        modCard.addView(rowMonitor);

        mainContentContainer.addView(modCard);


        // 5. 使用指南分组
        View sp3 = new View(this);
        sp3.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        mainContentContainer.addView(sp3);

        mainContentContainer.addView(makeGroupHeader("使用指南"));

        LinearLayout tipsCard = new LinearLayout(this);
        tipsCard.setOrientation(LinearLayout.VERTICAL);
        tipsCard.setBackground(getThemedCardBg(18));
        tipsCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) tipsCard.setElevation(dp(2));

        String[] gestures = {
            "单击墨墨：互动吐槽、根据动作匹配治愈鼓励台词",
            "按住拖拽：悬空拎起移动，松手轻盈平滑落地",
            "拖至边缘：贴紧屏幕左/右开启专属靠边微缩动画",
            "双击人物：循环切换迷你 / 小巧 / 标准 / 大体型 (共4档)",
            "长按人物：唤出极简星轨圆环菜单（专注/休闲/音乐/统计/守护）",
            "听歌自适应：播放音乐时自动戴上耳机随节拍微晃"
        };

        for (int i = 0; i < gestures.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView dot = new TextView(this);
            dot.setText("• ");
            dot.setTextSize(14);
            dot.setTextColor(0xFFF43F5E);
            row.addView(dot);

            TextView tv = new TextView(this);
            tv.setText(gestures[i]);
            tv.setTextSize(13);
            tv.setTextColor(getBodyTextColor());
            tv.setLineSpacing(dp(3), 1.0f);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            tipsCard.addView(row);
        }
        mainContentContainer.addView(tipsCard);
    }

    // 紧凑协调的高质感胶囊按钮组件 (无原生粗边框，文字饱满居中，支持暗黑壁纸自适应)
    private TextView makeActionPill(String text, int bgColor, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);

        int finalBg = bgColor;
        int finalTc = textColor;
        if (isDarkBgTheme) {
            // 如果原本是浅灰底暗字(如 0xFFF3EFEA, 0xFF4A443E)，在深色壁纸下转为透光暗夜药丸
            if (bgColor == 0xFFF3EFEA || bgColor == 0xFFEDE7DF || bgColor == 0xFFF0EBE3) {
                finalBg = 0x40FFFFFF; // 半透明白
                finalTc = 0xFFF8FAFC; // 亮白
            } else if (bgColor == 0x2094A3B8) {
                finalBg = 0x4094A3B8;
                finalTc = 0xFFF1F5F9;
            }
        }
        tv.setTextColor(finalTc);
        tv.setBackground(makeRounded(finalBg, 12));
        tv.setPadding(dp(11), dp(6), dp(11), dp(6));
        tv.setGravity(Gravity.CENTER);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private LinearLayout makeModuleSwitchRow(String title, String desc, MenuIconView.IconType iconType, int iconBg, boolean isChecked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        FrameLayout iconBox = makeIconBox(iconBg, iconType);
        row.addView(iconBox);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(12), 0, dp(8), 0);
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextSize(14.5f);
        tvT.setTypeface(null, Typeface.BOLD);
        tvT.setTextColor(getTitleTextColor());
        textCol.addView(tvT);
        TextView tvD = new TextView(this);
        tvD.setText(desc);
        tvD.setTextSize(11);
        tvD.setTextColor(getSubTextColor());
        textCol.addView(tvD);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Switch sw = new Switch(this);
        sw.setChecked(isChecked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);
        return row;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BG && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    File outFile = new File(getFilesDir(), "custom_bg.jpg");
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    is.close();
                    ModuleConfigManager.setCustomBgPath(this, outFile.getAbsolutePath());
                    applyCustomBackground();
                    switchTabByKey("MINE");
                    Toast.makeText(this, "🌸 背景壁纸更换成功啦！", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "设置背景失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ════════ 智能双主题文字与微光适配 ════════
    private int getTitleTextColor() {
        return isDarkBgTheme ? 0xFFFDF2F8 : 0xFF1F2937; // 纯澈白粉 / 深暗玄灰
    }
    private int getBodyTextColor() {
        return isDarkBgTheme ? 0xFFE2E8F0 : 0xFF332D27; // 银白亮灰 / 温暖炭灰
    }
    private int getSubTextColor() {
        return isDarkBgTheme ? 0xFFCBD5E1 : 0xFF64748B; // 柔亮浅蓝灰 / 典雅灰
    }
    private int getMutedTextColor() {
        return isDarkBgTheme ? 0xFFA0AEC0 : 0xFF8A827A; // 高可见浅灰 / 温润灰
    }
    private int getDividerColor() {
        return isDarkBgTheme ? 0x25FFFFFF : 0xFFF3EFEA; // 柔和微白细线 / 原浅灰线
    }

    // ════════════════════════════════════════════════════════════════
    // 现代组件工厂（对齐参考图中的纯白浮雕双数字卡与图标徽标）
    // ════════════════════════════════════════════════════════════════
    private LinearLayout makeBigNumberCard(String bigVal, String label, int valColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
card.setBackground(getThemedCardBg(16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) card.setElevation(dp(2));

        TextView tvVal = new TextView(this);
        tvVal.setText(bigVal);
        tvVal.setTextSize(18);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setTextColor(valColor);
        card.addView(tvVal);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        tvLabel.setTextColor(getSubTextColor());
        tvLabel.setPadding(0, dp(4), 0, 0);
        card.addView(tvLabel);

        return card;
    }

    private FrameLayout makeIconBox(int color, MenuIconView.IconType iconType) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(makeRounded(color, 10));
        MenuIconView icon = new MenuIconView(this, iconType, 0xFFFFFFFF);
        box.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        return box;
    }

    private TextView makeGroupHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(getSubTextColor());
        tv.setTypeface(null, Typeface.BOLD);
        if (isDarkBgTheme) {
            tv.setShadowLayer(dp(3), 0, dp(1), 0xCC000000);
        }
        tv.setPadding(dp(4), 0, dp(4), dp(8));
        return tv;
    }

    private LinearLayout makeKeyValueRow(String label, String value, int valColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(getBodyTextColor());
        row.addView(tvLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextSize(14.5f);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setTextColor(valColor);
        row.addView(tvVal);

        return row;
    }

    private LinearLayout makeLegendDot(int color, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(makeRounded(color, 4));
        row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(0xFF6B635A);
        tv.setPadding(dp(4), 0, 0, 0);
        row.addView(tv);

        return row;
    }

    private void showDatePicker(final Calendar cal, final Runnable onPicked) {
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dlg = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month);
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                if (onPicked != null) onPicked.run();
            }
        }, y, m, d);
        dlg.show();
    }

    private void updateTopBarBadge() {
        boolean isRunning = (PetFloatingService.getInstance() != null);
        if (tvPetRunningBadge != null) {
            tvPetRunningBadge.setText(isRunning ? "陪伴中" : "待命中");
            tvPetRunningBadge.setTextColor(isRunning ? 0xFF0D9488 : 0xFF8A827A);
            tvPetRunningBadge.setBackground(makeRounded(isRunning ? 0xFFCCFBF1 : 0xFFEFEAE4, 6));
        }
    }

    private boolean isNotificationListenerEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (flat != null) {
            final String[] names = flat.split(":");
            for (String name : names) {
                if (name.contains(pkgName)) return true;
            }
        }
        return false;
    }

    private void checkAndStartPet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要授予【悬浮窗/显示在其他应用上层】权限哦", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_CODE);
                return;
            }
        }
        startPetService();
    }

    private void startPetService() {
        Intent serviceIntent = new Intent(this, PetFloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "墨墨来啦！", Toast.LENGTH_SHORT).show();
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(50);
        } catch (Exception ignored) {}
        switchTabByKey("MINE"); // 刷新“我的”控制台
    }

    private void ensureNotificationListenerBound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (isNotificationListenerEnabled(this)) {
                    PackageManager pm = getPackageManager();
                    pm.setComponentEnabledSetting(
                            new android.content.ComponentName(this, PetNotificationListenerService.class),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                    pm.setComponentEnabledSetting(
                            new android.content.ComponentName(this, PetNotificationListenerService.class),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                    );
                }
            } catch (Exception ignored) {}
        }
    }

}
