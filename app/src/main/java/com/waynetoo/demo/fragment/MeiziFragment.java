package com.waynetoo.demo.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.Toolbar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.waynetoo.demo.MainActivity;
import com.waynetoo.demo.adapter.MeiziAdapter;
import com.waynetoo.demo.base.BaseFragment;
import com.waynetoo.demo.bean.ContentBean;
import com.waynetoo.flip.demo.R;
import com.waynetoo.swipecardsview.SwipeCardsView;

import java.util.ArrayList;
import java.util.List;

public class MeiziFragment extends BaseFragment {
    private MainActivity activity;
    private SwipeCardsView swipeCardsView;
    private List<ContentBean> mList = new ArrayList<>();
    private MeiziAdapter adapter;
    private FloatingActionButton floatingActionButton;
    private int curIndex;

    public MeiziFragment() {
    }

    public static MeiziFragment getInstance() {
        MeiziFragment fragment = new MeiziFragment();
        return fragment;
    }

    /**
     * 卡片向左边飞出
     */
    public void doTOPOut() {
        swipeCardsView.slideCardOut(SwipeCardsView.SlideType.TOP);
    }

    /**
     * 卡片向右边飞出
     */
    public void doRightOut() {
        swipeCardsView.slideCardOut(SwipeCardsView.SlideType.BOTTOM);
    }

    /**
     * 上一张
     */
    public void swipeToPre() {
        //必须先改变adapter中的数据，然后才能由数据变化带动页面刷新
        if (mList != null) {
            adapter.setData(mList);
            if (curIndex > 0) {
                swipeCardsView.notifyDatasetChanged(curIndex - 1);
            } else {
                toast("已经是第一张卡片了");
            }
        }
    }

    /**
     * 从头开始，重新浏览
     */
    public void doRetry() {
        //必须先改变adapter中的数据，然后才能由数据变化带动页面刷新
        if (mList != null) {
            adapter.setData(mList);
            swipeCardsView.notifyDatasetChanged(0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        container = (ViewGroup) inflater.inflate(R.layout.fragment_meizi, container, false);
        Toolbar toolbar = (Toolbar) container.findViewById(R.id.toolbar);
        swipeCardsView = (SwipeCardsView) container.findViewById(R.id.swipCardsView);
        floatingActionButton = (FloatingActionButton) container.findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getData();
            }
        });
        activity = (MainActivity) getActivity();
        activity.setSupportActionBar(toolbar);
        //whether retain last card,defalut false
        swipeCardsView.retainLastCard(true);
        //Pass false if you want to disable swipe feature,or do nothing.
        swipeCardsView.enableSwipe(true);
        getData();
        //设置滑动监听
        swipeCardsView.setCardsSlideListener(new SwipeCardsView.CardsSlideListener() {
            @Override
            public void onShow(int index) {
                curIndex = index;
            }

            @Override
            public void onCardVanish(int index, SwipeCardsView.SlideType type) {
                String orientation = "";
                switch (type) {
                    case TOP:
                        orientation = "向左飞出";
                        break;
                    case BOTTOM:
                        orientation = "向右飞出";
                        break;
                }
//                toast("test position = "+index+";卡片"+orientation);
            }

            @Override
            public void onItemClick(View cardImageView, int index) {
                toast("点击了 position=" + index);
            }
        });
        return container;
    }

    public void getData() {
        List<ContentBean> bean = new ArrayList<>();

        for (int i = 1; i < 18; i++) {
            ContentBean contentBean = new ContentBean();
            contentBean.setUrl("file:///android_asset/animal/a_" + (i % 8) + ".jpg");
            contentBean.setOrder(i);
            bean.add(contentBean);
        }
        mList.addAll(bean);
        show();
    }

    /**
     * 显示cardsview
     */
    private void show() {
        if (adapter == null) {
            adapter = new MeiziAdapter(mList, getActivity());
            swipeCardsView.setAdapter(adapter);
        } else {
            //if you want to change the UI of SwipeCardsView,you must modify the data first
            adapter.setData(mList);
            swipeCardsView.notifyDatasetChanged(curIndex);
        }
    }
}
