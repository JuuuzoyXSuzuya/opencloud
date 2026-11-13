package dev.opencloud.region;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.animation.OvershootInterpolator;
import android.widget.CompoundButton;

/**
 * Material 3 Expressive switch.
 *
 * <p>The platform switch is drawn by the theme and cannot be restyled beyond
 * tinting, so the expressive one is drawn by hand: a wide track, a thumb that
 * grows as it travels and an overshoot on the way back.  It stays a
 * {@link CompoundButton}, so every existing listener keeps working.</p>
 */
final class Toggle extends CompoundButton {
    private static final ArgbEvaluator EVALUATOR = new ArgbEvaluator();

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int trackOn;
    private final int trackOff;
    private final int thumbOn;
    private final int thumbOff;
    private float progress;
    private ValueAnimator animator;

    Toggle(Context context, int trackOn, int trackOff, int thumbOn, int thumbOff) {
        super(context);
        this.trackOn = trackOn;
        this.trackOff = trackOff;
        this.thumbOn = thumbOn;
        this.thumbOff = thumbOff;
        setButtonDrawable(null);
        setClickable(true);
        setFocusable(true);
        // The thumb casts a small shadow, which needs a software layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        thumbPaint.setShadowLayer(dp(2f), 0f, dp(1f), 0x40000000);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(18f));
        mask.setColor(0xFFFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (isChecked() == checked) {
            return;
        }
        super.setChecked(checked);
        float target = checked ? 1f : 0f;
        if (!isLaidOut() || !isAttachedToWindow()) {
            progress = target;
            invalidate();
            return;
        }
        animateTo(target);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Switch";
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(260);
        animator.setInterpolator(new OvershootInterpolator(1.6f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float trackHeight = dp(32f);
        float trackWidth = Math.min(width - dp(4f), dp(52f));
        float left = (width - trackWidth) / 2f;
        float top = (height - trackHeight) / 2f;
        float radius = trackHeight / 2f;
        float position = Math.max(0f, Math.min(1f, progress));

        trackPaint.setColor((Integer) EVALUATOR.evaluate(position, trackOff, trackOn));
        trackPaint.setAlpha(isEnabled() ? 255 : 140);
        canvas.drawRoundRect(left, top, left + trackWidth, top + trackHeight,
                radius, radius, trackPaint);

        float thumbRadius = dp(11f) + dp(2f) * position;
        float centerX = left + radius + (trackWidth - 2f * radius) * position;
        float centerY = top + radius;
        thumbPaint.setColor((Integer) EVALUATOR.evaluate(position, thumbOff, thumbOn));
        thumbPaint.setAlpha(isEnabled() ? 255 : 140);
        canvas.drawCircle(centerX, centerY, thumbRadius, thumbPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}