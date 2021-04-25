package com.waynetoo.demo;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.waynetoo.demo.base.BaseActivity;
import com.waynetoo.demo.fragment.MeiziFragment;
import com.waynetoo.flip.demo.R;

import java.util.ArrayList;

public class MainActivity extends BaseActivity {
    private MeiziFragment meiziFragment;
    private ViewPager mPager;
    private ArrayList<Fragment> fragmentList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        meiziFragment = MeiziFragment.getInstance();
        MeiziFragment  meiziFragment2 = MeiziFragment.getInstance();
        mPager = (ViewPager)findViewById(R.id.view_page);
        fragmentList = new ArrayList<Fragment>();
        fragmentList.add(meiziFragment);
        fragmentList.add(meiziFragment2);

        mPager.setAdapter(new MyFragmentPagerAdapter(getSupportFragmentManager(), fragmentList));
        mPager.setCurrentItem(1);//设置当前显示标签页为第一页

    }

    private class MyFragmentPagerAdapter extends FragmentPagerAdapter {
        ArrayList<Fragment> list;
        public MyFragmentPagerAdapter(FragmentManager fm,ArrayList<Fragment> list) {
            super(fm);
            this.list = list;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return list.get(position);
        }

        @Override
        public int getCount() {
            return list.size();
        }
    }
}
