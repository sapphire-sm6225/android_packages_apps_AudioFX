/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.eq;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;

import java.util.ArrayList;
import java.util.List;

public class EqContainerView extends FrameLayout
        implements StateCallbacks.EqUpdatedCallback, StateCallbacks.EqControlStateCallback {

    private static final String TAG = EqContainerView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private int mWidth;
    private int mHeight;
    private MasterConfigControl mConfig;
    private EqualizerManager mEqManager;
    private List<EqBandInfo> mBandInfo;
    private List<EqBarView> mBarViews;
    private List<Integer> mSelectedBands;

    private CheckBox mLockBox;
    private ImageView mRenameControl;
    private ImageView mRemoveControl;
    private ImageView mSaveControl;
    private ViewGroup mControls;
    private boolean mControlsVisible;

    private boolean mSaveVisible;
    private boolean mRemoveVisible;
    private boolean mRenameVisible;
    private boolean mUnlockVisible;

    private int mSelectedBandColor;
    private boolean mFirstLayout = true;

    private Paint mTextPaint;
    private Paint mFreqPaint;
    private Paint mSelectedFreqPaint;
    private Paint mCenterLinePaint;
    private final Paint mScalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF mTrackRect = new android.graphics.RectF();
    private Path mDashPath;

    private Context mContext;
    private final Runnable mVibrateRunnable = new Runnable() {
        @Override
        public void run() {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(30);
        }
    };

    private int mPaddingTop;
    private int mPaddingBottom;
    private int mBarWidth;
    private int mBarSeparation;
    private int mBarBottomGrabSpacePadding;

    public void stopListening() {
        for (EqBarView barView : mBarViews) {
            barView.setTag(null);
            mConfig.getCallbacks().removeEqUpdatedCallback(barView);
        }
        mConfig.getCallbacks().removeEqUpdatedCallback(this);
    }

    public void startListening() {
        for (int i = 0; i < mBandInfo.size(); i++) {

            final EqBarView eqBarView = mBarViews.get(i);
            eqBarView.setTag(mBandInfo.get(i));
            mConfig.getCallbacks().addEqUpdatedCallback(eqBarView);
        }
        mConfig.getCallbacks().addEqUpdatedCallback(this);
    }

    public static class EqBandInfo {
        public int mIndex;

        public String mFreq;
        public String mDb;
        public EqBarView mBar;
    }

    public EqContainerView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public EqContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public EqContainerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        final Resources r = getResources();

        mBarWidth = r.getDimensionPixelSize(R.dimen.eq_bar_width);
        mBarSeparation = r.getDimensionPixelSize(R.dimen.separator_width);

        mBarBottomGrabSpacePadding = r.getDimensionPixelSize(R.dimen.eq_bar_bottom_grab_space);
        int freqTextSize = r.getDimensionPixelSize(R.dimen.eq_label_text_size);
        int selectedBoxTextSize = r.getDimensionPixelSize(R.dimen.eq_selected_box_height);

        int extraTopSpace = r.getDimensionPixelSize(R.dimen.eq_bar_top_padding);

        mPaddingTop = selectedBoxTextSize + extraTopSpace
                + r.getDimensionPixelSize(R.dimen.eq_header_height);
        mPaddingBottom = selectedBoxTextSize + mBarBottomGrabSpacePadding
                + r.getDimensionPixelSize(R.dimen.eq_bottom_padding);

        mConfig = MasterConfigControl.getInstance(mContext);
        mEqManager = mConfig.getEqualizerManager();

        mBarViews = new ArrayList<>();
        mBandInfo = new ArrayList<>();
        mSelectedBands = new ArrayList<>();

        setWillNotDraw(false);

        mSelectedBandColor = r.getColor(R.color.afx_accent, mContext.getTheme());
        final int onSurface = r.getColor(R.color.afx_on_surface, mContext.getTheme());

        final int variant = r.getColor(R.color.afx_on_surface_variant, mContext.getTheme());
        final float density = r.getDisplayMetrics().density;
        mTitlePaint.setColor(r.getColor(R.color.afx_on_surface, mContext.getTheme()));
        mTitlePaint.setTextSize(18 * density);
        mTitlePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        mSubtitlePaint.setColor(variant);
        mSubtitlePaint.setTextSize(14 * density);
        mScalePaint.setColor(variant);
        mScalePaint.setTextSize(r.getDimension(R.dimen.eq_label_text_size));
        mTrackPaint.setColor(r.getColor(R.color.afx_track, mContext.getTheme()));

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(onSurface);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setElegantTextHeight(true);
        mTextPaint.setTextSize(13 * r.getDisplayMetrics().density);

        mFreqPaint = new Paint();
        mFreqPaint.setAntiAlias(true);
        mFreqPaint.setColor(variant);
        mFreqPaint.setTextAlign(Paint.Align.CENTER);
        mFreqPaint.setTextSize(freqTextSize);

        mSelectedFreqPaint = new Paint(mFreqPaint);
        mSelectedFreqPaint.setAntiAlias(true);
        mSelectedFreqPaint.setTextSize(selectedBoxTextSize);

        mCenterLinePaint = new Paint();
        mCenterLinePaint.setColor(onSurface);
        mCenterLinePaint.setAntiAlias(true);
        mCenterLinePaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));
        mCenterLinePaint.setStyle(Paint.Style.STROKE);
        mCenterLinePaint.setAntiAlias(true);

        getViewTreeObserver().addOnGlobalLayoutListener(this::generateAndAddBars);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mControls = findViewById(R.id.eq_controls);

        mLockBox = findViewById(R.id.lock);
        mLockBox.setOnCheckedChangeListener(mEqManager.getLockChangeListener());

        mRenameControl = findViewById(R.id.rename);
        mRemoveControl = findViewById(R.id.remove);
        mSaveControl = findViewById(R.id.save);
    }

    @Override
    protected void onAttachedToWindow() {
        if (DEBUG) Log.d(TAG, "onAttachedToWindow()");
        super.onAttachedToWindow();

        mConfig.getCallbacks().addEqControlStateCallback(this);
        onPresetChanged(mEqManager.getCurrentPresetIndex()); // update initial state
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DEBUG) Log.d(TAG, "onDetachedFromWindow()");
        mConfig.getCallbacks().removeEqControlStateCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    private void generateAndAddBars() {
        if (mFirstLayout) {
            mFirstLayout = false;
            mBarViews.clear();

            for (int i = 0; i < mEqManager.getNumBands(); i++) {
                final EqBandInfo band = new EqBandInfo();
                band.mIndex = i;
                mBandInfo.add(band);

                final EqBarView bar = new EqBarView(mContext);
                band.mBar = bar;
                bar.setTag(band);
                bar.setOnTouchListener((v, event) -> {
                    if (mEqManager.isEqualizerLocked()) {
                        return false;
                    }
                    switch (event.getActionMasked()) {

                        case MotionEvent.ACTION_DOWN:
                            startBarInteraction(bar);
                            break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:
                            stopBarInteraction(bar);
                            break;
                    }

                    return false;
                });

                // set correct initial alpha
                if (i % 2 == 0) {
                    bar.setAlpha(1f);
                } else {
                    bar.setAlpha(1f);
                }

                addView(bar, getFrameParams(i));
                bar.setParentHeight(mHeight, getTop());

                final float freq = mEqManager.getCenterFreq(i);
                band.mFreq = String.format(freq < 1000 ? "%.0f Hz" : "%.0f kHz",
                        freq < 1000 ? freq : freq / 1000);;
                mBarViews.add(bar);
            }
            updateSelectedBands();
        } else {
            for (EqBarView barView : mBarViews) {
                barView.setParentHeight(mHeight, getTop());
            }
        }
    }

    public EqBarView startTouchingBarUnder(MotionEvent event) {
        EqBarView foundBar = findBar(event.getX(), event.getY(), mBarViews);
        if (foundBar != null) {
            foundBar.updateHeight();

            foundBar.startInteraction(event.getRawX(), event.getRawY());
            startBarInteraction(foundBar);
        }
        return foundBar;
    }

    public void startBarInteraction(EqBarView bar) {
        setControlsVisible(false, false);
        EqBandInfo band = (EqBandInfo) bar.getTag();
        mSelectedBands.add(band.mIndex);
        updateSelectedBands();
        AsyncTask.execute(mVibrateRunnable);
    }

    public void stopBarInteraction(EqBarView bar) {
        EqBandInfo band = (EqBandInfo) bar.getTag();
        mSelectedBands.remove((Integer) band.mIndex);
        updateSelectedBands();
        setControlsVisible(mControlsVisible, true);
    }

    private EqBarView findBar(float x, float y, List<EqBarView> targets) {
        final int count = targets.size();
        for (int i = 0; i < count; i++) {
            final EqBarView target = targets.get(i);
            if (target.getRight() > x && target.getTop() < y
                    && target.getBottom() > y && target.getLeft() < x) {
                return target;
            }
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h - mPaddingTop - mPaddingBottom;
        generateAndAddBars();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        //---------------------------------------------------

        if (mFirstLayout) {
            return;
        }

        // child coordinates are relative to this view, not to its parent
        final int w = right - left;
        final int h = bottom - top;
        int dashY = h - mPaddingBottom - (mHeight / 2);

        final int widthOfBars = (mEqManager.getNumBands() * mBarWidth)
                + ((mEqManager.getNumBands() - 1) * mBarSeparation);
        final int freeSpace = mWidth - widthOfBars;

        // leave room on the left for the dB scale
        final int scaleGutter = (int) (10 * getResources().getDisplayMetrics().density);
        int mCurLeft = (freeSpace / 2) + scaleGutter;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child instanceof EqBarView) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                int l = mCurLeft;
                int r = l + mBarWidth;

                mCurLeft += mBarWidth + mBarSeparation;

                if (((EqBarView) child).isUserInteracting()) {
                    l -= childWidth / 4;
                    r += childWidth / 4;
                }

                final int layoutTop = mHeight - childHeight + mPaddingTop;
                final int layoutBottom = layoutTop + childHeight
                        + mPaddingBottom - (mPaddingBottom - mBarBottomGrabSpacePadding);
                child.layout(l, layoutTop, r, layoutBottom);
            }
        }

        if (changed || mDashPath == null) {
            mDashPath = new Path();
            mDashPath.reset();
            mDashPath.moveTo(freeSpace / 2f + scaleGutter, dashY);
            mDashPath.lineTo(widthOfBars + (freeSpace / 2f) + scaleGutter, dashY);
        }

        mControls.layout(
                w - mControls.getMeasuredWidth() - mControls.getPaddingLeft(),
                mControls.getPaddingTop(),
                w - mControls.getPaddingRight(),
                mControls.getMeasuredHeight() + mControls.getPaddingTop()
                        + mControls.getPaddingBottom()
        );
    }


    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // card header: title and the current preset
        final float d = getResources().getDisplayMetrics().density;
        canvas.drawText(getResources().getString(R.string.eq_title), 20 * d, 34 * d, mTitlePaint);
        canvas.drawText(getResources().getString(R.string.eq_preset_format,
                mEqManager.getLocalizedPresetName(mEqManager.getCurrentPresetIndex())),
                20 * d, 54 * d, mSubtitlePaint);

        // full-height rounded track behind each bar
        final float tw = getResources().getDimension(R.dimen.eq_track_width);
        for (int i = 0; i < mBandInfo.size(); i++) {
            final EqBarView bar = mBandInfo.get(i).mBar;
            final float cx = bar.getX() + (bar.getWidth() / 2f);
            mTrackRect.set(cx - tw / 2f, mPaddingTop, cx + tw / 2f, bar.getBottom());
            canvas.drawRoundRect(mTrackRect, tw / 2f, tw / 2f, mTrackPaint);
        }

        canvas.drawPath(mDashPath, mCenterLinePaint);

        // dB scale on the left: max at the top of the tracks, 0 on the dashed line, min at the bottom
        final float scaleX = getResources().getDisplayMetrics().density * 20; // same edge as the title
        final float half = mScalePaint.getTextSize() / 3f;
        mScalePaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format("%+d dB", Math.round(mEqManager.getMaxDB())), scaleX,
                mPaddingTop + half, mScalePaint);
        canvas.drawText("0 dB", scaleX, mPaddingTop + (mHeight / 2f) + half, mScalePaint);
        canvas.drawText(String.format("%d dB", Math.round(mEqManager.getMinDB())), scaleX,
                mPaddingTop + mHeight + half, mScalePaint);

        for (int i = 0; i < mBandInfo.size(); i++) {
            EqBandInfo info = mBandInfo.get(i);

            final float x = info.mBar.getX() + (info.mBar.getWidth() / 2f);
            final boolean userInteracting = info.mBar.isUserInteracting();
            // current level above the handle, always visible
            final float level = mEqManager.getLevel(i);
            canvas.drawText(String.format("%+d", Math.round(level)).replace("+0", "0"), x,
                    info.mBar.getY() - (mTextPaint.getTextSize() / 2), mTextPaint);

            Paint drawPaint = userInteracting ? mSelectedFreqPaint : mFreqPaint;

            canvas.drawText(info.mFreq, x,
                    info.mBar.getBottom() + drawPaint.getTextSize(),
                    drawPaint);
        }
    }

    private void updateSelectedBands() {
        for (int i = 0; i < mEqManager.getNumBands(); i++) {
            EqBandInfo tag = mBandInfo.get(i);
            final EqBarView bar = findViewWithTag(tag);
            if (bar != null) {
                final ViewPropertyAnimator barAnimation = bar.animate().withLayer();
                if (mSelectedBands.isEmpty()) {
                    if (i % 2 == 0) {
                        barAnimation.alpha(1f);
                    } else {
                        barAnimation.alpha(1f);
                    }
                } else if (mSelectedBands.contains(i)) {
                    barAnimation.alpha(1f);
                } else {
                    barAnimation.alpha(0.40f);
                }
            }
        }
    }

    private FrameLayout.LayoutParams getFrameParams(int index) {
        int width = getResources().getDimensionPixelSize(R.dimen.eq_bar_width);
        int height = Math.round((1 - mEqManager.projectY(mEqManager.getLevel(index))) * mHeight);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(width, height);
        ll.gravity = Gravity.TOP;
        return ll;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (mFirstLayout) return;
        mBandInfo.get(band).mDb = dB != 0 ? String.format("%+1.1f", dB) : "0.0";
        invalidate();
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {
        updateEqState();
        invalidate();
        if (mEqManager.isUserPreset()) {
            mLockBox.setChecked(mEqManager.isEqualizerLocked());
        }
    }

    @Override
    public void updateEqState(boolean saveVisible, boolean removeVisible,
            boolean renameVisible, boolean unlockVisible) {
        mControlsVisible = mEqManager.isUserPreset() || mEqManager.isCustomPreset();
        mSaveVisible = saveVisible;
        mRemoveVisible = removeVisible;
        mRenameVisible = renameVisible;
        mUnlockVisible = unlockVisible;
        updateEqState();
    }

    public void updateEqState() {
        setControlsVisible(mControlsVisible && mSelectedBands.isEmpty(), false);

        animateControl(mLockBox, mUnlockVisible);
        animateControl(mRemoveControl, mRemoveVisible);
        animateControl(mRenameControl, mRenameVisible);
        animateControl(mSaveControl, mSaveVisible);
    }

    private void animateControl(final View v, boolean visible) {
        if (visible) {
            v.setVisibility(View.VISIBLE);
            v.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .withEndAction(null);
        } else {
            v.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction(() -> v.setVisibility(View.INVISIBLE));
        }
    }

    @Override
    public void onPresetsChanged() {
    }

    public void setControlsVisible(boolean visible, boolean keepChange) {
        if (keepChange) {
            mControlsVisible = visible;
        }

        if (mControls != null) {
            animateControl(mControls, visible);
        }
    }

}
