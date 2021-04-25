package com.waynetoo.demo.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.waynetoo.demo.bean.ContentBean;
import com.waynetoo.flip.demo.R;
import com.waynetoo.swipecardsview.BaseCardAdapter;
import com.squareup.picasso.Picasso;

import java.util.List;


public class MeiziAdapter extends BaseCardAdapter<View> {
    private List<ContentBean> datas;
    private Context context;

    public MeiziAdapter(List<ContentBean> datas, Context context) {
        this.datas = datas;
        this.context = context;
    }

    public void setData(List<ContentBean> datas) {
        this.datas = datas;
    }

    @Override
    public int getCount() {
        return datas.size();
    }

    @Override
    public int getCardLayoutId() {
        return R.layout.card_item;
    }

    @Override
    public void onBindData(int position, View cardView) {
        if (datas == null || datas.size() == 0) {
            return;
        }
        ImageView imageView = cardView.findViewById(R.id.iv_meizi);
        View view = cardView.findViewById(R.id.click);
        TextView textView = cardView.findViewById(R.id.text);

        view.setOnClickListener(it -> {
            System.out.println("position ：" + position);
        });
        ContentBean meizi = datas.get(position);
        textView.setText("--" + meizi.getOrder() + "--");
        String url = meizi.getUrl();
        Bitmap.Config config = Bitmap.Config.RGB_565;
        Picasso.with(context).load(url).config(config).into(imageView);
    }



    /**
     * 如果可见的卡片数是3，则可以不用实现这个方法
     *
     * @return
     */
    @Override
    public int getVisibleCardCount() {
        return 4;
    }
}
