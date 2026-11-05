package com.example.devbox.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.devbox.R;
import com.google.android.material.button.MaterialButton;

public class DepthButton extends FrameLayout {

    private View shadowView;
    private MaterialButton faceButton;
    private float depth;
    private float cornerRadius;
    private boolean isPressedState = false;

    public DepthButton(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public DepthButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public DepthButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        // Prevent clipping of child views during animation
        setClipChildren(false);
        setClipToPadding(false);

        shadowView = new View(context);
        faceButton = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;

        // Default values
        int faceColor = Color.WHITE;
        int shadowColor = Color.parseColor("#4A7CED"); // Blue
        cornerRadius = 14f * density;
        depth = 6f * density;
        String buttonText = "";
        int textColor = Color.parseColor("#202124");
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, metrics);
        int fontResId = 0;
        int textStyle = Typeface.BOLD; // Default to bold

        int strokeWidth = (int) (2f * density);
        int strokeColor = shadowColor;

        // Icon defaults
        int iconResId = 0;
        int iconTint = textColor;
        int iconGravity = 2; // textStart
        int iconSize = 0;
        int iconPadding = 0;

        // Lines & Ellipsize defaults
        int maxLines = -1;
        boolean singleLine = false;
        int ellipsizeEnum = -1;

        // Read XML attributes
        if (attrs != null) {
            try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DepthButton, defStyleAttr, 0)) {
                // Standard Android attributes
                buttonText = a.getString(R.styleable.DepthButton_android_text);
                if (buttonText == null) buttonText = "";

                textColor = a.getColor(R.styleable.DepthButton_android_textColor, textColor);
                textSize = a.getDimension(R.styleable.DepthButton_android_textSize, textSize);
                textStyle = a.getInt(R.styleable.DepthButton_android_textStyle, textStyle);
                fontResId = a.getResourceId(R.styleable.DepthButton_android_fontFamily, 0);
                iconResId = a.getResourceId(R.styleable.DepthButton_android_icon, 0);
                maxLines = a.getInt(R.styleable.DepthButton_android_maxLines, maxLines);
                singleLine = a.getBoolean(R.styleable.DepthButton_android_singleLine, singleLine);
                ellipsizeEnum = a.getInt(R.styleable.DepthButton_android_ellipsize, ellipsizeEnum);

                // Custom 3D Depth Button attributes
                depth = a.getDimension(R.styleable.DepthButton_db_depth, depth);
                faceColor = a.getColor(R.styleable.DepthButton_db_faceColor, faceColor);
                shadowColor = a.getColor(R.styleable.DepthButton_db_shadowColor, shadowColor);
                cornerRadius = a.getDimension(R.styleable.DepthButton_db_cornerRadius, cornerRadius);
                strokeWidth = (int) a.getDimension(R.styleable.DepthButton_db_strokeWidth, strokeWidth);
                strokeColor = a.getColor(R.styleable.DepthButton_db_strokeColor, shadowColor);

                iconTint = a.getColor(R.styleable.DepthButton_db_iconTint, textColor);
                iconGravity = a.getInt(R.styleable.DepthButton_db_iconGravity, iconGravity);
                iconSize = a.getDimensionPixelSize(R.styleable.DepthButton_db_iconSize, 0);
                iconPadding = a.getDimensionPixelSize(R.styleable.DepthButton_db_iconPadding, 0);
            }
        }

        // 1. Create base layer (Shadow)
        GradientDrawable shadowDrawable = new GradientDrawable();
        shadowDrawable.setShape(GradientDrawable.RECTANGLE);
        shadowDrawable.setColor(shadowColor);
        shadowDrawable.setCornerRadius(cornerRadius);
        shadowView.setBackground(shadowDrawable);

        addView(shadowView);

        // 2. Create face layer (The button itself)
        faceButton.setText(buttonText);
        faceButton.setTextColor(textColor);
        faceButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        faceButton.setCornerRadius((int) cornerRadius);
        faceButton.setBackgroundTintList(ColorStateList.valueOf(faceColor));

        // Multiline & SingleLine configuration
        if (singleLine) {
            faceButton.setSingleLine(true);
        } else if (maxLines > 0) {
            faceButton.setMaxLines(maxLines);
        }

        // Ellipsize configuration
        if (ellipsizeEnum >= 0) {
            switch (ellipsizeEnum) {
                case 1:
                    faceButton.setEllipsize(TextUtils.TruncateAt.START);
                    break;
                case 2:
                    faceButton.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    break;
                case 3:
                    faceButton.setEllipsize(TextUtils.TruncateAt.END);
                    break;
                case 4:
                    faceButton.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                    break;
                default:
                    faceButton.setEllipsize(null);
                    break;
            }
        }

        // Stroke
        faceButton.setStrokeWidth(strokeWidth);
        faceButton.setStrokeColor(ColorStateList.valueOf(strokeColor));

        // Font Family & Style
        int typefaceStyle;
        switch (textStyle) {
            case 1:
                typefaceStyle = Typeface.BOLD;
                break;
            case 2:
                typefaceStyle = Typeface.ITALIC;
                break;
            case 3:
                typefaceStyle = Typeface.BOLD_ITALIC;
                break;
            case 0:
            default:
                typefaceStyle = Typeface.NORMAL;
                break;
        }

        if (fontResId != 0) {
            try {
                Typeface tf = ResourcesCompat.getFont(context, fontResId);
                if (tf != null) {
                    faceButton.setTypeface(tf, typefaceStyle);
                } else {
                    faceButton.setTypeface(faceButton.getTypeface(), typefaceStyle);
                }
            } catch (Exception ignored) {
                faceButton.setTypeface(faceButton.getTypeface(), typefaceStyle);
            }
        } else {
            faceButton.setTypeface(faceButton.getTypeface(), typefaceStyle);
        }

        // Reset Material defaults & min constraints to allow exact circular/square sizing
        faceButton.setStateListAnimator(null);
        faceButton.setElevation(0f);
        faceButton.setInsetTop(0);
        faceButton.setInsetBottom(0);
        faceButton.setMinimumWidth(0);
        faceButton.setMinimumHeight(0);
        faceButton.setMinWidth(0);
        faceButton.setMinHeight(0);

        // Icon Setup
        if (iconResId != 0) {
            faceButton.setIconResource(iconResId);
        }
        faceButton.setIconTint(ColorStateList.valueOf(iconTint));
        faceButton.setIconGravity(mapIconGravity(iconGravity));
        if (iconSize > 0) {
            faceButton.setIconSize(iconSize);
        }
        if (iconPadding > 0) {
            faceButton.setIconPadding(iconPadding);
        } else if (TextUtils.isEmpty(buttonText)) {
            faceButton.setIconPadding(0);
        }

        // Disable inner click/focus handling so FrameLayout receives touch events
        faceButton.setClickable(false);
        faceButton.setFocusable(false);

        addView(faceButton);

        setClickable(true);
        setFocusable(true);
    }

    private int mapIconGravity(int value) {
        switch (value) {
            case 1:
                return MaterialButton.ICON_GRAVITY_START;
            case 3:
                return MaterialButton.ICON_GRAVITY_END;
            case 4:
                return MaterialButton.ICON_GRAVITY_TEXT_END;
            case 16:
                return MaterialButton.ICON_GRAVITY_TOP;
            case 32:
                return MaterialButton.ICON_GRAVITY_TEXT_TOP;
            case 2:
            default:
                return MaterialButton.ICON_GRAVITY_TEXT_START;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int depthInt = (int) depth;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int faceWidthDimension = (widthMode == MeasureSpec.EXACTLY) ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        int faceHeightDimension = (heightMode == MeasureSpec.EXACTLY) ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;

        int childWidthSpec = getChildMeasureSpec(widthMeasureSpec, paddingLeft + paddingRight, faceWidthDimension);
        int childHeightSpec = getChildMeasureSpec(heightMeasureSpec, paddingTop + paddingBottom + depthInt, faceHeightDimension);

        faceButton.measure(childWidthSpec, childHeightSpec);

        int faceWidth = faceButton.getMeasuredWidth();
        int faceHeight = faceButton.getMeasuredHeight();

        // If text is empty (icon-only circular button) and width mode is wrap_content,
        // force faceWidth equal faceHeight so it forms a perfect square before rounding corners into a circle
        if (TextUtils.isEmpty(faceButton.getText()) && widthMode != MeasureSpec.EXACTLY) {
            int size = Math.max(faceWidth, faceHeight);
            faceWidth = size;
            faceHeight = size;

            int squareSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            faceButton.measure(squareSpec, squareSpec);
        }

        int shadowWidthSpec = MeasureSpec.makeMeasureSpec(faceWidth, MeasureSpec.EXACTLY);
        int shadowHeightSpec = MeasureSpec.makeMeasureSpec(faceHeight, MeasureSpec.EXACTLY);
        shadowView.measure(shadowWidthSpec, shadowHeightSpec);

        int totalWidth = faceWidth + paddingLeft + paddingRight;
        int totalHeight = faceHeight + depthInt + paddingTop + paddingBottom;

        setMeasuredDimension(
                resolveSize(totalWidth, widthMeasureSpec),
                resolveSize(totalHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();

        int faceWidth = faceButton.getMeasuredWidth();
        int faceHeight = faceButton.getMeasuredHeight();
        int depthInt = (int) depth;

        shadowView.layout(
                paddingLeft,
                paddingTop + depthInt,
                paddingLeft + faceWidth,
                paddingTop + depthInt + faceHeight
        );

        faceButton.layout(
                paddingLeft,
                paddingTop,
                paddingLeft + faceWidth,
                paddingTop + faceHeight
        );
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true; // Intercept all touch events so child views do not block parent gestures
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();
        boolean inside = isPointInside(x, y);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isPressedState = true;
                faceButton.setPressed(true);
                faceButton.animate().translationY(depth).setDuration(60).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPressedState && !inside) {
                    isPressedState = false;
                    faceButton.setPressed(false);
                    faceButton.animate().translationY(0f).setDuration(60).start();
                } else if (!isPressedState && inside) {
                    isPressedState = true;
                    faceButton.setPressed(true);
                    faceButton.animate().translationY(depth).setDuration(60).start();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isPressedState) {
                    isPressedState = false;
                    faceButton.setPressed(false);
                    faceButton.animate().translationY(0f).setDuration(60).start();
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (isPressedState) {
                    isPressedState = false;
                    faceButton.setPressed(false);
                    faceButton.animate().translationY(0f).setDuration(60).start();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean isPointInside(float x, float y) {
        return x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    // Public Getters and Setters

    public void setText(CharSequence text) {
        if (faceButton != null) {
            faceButton.setText(text);
        }
    }

    public CharSequence getText() {
        return faceButton != null ? faceButton.getText() : "";
    }

    public void setTextColor(int color) {
        if (faceButton != null) {
            faceButton.setTextColor(color);
        }
    }

    public void setTextStyle(int typefaceStyle) {
        if (faceButton != null) {
            faceButton.setTypeface(faceButton.getTypeface(), typefaceStyle);
        }
    }

    public void setFontFamily(int fontResId) {
        if (faceButton != null && fontResId != 0) {
            try {
                Typeface tf = ResourcesCompat.getFont(getContext(), fontResId);
                if (tf != null) {
                    faceButton.setTypeface(tf, faceButton.getTypeface() != null ? faceButton.getTypeface().getStyle() : Typeface.NORMAL);
                }
            } catch (Exception ignored) {}
        }
    }

    public void setFaceColor(int color) {
        if (faceButton != null) {
            faceButton.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    public void setShadowColor(int color) {
        if (shadowView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(color);
            drawable.setCornerRadius(cornerRadius);
            shadowView.setBackground(drawable);
        }
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        if (faceButton != null) {
            faceButton.setCornerRadius((int) radiusPx);
        }
        if (shadowView != null && shadowView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) shadowView.getBackground()).setCornerRadius(radiusPx);
        }
    }

    public void setStroke(int widthPx, int color) {
        if (faceButton != null) {
            faceButton.setStrokeWidth(widthPx);
            faceButton.setStrokeColor(ColorStateList.valueOf(color));
        }
    }

    public void setIconResource(int resId) {
        if (faceButton != null) {
            faceButton.setIconResource(resId);
        }
    }

    public void setIcon(Drawable icon) {
        if (faceButton != null) {
            faceButton.setIcon(icon);
        }
    }

    public void setIconTint(int color) {
        if (faceButton != null) {
            faceButton.setIconTint(ColorStateList.valueOf(color));
        }
    }

    public void setIconTint(ColorStateList tint) {
        if (faceButton != null) {
            faceButton.setIconTint(tint);
        }
    }

    public void setIconGravity(int gravity) {
        if (faceButton != null) {
            faceButton.setIconGravity(mapIconGravity(gravity));
        }
    }

    public void setIconSize(int sizePx) {
        if (faceButton != null) {
            faceButton.setIconSize(sizePx);
        }
    }

    public void setIconPadding(int paddingPx) {
        if (faceButton != null) {
            faceButton.setIconPadding(paddingPx);
        }
    }

    public void setMaxLines(int maxLines) {
        if (faceButton != null) {
            faceButton.setMaxLines(maxLines);
        }
    }

    public void setSingleLine(boolean singleLine) {
        if (faceButton != null) {
            faceButton.setSingleLine(singleLine);
        }
    }

    public void setEllipsize(TextUtils.TruncateAt where) {
        if (faceButton != null) {
            faceButton.setEllipsize(where);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (faceButton != null) {
            faceButton.setEnabled(enabled);
        }
        if (shadowView != null) {
            shadowView.setEnabled(enabled);
        }
    }
}
