package com.momo.pet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 精致线条/微填充矢量图标组件
 */
public class MenuIconView extends View {
    public enum IconType {
        STUDY, LEISURE, MUSIC, STATS, MONITOR, PAUSE, RESUME, STOP,
        NAV_FOCUS, NAV_BILL, NAV_MONITOR, NAV_MINE,
        SETTINGS, NOTIF, SOUND, POWER, CHEVRON_RIGHT, CHECK, PALETTE, SHIELD
    }

    private IconType iconType;
    private int iconColor;
    private Paint paint;
    private Paint strokePaint;
    private Path path;

    public MenuIconView(Context context, IconType type, int color) {
        super(context);
        this.iconType = type;
        this.iconColor = color;
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(iconColor);
        paint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(iconColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        path = new Path();
    }

    public void setIconType(IconType type, int color) {
        this.iconType = type;
        this.iconColor = color;
        paint.setColor(color);
        strokePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float s = Math.min(w, h) * 0.44f;
        strokePaint.setStrokeWidth(Math.max(2.2f, s * 0.14f));

        switch (iconType) {
            case NAV_FOCUS:
            case STUDY: {
                // 极简书本
                float bw = s * 0.70f;
                float bh = s * 0.56f;
                path.reset();
                path.moveTo(cx, cy + bh * 0.50f);
                path.quadTo(cx - bw * 0.5f, cy + bh * 0.35f, cx - bw, cy + bh * 0.45f);
                path.lineTo(cx - bw, cy - bh * 0.45f);
                path.quadTo(cx - bw * 0.5f, cy - bh * 0.55f, cx, cy - bh * 0.35f);
                path.close();

                path.moveTo(cx, cy + bh * 0.50f);
                path.quadTo(cx + bw * 0.5f, cy + bh * 0.35f, cx + bw, cy + bh * 0.45f);
                path.lineTo(cx + bw, cy - bh * 0.45f);
                path.quadTo(cx + bw * 0.5f, cy - bh * 0.55f, cx, cy - bh * 0.35f);
                path.close();

                canvas.drawPath(path, strokePaint);
                canvas.drawLine(cx, cy - bh * 0.35f, cx, cy + bh * 0.50f, strokePaint);
                break;
            }
            case NAV_BILL: {
                // 极简票据/信用卡
                float rw = s * 0.68f;
                float rh = s * 0.52f;
                RectF cardR = new RectF(cx - rw, cy - rh, cx + rw, cy + rh);
                canvas.drawRoundRect(cardR, s * 0.16f, s * 0.16f, strokePaint);
                canvas.drawLine(cx - rw * 0.55f, cy - rh * 0.30f, cx + rw * 0.15f, cy - rh * 0.30f, strokePaint);
                canvas.drawLine(cx - rw * 0.55f, cy + rh * 0.10f, cx - rw * 0.05f, cy + rh * 0.10f, strokePaint);
                canvas.drawCircle(cx + rw * 0.42f, cy + rh * 0.15f, s * 0.18f, strokePaint);
                break;
            }
            case NAV_MONITOR:
            case MONITOR:
            case SHIELD: {
                // 极简心安微弧盾牌
                float sw = s * 0.64f;
                float sh = s * 0.72f;
                path.reset();
                path.moveTo(cx, cy - sh);
                path.lineTo(cx + sw, cy - sh * 0.75f);
                path.lineTo(cx + sw, cy + sh * 0.05f);
                path.quadTo(cx + sw * 0.90f, cy + sh * 0.65f, cx, cy + sh);
                path.quadTo(cx - sw * 0.90f, cy + sh * 0.65f, cx - sw, cy + sh * 0.05f);
                path.lineTo(cx - sw, cy - sh * 0.75f);
                path.close();
                canvas.drawPath(path, strokePaint);

                path.reset();
                path.moveTo(cx - sw * 0.40f, cy - sh * 0.05f);
                path.lineTo(cx - sw * 0.08f, cy + sh * 0.24f);
                path.lineTo(cx + sw * 0.42f, cy - sh * 0.22f);
                canvas.drawPath(path, strokePaint);
                break;
            }
            case NAV_MINE: {
                // 极简人物轮廓
                float headR = s * 0.34f;
                canvas.drawCircle(cx, cy - s * 0.34f, headR, strokePaint);
                RectF bodyR = new RectF(cx - s * 0.72f, cy + s * 0.12f, cx + s * 0.72f, cy + s * 0.95f);
                canvas.drawArc(bodyR, -165, 150, false, strokePaint);
                break;
            }
            case LEISURE: {
                float cw = s * 0.38f;
                float ch = s * 0.34f;
                RectF cupRect = new RectF(cx - cw, cy - ch * 0.35f, cx + cw * 0.6f, cy + ch);
                canvas.drawRoundRect(cupRect, cw * 0.32f, cw * 0.32f, strokePaint);
                RectF handle = new RectF(cx + cw * 0.38f, cy - ch * 0.1f, cx + cw * 1.05f, cy + ch * 0.65f);
                canvas.drawArc(handle, -75, 150, false, strokePaint);
                path.reset();
                path.moveTo(cx - cw * 0.42f, cy - ch * 0.65f);
                path.quadTo(cx - cw * 0.18f, cy - ch * 0.95f, cx - cw * 0.38f, cy - ch * 1.25f);
                canvas.drawPath(path, strokePaint);
                path.reset();
                path.moveTo(cx + cw * 0.08f, cy - ch * 0.65f);
                path.quadTo(cx + cw * 0.32f, cy - ch * 0.95f, cx + cw * 0.12f, cy - ch * 1.25f);
                canvas.drawPath(path, strokePaint);
                break;
            }
            case MUSIC: {
                float nr = s * 0.14f;
                canvas.drawCircle(cx - s * 0.24f, cy + s * 0.25f, nr, paint);
                canvas.drawCircle(cx + s * 0.24f, cy + s * 0.12f, nr, paint);
                canvas.drawLine(cx - s * 0.24f + nr, cy + s * 0.25f, cx - s * 0.24f + nr, cy - s * 0.30f, strokePaint);
                canvas.drawLine(cx + s * 0.24f + nr, cy + s * 0.12f, cx + s * 0.24f + nr, cy - s * 0.43f, strokePaint);
                float oldW = strokePaint.getStrokeWidth();
                strokePaint.setStrokeWidth(s * 0.22f);
                canvas.drawLine(cx - s * 0.24f + nr, cy - s * 0.28f, cx + s * 0.24f + nr, cy - s * 0.41f, strokePaint);
                strokePaint.setStrokeWidth(oldW);
                break;
            }
            case STATS: {
                strokePaint.setStrokeWidth(s * 0.14f);
                canvas.drawLine(cx - s * 0.32f, cy + s * 0.38f, cx - s * 0.32f, cy + s * 0.06f, strokePaint);
                canvas.drawLine(cx, cy + s * 0.38f, cx, cy - s * 0.16f, strokePaint);
                canvas.drawLine(cx + s * 0.32f, cy + s * 0.38f, cx + s * 0.32f, cy - s * 0.40f, strokePaint);
                float thinW = Math.max(1.8f, s * 0.08f);
                strokePaint.setStrokeWidth(thinW);
                canvas.drawLine(cx - s * 0.45f, cy + s * 0.42f, cx + s * 0.45f, cy + s * 0.42f, strokePaint);
                break;
            }
            case SOUND: {
                // 喇叭音响
                path.reset();
                path.moveTo(cx - s * 0.50f, cy - s * 0.20f);
                path.lineTo(cx - s * 0.25f, cy - s * 0.20f);
                path.lineTo(cx + s * 0.15f, cy - s * 0.50f);
                path.lineTo(cx + s * 0.15f, cy + s * 0.50f);
                path.lineTo(cx - s * 0.25f, cy + s * 0.20f);
                path.lineTo(cx - s * 0.50f, cy + s * 0.20f);
                path.close();
                canvas.drawPath(path, strokePaint);
                // 声波弧
                RectF wR = new RectF(cx - s * 0.15f, cy - s * 0.35f, cx + s * 0.45f, cy + s * 0.35f);
                canvas.drawArc(wR, -45, 90, false, strokePaint);
                break;
            }
            case NOTIF: {
                // 小铃铛
                path.reset();
                path.moveTo(cx, cy - s * 0.60f);
                path.quadTo(cx + s * 0.45f, cy - s * 0.20f, cx + s * 0.55f, cy + s * 0.35f);
                path.lineTo(cx - s * 0.55f, cy + s * 0.35f);
                path.quadTo(cx - s * 0.45f, cy - s * 0.20f, cx, cy - s * 0.60f);
                path.close();
                canvas.drawPath(path, strokePaint);
                canvas.drawCircle(cx, cy + s * 0.50f, s * 0.12f, paint);
                break;
            }
            case PALETTE: {
                // 调色盘/外观
                RectF pR = new RectF(cx - s * 0.65f, cy - s * 0.60f, cx + s * 0.65f, cy + s * 0.60f);
                canvas.drawRoundRect(pR, s * 0.30f, s * 0.30f, strokePaint);
                canvas.drawCircle(cx - s * 0.25f, cy - s * 0.18f, s * 0.10f, paint);
                canvas.drawCircle(cx + s * 0.15f, cy - s * 0.18f, s * 0.10f, paint);
                canvas.drawCircle(cx - s * 0.05f, cy + s * 0.20f, s * 0.10f, paint);
                break;
            }
            case SETTINGS: {
                canvas.drawCircle(cx, cy, s * 0.28f, strokePaint);
                int spokes = 6;
                for (int i = 0; i < spokes; i++) {
                    double rad = Math.toRadians(i * (360.0 / spokes));
                    float x1 = cx + (float) (s * 0.45f * Math.cos(rad));
                    float y1 = cy + (float) (s * 0.45f * Math.sin(rad));
                    float x2 = cx + (float) (s * 0.72f * Math.cos(rad));
                    float y2 = cy + (float) (s * 0.72f * Math.sin(rad));
                    canvas.drawLine(x1, y1, x2, y2, strokePaint);
                }
                break;
            }
            case POWER: {
                RectF pRect = new RectF(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f);
                canvas.drawArc(pRect, -60, 300, false, strokePaint);
                canvas.drawLine(cx, cy - s * 0.65f, cx, cy - s * 0.10f, strokePaint);
                break;
            }
            case CHEVRON_RIGHT: {
                path.reset();
                float cw = s * 0.28f;
                float ch = s * 0.42f;
                path.moveTo(cx - cw, cy - ch);
                path.lineTo(cx + cw, cy);
                path.lineTo(cx - cw, cy + ch);
                canvas.drawPath(path, strokePaint);
                break;
            }
            case CHECK: {
                path.reset();
                path.moveTo(cx - s * 0.35f, cy);
                path.lineTo(cx - s * 0.05f, cy + s * 0.30f);
                path.lineTo(cx + s * 0.40f, cy - s * 0.30f);
                canvas.drawPath(path, strokePaint);
                break;
            }
            case PAUSE: {
                float ph = s * 0.38f;
                strokePaint.setStrokeWidth(s * 0.18f);
                canvas.drawLine(cx - s * 0.18f, cy - ph, cx - s * 0.18f, cy + ph, strokePaint);
                canvas.drawLine(cx + s * 0.18f, cy - ph, cx + s * 0.18f, cy + ph, strokePaint);
                break;
            }
            case RESUME: {
                float tw = s * 0.36f;
                float th = s * 0.42f;
                path.reset();
                path.moveTo(cx - tw * 0.55f, cy - th);
                path.lineTo(cx + tw * 0.85f, cy);
                path.lineTo(cx - tw * 0.55f, cy + th);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
            case STOP: {
                float rw = s * 0.34f;
                RectF stopR = new RectF(cx - rw, cy - rw, cx + rw, cy + rw);
                canvas.drawRoundRect(stopR, rw * 0.28f, rw * 0.28f, paint);
                break;
            }
        }
    }
}