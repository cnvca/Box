package com.github.tvbox.osc.bean;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
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
    
    // 新增用户选择状态标志
    private boolean isUserSelected = false;
    private List<Long> sourceLatencies = new ArrayList<>();
    private String channelId;
    private String contentId;
    private String stbId = "toShengfen"; // 默认值

    public void setinclude_back(boolean include_back) {
        this.include_back = include_back;
    }

    public boolean getinclude_back() {
        return include_back;
    }

    // 用户选择状态相关方法
    public boolean isUserSelected() {
        return isUserSelected;
    }

    public void setUserSelected(boolean userSelected) {
        isUserSelected = userSelected;
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

    // 测速相关方法
    public void setSourceLatency(int sourceIndex, long latency) {
        if (sourceLatencies.size() <= sourceIndex) {
            for (int i = sourceLatencies.size(); i <= sourceIndex; i++) {
                sourceLatencies.add(Long.MAX_VALUE);
            }
        }
        sourceLatencies.set(sourceIndex, latency);
    }

    public int getFastestSourceIndex() {
        int fastestIndex = 0;
        long minLatency = Long.MAX_VALUE;
        for (int i = 0; i < sourceLatencies.size(); i++) {
            if (sourceLatencies.get(i) < minLatency) {
                minLatency = sourceLatencies.get(i);
                fastestIndex = i;
            }
        }
        return fastestIndex;
    }

    // STB相关方法
    public String getChannelId() { return channelId; }
    public String getContentId() { return contentId; }
    public String getStbId() { return stbId; }

    public void setChannelId(String id) { 
        this.channelId = (id != null) ? id.trim() : "";
    }

    public void setContentId(String id) {
        this.contentId = (id != null) ? id.trim() : "";
    }

    public void setStbId(String id) {
        this.stbId = (id != null) ? id : "toShengfen";
    }

    // JSON初始化方法
    public void initFromJson(JsonObject json) {
        if (json != null) {
            setChannelId(json.get("channelId").getAsString());
            setContentId(json.get("contentId").getAsString());
            if (json.has("stbId")) {
                setStbId(json.get("stbId").getAsString());
            }
        }
    }
}
