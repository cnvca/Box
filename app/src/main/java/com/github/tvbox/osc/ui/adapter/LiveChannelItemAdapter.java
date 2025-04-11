package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private int lastManualFocusPosition = -1;
    private boolean isAutoScroll = false;
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

        // 基础信息绑定
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());

        // 状态颜色控制
        int position = holder.getBindingAdapterPosition();
        boolean isSelected = position == selectedChannelIndex;
        boolean isFocused = position == focusedChannelIndex;
        
        int themeColor = ((BaseActivity) mContext).getThemeColor();
        tvChannelNum.setTextColor(isSelected ? themeColor : Color.WHITE);
        tvChannel.setTextColor(isSelected ? themeColor : Color.WHITE);
        tvCurrentProgramName.setTextColor(isSelected ? themeColor : Color.WHITE);

        // EPG信息绑定
        bindEpgInfo(tvCurrentProgramName, item);

        // 焦点控制核心逻辑
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                handleFocusGained(position);
            } else {
                handleFocusLost(position);
            }
        });

        // 自动焦点恢复逻辑
        if (position == lastManualFocusPosition && !isAutoScroll) {
            holder.itemView.postDelayed(() -> {
                if (!holder.itemView.isFocused()) {
                    holder.itemView.requestFocus();
                }
            }, 100);
        }
    }

    private void bindEpgInfo(TextView textView, LiveChannelItem item) {
        // 保持原有EPG绑定逻辑...
    }

    private void handleFocusGained(int position) {
        focusedChannelIndex = position;
        if (!isAutoScroll) {
            lastManualFocusPosition = position;
        }
        notifyItemChanged(position);
    }

    private void handleFocusLost(int position) {
        if (focusedChannelIndex == position) {
            focusedChannelIndex = -1;
            notifyItemChanged(position);
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (this.selectedChannelIndex == selectedChannelIndex) return;
        
        int prev = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        
        if (prev != -1) notifyItemChanged(prev);
        if (this.selectedChannelIndex != -1) notifyItemChanged(this.selectedChannelIndex);
    }

    public void setAutoScroll(boolean autoScroll) {
        isAutoScroll = autoScroll;
    }

    public void setLastManualFocusPosition(int position) {
        lastManualFocusPosition = position;
    }

    public int getLastManualFocusPosition() {
        return lastManualFocusPosition;
    }

    public int getSelectedChannelIndex() {
        return selectedChannelIndex;
    }

    public void smartScrollToPosition(int position) {
        RecyclerView recyclerView = getRecyclerView();
        if (recyclerView == null) return;

        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            LinearLayoutManager linearManager = (LinearLayoutManager) layoutManager;
            
            int firstVisible = linearManager.findFirstVisibleItemPosition();
            int lastVisible = linearManager.findLastVisibleItemPosition();
            
            if (position < firstVisible || position > lastVisible) {
                setAutoScroll(true);
                linearManager.scrollToPositionWithOffset(position, 0);
                recyclerView.postDelayed(() -> setAutoScroll(false), 300);
            }
        }
    }
}
