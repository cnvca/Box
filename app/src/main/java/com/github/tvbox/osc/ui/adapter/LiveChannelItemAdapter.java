package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    
    private Context context; // 添加 context 变量
    public LiveChannelItemAdapter(Context context) { // 修改构造函数
        super(R.layout.item_live_channel, new ArrayList<>());
        this.context = context; // 初始化 context
    }
//    public LiveChannelItemAdapter() {
//        super(R.layout.item_live_channel, new ArrayList<>())
//   }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
//        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannelNum.setText(String.valueOf(item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
          int position = holder.getAdapterPosition();
          if (position != RecyclerView.NO_POSITION) {
            // 触发更新EPG信息的逻辑
            ((LivePlayActivity) context).updateEpgInfoDisplay(position);
          }
        });
        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            // takagen99: Added Theme Color
//            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_theme));
//            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_theme));
            tvChannelNum.setTextColor(((BaseActivity) mContext).getThemeColor());
            tvChannel.setTextColor(((BaseActivity) mContext).getThemeColor());
        }
        else{
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1)
            notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1)
            notifyItemChanged(preFocusedChannelIndex);
        if (this.focusedChannelIndex != -1)
            notifyItemChanged(this.focusedChannelIndex);
        else if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }
}
