package com.momo.pet;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StatsActivity extends Activity {

    private final Calendar selectedCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateDisplayFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());

    // 选定日期卡片组件引用
    private TextView tvSelectedDateTitle;
    private TextView tvDateStudyVal;
    private TextView tvDateLeisureVal;

    // 柱状图容器引用
    private LinearLayout chartBarsContainer;

    // 周期切换状态: 0=最近7天, 1=最近14天
    private int periodMode = 0; 
    private Button btnTab7Days;
    private Button btnTab14Days;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable makeRoundedCard(int bgColor, int cornerDp) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFFFAF8F5);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(36));
        scrollView.addView(root);

        // 1. 顶栏: 返回按钮 + 标题
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(14));

        Button backBtn = new Button(this);
        backBtn.setText("‹ 返回");
        backBtn.setTextSize(14);
        backBtn.setTextColor(0xFF5A5550);
        backBtn.setBackground(makeRoundedCard(0xFFEFEAE4, 12));
        backBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("🐾 墨墨与团子的陪伴时光");
        title.setTextSize(18);
        title.setTextColor(0xFF33302C);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        topBar.addView(title);
        root.addView(topBar);

        TextView subtitle = new TextView(this);
        subtitle.setText("“无论是沉浸专注还是惬意放松，团子都悄悄记在小账本里啦~” (｡•̀ᴗ-)✧");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF8A827A);
        subtitle.setPadding(dp(4), 0, dp(4), dp(16));
        root.addView(subtitle);

        // 2. 交互式柱状图卡片 (支持 7天/14天 切换)
        root.addView(makeSectionTitle("📊 陪伴趋势柱状图"));
        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        chartCard.setElevation(dp(2));

        // 切换 Tab + 图例横排
        LinearLayout chartHeader = new LinearLayout(this);
        chartHeader.setOrientation(LinearLayout.HORIZONTAL);
        chartHeader.setGravity(Gravity.CENTER_VERTICAL);
        chartHeader.setPadding(0, 0, 0, dp(14));

        btnTab7Days = new Button(this);
        btnTab7Days.setText("近7天");
        btnTab7Days.setTextSize(12);
        btnTab7Days.setPadding(dp(10), dp(4), dp(10), dp(4));

        btnTab14Days = new Button(this);
        btnTab14Days.setText("近14天");
        btnTab14Days.setTextSize(12);
        btnTab14Days.setPadding(dp(10), dp(4), dp(10), dp(4));

        View.OnClickListener tabListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                periodMode = (v == btnTab7Days) ? 0 : 1;
                updateTabStyles();
                renderBarChart();
            }
        };
        btnTab7Days.setOnClickListener(tabListener);
        btnTab14Days.setOnClickListener(tabListener);

        chartHeader.addView(btnTab7Days);
        View tspace = new View(this);
        tspace.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
        chartHeader.addView(tspace);
        chartHeader.addView(btnTab14Days);

        // 弹性占位
        View flexSpace = new View(this);
        chartHeader.addView(flexSpace, new LinearLayout.LayoutParams(0, 1, 1.0f));

        // 图例 (学习/休闲)
        chartHeader.addView(makeLegendDot(0xFF14B8A6, "专注"));
        View lspace = new View(this);
        lspace.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        chartHeader.addView(lspace);
        chartHeader.addView(makeLegendDot(0xFFF59E0B, "休闲"));

        chartCard.addView(chartHeader);

        // 柱状图条目横向排布容器
        chartBarsContainer = new LinearLayout(this);
        chartBarsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chartBarsContainer.setGravity(Gravity.BOTTOM);
        chartBarsContainer.setPadding(0, dp(10), 0, dp(4));
        chartCard.addView(chartBarsContainer);

        root.addView(chartCard);

        // 3. 日期选择器卡片 (选择具体某一天)
        View space1 = new View(this);
        space1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        root.addView(space1);

        root.addView(makeSectionTitle("📅 单日详细数据查询"));
        LinearLayout pickerCard = new LinearLayout(this);
        pickerCard.setOrientation(LinearLayout.VERTICAL);
        pickerCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        pickerCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        pickerCard.setElevation(dp(2));

        LinearLayout pickerHeader = new LinearLayout(this);
        pickerHeader.setOrientation(LinearLayout.HORIZONTAL);
        pickerHeader.setGravity(Gravity.CENTER_VERTICAL);
        pickerHeader.setPadding(0, 0, 0, dp(12));

        tvSelectedDateTitle = new TextView(this);
        tvSelectedDateTitle.setTextSize(15);
        tvSelectedDateTitle.setTypeface(null, Typeface.BOLD);
        tvSelectedDateTitle.setTextColor(0xFF33302C);
        LinearLayout.LayoutParams ptlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        pickerHeader.addView(tvSelectedDateTitle, ptlp);

        Button btnChooseDate = new Button(this);
        btnChooseDate.setText("🗓️ 选择日期");
        btnChooseDate.setTextSize(13);
        btnChooseDate.setTextColor(0xFF2E3842);
        btnChooseDate.setBackground(makeBorderCard(0xFFF7F5F2, 0xFFDDD6CE, 12, 1));
        btnChooseDate.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnChooseDate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showDatePickerDialog();
            }
        });
        pickerHeader.addView(btnChooseDate);
        pickerCard.addView(pickerHeader);

        // 选定日期的学习与休闲时长
        tvDateStudyVal = new TextView(this);
        tvDateLeisureVal = new TextView(this);

        pickerCard.addView(makeStatRowWithView("🎓 选定日专注时长", tvDateStudyVal, 0xFF14B8A6));
        View div1 = new View(this);
        div1.setBackgroundColor(0xFFF3EFEA);
        LinearLayout.LayoutParams dlp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp1.setMargins(0, dp(10), 0, dp(10));
        pickerCard.addView(div1, dlp1);

        pickerCard.addView(makeStatRowWithView("☕ 选定日休闲时长", tvDateLeisureVal, 0xFFF59E0B));
        root.addView(pickerCard);

        // 4. 本周与今日累计打卡卡片
        View space2 = new View(this);
        space2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        root.addView(space2);

        root.addView(makeSectionTitle("📈 周期总览打卡"));
        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        summaryCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        summaryCard.setElevation(dp(2));

        long todayStudy = StatsManager.getTodayStudySec(this);
        long todayLeisure = StatsManager.getTodayLeisureSec(this);
        long weekStudy = StatsManager.getWeekStudySec(this);
        long weekLeisure = StatsManager.getWeekLeisureSec(this);

        summaryCard.addView(makeStatRow("📅 今日专注打卡", StatsManager.formatDuration(todayStudy), 0xFF14B8A6));
        summaryCard.addView(makeStatRow("📅 今日休闲小憩", StatsManager.formatDuration(todayLeisure), 0xFFF59E0B));

        View div2 = new View(this);
        div2.setBackgroundColor(0xFFF3EFEA);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp2.setMargins(0, dp(10), 0, dp(10));
        summaryCard.addView(div2, dlp2);

        summaryCard.addView(makeStatRow("📊 本周累计专注", StatsManager.formatDuration(weekStudy), 0xFF0D9488));
        summaryCard.addView(makeStatRow("📊 本周累计休闲", StatsManager.formatDuration(weekLeisure), 0xFFD97706));
        root.addView(summaryCard);

        // 5. 墨墨寄语彩蛋卡片
        View space3 = new View(this);
        space3.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        root.addView(space3);

        LinearLayout encouragementCard = new LinearLayout(this);
        encouragementCard.setOrientation(LinearLayout.VERTICAL);
        encouragementCard.setBackground(makeRoundedCard(0xFFF0EBE5, 14));
        encouragementCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView tipHeader = new TextView(this);
        tipHeader.setText("💡 墨墨的今日陪伴寄语");
        tipHeader.setTextSize(13);
        tipHeader.setTypeface(null, Typeface.BOLD);
        tipHeader.setTextColor(0xFF4A443E);
        encouragementCard.addView(tipHeader);

        TextView tipContent = new TextView(this);
        String eggMsg = todayStudy >= 3600
            ? "哇……今天已经专注了超过 1 个小时！团子在键盘上打出了五星好评，快站起来伸展一下关节吧。"
            : (todayStudy > 0
                ? "每一次点击开始，都是对自己的负责。墨墨陪着你一点一点走，完全不用慌张。"
                : "今天还没开启计时专注呢。想学就长按墨墨开始，不想学就瘫着放空，都随你。");
        tipContent.setText(eggMsg);
        tipContent.setTextSize(12);
        tipContent.setTextColor(0xFF756C64);
        tipContent.setPadding(0, dp(6), 0, 0);
        encouragementCard.addView(tipContent);
        root.addView(encouragementCard);

        setContentView(scrollView);

        // 初始化数据与图表
        updateTabStyles();
        renderBarChart();
        updateSelectedDateData();
    }

    private void updateTabStyles() {
        if (periodMode == 0) {
            btnTab7Days.setBackground(makeRoundedCard(0xFF2E3842, 10));
            btnTab7Days.setTextColor(0xFFFFFFFF);
            btnTab14Days.setBackground(makeRoundedCard(0xFFEFEAE4, 10));
            btnTab14Days.setTextColor(0xFF6B635A);
        } else {
            btnTab7Days.setBackground(makeRoundedCard(0xFFEFEAE4, 10));
            btnTab7Days.setTextColor(0xFF6B635A);
            btnTab14Days.setBackground(makeRoundedCard(0xFF2E3842, 10));
            btnTab14Days.setTextColor(0xFFFFFFFF);
        }
    }

    // 动态渲染双色并排柱状图
    private void renderBarChart() {
        chartBarsContainer.removeAllViews();
        int days = periodMode == 0 ? 7 : 14;

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -(days - 1));

        long[] studySecs = new long[days];
        long[] leisureSecs = new long[days];
        String[] labels = new String[days];
        Date[] dates = new Date[days];

        SimpleDateFormat dfLabel = new SimpleDateFormat(days <= 7 ? "MM/dd" : "d", Locale.getDefault());
        long maxVal = 1; // 防止除以零

        for (int i = 0; i < days; i++) {
            Date d = c.getTime();
            dates[i] = d;
            labels[i] = dfLabel.format(d);
            long s = StatsManager.getDaySec(this, true, d);
            long l = StatsManager.getDaySec(this, false, d);
            studySecs[i] = s;
            leisureSecs[i] = l;
            if (s > maxVal) maxVal = s;
            if (l > maxVal) maxVal = l;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }

        int maxBarHeightPx = dp(110);
        int minBarHeightPx = dp(3);

        for (int i = 0; i < days; i++) {
            final Date itemDate = dates[i];
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            col.setLayoutParams(clp);

            // 点击柱子查看当天
            col.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedCalendar.setTime(itemDate);
                    updateSelectedDateData();
                }
            });

            // 柱子容器 (双柱并排)
            LinearLayout barsPair = new LinearLayout(this);
            barsPair.setOrientation(LinearLayout.HORIZONTAL);
            barsPair.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            int barW = dp(days <= 7 ? 10 : 5);

            // 学习柱 (青绿)
            int hStudy = studySecs[i] <= 0 ? minBarHeightPx : Math.max(minBarHeightPx, (int) ((studySecs[i] * 1.0f / maxVal) * maxBarHeightPx));
            View barStudy = new View(this);
            GradientDrawable gdStudy = new GradientDrawable();
            gdStudy.setColor(studySecs[i] > 0 ? 0xFF14B8A6 : 0xFFE2DDD7);
            gdStudy.setCornerRadii(new float[]{dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
            barStudy.setBackground(gdStudy);
            LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(barW, hStudy);
            bslp.setMargins(0, 0, dp(1), 0);
            barsPair.addView(barStudy, bslp);

            // 休闲柱 (暖橙)
            int hLeisure = leisureSecs[i] <= 0 ? minBarHeightPx : Math.max(minBarHeightPx, (int) ((leisureSecs[i] * 1.0f / maxVal) * maxBarHeightPx));
            View barLeisure = new View(this);
            GradientDrawable gdLeisure = new GradientDrawable();
            gdLeisure.setColor(leisureSecs[i] > 0 ? 0xFFF59E0B : 0xFFEFEAE4);
            gdLeisure.setCornerRadii(new float[]{dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
            barLeisure.setBackground(gdLeisure);
            LinearLayout.LayoutParams bllp = new LinearLayout.LayoutParams(barW, hLeisure);
            barsPair.addView(barLeisure, bllp);

            col.addView(barsPair);

            // 日期短标签
            TextView tvLabel = new TextView(this);
            tvLabel.setText(labels[i]);
            tvLabel.setTextSize(days <= 7 ? 10 : 8);
            tvLabel.setTextColor(0xFF8A827A);
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setPadding(0, dp(6), 0, 0);
            col.addView(tvLabel);

            chartBarsContainer.addView(col);
        }
    }

    private void showDatePickerDialog() {
        int year = selectedCalendar.get(Calendar.YEAR);
        int month = selectedCalendar.get(Calendar.MONTH);
        int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int y, int m, int d) {
                selectedCalendar.set(Calendar.YEAR, y);
                selectedCalendar.set(Calendar.MONTH, m);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, d);
                updateSelectedDateData();
            }
        }, year, month, day);
        dialog.show();
    }

    private void updateSelectedDateData() {
        Date d = selectedCalendar.getTime();
        tvSelectedDateTitle.setText(dateDisplayFormat.format(d));

        long s = StatsManager.getDaySec(this, true, d);
        long l = StatsManager.getDaySec(this, false, d);

        tvDateStudyVal.setText(StatsManager.formatDuration(s));
        tvDateLeisureVal.setText(StatsManager.formatDuration(l));
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF5A524A);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), 0, dp(4), dp(8));
        return tv;
    }

    private LinearLayout makeLegendDot(int color, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(4));
        dot.setBackground(gd);
        row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(0xFF6B635A);
        tv.setPadding(dp(4), 0, 0, 0);
        row.addView(tv);

        return row;
    }

    private LinearLayout makeStatRow(String label, String value, int color) {
        TextView vtv = new TextView(this);
        vtv.setText(value);
        return makeStatRowWithView(label, vtv, color);
    }

    private LinearLayout makeStatRowWithView(String label, TextView vtv, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView ltv = new TextView(this);
        ltv.setText(label);
        ltv.setTextSize(13);
        ltv.setTextColor(0xFF4B453F);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(ltv, lp1);

        vtv.setTextSize(15);
        vtv.setTypeface(null, Typeface.BOLD);
        vtv.setTextColor(color);
        row.addView(vtv);

        return row;
    }
}