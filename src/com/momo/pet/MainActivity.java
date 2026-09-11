package com.momo.pet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY_CODE = 1001;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFFFAF8F5); // 治愈奶杏白
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(40));
        scrollView.addView(root);

        // 1. 头像立绘卡片
        LinearLayout avatarCard = new LinearLayout(this);
        avatarCard.setOrientation(LinearLayout.VERTICAL);
        avatarCard.setGravity(Gravity.CENTER);
        avatarCard.setPadding(0, 0, 0, dp(16));

        ImageView ivAvatar = new ImageView(this);
        ivAvatar.setImageResource(R.drawable.ic_launcher);
        ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(86), dp(86));
        alp.bottomMargin = dp(12);
        avatarCard.addView(ivAvatar, alp);

        TextView title = new TextView(this);
        title.setText("墨墨与团子");
        title.setTextSize(22);
        title.setTextColor(0xFF33302C);
        title.setTypeface(null, Typeface.BOLD);
        avatarCard.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("“……先说好，不要一直戳我，团子会醒的。” 🐱");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF8A827A);
        subtitle.setPadding(0, dp(6), 0, 0);
        avatarCard.addView(subtitle);

        root.addView(avatarCard);

        // 2. 核心操作按键卡片 (悬浮启动/关闭/统计页面)
        LinearLayout actionCard = new LinearLayout(this);
        actionCard.setOrientation(LinearLayout.VERTICAL);
        actionCard.setBackground(makeRounded(0xFFFFFFFF, 16));
        actionCard.setPadding(dp(18), dp(20), dp(18), dp(20));
        actionCard.setElevation(dp(2));

        // 召唤墨墨主按键
        Button startBtn = new Button(this);
        startBtn.setText("✨ 召唤墨墨与团子 (开启悬浮)");
        startBtn.setTextSize(15);
        startBtn.setTypeface(null, Typeface.BOLD);
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setBackground(makeRounded(0xFF2E3842, 14));
        startBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkAndStartPet(); }
        });
        actionCard.addView(startBtn);

        // 统计页面快捷入口按键
        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        actionCard.addView(sp1);

        Button statsBtn = new Button(this);
        statsBtn.setText("📊 陪伴时光统计 (学习 / 休闲)");
        statsBtn.setTextSize(14);
        statsBtn.setTextColor(0xFF2E3842);
        statsBtn.setBackground(makeBorderCard(0xFFF7F5F2, 0xFFDDD6CE, 14, 1));
        statsBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        statsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent it = new Intent(MainActivity.this, StatsActivity.class);
                startActivity(it);
            }
        });
        actionCard.addView(statsBtn);

        // 收起墨墨按键
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        actionCard.addView(sp2);

        Button stopBtn = new Button(this);
        stopBtn.setText("💤 让墨墨回房间休息 (关闭悬浮)");
        stopBtn.setTextSize(13);
        stopBtn.setTextColor(0xFF8A827A);
        stopBtn.setBackground(makeRounded(0xFFF0EBE5, 14));
        stopBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent stopIntent = new Intent(MainActivity.this, PetFloatingService.class);
                stopIntent.setAction("STOP");
                startService(stopIntent);
                Toast.makeText(MainActivity.this, "墨墨抱着团子回去睡觉了~", Toast.LENGTH_SHORT).show();
            }
        });
        actionCard.addView(stopBtn);

        root.addView(actionCard);

        // 3. 玩法小贴士卡片
        View sp3 = new View(this);
        sp3.setLayoutParams(new LinearLayout.LayoutParams(1, dp(16)));
        root.addView(sp3);

        LinearLayout tipsCard = new LinearLayout(this);
        tipsCard.setOrientation(LinearLayout.VERTICAL);
        tipsCard.setBackground(makeRounded(0xFFFFFFFF, 16));
        tipsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        tipsCard.setElevation(dp(2));

        TextView tipsHeader = new TextView(this);
        tipsHeader.setText("💡 玩法与互动指南");
        tipsHeader.setTextSize(14);
        tipsHeader.setTypeface(null, Typeface.BOLD);
        tipsHeader.setTextColor(0xFF4A443E);
        tipsCard.addView(tipsHeader);

        TextView tipsBody = new TextView(this);
        tipsBody.setText(
            "• 单击墨墨：互动吐槽、根据动作匹配鼓励台词\n" +
            "• 按住拖拽：拎起来悬空移动，松开轻盈落地\n" +
            "• 拖至边缘：贴紧左/右屏幕边缘开启专属靠边动画\n" +
            "• 双击人物：循环切换迷你 / 小巧 / 标准 / 大体型\n" +
            "• 长按人物：唤出学习/休闲模式、正倒计时与统计页面\n" +
            "• 长时间不理：墨墨和团子会自己发呆、喝水或呼呼大睡"
        );
        tipsBody.setTextSize(12);
        tipsBody.setTextColor(0xFF6E665E);
        tipsBody.setLineSpacing(dp(4), 1.0f);
        tipsBody.setPadding(0, dp(8), 0, 0);
        tipsCard.addView(tipsBody);

        root.addView(tipsCard);

        setContentView(scrollView);
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
        Toast.makeText(this, "墨墨来啦！(。•̀ᴗ-)✧", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startPetService();
            } else {
                Toast.makeText(this, "没有开启悬浮窗权限，墨墨出不来啦", Toast.LENGTH_SHORT).show();
            }
        }
    }
}