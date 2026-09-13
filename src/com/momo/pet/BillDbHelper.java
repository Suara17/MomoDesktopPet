package com.momo.pet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BillDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "momo_bills.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_BILLS = "bills";
    public static final String COL_ID = "_id";
    public static final String COL_CHANNEL = "channel";       // 微信 / 支付宝
    public static final String COL_AMOUNT = "amount";         // 金额 (浮点数)
    public static final String COL_SHOP = "shop";             // 商户 / 交易对象
    public static final String COL_REMARK = "remark";         // 原始文本摘要
    public static final String COL_TIMESTAMP = "timestamp";   // 毫秒时间戳
    public static final String COL_DATE_STR = "date_str";     // yyyy-MM-dd

    private static BillDbHelper instance;

    public static synchronized BillDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new BillDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private BillDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_BILLS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CHANNEL + " TEXT, "
                + COL_AMOUNT + " REAL, "
                + COL_SHOP + " TEXT, "
                + COL_REMARK + " TEXT, "
                + COL_TIMESTAMP + " INTEGER, "
                + COL_DATE_STR + " TEXT"
                + ");";
        db.execSQL(sql);
        db.execSQL("CREATE INDEX idx_bills_date ON " + TABLE_BILLS + "(" + COL_DATE_STR + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static class BillItem {
        public long id;
        public String channel;
        public double amount;
        public String shop;
        public String remark;
        public long timestamp;
        public String dateStr;

        public String getFormattedTime() {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }
    }

    /**
     * 插入账单（带 15 秒防重校验）
     * @return 成功插入返回 true，去重忽略返回 false
     */
    public synchronized boolean insertBillWithDeduplication(String channel, double amount, String shop, String remark, long time) {
        SQLiteDatabase db = getWritableDatabase();
        // 防重检查：15 秒内相同渠道、相同金额、相同商户视为重复通知
        long threshold = time - 15000;
        String query = "SELECT COUNT(*) FROM " + TABLE_BILLS + " WHERE "
                + COL_CHANNEL + "=? AND abs(" + COL_AMOUNT + " - ?) < 0.001 AND "
                + COL_SHOP + "=? AND " + COL_TIMESTAMP + ">=?";
        Cursor cursor = db.rawQuery(query, new String[]{channel, String.valueOf(amount), shop, String.valueOf(threshold)});
        boolean exists = false;
        if (cursor != null) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                exists = true;
            }
            cursor.close();
        }
        if (exists) {
            return false;
        }

        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(time));
        ContentValues cv = new ContentValues();
        cv.put(COL_CHANNEL, channel);
        cv.put(COL_AMOUNT, amount);
        cv.put(COL_SHOP, shop);
        cv.put(COL_REMARK, remark);
        cv.put(COL_TIMESTAMP, time);
        cv.put(COL_DATE_STR, dateStr);
        db.insert(TABLE_BILLS, null, cv);
        return true;
    }

    /**
     * 获取指定日期的累计开销金额
     */
    public synchronized double getDayExpense(String dateStr) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_BILLS + " WHERE " + COL_DATE_STR + "=?";
        Cursor c = db.rawQuery(sql, new String[]{dateStr});
        double sum = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                sum = c.getDouble(0);
            }
            c.close();
        }
        return sum;
    }

    /**
     * 获取今日累计开销
     */
    public synchronized double getTodayExpense() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return getDayExpense(today);
    }

    /**
     * 获取本周累计开销（从本周一开始计算）
     */
    public synchronized double getWeekExpense() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long mondayTime = cal.getTimeInMillis();

        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_BILLS + " WHERE " + COL_TIMESTAMP + ">=?";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(mondayTime)});
        double sum = 0;
        if (c != null) {
            if (c.moveToFirst()) {
                sum = c.getDouble(0);
            }
            c.close();
        }
        return sum;
    }

    /**
     * 获取指定日期的所有明细列表（按时间倒序）
     */
    public synchronized List<BillItem> getBillsByDate(String dateStr) {
        List<BillItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_BILLS, null, COL_DATE_STR + "=?", new String[]{dateStr}, null, null, COL_TIMESTAMP + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                BillItem item = new BillItem();
                item.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                item.channel = c.getString(c.getColumnIndexOrThrow(COL_CHANNEL));
                item.amount = c.getDouble(c.getColumnIndexOrThrow(COL_AMOUNT));
                item.shop = c.getString(c.getColumnIndexOrThrow(COL_SHOP));
                item.remark = c.getString(c.getColumnIndexOrThrow(COL_REMARK));
                item.timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                item.dateStr = c.getString(c.getColumnIndexOrThrow(COL_DATE_STR));
                list.add(item);
            }
            c.close();
        }
        return list;
    }
}
