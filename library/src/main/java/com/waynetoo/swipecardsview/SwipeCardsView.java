package com.waynetoo.swipecardsview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;

import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;

import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

public class SwipeCardsView extends LinearLayout {
    private final String TAG = "SwipeCardsView";
    private List<View> viewList = new ArrayList<>(); // 存放的是每一层的view，从顶到底
    private List<View> releasedViewList = new ArrayList<>(); // 手指松开后存放的view列表

    private int initLeft = 0, initTop = 0; // 正常状态下 topView的left和top
    private int mWidth = 0; // swipeCardsView的宽度
    private int mHeight = 0; // swipeCardsView的高度
    private int mCardWidth = 0; // 每一个子View对应的宽度
    private int mCardHeight = 0; // 每一个子View对应的宽度

    private static final int MAX_SLIDE_DISTANCE_LINKAGE = 400; // 水平距离+垂直距离

    private int yOffsetStep = 0; // view叠加垂直偏移量的步长
    private float scaleOffsetStep = 0f; // view叠加缩放的步长
    private int alphaOffsetStep = 0; //view叠加透明度的步长

    private static final int X_VEL_THRESHOLD = 900;
    private static final int Y_VEL_THRESHOLD = 900;
    private static final int X_DISTANCE_THRESHOLD = 300;
    private static final int Y_DISTANCE_THRESHOLD = 400;

    private CardsSlideListener mCardsSlideListener; // 回调接口
    private int mCount; // 卡片的数量
    private int mShowingIndex = 0; // 当前正在显示的卡片位置
    private OnClickListener btnListener;

    private BaseCardAdapter mAdapter;
    private Scroller mScroller;
    private int mTouchSlop;
    private int mLastY = -1; // save event y
    //    private int mLastX = -1; // save event x
    private int mInitialMotionY;
    private int mInitialMotionX;
    private final int SCROLL_DURATION = 200; // scroll back duration
    private boolean hasTouchTopView;
    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;
    private float mMinVelocity;
    private boolean isIntercepted = false;
    private boolean isTouching = false;
    private int tempShowingIndex = -1;
    private int cardVisibleCount = 3;
    /**
     * 卡片是否在移动的标记，如果在移动中则不执行onLayout中的layout操作
     */
    private boolean mScrolling = false;
    //是否在下拉
    private boolean mIsPull = false;

    public SwipeCardsView(Context context) {
        this(context, null);
    }

    public SwipeCardsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeCardsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwipCardsView);
        yOffsetStep = (int) a.getDimension(R.styleable.SwipCardsView_yOffsetStep, yOffsetStep);
        alphaOffsetStep = a.getInt(R.styleable.SwipCardsView_alphaOffsetStep, alphaOffsetStep);
        scaleOffsetStep = a.getFloat(R.styleable.SwipCardsView_scaleOffsetStep, scaleOffsetStep);

        a.recycle();

        btnListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (null != mCardsSlideListener && view.getScaleX() == 1f) {
                    mCardsSlideListener.onItemClick(view, mShowingIndex);
                }
            }
        };
        mScroller = new Scroller(getContext(), new LinearInterpolator());
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mMaxVelocity = ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity();
        mMinVelocity = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
