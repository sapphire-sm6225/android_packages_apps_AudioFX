/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.eq;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;

public class EqBarView extends FrameLayout implements StateCallbacks.EqUpdatedCallback {

    private static final String TAG = EqBarView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private EqualizerManager mEqManager;

    private float mNormalWidth;
    private float mParentHeight = -1;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosY = -1;
    private boolean mUserInteracting;
    private int mParentTop;
    private Integer mIndex;
    private float mInitialLevel;
    private final Context mContext;

    public EqBarView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public EqBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public EqBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private float mTrackWidth;
    private float mHandleHeight;
    private float mHandleGap;

    private void init() {
        setWillNotDraw(false);
        final android.content.res.Resources res = getResources();
        mTrackWidth = res.getDimension(R.dimen.eq_track_width);
        mHandleHeight = res.getDimension(R.dimen.eq_handle_height);
        mHandleGap = res.getDimension(R.dimen.eq_handle_gap);
        mPaint.setColor(res.getColor(R.color.afx_accent, null));
        mEqManager = MasterConfigControl.getInstance(mContext).getEqualizerManager();
        mNormalWidth = getResources().getDimension(R.dimen.eq_bar_width);
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        // handle on top, a gap, then the rounded fill down to the bottom
        final float cx = getWidth() / 2f;
        final float h = getHeight();
        mRect.set(cx - mNormalWidth / 2f, 0, cx + mNormalWidth / 2f, mHandleHeight);
        canvas.drawRoundRect(mRect, mHandleHeight / 2f, mHandleHeight / 2f, mPaint);
        final float fillTop = mHandleHeight + mHandleGap;
        if (h > fillTop) {
            mRect.set(cx - mTrackWidth / 2f, fillTop, cx + mTrackWidth / 2f, h);
            canvas.drawRoundRect(mRect, mTrackWidth / 2f, mTrackWidth / 2f, mPaint);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private EqContainerView.EqBandInfo getInfo() {
        return (EqContainerView.EqBandInfo) getTag();
    }

    public void setParentHeight(float h, int top) {
        mParentHeight = h;
        mParentTop = top;
        updateHeight();
    }

    void updateHeight() {
        if (DEBUG) Log.d(TAG, "updateHeight()");

        if (getInfo() != null) {
            float level = mEqManager.getLevel(getIndex());
            float yProjection = 1 - mEqManager.projectY(level);
            float height = (yProjection * (mParentHeight));
            mPosY = height;

            if (DEBUG) {
                Log.d(TAG, getIndex() + "level: " + level + ", yProjection: "
                        + yProjection + ", mPosY: " + mPosY);
            }
            updateHeight((int) mPosY);
        } else {
            if (DEBUG) Log.d(TAG, "could not updateHeight()");
        }
    }

    public int getIndex() {
        if (mIndex == null) {
            mIndex = (getInfo()).mIndex;
        }
        return mIndex;
    }

    public boolean isUserInteracting() {
        return mUserInteracting;
    }

    /* package */ void startInteraction(float x, float y) {

        mLastTouchX = x;
        mLastTouchY = y;
        mUserInteracting = true;

        if (DEBUG) Log.d(TAG, "initial level: " + mInitialLevel);
        mInitialLevel =
                (1 - (mPosY / mParentHeight)) * (mEqManager.getMinDB() - mEqManager.getMaxDB())
                        - mEqManager.getMinDB();

        updateWidth((int) (mNormalWidth * 2));
    }

    /* package */ void endInteraction() {
        mUserInteracting = false;

        updateWidth((int) mNormalWidth);
    }

    private void updateHeight(int h) {
        if (!isInLayout()) {
            final ViewGroup.LayoutParams params = getLayoutParams();
            params.height = h;
            setLayoutParams(params);
        }
    }

    private void updateWidth(int w) {
        final ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        setLayoutParams(params);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEqManager.isEqualizerLocked()) {
            return false;
        }

        final float x = event.getRawX();
        final float y = event.getRawY() - mParentTop;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startInteraction(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                mPosY -= dy;

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;

                int wy = (int) mParentHeight;
                float level = (1 - (mPosY / wy)) * (mEqManager.getMinDB() - mEqManager.getMaxDB())
                        - mEqManager.getMinDB();

                if (DEBUG) Log.d(TAG, "new level: " + level);
                if (level < mEqManager.getMinDB()) {
                    level = mEqManager.getMinDB();
                } else if (level > mEqManager.getMaxDB()) {
                    level = mEqManager.getMaxDB();
                }

                if (mInitialLevel != level) {
                    mEqManager.setLevel(getInfo().mIndex, level, false);
                } else {
                    updateHeight();
                }

                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                endInteraction();
                break;

        }

        return true;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (getInfo().mIndex != band) {
            return;
        }

        updateHeight();
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {

    }

    @Override
    public void onPresetsChanged() {

    }
}
