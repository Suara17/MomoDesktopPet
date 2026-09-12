package com.momo.pet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class MonitorSettingsActivity extends Activity {

    private Switch swEnableMonitor;
    private Switch swMonitorAll;
    private TextView tvLimitVal;
    private LinearLayout pkgListContainer;
    private TextView tvPermissionStatus;
    private Button btnGrantPermission;

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
        title.setText("🛡️ 墨墨防沉迷守护设置");
        title.setTextSize(18);
        title.setTextColor(0xFF33302C);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        topBar.addView(title);
        root.addView(topBar);

        TextView subtitle = new TextView(this);
        subtitle.setText("“一个 App 玩太久了，墨墨会悄悄探头吐槽你哦。” 🐱");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF8A827A);
        subtitle.setPadding(dp(4), 0, dp(4), dp(16));
        root.addView(subtitle);

        // 2. 权限状态引导卡片
        LinearLayout permCard = new LinearLayout(this);
        permCard.setOrientation(LinearLayout.VERTICAL);
        permCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        permCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        permCard.setElevation(dp(2));

        TextView permTitle = new TextView(this);
        permTitle.setText("🔑 系统使用情况权限");
        permTitle.setTextSize(14);
        permTitle.setTypeface(null, Typeface.BOLD);
        permTitle.setTextColor(0xFF4A443E);
        permCard.addView(permTitle);

        tvPermissionStatus = new TextView(this);
        tvPermissionStatus.setTextSize(12);
        tvPermissionStatus.setPadding(0, dp(6), 0, dp(10));
        permCard.addView(tvPermissionStatus);

        btnGrantPermission = new Button(this);
        btnGrantPermission.setText("去系统开启【有权查看使用情况的应用】");
        btnGrantPermission.setTextSize(13);
        btnGrantPermission.setTextColor(0xFFFFFFFF);
        btnGrantPermission.setBackground(makeRoundedCard(0xFF2E3842, 12));
        btnGrantPermission.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnGrantPermission.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MonitorSettingsActivity.this, "无法自动打开设置，请在手机系统设置搜索【有权查看使用情况】", Toast.LENGTH_LONG).show();
                }
            }
        });
        permCard.addView(btnGrantPermission);
        root.addView(permCard);

        // 3. 基础参数控制卡片 (开关、时长阈值)
        View space1 = new View(this);
        space1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(16)));
        root.addView(space1);

        LinearLayout controlCard = new LinearLayout(this);
        controlCard.setOrientation(LinearLayout.VERTICAL);
        controlCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        controlCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        controlCard.setElevation(dp(2));

        // 总开关
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv1 = new TextView(this);
        tv1.setText("启用墨墨防沉迷提醒");
        tv1.setTextSize(14);
        tv1.setTextColor(0xFF33302C);
        row1.addView(tv1, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        swEnableMonitor = new Switch(this);
        swEnableMonitor.setChecked(AppMonitorManager.isMonitorEnabled(this));
        swEnableMonitor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppMonitorManager.setMonitorEnabled(MonitorSettingsActivity.this, checked);
                updateUiState();
            }
        });
        row1.addView(swEnableMonitor);
        controlCard.addView(row1);

        View div1 = new View(this);
        div1.setBackgroundColor(0xFFF3EFEA);
        LinearLayout.LayoutParams dlp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp1.setMargins(0, dp(12), 0, dp(12));
        controlCard.addView(div1, dlp1);

        // 时长阈值设置行
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv2 = new TextView(this);
        tv2.setText("单次连续使用触发时长");
        tv2.setTextSize(14);
        tv2.setTextColor(0xFF33302C);
        row2.addView(tv2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        tvLimitVal = new TextView(this);
        tvLimitVal.setText(AppMonitorManager.getLimitMinutes(this) + " 分钟");
        tvLimitVal.setTextSize(14);
        tvLimitVal.setTextColor(0xFF14B8A6);
        tvLimitVal.setTypeface(null, Typeface.BOLD);
        tvLimitVal.setPadding(0, 0, dp(10), 0);
        row2.addView(tvLimitVal);

        Button btnChangeLimit = new Button(this);
        btnChangeLimit.setText("修改");
        btnChangeLimit.setTextSize(12);
        btnChangeLimit.setTextColor(0xFF5A5550);
        btnChangeLimit.setBackground(makeRoundedCard(0xFFEFEAE4, 10));
        btnChangeLimit.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnChangeLimit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showLimitPickerDialog();
            }
        });
        row2.addView(btnChangeLimit);
        controlCard.addView(row2);

        View div2 = new View(this);
        div2.setBackgroundColor(0xFFF3EFEA);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp2.setMargins(0, dp(12), 0, dp(12));
        controlCard.addView(div2, dlp2);

        // 监控范围模式 (全部应用 vs 重点关照清单)
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout row3Text = new LinearLayout(this);
        row3Text.setOrientation(LinearLayout.VERTICAL);
        TextView tv3 = new TextView(this);
        tv3.setText("监控所有第三方应用");
        tv3.setTextSize(14);
        tv3.setTextColor(0xFF33302C);
        row3Text.addView(tv3);

        TextView tv3Sub = new TextView(this);
        tv3Sub.setText("关闭后，仅对下方【重点关照清单】内的应用生效");
        tv3Sub.setTextSize(11);
        tv3Sub.setTextColor(0xFF8A827A);
        row3Text.addView(tv3Sub);

        row3.addView(row3Text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        swMonitorAll = new Switch(this);
        swMonitorAll.setChecked(AppMonitorManager.isMonitorAll(this));
        swMonitorAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppMonitorManager.setMonitorAll(MonitorSettingsActivity.this, checked);
            }
        });
        row3.addView(swMonitorAll);
        controlCard.addView(row3);

        root.addView(controlCard);

        // 4. 重点关照清单卡片
        View space2 = new View(this);
        space2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(16)));
        root.addView(space2);

        LinearLayout listCard = new LinearLayout(this);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setBackground(makeRoundedCard(0xFFFFFFFF, 16));
        listCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        listCard.setElevation(dp(2));

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setPadding(0, 0, 0, dp(12));

        TextView listTitle = new TextView(this);
        listTitle.setText("📋 重点关照应用清单");
        listTitle.setTextSize(14);
        listTitle.setTypeface(null, Typeface.BOLD);
        listTitle.setTextColor(0xFF4A443E);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button btnAddPkg = new Button(this);
        btnAddPkg.setText("+ 添加应用");
        btnAddPkg.setTextSize(12);
        btnAddPkg.setTextColor(0xFF14B8A6);
        btnAddPkg.setBackground(makeBorderCard(0xFFF0FDF4, 0xFF86EFAC, 10, 1));
        btnAddPkg.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnAddPkg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showAddAppDialog();
            }
        });
        listHeader.addView(btnAddPkg);
        listCard.addView(listHeader);

        pkgListContainer = new LinearLayout(this);
        pkgListContainer.setOrientation(LinearLayout.VERTICAL);
        listCard.addView(pkgListContainer);

        root.addView(listCard);

        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionDisplay();
        renderPackageList();
    }

    private void updateUiState() {
        boolean enabled = swEnableMonitor.isChecked();
        swMonitorAll.setEnabled(enabled);
    }

    private void updatePermissionDisplay() {
        boolean hasPerm = AppMonitorManager.hasUsageStatsPermission(this);
        if (hasPerm) {
            tvPermissionStatus.setText("✅ 已获得权限，墨墨可以灵敏感知当前 App 时长并准时提醒。");
            tvPermissionStatus.setTextColor(0xFF10B981);
            btnGrantPermission.setVisibility(View.GONE);
        } else {
            tvPermissionStatus.setText("⚠️ 未开启权限。没有此权限时系统会限制墨墨感知前台应用，提醒可能无法触发。");
            tvPermissionStatus.setTextColor(0xFFF59E0B);
            btnGrantPermission.setVisibility(View.VISIBLE);
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
                    AppMonitorManager.setLimitMinutes(MonitorSettingsActivity.this, options[which]);
                    tvLimitVal.setText(options[which] + " 分钟");
                    dialog.dismiss();
                    Toast.makeText(MonitorSettingsActivity.this, "已设定为 " + options[which] + " 分钟", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void renderPackageList() {
        pkgListContainer.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> set = AppMonitorManager.getMonitoredPackages(this);

        if (set.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("清单为空，点击右上角【+ 添加应用】添加你要重点监督的软件吧~");
            emptyTv.setTextSize(12);
            emptyTv.setTextColor(0xFF999999);
            emptyTv.setPadding(dp(4), dp(8), dp(4), dp(8));
            pkgListContainer.addView(emptyTv);
            return;
        }

        for (final String pkg : set) {
            String appName = pkg;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(appName);
            tvName.setTextSize(13);
            tvName.setTextColor(0xFF33302C);
            tvName.setTypeface(null, Typeface.BOLD);
            infoCol.addView(tvName);

            TextView tvPkg = new TextView(this);
            tvPkg.setText(pkg);
            tvPkg.setTextSize(10);
            tvPkg.setTextColor(0xFF8A827A);
            infoCol.addView(tvPkg);

            row.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            Button delBtn = new Button(this);
            delBtn.setText("移除");
            delBtn.setTextSize(11);
            delBtn.setTextColor(0xFFEF4444);
            delBtn.setBackground(makeRoundedCard(0xFFFEE2E2, 8));
            delBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    AppMonitorManager.removePackage(MonitorSettingsActivity.this, pkg);
                    renderPackageList();
                }
            });
            row.addView(delBtn);

            pkgListContainer.addView(row);

            View div = new View(this);
            div.setBackgroundColor(0xFFF3EFEA);
            pkgListContainer.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private void showAddAppDialog() {
        final PackageManager pm = getPackageManager();
        final List<AppItem> allApps = new ArrayList<>();
        final String myPkg = getPackageName();

        // 1. 通过 PackageManager 获取已安装应用
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(myPkg)) continue;
            // 排除无启动图标的纯底层系统组件，优先保留用户可见的 App
            boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSys = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            boolean hasLaunchIntent = pm.getLaunchIntentForPackage(ai.packageName) != null;

            // 只要是有启动图标的 App，或者是第三方用户应用，都列出
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

        // 2. 自定义带实时搜索框与滚动的现代化弹窗
        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setPadding(dp(18), dp(16), dp(18), dp(16));

        final EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 搜索应用名称或包名（如 微信、xhs）");
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

        // 动态过滤渲染函数
        final Runnable filterRunnable = new Runnable() {
            @Override public void run() {
                listContainer.removeAllViews();
                String query = etSearch.getText().toString().trim().toLowerCase();

                int count = 0;
                for (final AppItem item : allApps) {
                    if (query.isEmpty() || item.name.toLowerCase().contains(query) || item.pkg.toLowerCase().contains(query)) {
                        count++;
                        LinearLayout row = new LinearLayout(MonitorSettingsActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(4), dp(8), dp(4), dp(8));
                        row.setBackground(makeRoundedCard(0x00000000, 8));

                        LinearLayout textCol = new LinearLayout(MonitorSettingsActivity.this);
                        textCol.setOrientation(LinearLayout.VERTICAL);

                        TextView tvN = new TextView(MonitorSettingsActivity.this);
                        tvN.setText(item.name);
                        tvN.setTextSize(14);
                        tvN.setTextColor(0xFF33302C);
                        tvN.setTypeface(null, Typeface.BOLD);
                        textCol.addView(tvN);

                        TextView tvP = new TextView(MonitorSettingsActivity.this);
                        tvP.setText(item.pkg);
                        tvP.setTextSize(11);
                        tvP.setTextColor(0xFF8A827A);
                        textCol.addView(tvP);

                        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                        Button addBtn = new Button(MonitorSettingsActivity.this);
                        addBtn.setText("添加");
                        addBtn.setTextSize(11);
                        addBtn.setTextColor(0xFF14B8A6);
                        addBtn.setBackground(makeBorderCard(0xFFF0FDF4, 0xFF86EFAC, 8, 1));
                        addBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
                        addBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                AppMonitorManager.addPackage(MonitorSettingsActivity.this, item.pkg);
                                renderPackageList();
                                Toast.makeText(MonitorSettingsActivity.this, "已将「" + item.name + "」加入守护清单", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                        });
                        row.addView(addBtn);

                        listContainer.addView(row);

                        View div = new View(MonitorSettingsActivity.this);
                        div.setBackgroundColor(0xFFF3EFEA);
                        listContainer.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                    }
                }

                if (count == 0) {
                    TextView emptyTv = new TextView(MonitorSettingsActivity.this);
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

        // 初始填充列表
        filterRunnable.run();
        dialog.show();
    }

    private static class AppItem {
        String name;
        String pkg;
        AppItem(String n, String p) { name = n; pkg = p; }
    }
}