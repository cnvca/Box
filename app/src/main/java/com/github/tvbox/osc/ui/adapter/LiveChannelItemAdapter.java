package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.os.AsyncTask;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private Hashtable<String, ArrayList<Epginfo>> hsEpg;
    private LiveEpgDateAdapter epgDateAdapter;
    
    // 新增缓存和防抖控制
    private final Map<String, String> epgCache = new HashMap<>();
    private long lastUpdateTime = 0L;
    private static final long EPG_UPDATE_THROTTLE = 500L; // 500ms防抖

    public LiveChannelItemAdapter(Hashtable<String, ArrayList<Epginfo>> hsEpg, LiveEpgDateAdapter epgDateAdapter) {
        super(R.layout.item_live_channel, new ArrayList<>());
        this.hsEpg = hsEpg;
        this.epgDateAdapter = epgDateAdapter;
        this.mHandler = handler;		
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvCurrentProgramName = holder.getView(R.id.tv_current_program_name);

        // 设置焦点属性（关键修改）
        holder.itemView.setFocusable(true);
        tvChannelNum.setFocusable(false);
        tvChannel.setFocusable(false);
        tvCurrentProgramName.setFocusable(false);
        tvChannelNum.setFocusableInTouchMode(false);
        tvChannel.setFocusableInTouchMode(false);
        tvCurrentProgramName.setFocusableInTouchMode(false);

        // 频道编号和名称
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 颜色状态（优化性能，减少对象创建）
        int themeColor = ((BaseActivity) mContext).getThemeColor();
        int textColor = (holder.getAdapterPosition() == selectedChannelIndex && 
                        holder.getAdapterPosition() != focusedChannelIndex) ? 
                        themeColor : Color.WHITE;
        
        tvChannelNum.setTextColor(textColor);
        tvChannel.setTextColor(textColor);
        tvCurrentProgramName.setTextColor(textColor);

        // EPG加载优化（异步+缓存+防抖）
        loadEpgWithThrottle(holder, item);
    }
    // 新增焦点控制
        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, boolean hasFocus) {
                if (hasFocus) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (v.isFocused()) {
                                v.setBackgroundResource(R.drawable.live_channel_focused_bg);
                                v.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        v.requestFocus();
                                    }
                                });
                            }
                        }
                    }, 50);
                } else {
                    v.setBackgroundResource(0);
                }
            }
        });
    private void loadEpgWithThrottle(BaseViewHolder holder, LiveChannelItem item) {
        // 防抖处理
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < EPG_UPDATE_THROTTLE) {
            return;
        }
        lastUpdateTime = currentTime;

        // 使用AsyncTask进行异步加载
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                return getEpgText(item);
            }

            @Override
            protected void onPostExecute(String epgText) {
                if (holder.getAdapterPosition() == item.getChannelIndex()) {
                    TextView tv = holder.getView(R.id.tv_current_program_name);
                    if (tv != null) {
                        tv.setText(epgText);
                    }
                }
            }
        }.execute();
    }

    private String getEpgText(LiveChannelItem item) {
        if (hsEpg == null || epgDateAdapter == null) {
            return "暂无节目信息";
        }

        String cacheKey = item.getChannelName() + "_" + epgDateAdapter.getSelectedIndex();
        if (epgCache.containsKey(cacheKey)) {
            return epgCache.get(cacheKey);
        }

        String epgKey = item.getChannelName() + "_" + epgDateAdapter.getItem(epgDateAdapter.getSelectedIndex()).getDatePresented();
        String result = "暂无节目信息";

        try {
            ArrayList<Epginfo> epgList = hsEpg.get(epgKey);
            if (epgList != null && !epgList.isEmpty()) {
                Date now = new Date();
                for (Epginfo epg : epgList) {
                    if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                        result = epg.title;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        epgCache.put(cacheKey, result);
        return result;
    }

    // 新增方法：清空缓存
    public void clearEpgCache() {
        epgCache.clear();
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (this.selectedChannelIndex == selectedChannelIndex) return;
        
        int previous = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        
        if (previous != -1) notifyItemChanged(previous);
        if (selectedChannelIndex != -1) notifyItemChanged(selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        if (this.focusedChannelIndex == focusedChannelIndex) return;
        
        int previous = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        
        if (previous != -1) notifyItemChanged(previous);
        if (focusedChannelIndex != -1) {
            notifyItemChanged(focusedChannelIndex);
        } else if (selectedChannelIndex != -1) {
            notifyItemChanged(selectedChannelIndex);
        }
    }

    // 新增方法：批量更新EPG数据
    public void updateEpgData(Hashtable<String, ArrayList<Epginfo>> newEpgData) {
        this.hsEpg = newEpgData;
        clearEpgCache();
        notifyDataSetChanged();
    }
}