//        setWillNotDraw(false);
    }

    /**
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = new Interpolator() {

        private float mTension = 1.6f;

        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            float t1 = t * t * ((mTension + 1) * t + mTension) + 1.0f;
            Log.i("SwipeCardsView", "getInterpolation: " + t1);
            return t1;
        }
    };

    private int getCardLayoutId(int layoutid) {
        String resourceTypeName = getContext().getResources().getResourceTypeName(layoutid);
        if (!resourceTypeName.contains("layout")) {
            String errorMsg = getContext().getResources().getResourceName(layoutid) + " is a illegal layoutid , please check your layout id first !";
            throw new RuntimeException(errorMsg);
        }
        return layoutid;
    }

    private void bindCardData(int position, View cardview) {
        if (mAdapter != null) {
            mAdapter.onBindData(position, cardview);
            cardview.setTag(position);
        }
        cardview.setVisibility(View.VISIBLE);
    }

    private boolean mWaitRefresh = false;


    /**
     * 更新cardElevation
     */
    private void syncCardElevation() {
        for (int i = 0; i < viewList.size(); i++) {
            CardView item = (CardView) viewList.get(i);
            item.setCardElevation(viewList.size() - i + getContext().getResources().getDimension(R.dimen.card_elevation));
        }
    }

    /**
     * 刷新ui
     *
     * @param index 当前显示的卡片下标
     */
    public void notifyDatasetChanged(int index) {
        if (canResetView()) {
//            LogUtil.d("test notifyDatasetChanged canResetView=" + index);
            refreshUI(index);
        } else {
//            LogUtil.d("test notifyDatasetChanged can not Reset View=" + index);
            mWaitRefresh = true;
            tempShowingIndex = index;
        }
    }

    private void refreshUI(int index) {
        if (mAdapter == null) {
            throw new RuntimeException("adapter==null");
        }
        mShowingIndex = index;
        mCount = mAdapter.getCount();
        cardVisibleCount = mAdapter.getVisibleCardCount();
        cardVisibleCount = Math.min(cardVisibleCount, mCount);
        for (int i = mShowingIndex; i < mShowingIndex + cardVisibleCount; i++) {
            View childView = viewList.get(i - mShowingIndex);
            if (childView == null) {
                return;
            }
            if (i < mCount) {
                bindCardData(i, childView);
            } else {
                childView.setVisibility(View.GONE);
            }
            setOnItemClickListener(childView);
        }
        if (null != mCardsSlideListener) {
            mCardsSlideListener.onShow(mShowingIndex);
        }
        syncCardElevation();
    }

    private void setOnItemClickListener(View childView) {
        childView.setOnClickListener(btnListener);
    }

    public void setAdapter(BaseCardAdapter adapter) {
        if (adapter == null) {
            throw new RuntimeException("adapter==null");
        }
        mAdapter = adapter;
        mShowingIndex = 0;
        removeAllViewsInLayout();
        viewList.clear();
        mCount = mAdapter.getCount();
        int cardVisibleCount = mAdapter.getVisibleCardCount();
        cardVisibleCount = Math.min(cardVisibleCount, mCount);
        for (int i = mShowingIndex; i < mShowingIndex + cardVisibleCount; i++) {
            View childView = LayoutInflater.from(getContext()).inflate(getCardLayoutId(mAdapter.getCardLayoutId()), this, false);
            if (childView == null) {
                return;
            }
            if (i < mCount) {
                bindCardData(i, childView);
            } else {
                childView.setVisibility(View.GONE);
            }
            viewList.add(childView);
            setOnItemClickListener(childView);
            addView(childView, 0);
        }
        if (null != mCardsSlideListener) {
            mCardsSlideListener.onShow(mShowingIndex);
        }
        syncCardElevation();
    }

    private boolean mRetainLastCard = false;

    /**
     * whether retain last card
     *
     * @param retain defalut false
     */
    public void retainLastCard(boolean retain) {
        mRetainLastCard = retain;
    }

    private boolean canMoveCard() {
        return !mRetainLastCard || mRetainLastCard && mShowingIndex != mCount - 1;
    }

    private boolean mEnableSwipe = true;

    public void enableSwipe(boolean enable) {
        mEnableSwipe = enable;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        acquireVelocityTracker(ev);
        int deltaY = 0;
        int deltaX = 0;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScroller.abortAnimation();
                resetViewGroup();
                if (isTouchTopView(ev) /*&& canMoveCard()*/ && mEnableSwipe) {
                    isTouching = true;
                }
                hasTouchTopView = false;
                mLastY = (int) ev.getRawY();
                mInitialMotionY = mLastY;
                break;
            case MotionEvent.ACTION_MOVE:
                if (/*!canMoveCard() || */!mEnableSwipe) {
                    return super.dispatchTouchEvent(ev);
                }
                mLastMoveEvent = ev;
                int currentY = (int) ev.getRawY();
                deltaY = currentY - mLastY;
                mLastY = currentY;
//                Log.i(TAG, "dispatchTouchEvent: currentY-mInitialMotionY:" + (mLastY - mInitialMotionY));
                if (!isIntercepted) {
//                    int distanceX = Math.abs(currentX - mInitialMotionX);
                    int distanceY = Math.abs(currentY - mInitialMotionY);
                    if (/*distanceX * distanceX +*/ distanceY * distanceY >= mTouchSlop * mTouchSlop) {
                        isIntercepted = true;
                    } else {
                        return super.dispatchTouchEvent(ev);
                    }
                }

                if (isIntercepted && (hasTouchTopView || isTouchTopView(ev))) {
                    hasTouchTopView = true;
                    boolean isPull = currentY - mInitialMotionY > 0;
                    if (isPull) {
                        //init 顶部的view
                        if (!mIsPull) {
                            View topView = getTopView();
                            if (mShowingIndex == 0 && topView.getTop() <= initTop) {
                                topView.offsetTopAndBottom(initTop - topView.getTop());
                                topView.setAlpha(1f);
                                return super.dispatchTouchEvent(ev);
                            } else if (mShowingIndex > 0) {
                                mIsPull = true;
                                refreshUI(mShowingIndex - 1);
                                topView = getTopView();
                                topView.offsetTopAndBottom(-(mCardHeight + initTop));
                            }
                        }
                    } else {
                        //向上
                        if (mIsPull) {
                            //先下后上
                            mIsPull = false;
                            if (canMoveCard()) {
                                //刷新顶部view
                                refreshUI(mShowingIndex + 1);
                                View topView = getTopView();
                                topView.offsetTopAndBottom((mCardHeight + initTop));
                            }
                        }
                        View topView = getTopView();
                        if (!canMoveCard() && topView.getTop() >= initTop) {
                            topView.offsetTopAndBottom(initTop - topView.getTop());
                            topView.setAlpha(1f);
                            return super.dispatchTouchEvent(ev);
                        }
                    }

                    moveTopView(deltaX, deltaY, mIsPull);
                    invalidate();
                    sendCancelEvent();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean isPull = mIsPull;
                mIsPull = false;
                hasTouchTopView = false;
                isTouching = false;
                isIntercepted = false;
                mHasSendCancelEvent = false;
                mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                final float velocityX = mVelocityTracker.getXVelocity();
                final float velocityY = mVelocityTracker.getYVelocity();
                final float xvel = clampMag(velocityX, mMinVelocity, mMaxVelocity);
                final float yvel = clampMag(velocityY, mMinVelocity, mMaxVelocity);

                releaseTopView(xvel, yvel, isPull);
                releaseVelocityTracker();
//                invalidate();
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private MotionEvent mLastMoveEvent;
    private boolean mHasSendCancelEvent = false;

    private void sendCancelEvent() {
        if (!mHasSendCancelEvent) {
            mHasSendCancelEvent = true;
            MotionEvent last = mLastMoveEvent;
            MotionEvent e = MotionEvent.obtain(
                    last.getDownTime(),
                    last.getEventTime()
                            + ViewConfiguration.getLongPressTimeout(),
                    MotionEvent.ACTION_CANCEL, last.getX(), last.getY(),
                    last.getMetaState());
            dispatchTouchEventSupper(e);
        }
    }

    public boolean dispatchTouchEventSupper(MotionEvent e) {
        return super.dispatchTouchEvent(e);
    }

    private void releaseTopView(float xvel, float yvel, boolean isPull) {
        mScrolling = true;
        View topView = getTopView();
        if (topView != null /*&& canMoveCard()*/ && mEnableSwipe) {
            onTopViewReleased(topView, yvel, isPull);
        }
    }

    /**
     * 是否摸到了某个view
     *
     * @param ev
     * @return
     */
    private boolean isTouchTopView(MotionEvent ev) {
        View topView = getTopView();
        if (topView != null && topView.getVisibility() == VISIBLE) {
            Rect bounds = new Rect();
            topView.getGlobalVisibleRect(bounds);
            int x = (int) ev.getRawX();
            int y = (int) ev.getRawY();
            if (bounds.contains(x, y)) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private void moveTopView(int deltaX, int deltaY, boolean isPull) {
        View topView = getTopView();
        if (topView != null) {
            if (isPull && topView.getTop() >= initTop) {
                return;
            }

//            topView.offsetLeftAndRight(deltaX);
            topView.offsetTopAndBottom(deltaY);
            //透明度处理
            int distance = initTop + mCardHeight;
            float moveDis = initTop - topView.getTop();
            float v = Math.abs(moveDis / distance);
            //优化误差
            Log.i(TAG, "dispatchTouchEvent  moveTopView:moveDis=" + moveDis + "  distance=" + distance + " alpha =" + topView.getAlpha());
            if (v < 0.05f) {
                v = 0.0f;
            } else if (v > 0.95f) {
                v = 1.0f;
            }
            float alpha = 1.0f - v;
            topView.setAlpha(alpha);
            processLinkageView(topView, alpha, isPull);
        }
    }

    /**
     * 获取顶部的view
     *
     * @return
     */
    private View getTopView() {
        if (viewList.size() > 0) {
            return viewList.get(0);
        }
        return null;
    }

    public void startScrollTopView(int finalLeft, int finalTop, int duration, SlideType flyType, boolean addReleaseList) {
        View topView = getTopView();
        if (topView == null) {
            mScrolling = false;
            return;
        }
        if (addReleaseList) {
            releasedViewList.add(topView);
        }
        final int startLeft = topView.getLeft();
        final int startTop = topView.getTop();
        final int dx = finalLeft - startLeft;
        final int dy = finalTop - startTop;
        if (dy != 0) {
            mScroller.startScroll(startLeft, startTop, dx, dy, duration);
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            mScrolling = false;
        }
        if (flyType != SlideType.NONE && mCardsSlideListener != null) {
            mCardsSlideListener.onCardVanish(mShowingIndex, flyType);
        }
    }

    /**
     * @param event 向VelocityTracker添加MotionEvent
     * @see android.view.VelocityTracker#obtain()
     * @see android.view.VelocityTracker#addMovement(MotionEvent)
     */
    private void acquireVelocityTracker(final MotionEvent event) {
        if (null == mVelocityTracker) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * 释放VelocityTracker
     *
     * @see android.view.VelocityTracker#clear()
     * @see android.view.VelocityTracker#recycle()
     */
    private void releaseVelocityTracker() {
        if (null != mVelocityTracker) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value  Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as <code>value</code>
     */
    private float clampMag(float value, float absMin, float absMax) {
        final float absValue = Math.abs(value);
        if (absValue < absMin) return 0;
        if (absValue > absMax) return value > 0 ? absMax : -absMax;
        return value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildrenWithMargins(widthMeasureSpec, heightMeasureSpec);
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, 0), resolveSizeAndState(maxHeight, heightMeasureSpec, 0));
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
    }

    private void measureChildrenWithMargins(int widthMeasureSpec, int heightMeasureSpec) {
        final int size = getChildCount();
        for (int i = 0; i < size; ++i) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (hasTouchTopView || mScrolling) {
            return;
        }
        int size = viewList.size();
        if (size == 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            View child = viewList.get(i);
            layoutChild(child, i);
        }
        // 初始化一些中间参数
        initLeft = viewList.get(0).getLeft();
        initTop = viewList.get(0).getTop();
        mCardWidth = viewList.get(0).getMeasuredWidth();
        mCardHeight = viewList.get(0).getMeasuredHeight();
        Log.i(TAG, "onLayout: initLeft=" + initLeft + "  initTop=" + initTop);
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void layoutChild(View child, int index) {
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        int width = child.getMeasuredWidth();
        int height = child.getMeasuredHeight();

        int gravity = lp.gravity;
        if (gravity == -1) {
            gravity = Gravity.TOP | Gravity.START;
        }

        int layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

        int childLeft;
        int childTop;
        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                childLeft = (getWidth() + getPaddingLeft() - getPaddingRight() - width) / 2 +
                        lp.leftMargin - lp.rightMargin;
                break;
            case Gravity.END:
                childLeft = getWidth() + getPaddingRight() - width - lp.rightMargin;
                break;
            case Gravity.START:
            default:
                childLeft = getPaddingLeft() + lp.leftMargin;
                break;
        }
        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL:
                childTop = (getHeight() + getPaddingTop() - getPaddingBottom() - height) / 2 +
                        lp.topMargin - lp.bottomMargin;
                break;
            case Gravity.BOTTOM:
                childTop = getHeight() - getPaddingBottom() - height - lp.bottomMargin;
                break;
            case Gravity.TOP:
            default:
                childTop = getPaddingTop() + lp.topMargin;
                break;
        }
        child.layout(childLeft, childTop, childLeft + width, childTop + height);
        //最后一个出现 ，位置不动
        if (index == viewList.size() - 1) {
            index = index - 1;
        }

        int offset = yOffsetStep * index;
        float scale = 1 - scaleOffsetStep * index;
//        float alpha = 1.0f * (100 - alphaOffsetStep * index) / 100;
        child.offsetTopAndBottom(offset);
        child.setScaleX(scale);
        child.setScaleY(scale);
//        child.setAlpha(alpha);
        Log.i(TAG, child + " layoutChild: offset->" + offset + " scale->" + scale);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            View topView = getTopView();
            if (topView == null) {
                return;
            }
            final int x = mScroller.getCurrX();
            final int y = mScroller.getCurrY();
            final int dx = x - topView.getLeft();
            final int dy = y - topView.getTop();
            if (y != mScroller.getFinalY()) {
                moveTopView(0/*dx*/, dy, dy > 0);
            }
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            mScrolling = false;
            onAnimalStop();
        }
    }

    private void onAnimalStop() {
        if (canResetView()) {
            Log.i(TAG, "computeScroll: releasedViewList " + mScroller.getFinalY() + " releasedViewList=" + releasedViewList.size());
            resetViewGroup();
        }
    }

    private boolean canResetView() {
        return !mScroller.computeScrollOffset() && !isTouching;
    }

    /**
     * 对View重新排序
     */
    private void resetViewGroup() {
        Log.i(TAG, "resetViewGroup:releasedViewList.size()=" + releasedViewList.size() + "   viewList.size()=" + viewList.size());
        // 回到原来的位置
        if (releasedViewList.size() == 0) {
            mScrolling = false;
            if (mWaitRefresh) {
                mWaitRefresh = false;
                refreshUI(tempShowingIndex);
            }
            if (viewList.size() != 0) {
                View topView = getTopView();
                if (topView != null) {
                    if (topView.getTop() != initTop) {
//                        topView.offsetLeftAndRight(initLeft - topView.getLeft());
                        topView.offsetTopAndBottom(initTop - topView.getTop());
                    }
                }
            }
//            viewList.get(viewList.size() - 1).setVisibility(View.GONE);
        } else {
            //滑到另一个位置
            View changedView = releasedViewList.get(0);
            if (changedView.getTop() == initTop || changedView.getTop() == -mCardHeight) {
                releasedViewList.remove(0);
                mScrolling = false;
                return;
            }
            changedView.setAlpha(1f);
            viewList.remove(changedView);
            viewList.add(changedView);
            mScrolling = false;
            int viewSize = viewList.size();
            removeViewInLayout(changedView);
            addViewInLayout(changedView, 0, changedView.getLayoutParams(), true);
            requestLayout();
//            removeView(changedView);
//            addView(changedView,0);
            if (mWaitRefresh) {
                mWaitRefresh = false;
                int index = ++tempShowingIndex;
                refreshUI(index);
            } else {
                int newIndex = mShowingIndex + viewSize;
                if (newIndex < mCount) {
                    bindCardData(newIndex, changedView);
                } else {
                    changedView.setVisibility(View.GONE);
                }
                if (mShowingIndex + 1 < mCount) {
                    mShowingIndex++;
                    if (null != mCardsSlideListener) {
                        mCardsSlideListener.onShow(mShowingIndex);
                    }
                    syncCardElevation();
                } else {
                    //no card showing
                    mShowingIndex = -1;
                }
            }
            releasedViewList.remove(0);
        }
        tempShowingIndex = -1;
    }

    /**
     * 顶层卡片View位置改变，底层的位置需要调整
     *
     * @param changedView 顶层的卡片view
     * @param alpha
     */
    private void processLinkageView(View changedView, float alpha, boolean isPull) {
        int changeViewLeft = changedView.getLeft();
        int changeViewTop = changedView.getTop();
        int distance = Math.abs(changeViewTop - initTop) + Math.abs(changeViewLeft - initLeft);
        if (isPull) {
            distance = Math.abs(changeViewTop + initTop + mCardHeight);
        }
        float rate = distance / ((float) MAX_SLIDE_DISTANCE_LINKAGE * 2f);
        Log.i(TAG, "processLinkageView:  rate=" + rate + "   distance=" + distance + "  height =" + (initTop + mCardHeight) + " distance  " + Math.abs(changeViewTop + (initTop + mCardHeight)));
        for (int i = 1; i < viewList.size(); i++) {
            float rate3 = rate - 0.2f * i;
            if (isPull) {
                rate3 = 1 - rate3;
            }
            if (rate3 > 1) {
                rate3 = 1;
            } else if (rate3 < 0) {
                rate3 = 0;
            }
            ajustLinkageViewItem(changedView, rate3, alpha, i);
        }
    }

    // 由index对应view变成index-1对应的view
    private void ajustLinkageViewItem(View changedView, float rate, float v, int index) {
//        Log.i(TAG, "ajustLinkageViewItem: rate=" + rate + "  alpha=" + v);
        int changeIndex = viewList.indexOf(changedView);
        if (viewList.size() - 1 > index) {
            int initPosY = yOffsetStep * index;
            float initScale = 1 - scaleOffsetStep * index;

            int nextPosY = yOffsetStep * (index - 1);
            float nextScale = 1 - scaleOffsetStep * (index - 1);

            int offset = (int) (initPosY + (nextPosY - initPosY) * rate);
            float scale = initScale + (nextScale - initScale) * rate;

            View adjustView = viewList.get(changeIndex + index);
            adjustView.offsetTopAndBottom(offset - adjustView.getTop() + initTop);
            adjustView.setScaleX(scale);
            adjustView.setScaleY(scale);
        } else {
            //最后一个位置不动
        }
    }

    /**
     * 松手时处理滑动到边缘的动画
     *
     * @param yvel   Y方向上的滑动速度
     * @param isPull
     */
    private void onTopViewReleased(View changedView, float yvel, boolean isPull) {
        int finalX = initLeft;
        int finalY = initTop;
        if (isPull) {
            finalY = -mCardHeight;
        }
        SlideType flyType = SlideType.NONE;

        int dy = changedView.getTop() - initTop;
        if (isPull) {
            dy = changedView.getTop() + mCardHeight;
        }

        if (dy == 0) {
            // 由于dx作为分母，此处保护处理
            dy = 1;
        }

        boolean addReleaseList = false;//结束后是否需要移到后面

        if (!isPull && (dy < -Y_DISTANCE_THRESHOLD || (yvel < -Y_VEL_THRESHOLD && dy < 0))) {//向上滑出
            finalY = -mCardHeight;
            flyType = SlideType.TOP;
            addReleaseList = true;
        } else if (isPull && (dy > Y_DISTANCE_THRESHOLD || (yvel > Y_VEL_THRESHOLD && dy > 0))) {//向下拉出来
            finalY = initTop;
            flyType = SlideType.BOTTOM;
        } else if (isPull) {
            addReleaseList = true;
        }

        startScrollTopView(finalX, finalY, SCROLL_DURATION, flyType, addReleaseList);
    }

    /**
     * use this method to Slide the card out of the screen
     *
     * @param type {@link SwipeCardsView.SlideType}
     */
    public void slideCardOut(SlideType type) {
        if (!canMoveCard()) {
            return;
        }
        mScroller.abortAnimation();
        resetViewGroup();
        View topview = getTopView();
        if (topview == null) {
            return;
        }
        if (releasedViewList.contains(topview) || type == SlideType.NONE) {
            return;
        }
        int finalX = 0;
        int finalY = 0;
        switch (type) {
//            case LEFT:
//                finalX = -mCardWidth;
//                break;
//            case RIGHT:
//                finalX = mWidth;
//                break;
            case TOP:
                finalY = -mCardHeight;
                break;
            case BOTTOM:
                finalY = mHeight;
                break;
        }
        if (finalX != 0) {
            startScrollTopView(finalX, finalY, SCROLL_DURATION, type, true);
        }
    }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
                if (specSize < size) {
                    result = specSize | MEASURED_STATE_TOO_SMALL;
                } else {
                    result = size;
                }
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result | (childMeasuredState & MEASURED_STATE_MASK);
    }

    /**
     * 设置卡片操作回调
     *
     * @param cardsSlideListener 回调接口
     */
    public void setCardsSlideListener(CardsSlideListener cardsSlideListener) {
        this.mCardsSlideListener = cardsSlideListener;
    }

    /**
     * 卡片回调接口
     */
    public interface CardsSlideListener {
        /**
         * 新卡片显示回调
         *
         * @param index 最顶层显示的卡片的index
         */
        void onShow(int index);

        void onCardVanish(int index, SlideType type);

        /**
         * 卡片点击事件
         *
         * @param cardImageView 卡片上的图片view
         * @param index         点击到的index
         */
        void onItemClick(View cardImageView, int index);
    }

    /**
     * <p>
     * {@link #TOP} 从屏幕上边滑出
     * </p>
     * {@link #BOTTOM} 下拉展示前一个卡片
     */
    public enum SlideType {
        TOP, BOTTOM, NONE
    }
}
