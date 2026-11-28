package com.example.devbox.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;

import com.example.devbox.R;

public class UploadHoldButton extends AppCompatImageView {

    private final ValueAnimator timer = new ValueAnimator();
    private SeekableAnimatedVectorDrawable holdAvd;
    private SeekableAnimatedVectorDrawable loaderAvd;
    private MediaPlayer seHold;
    private MediaPlayer seSuccess;
    private long maxDuration;
    private Runnable onUploadTriggerListener;

    public UploadHoldButton(@NonNull Context context) {
        super(context);
        init(context);
    }

    public UploadHoldButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UploadHoldButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);

        if (isInEditMode()) {
            setImageResource(R.drawable.avd_upload_hold);
            return;
        }

        holdAvd = SeekableAnimatedVectorDrawable.create(context, R.drawable.avd_upload_hold);
        setImageDrawable(holdAvd);
        maxDuration = holdAvd != null ? holdAvd.getTotalDuration() : 0L;

        seHold = MediaPlayer.create(context, R.raw.se_button_charge);
        seSuccess = MediaPlayer.create(context, R.raw.se_upload_success);

        timer.setInterpolator(new LinearInterpolator());
        timer.addUpdateListener(animation -> {
            if (holdAvd == null) return;
            int animatedValue = (int) animation.getAnimatedValue();
            long playTime = Math.max(0L, Math.min(maxDuration, animatedValue));
            holdAvd.setCurrentPlayTime(playTime);
        });
        timer.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                switchToLoader();
            }
        });
    }

    public void setOnUploadTriggerListener(@Nullable Runnable listener) {
        this.onUploadTriggerListener = listener;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || loaderAvd != null || maxDuration <= 0) {
            return true;
        }

        int currentPos = timer.getAnimatedValue() != null ? (int) timer.getAnimatedValue() : 0;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (seHold != null) {
                    seHold.seekTo(0);
                    seHold.start();
                }
                timer.cancel();
                timer.setIntValues(currentPos, (int) maxDuration);
                timer.setDuration(maxDuration - currentPos);
                timer.start();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentPos > 0 && currentPos < maxDuration) {
                    if (seHold != null && seHold.isPlaying()) {
                        seHold.pause();
                    }
                    timer.cancel();
                    timer.setIntValues(currentPos, 0);
                    timer.setDuration(300L);
                    timer.start();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void switchToLoader() {
        if (loaderAvd != null) return;
        if (timer.getAnimatedValue() != null && (int) timer.getAnimatedValue() == maxDuration) {
            loaderAvd = SeekableAnimatedVectorDrawable.create(getContext(), R.drawable.avd_upload_loader);
            setImageDrawable(loaderAvd);
            if (loaderAvd != null) {
                setClickable(false);
                loaderAvd.start();
                if (onUploadTriggerListener != null) {
                    onUploadTriggerListener.run();
                }
            }
        }
    }

    public void playFinishAnimation(@Nullable Runnable onAnimationEnd) {
        if (loaderAvd != null) {
            loaderAvd.stop();
        }
        SeekableAnimatedVectorDrawable finishAvd =
                SeekableAnimatedVectorDrawable.create(getContext(), R.drawable.avd_upload_finish);
        setImageDrawable(finishAvd);

        if (finishAvd != null) {
            finishAvd.registerAnimationCallback(new SeekableAnimatedVectorDrawable.AnimationCallback() {
                @Override
                public void onAnimationEnd(@NonNull SeekableAnimatedVectorDrawable drawable) {
                    finishAvd.unregisterAnimationCallback(this);
                    if (onAnimationEnd != null) {
                        onAnimationEnd.run();
                    }
                }
            });
            finishAvd.start();
        } else if (onAnimationEnd != null) {
            onAnimationEnd.run();
        }

        if (seSuccess != null) {
            seSuccess.start();
        }
    }

    public void reset() {
        timer.cancel();
        if (seHold != null && seHold.isPlaying()) {
            seHold.pause();
        }
        if (loaderAvd != null) {
            loaderAvd.stop();
            loaderAvd = null;
        }
        if (holdAvd != null) {
            holdAvd.setCurrentPlayTime(0);
            setImageDrawable(holdAvd);
        }
        setClickable(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timer.cancel();
        if (loaderAvd != null) {
            loaderAvd.stop();
        }
        if (seHold != null) {
            seHold.release();
            seHold = null;
        }
        if (seSuccess != null) {
            seSuccess.release();
            seSuccess = null;
        }
    }
}
