package com.github.tvbox.osc.util;

import android.content.Context;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.player.IjkmPlayer;
import com.github.tvbox.osc.player.render.SurfaceRenderViewFactory;
import com.github.tvbox.osc.ui.activity.ItvDns;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import xyz.doikki.videoplayer.aliplayer.AliyunMediaPlayerFactory;
import xyz.doikki.videoplayer.player.AndroidMediaPlayerFactory;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.PlayerViewRenderViewFactory;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class PlayerHelper {
    private static final String TAG = "PlayerHelper";

    public static String rewriteProxyUrl(String url) {
        try {
            if (url != null && url.startsWith("http://127.0.0.1:9978/?channel-id=")) {
                return url.split("channel-id=")[1];
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    public static void updateCfg(VideoView videoView, JSONObject playerCfg) {
        updateCfg(videoView, playerCfg, -1);
    }

    public static void updateCfg(VideoView videoView, JSONObject playerCfg, int forcePlayerType) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        String ijkCode = Hawk.get(HawkConfig.IJK_CODEC, "软解码");
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        
        try {
            playerType = playerCfg.getInt("pl");
            ijkCode = playerCfg.getString("ijk");
            scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        if (forcePlayerType >= 0) playerType = forcePlayerType;
        IJKCode codec = ApiConfig.get().getIJKCodec(ijkCode);
        
        PlayerFactory playerFactory;
        if (playerType == 1) {
            playerFactory = new PlayerFactory<IjkmPlayer>() {
                @Override
                public IjkmPlayer createPlayer(Context context) {
                    return new IjkmPlayer(context, codec);
                }
            };
        } else if (playerType == 2) {
            playerFactory = new PlayerFactory<EXOmPlayer>() {
                @Override
                public EXOmPlayer createPlayer(Context context) {
                    return new EXOmPlayer(context);
                }
            };
        } else if (playerType == 3) {
            playerFactory = AliyunMediaPlayerFactory.create();
        } else {
            playerFactory = AndroidMediaPlayerFactory.create();
        }

        RenderViewFactory renderViewFactory = null;
        if (playerType == 2) {
            renderViewFactory = PlayerViewRenderViewFactory.create(renderType);
        } else {
            switch (renderType) {
                case 0:
                default:
                    renderViewFactory = TextureRenderViewFactory.create();
                    break;
                case 1:
                    renderViewFactory = SurfaceRenderViewFactory.create();
                    break;
            }
        }

        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
        videoView.setScreenScaleType(scale);
    }

    public static void init() {
        IjkMediaPlayer.loadLibrariesOnce(null);
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(ItvDns.PORT));
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", String.valueOf(ItvDns.PORT));
    }

    public static String getPlayerName(int playType) {
        switch (playType) {
            case 1: return "IJK";
            case 2: return "Exo";
            case 3: return "阿里";
            case 10: return "MX";
            case 11: return "Reex";
            case 12: return "Kodi";
            default: return "系统";
        }
    }

    public static String getRenderName(int renderType) {
        return renderType == 1 ? "SurfaceView" : "TextureView";
    }

    public static String getScaleName(int screenScaleType) {
        switch (screenScaleType) {
            case VideoView.SCREEN_SCALE_DEFAULT: return "默认";
            case VideoView.SCREEN_SCALE_16_9: return "16:9";
            case VideoView.SCREEN_SCALE_4_3: return "4:3";
            case VideoView.SCREEN_SCALE_MATCH_PARENT: return "填充";
            case VideoView.SCREEN_SCALE_ORIGINAL: return "原始";
            case VideoView.SCREEN_SCALE_CENTER_CROP: return "裁剪";
            default: return "默认";
        }
    }

    public static String getRootCauseMessage(Throwable th) {
        for (int i = 0; i < 10 && th.getCause() != null; i++) {
            th = th.getCause();
        }
        return th.getLocalizedMessage();
    }
}
