package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.Epginfo;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private Hashtable<String, ArrayList<Epginfo>> hsEpg;
    private LiveEpgDateAdapter epgDateAdapter;

    public LiveChannelItemAdapter(Hashtable<String, ArrayList<Epginfo>> hsEpg, LiveEpgDateAdapter epgDateAdapter) {
        super(R.layout.item_live_channel, new ArrayList<>());
        this.hsEpg = hsEpg;
        this.epgDateAdapter = epgDateAdapter;
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvCurrentProgramName = holder.getView(R.id.tv_current_program_name);

        // 基础信息设置
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 焦点和选中状态
        int channelIndex = item.getChannelIndex();
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(((BaseActivity) mContext).getThemeColor());
            tvChannel.setTextColor(((BaseActivity) mContext).getThemeColor());
            tvCurrentProgramName.setTextColor(((BaseActivity) mContext).getThemeColor());
        } else {
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
            tvCurrentProgramName.setTextColor(Color.WHITE);
        }

        // EPG信息绑定（完全使用预加载数据）
        bindEpgInfo(tvCurrentProgramName, item);

        // 焦点控制强化
        holder.itemView.setFocusable(true);
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusedChannelIndex = channelIndex;
                v.post(() -> {
                    if (!v.isFocused()) {
                        v.requestFocus();
                    }
                });
            }
        });
    }

    private void bindEpgInfo(TextView tvCurrentProgramName, LiveChannelItem item) {
        try {
            if (epgDateAdapter == null || epgDateAdapter.getSelectedIndex() < 0) return;

            // 从左侧预加载数据直接获取
            String dateKey = epgDateAdapter.getItem(epgDateAdapter.getSelectedIndex()).getDatePresented();
            String epgKey = item.getChannelName() + "_" + dateKey;
            
            ArrayList<Epginfo> epgList = hsEpg.get(epgKey);
            if (epgList != null && !epgList.isEmpty()) {
                Date now = new Date();
                for (Epginfo epg : epgList) {
                    if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                        tvCurrentProgramName.setText(epg.title);
                        return;
                    }
                }
                tvCurrentProgramName.setText("暂无当前节目");
            } else {
                tvCurrentProgramName.setText("--");
            }
        } catch (Exception e) {
            Log.e("EPGBind", "EPG数据加载异常", e);
            tvCurrentProgramName.setText("节目信息异常");
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (this.selectedChannelIndex == selectedChannelIndex) return;
        int preIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preIndex != -1) notifyItemChanged(preIndex);
        if (this.selectedChannelIndex != -1) notifyItemChanged(this.selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preIndex != -1) notifyItemChanged(preIndex);
        if (this.focusedChannelIndex != -1) {
            notifyItemChanged(this.focusedChannelIndex);
        } else if (this.selectedChannelIndex != -1) {
            notifyItemChanged(this.selectedChannelIndex);
        }
    }
}
