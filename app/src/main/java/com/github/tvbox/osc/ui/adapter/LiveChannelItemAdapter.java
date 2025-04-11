package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelItem;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private int mFocusedPosition = -1;
    private final Hashtable<String, ArrayList<Epginfo>> hsEpg;
    private final LiveEpgDateAdapter epgDateAdapter;

    public LiveChannelItemAdapter(Hashtable<String, ArrayList<Epginfo>> hsEpg, LiveEpgDateAdapter epgDateAdapter) {
        super(R.layout.item_live_channel, new ArrayList<>());
        this.hsEpg = hsEpg;
        this.epgDateAdapter = epgDateAdapter;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, LiveChannelItem item) {
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        TextView tvCurrentProgramName = holder.getView(R.id.tv_current_program_name);

        // 设置基础信息
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 焦点和选中状态处理
        int position = holder.getBindingAdapterPosition();
        boolean isSelected = position == selectedChannelIndex;
        boolean isFocused = position == focusedChannelIndex || position == mFocusedPosition;

        // 颜色状态
        int themeColor = ((BaseActivity) mContext).getThemeColor();
        tvChannelNum.setTextColor(isSelected ? themeColor : Color.WHITE);
        tvChannel.setTextColor(isSelected ? themeColor : Color.WHITE);
        tvCurrentProgramName.setTextColor(isSelected ? themeColor : Color.WHITE);

        // EPG信息绑定
        bindEpgInfo(tvCurrentProgramName, item);

        // 焦点控制
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setFocusedPosition(position);
                focusedChannelIndex = position;
                notifyItemChanged(position);
            } else if (focusedChannelIndex == position) {
                focusedChannelIndex = -1;
            }
        });

        // 自动请求焦点逻辑
        if (isFocused) {
            holder.itemView.postDelayed(() -> {
                if (!holder.itemView.isFocused()) {
                    holder.itemView.requestFocus();
                }
            }, 100);
        }
    }

    private void bindEpgInfo(TextView textView, LiveChannelItem item) {
        if (hsEpg == null || epgDateAdapter == null) {
            textView.setText("暂无节目信息");
            return;
        }

        String epgKey = item.getChannelName() + "_" + epgDateAdapter.getSelectedDate();
        ArrayList<Epginfo> epgList = hsEpg.get(epgKey);
        
        if (epgList == null || epgList.isEmpty()) {
            textView.setText("暂无节目信息");
            return;
        }

        Date now = new Date();
        for (Epginfo epg : epgList) {
            if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                textView.setText(epg.title);
                return;
            }
        }
        textView.setText("暂无节目信息");
    }

    // 新增焦点位置管理
    public void setFocusedPosition(int position) {
        int oldPosition = mFocusedPosition;
        mFocusedPosition = position;
        if (oldPosition != -1) notifyItemChanged(oldPosition);
        if (mFocusedPosition != -1) notifyItemChanged(mFocusedPosition);
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (this.selectedChannelIndex == selectedChannelIndex) return;
        int prev = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (prev != -1) notifyItemChanged(prev);
        if (selectedChannelIndex != -1) {
            notifyItemChanged(selectedChannelIndex);
            // 选中时自动滚动到可视区域
            if (getRecyclerView() != null) {
                getRecyclerView().post(() -> {
                    int first = getRecyclerView().getLayoutManager().findFirstVisibleItemPosition();
                    int last = getRecyclerView().getLayoutManager().findLastVisibleItemPosition();
                    if (selectedChannelIndex < first || selectedChannelIndex > last) {
                        getRecyclerView().smoothScrollToPosition(selectedChannelIndex);
                    }
                });
            }
        }
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        if (this.focusedChannelIndex == focusedChannelIndex) return;
        int prev = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (prev != -1) notifyItemChanged(prev);
        if (focusedChannelIndex != -1) notifyItemChanged(focusedChannelIndex);
    }

    // 新增：处理数据更新时的焦点保持
    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        if (position == mFocusedPosition) {
            holder.itemView.post(() -> {
                if (!holder.itemView.isFocused()) {
                    holder.itemView.requestFocus();
                }
            });
        }
    }
}
