package com.momo.pet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(60, 60, 60, 60);
        layout.setBackgroundColor(0xFFF9F7F5);

        TextView title = new TextView(this);
        title.setText("墨墨 & 团子 桌宠");
        title.setTextSize(26);
        title.setTextColor(0xFF333333);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("“……先说好，不要一直戳我，团子会醒的。”\n");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF888888);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 20, 0, 40);
        layout.addView(subtitle);

        Button startBtn = new Button(this);
        startBtn.setText("召唤墨墨与团子 (开启悬浮)");
        startBtn.setTextSize(16);
        startBtn.setBackgroundColor(0xFF4A4A4A);
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setPadding(40, 30, 40, 30);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndStartPet();
            }
        });
        layout.addView(startBtn);

        LinearLayout.LayoutParams btnParams = (LinearLayout.LayoutParams) startBtn.getLayoutParams();
        btnParams.bottomMargin = 30;
        startBtn.setLayoutParams(btnParams);

        Button stopBtn = new Button(this);
        stopBtn.setText("让墨墨回房间休息 (关闭悬浮)");
        stopBtn.setTextSize(14);
        stopBtn.setBackgroundColor(0xFFDDDDDD);
        stopBtn.setTextColor(0xFF555555);
        stopBtn.setPadding(40, 24, 40, 24);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(MainActivity.this, PetFloatingService.class);
                stopIntent.setAction("STOP");
                startService(stopIntent);
                Toast.makeText(MainActivity.this, "墨墨抱着团子回去睡觉了~", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(stopBtn);

        TextView tips = new TextView(this);
        tips.setText("\n💡 玩法小提示：\n• 单击墨墨：互动吐槽、换动作\n• 按住拖动：拎起来悬空移动位置\n• 双击：切换大小缩放 / 弹出快捷菜单\n• 长时间不理：墨墨和团子会自己发呆、喝饮料或睡大觉");
        tips.setTextSize(13);
        tips.setTextColor(0xFF777777);
        tips.setPadding(20, 40, 20, 0);
        layout.addView(tips);

        setContentView(layout);
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
