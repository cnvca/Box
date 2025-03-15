package com.github.tvbox.osc.bean;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Epginfo {

    public Date startdateTime; // 节目开始时间
    public Date enddateTime;   // 节目结束时间
    public int datestart;      // 开始时间的数字表示（HHmm）
    public int dateend;        // 结束时间的数字表示（HHmm）
    public String title;       // 节目名称
    public String originStart; // 原始开始时间字符串
    public String originEnd;   // 原始结束时间字符串
    public String start;       // 格式化后的开始时间（HH:mm）
    public String end;         // 格式化后的结束时间（HH:mm）
    public int index;          // 节目索引
    public Date epgDate;       // EPG 日期
    public String currentEpgDate = null; // 当前 EPG 日期字符串
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd"); // 日期格式化工具

    // 构造函数
    public Epginfo(Date date, String title, Date epgDate, String startTime, String endTime, int index) {
        this.epgDate = date;
        this.currentEpgDate = timeFormat.format(epgDate);
        this.title = title;
        this.originStart = startTime;
        this.originEnd = endTime;
        this.index = index;

        // 设置时区并解析时间
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));

        SimpleDateFormat userSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        userSimpleDateFormat.setTimeZone(TimeZone.getDefault());

        // 解析开始时间和结束时间
        this.startdateTime = userSimpleDateFormat.parse(
                simpleDateFormat.format(epgDate) + " " + startTime + ":00 GMT+8:00", new ParsePosition(0));
        this.enddateTime = userSimpleDateFormat.parse(
                simpleDateFormat.format(epgDate) + " " + endTime + ":00 GMT+8:00", new ParsePosition(0));

        // 格式化时间为 HH:mm
        SimpleDateFormat zoneFormat = new SimpleDateFormat("HH:mm");
        this.start = zoneFormat.format(startdateTime);
        this.end = zoneFormat.format(enddateTime);

        // 将时间转换为数字表示（HHmm）
        this.datestart = Integer.parseInt(start.replace(":", ""));
        this.dateend = Integer.parseInt(end.replace(":", ""));
    }

    // 获取节目名称
    public String getTitle() {
        return title;
    }

    // 获取开始时间
    public String getStart() {
        return start;
    }

    // 获取结束时间
    public String getEnd() {
        return end;
    }

    // 获取原始开始时间
    public String getOriginStart() {
        return originStart;
    }

    // 获取原始结束时间
    public String getOriginEnd() {
        return originEnd;
    }

    // 获取节目索引
    public int getIndex() {
        return index;
    }

    // 获取 EPG 日期
    public Date getEpgDate() {
        return epgDate;
    }

    // 获取当前 EPG 日期字符串
    public String getCurrentEpgDate() {
        return currentEpgDate;
    }

    // 获取开始时间的数字表示（HHmm）
    public int getDatestart() {
        return datestart;
    }

    // 获取结束时间的数字表示（HHmm）
    public int getDateend() {
        return dateend;
    }
}
