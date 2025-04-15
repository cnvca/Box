package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.util.Log;
import android.widget.TextView;
import android.app.Activity;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.base.App;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.lang.ref.WeakReference;



/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private Hashtable<String, ArrayList<Epginfo>> hsEpg; // 用于存储 EPG 数据
    private LiveEpgDateAdapter epgDateAdapter; // 用于获取当前日期

    public LiveChannelItemAdapter(Hashtable<String, ArrayList<Epginfo>> hsEpg, LiveEpgDateAdapter epgDateAdapter) {
        super(R.layout.item_live_channel, new ArrayList<>());
        this.hsEpg = hsEpg;
        this.epgDateAdapter = epgDateAdapter;
    }
	
    // 使用弱引用防止内存泄漏
    private WeakReference<Hashtable<String, List<Epginfo>>> weakEpgRef;
    @Override
	
protected void convert(BaseViewHolder holder, LiveChannelItem item) {
    TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
    TextView tvChannel = holder.getView(R.id.tvChannelName);
    TextView tvCurrentProgramName = holder.getView(R.id.tv_current_program_name); // 获取 EPG 信息控件

        // 异步加载EPG
        App.getInstance().executorService.execute(() -> {
            Epginfo epg = getCurrentEPG(item);
            runOnUiThread(() -> {
                if(epg != null){
                    holder.setText(R.id.tvEpgInfo, epg.getTitle());
                }
            });
        });
		
    // 设置频道编号和名称
    tvChannelNum.setText(String.format("%s", item.getChannelNum()));
    tvChannel.setText(item.getChannelName());

    // 设置选中和焦点状态的颜色
    int channelIndex = item.getChannelIndex();
    if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
        // 如果频道正在播放，设置字体颜色为红色
        tvChannelNum.setTextColor(((BaseActivity) mContext).getThemeColor());
        tvChannel.setTextColor(((BaseActivity) mContext).getThemeColor());
        tvCurrentProgramName.setTextColor(((BaseActivity) mContext).getThemeColor()); // EPG 字体颜色为红色
    } else {
        // 如果频道未播放，设置字体颜色为白色
        tvChannelNum.setTextColor(Color.WHITE);
        tvChannel.setTextColor(Color.WHITE);
        tvCurrentProgramName.setTextColor(Color.WHITE); // EPG 字体颜色为白色
    }

    // 绑定 EPG 信息
    if (hsEpg != null && epgDateAdapter != null) {
        String epgKey = item.getChannelName() + "_" + epgDateAdapter.getItem(epgDateAdapter.getSelectedIndex()).getDatePresented();
        if (hsEpg.containsKey(epgKey)) {
            ArrayList<Epginfo> epgList = hsEpg.get(epgKey);
            if (epgList != null && epgList.size() > 0) {
                Date now = new Date();
                boolean found = false;
                for (Epginfo epg : epgList) {
                    if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                        tvCurrentProgramName.setText(epg.title); // 设置当前节目名称
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    tvCurrentProgramName.setText("暂无节目信息");
                }
            } else {
                tvCurrentProgramName.setText("暂无节目信息");
            }
        } else {
            tvCurrentProgramName.setText("暂无节目信息");
        }
    } else {
        tvCurrentProgramName.setText("暂无节目信息");
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
