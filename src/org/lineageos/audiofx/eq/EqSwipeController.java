/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.eq;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.preset.InfiniteViewPager;

public class EqSwipeController extends LinearLayout {

    /*
     * x velocity max for deciding whether to try to grab a bar
     */
    private static final int X_VELOCITY_THRESH = 20;

    private static final int MINIMUM_TIME_HOLD_TIME = 100;

    EqContainerView mEq;
    InfiniteViewPager mPager;
    private VelocityTracker mVelocityTracker = null;
    long mDownTime;
    EqBarView mBar;
    boolean mBarActive;
    private ViewGroup mControls;

    private final EqualizerManager mEqManager;
    private float mDownPositionX;
    private float mDownPositionY;

    public EqSwipeController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEqManager = MasterConfigControl.getInstance(context).getEqualizerManager();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEq = findViewById(R.id.eq_container);
        mPager = (InfiniteViewPager) findViewById(R.id.pager);
        mControls = findViewById(R.id.eq_controls);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // the preset chips sit above the card and must get their own taps and scrolls
        if (y < mEq.getTop()) {
            return false;
        }

        // don't intercept touches over the EQ controls (their bounds are card-local)
        final float cx = x - mEq.getLeft();
        final float cy = y - mEq.getTop();
        return !(mControls.getRight() > cx) || !(mControls.getTop() < cy)
                || !(mControls.getBottom() > cy) || !(mControls.getLeft() < cx);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // grab the bar under the finger straight away, like a slider
                mBar = null;
                mBarActive = false;
                if (!mEqManager.isChangingPresets() && !mEqManager.isEqualizerLocked()) {
                    final MotionEvent local = MotionEvent.obtain(event);
                    local.offsetLocation(-mEq.getLeft(), -mEq.getTop());
                    mBar = mEq.startTouchingBarUnder(local);
                    local.recycle();
                    mBarActive = mBar != null;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mBarActive && mBar != null) {
                    mEq.stopBarInteraction(mBar);
                    mBar.endInteraction();
                }
                mBar = null;
                mBarActive = false;
                break;
        }
        if (mBarActive && mBar != null) {
            return mBar.onTouchEvent(event);
        }
        return true;
    }
}
