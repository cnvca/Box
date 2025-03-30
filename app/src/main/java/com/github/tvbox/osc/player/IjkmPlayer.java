package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import xyz.doikki.videoplayer.ijk.IjkPlayer;
import xyz.doikki.videoplayer.ijk.RawDataSourceProvider;

public class IjkmPlayer extends IjkPlayer {

    private IJKCode codec = null;
    private OkHttpClient mOkClient; // 新增OkHttp客户端实例

    public IjkmPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
        this.mOkClient = OkGoHelper.getDefaultClient(); // 初始化全局OkHttp客户端
    }

    // 新增方法：检测是否需要使用OkHttp通道
    private boolean needUseOkHttp(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            Map<String, String> hosts = ApiConfig.get().getMyHost();
            if (host == null || hosts == null || hosts.isEmpty()) return false;

            // 支持通配符匹配（如 *.example.com）
            for (Map.Entry<String, String> entry : hosts.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.startsWith("*.")) {
                    String domain = key.substring(2);
                    if (host.endsWith("." + domain) || host.equals(domain)) {
                        LOG.i("HOSTS匹配 [通配符] " + key + " => " + host);
                        return true;
                    }
                } else if (host.equalsIgnoreCase(key)) {
                    LOG.i("HOSTS匹配 [精确] " + key + " => " + host);
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.e("HOSTS检测异常", e);
        }
        return false;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            // ================== 智能通道选择逻辑 ================== //
            boolean useOkHttp = needUseOkHttp(path);
            
            if (useOkHttp) {
                LOG.i("HOSTS映射触发，使用OkHttp通道");
                try {
                    // 构建请求（保留原始headers）
                    Request.Builder builder = new Request.Builder().url(path);
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            builder.addHeader(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    try (Response response = mOkClient.newCall(builder.build()).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("HTTP错误码: " + response.code());
                        }
                        
                        ResponseBody body = response.body();
                        if (body == null) throw new IOException("空响应体");
                        
                        // 根据内容类型处理数据流
                        if (path.contains(".m3u8") || path.contains(".mpd")) {
                            // 流媒体协议需要字符串形式
                            mMediaPlayer.setDataSource(body.string());
                        } else {
                            // 常规媒体使用输入流
                            mMediaPlayer.setDataSource(
                                new BufferedInputStream(body.byteStream()),
                                body.contentLength()
                            );
                        }
                        return; // 成功使用OkHttp通道后直接返回
                    }
                } catch (Exception e) {
                    LOG.e("OkHttp通道失败，降级到原生通道", e);
                    // 继续执行原生通道逻辑
                }
            }
            // ================== 智能通道逻辑结束 ================== //

            // 以下为原始IJK设置逻辑
            if (path != null && !TextUtils.isEmpty(path)) {
                if (path.startsWith("rtsp")) {
                    mMediaPlayer.setOption(1, "infbuf", 1);
                    mMediaPlayer.setOption(1, "rtsp_transport", "tcp");
                    mMediaPlayer.setOption(1, "rtsp_flags", "prefer_tcp");
                } else if (!path.contains(".m3u8") && (path.contains(".mp4") || path.contains(".mkv") || path.contains(".avi"))) {
                    if (Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)) {
                        String cachePath = FileUtils.getExternalCachePath() + "/ijkcaches/";
                        String cacheMapPath = cachePath;
                        File cacheFile = new File(cachePath);
                        if (!cacheFile.exists()) cacheFile.mkdirs();
                        String tmpMd5 = MD5.string2MD5(path);
                        cachePath += tmpMd5 + ".file";
                        cacheMapPath += tmpMd5 + ".map";
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_file_path", cachePath);
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_map_path", cacheMapPath);
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "parse_cache_map", 1);
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "auto_save_map", 1);
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cache_max_capacity", 60 * 1024 * 1024);
                        path = "ijkio:cache:ffio:" + path;
                    }
                }
            }
            setDataSourceHeader(headers);
            
            // 保留协议白名单设置
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, 
                "protocol_whitelist", 
                "ijkio,ffio,async,cache,crypto,file,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data,concat,subfile,ffconcat"
            );
            
            super.setDataSource(path, headers);
        } catch (Exception e) {
            mPlayerEventListener.onError(-1, PlayerHelper.getRootCauseMessage(e));
        }
    }

    // 以下保持原始方法不变
    @Override
    public void setOptions() {
        // 原有解码设置...
    }

    private String encodeSpaceChinese(String str) throws UnsupportedEncodingException {
        // 原有编码处理...
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // 原有资源加载...
    }

    private void setDataSourceHeader(Map<String, String> headers) {
        // 原有Header处理...
    }

    public TrackInfo getTrackInfo() {
        // 原有音轨处理...
    }

    public void setTrack(int trackIndex) {
        // 原有轨道选择...
    }

    public void setOnTimedTextListener(IMediaPlayer.OnTimedTextListener listener) {
        // 原有字幕监听...
    }
}
