package com.mobile.novabox.util;

import android.content.Context;

import com.mobile.novabox.player.ExoMediaPlayerFactory;
import com.mobile.novabox.player.MpvMediaPlayer;
import com.mobile.novabox.player.render.SurfaceRenderViewFactory;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class PlayerHelper {
    /** 播放器类型:0=EXO硬解,1=EXO软解,2=MPV硬解,3=MPV软解 */
    public static final int PLAY_TYPE_EXO_HW = 0;
    public static final int PLAY_TYPE_EXO_SW = 1;
    public static final int PLAY_TYPE_MPV_HW = 2;
    public static final int PLAY_TYPE_MPV_SW = 3;

    public static void updateCfg(VideoView videoView, JSONObject playerCfg) {
        updateCfg(videoView,playerCfg,-1);
    }
    public static void updateCfg(VideoView videoView, JSONObject playerCfg,int forcePlayerType) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, PLAY_TYPE_EXO_HW);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        try {
            playerType = playerCfg.getInt("pl");
            renderType = playerCfg.getInt("pr");
            scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(forcePlayerType>=0)playerType = forcePlayerType;

        PlayerFactory playerFactory = buildPlayerFactory(playerType);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        if(videoView!=null){
            videoView.setPlayerFactory(playerFactory);
            videoView.setRenderViewFactory(renderViewFactory);
            videoView.setScreenScaleType(scale);
        }
    }

    public static void updateCfg(VideoView videoView) {
        int playType = Hawk.get(HawkConfig.PLAY_TYPE, PLAY_TYPE_EXO_HW);
        PlayerFactory playerFactory = buildPlayerFactory(playType);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    public static void updateCfg(VideoView videoView, int playerType) {
        if (playerType < 0 || playerType > 3) playerType = PLAY_TYPE_EXO_HW;
        PlayerFactory playerFactory = buildPlayerFactory(playerType);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    public static void updateCfgAudioForceExo(VideoView videoView) {
        PlayerFactory playerFactory = buildPlayerFactory(PLAY_TYPE_EXO_HW);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    private static PlayerFactory buildPlayerFactory(int playerType) {
        switch (playerType) {
            case PLAY_TYPE_EXO_SW:
                return ExoMediaPlayerFactory.createSoftwareDecode();
            case PLAY_TYPE_MPV_HW:
                return new PlayerFactory<MpvMediaPlayer>() {
                    @Override
                    public MpvMediaPlayer createPlayer(Context context) {
                        return new MpvMediaPlayer(context, true);
                    }
                };
            case PLAY_TYPE_MPV_SW:
                return new PlayerFactory<MpvMediaPlayer>() {
                    @Override
                    public MpvMediaPlayer createPlayer(Context context) {
                        return new MpvMediaPlayer(context, false);
                    }
                };
            case PLAY_TYPE_EXO_HW:
            default:
                return ExoMediaPlayerFactory.create();
        }
    }

    public static String getPlayerName(int playType) {
        HashMap<Integer, String> playersInfo = getPlayersInfo();
        if (playersInfo.containsKey(playType)) {
            return playersInfo.get(playType);
        } else {
            return "未知播放器";
        }
    }

    private static java.util.LinkedHashMap<Integer, String> mPlayersInfo = null;
    public static HashMap<Integer, String> getPlayersInfo() {
        if (mPlayersInfo == null) {
            java.util.LinkedHashMap<Integer, String> playersInfo = new java.util.LinkedHashMap<>();
            playersInfo.put(PLAY_TYPE_EXO_HW, "EXO硬解");
            playersInfo.put(PLAY_TYPE_EXO_SW, "EXO软解");
            playersInfo.put(PLAY_TYPE_MPV_HW, "MPV硬解");
            playersInfo.put(PLAY_TYPE_MPV_SW, "MPV软解");
            mPlayersInfo = playersInfo;
        }
        return mPlayersInfo;
    }

    public static ArrayList<Integer> getExistPlayerTypes() {
        return new ArrayList<>(getPlayersInfo().keySet());
    }

    public static String getRenderName(int renderType) {
        if (renderType == 1) {
            return "SurfaceView";
        } else {
            return "TextureView";
        }
    }

    public static String getScaleName(int screenScaleType) {
        String scaleText = "默认";
        switch (screenScaleType) {
            case VideoView.SCREEN_SCALE_DEFAULT:
                scaleText = "默认";
                break;
            case VideoView.SCREEN_SCALE_16_9:
                scaleText = "16:9";
                break;
            case VideoView.SCREEN_SCALE_4_3:
                scaleText = "4:3";
                break;
            case VideoView.SCREEN_SCALE_MATCH_PARENT:
                scaleText = "填充";
                break;
            case VideoView.SCREEN_SCALE_ORIGINAL:
                scaleText = "原始";
                break;
            case VideoView.SCREEN_SCALE_CENTER_CROP:
                scaleText = "裁剪";
                break;
        }
        return scaleText;
    }

    public static String getDisplaySpeed(long speed,boolean show) {
        if(speed > 1048576)
            return new DecimalFormat("#.00").format(speed / 1048576d) + "Mb/s";
        else if(speed > 1024)
            return (speed / 1024) + "Kb/s";
        else
            return speed > 0?speed + "B/s":(show?"0B/s":"");
    }
    public static String getDisplaySpeedBps(long speed, boolean show) {
        long bitSpeed = speed * 8;
        if (bitSpeed >= 1_000_000_000) {
            return new DecimalFormat("0.00").format(bitSpeed / 1_000_000_000d) + "Gbps";
        } else if (bitSpeed >= 1_000_000) {
            return new DecimalFormat("0.0").format(bitSpeed / 1_000_000d) + "Mbps";
        } else if (bitSpeed >= 1_000) {
            return new DecimalFormat("0.0").format(bitSpeed / 1_000d) + "Kbps";
        } else {
            return bitSpeed > 0 ? bitSpeed + "bps" : (show ? "0bps" : "");
        }
    }
}
