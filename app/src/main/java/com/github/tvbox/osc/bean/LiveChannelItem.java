package com.github.tvbox.osc.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveChannelItem {
    /**
     * channelIndex : 频道索引号
     * channelNum : 频道名称
     * channelSourceNames : 频道源名称
     * channelUrls : 频道源地址
     * sourceIndex : 频道源索引
     * sourceNum : 频道源总数
     */
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private ArrayList<String> channelSourceNames;
    private ArrayList<String> channelUrls;
    public int sourceIndex = 0;
    public int sourceNum = 0;
    public boolean include_back = false;
    
    // ▼▼▼▼▼ 新增测速相关字段 ▼▼▼▼▼
    private Map<Integer, Long> sourceSpeedMap = new HashMap<>(); // 存储各线路延迟(毫秒)
    private boolean hasSpeedTested = false; // 是否已完成测速

    public void setinclude_back(boolean include_back) {
        this.include_back = include_back;
    }

    public boolean getinclude_back() {
        return include_back;
    }

    public void setChannelIndex(int channelIndex) {
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public ArrayList<String> getChannelUrls() {
        return channelUrls;
    }

    public void setChannelUrls(ArrayList<String> channelUrls) {
        this.channelUrls = channelUrls;
        sourceNum = channelUrls.size();
    }

    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }

    public void nextSource() {
        sourceIndex++;
        if (sourceIndex == sourceNum) sourceIndex = 0;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public String getUrl() {
        return channelUrls.get(sourceIndex);
    }

    public int getSourceNum() {
        return sourceNum;
    }

    public ArrayList<String> getChannelSourceNames() {
        return channelSourceNames;
    }

    public void setChannelSourceNames(ArrayList<String> channelSourceNames) {
        this.channelSourceNames = channelSourceNames;
    }

    public String getSourceName() {
        return channelSourceNames.get(sourceIndex);
    }

    // ▼▼▼▼▼ 新增测速相关方法 ▼▼▼▼▼
    public void setSourceLatency(int sourceIndex, long latency) {
        sourceSpeedMap.put(sourceIndex, latency);
    }

    public int getFastestSourceIndex() {
        if (sourceSpeedMap.isEmpty()) return 0;
        int fastestIndex = 0;
        long minLatency = Long.MAX_VALUE;
        for (Map.Entry<Integer, Long> entry : sourceSpeedMap.entrySet()) {
            if (entry.getValue() < minLatency) {
                minLatency = entry.getValue();
                fastestIndex = entry.getKey();
            }
        }
        return fastestIndex;
    }

    public boolean isHasSpeedTested() {
        return hasSpeedTested;
    }

    public void setHasSpeedTested(boolean hasSpeedTested) {
        this.hasSpeedTested = hasSpeedTested;
    }
}
