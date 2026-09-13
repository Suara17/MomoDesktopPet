package com.momo.pet;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PetNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MomoBillListener";

    // 通用金额匹配
    private static final Pattern PATTERN_RMB = Pattern.compile("[￥¥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern PATTERN_ACTION_AMOUNT = Pattern.compile("(?:付款|支付|消费|支出|扣款|交易金额|金额|收款|转出)[：:]?\\s*(?:[￥¥]\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:元)?");
    private static final Pattern PATTERN_YUAN = Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元");

    // 商户匹配
    private static final Pattern PATTERN_SHOP_PRE = Pattern.compile("(?:向|在)\\s*([^\\s,，。:：]+?)\\s*(?:成功付款|付款|消费|支出|扫码|转账)");
    private static final Pattern PATTERN_SHOP_LABEL = Pattern.compile("(?:商户名称|收款方|交易对象|商户|收款人)[：:]\\s*([^\\n\\r,，。]+)");

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "PetNotificationListenerService onCreate");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "PetNotificationListenerService onListenerConnected! Ready to capture notifications.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "PetNotificationListenerService onListenerDisconnected. Attempting rebind...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new ComponentName(this, PetNotificationListenerService.class));
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!ModuleConfigManager.isBillEnabled(this)) return; // 记账模块未开启时不处理
        String pkg = sbn.getPackageName();
        if (pkg == null) return;

        boolean isWeChat = "com.tencent.mm".equals(pkg);
        boolean isAlipay = "com.eg.android.AlipayGphone".equals(pkg);
        if (!isWeChat && !isAlipay) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle extras = n.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subTextCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleCs != null ? titleCs.toString() : "";
        String text = textCs != null ? textCs.toString() : "";
        String subText = subTextCs != null ? subTextCs.toString() : "";
        String bigText = bigTextCs != null ? bigTextCs.toString() : "";

        String fullContent = title + " " + text + " " + subText + " " + bigText;
        long postTime = sbn.getPostTime();

        Log.d(TAG, "Received notification from " + pkg + ": " + fullContent);

        if (isWeChat) {
            handleWeChatNotification(title, fullContent, postTime);
        } else {
            handleAlipayNotification(title, fullContent, postTime);
        }
    }

    private void handleWeChatNotification(String title, String fullContent, long time) {
        if (!fullContent.contains("微信支付") && !fullContent.contains("支付凭证")
                && !fullContent.contains("支付金额") && !fullContent.contains("扣款")
                && !title.contains("微信支付")) {
            return;
        }

        Double amount = extractAmount(fullContent);
        if (amount == null || amount <= 0) return;

        String shop = extractShop(fullContent, "微信商户");
        saveAndNotifyPet("微信", amount, shop, fullContent, time);
    }

    private void handleAlipayNotification(String title, String fullContent, long time) {
        if (!fullContent.contains("付款") && !fullContent.contains("支付")
                && !fullContent.contains("消费") && !fullContent.contains("交易提醒")
                && !fullContent.contains("支出") && !fullContent.contains("扣款")
                && !title.contains("支付宝") && !title.contains("支付助手")) {
            return;
        }

        // 过滤借呗/花呗营销广告
        if (fullContent.contains("额度") || fullContent.contains("申请成功") || fullContent.contains("恭喜获得")) {
            return;
        }

        Double amount = extractAmount(fullContent);
        if (amount == null || amount <= 0) return;

        String shop = extractShop(fullContent, "支付宝商户");
        saveAndNotifyPet("支付宝", amount, shop, fullContent, time);
    }

    private Double extractAmount(String content) {
        // 1. 优先匹配 ￥xx.xx
        Matcher mRmb = PATTERN_RMB.matcher(content);
        if (mRmb.find()) {
            try { return Double.parseDouble(mRmb.group(1)); } catch (Exception ignored) {}
        }
        // 2. 匹配 支付/付款/消费/支出 xx.xx元
        Matcher mAction = PATTERN_ACTION_AMOUNT.matcher(content);
        if (mAction.find()) {
            try { return Double.parseDouble(mAction.group(1)); } catch (Exception ignored) {}
        }
        // 3. 兜底匹配 xx.xx元
        Matcher mYuan = PATTERN_YUAN.matcher(content);
        if (mYuan.find()) {
            try { return Double.parseDouble(mYuan.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractShop(String content, String defaultShop) {
        Matcher m1 = PATTERN_SHOP_PRE.matcher(content);
        if (m1.find()) {
            return m1.group(1).trim();
        }
        Matcher m2 = PATTERN_SHOP_LABEL.matcher(content);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        return defaultShop;
    }

    private void saveAndNotifyPet(String channel, double amount, String shop, String raw, long time) {
        BillDbHelper db = BillDbHelper.getInstance(this);
        boolean inserted = db.insertBillWithDeduplication(channel, amount, shop, raw, time);
        if (!inserted) {
            Log.d(TAG, "Duplicate bill notification ignored: " + channel + " " + amount);
            return;
        }
        Log.d(TAG, "Bill recorded: " + channel + " ￥" + amount + " at " + shop);

        PetFloatingService petService = PetFloatingService.getInstance();
        if (petService != null) {
            petService.onBillRecorded(channel, amount, shop);
        }
    }
}