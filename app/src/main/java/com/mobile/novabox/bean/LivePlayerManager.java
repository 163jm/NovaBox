package com.mobile.novabox.bean;

import androidx.annotation.NonNull;

import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;
    private String currentApi="";

    public void init(VideoView videoView) {
        try {
            currentApi=Hawk.get(HawkConfig.LIVE_API_URL,"");
            // 4 档 LIVE_PLAY_TYPE:0=EXO硬解,1=EXO软解,2=MPV硬解,3=MPV软解;默认 MPV硬解(2)
            int playType = com.mobile.novabox.api.ApiConfig.safeGetInt(HawkConfig.LIVE_PLAY_TYPE, 2);
            if (playType < 0 || playType > 3) playType = 2;
            defaultPlayerConfig.put("pl", playType);
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        channelName=currentCfgKey(channelName);
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            try {
                defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            else
                videoView.setScreenScaleType(Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
            return;
        }
        try {
            playerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            if (playerType >= 0 && playerType <= 3) {
                playerTypeIndex = playerType;
            } else {
                playerTypeIndex = 0;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        channelName=currentCfgKey(channelName);
        JSONObject playerConfig = currentPlayerConfig;
        try {
            if (playerType >= 0 && playerType <= 3) {
                playerConfig.put("pl", playerType);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public boolean switchLivePlayer(VideoView videoView, String channelName, Set<Integer> triedPlayerTypes) {
        channelName = currentCfgKey(channelName);
        JSONObject playerConfig = currentPlayerConfig;
        if (playerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config");
            return false;
        }
        try {
            int playerType = playerConfig.getInt("pl");
            int switchPlayerType = PlayerSwitchUtil.nextPlayerType(playerType, triedPlayerTypes);
            if (switchPlayerType < 0) {
                LOG.i("echo-liveSwitchPlayer: all player types tried, skip");
                return false;
            }
            LOG.i("echo-liveSwitchPlayer: " + playerType + " -> " + switchPlayerType);
            playerConfig.put("pl", switchPlayerType);
        } catch (JSONException e) {
            LOG.i("echo-liveSwitchPlayer error: " + e.getMessage());
            return false;
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
        return true;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);
        Hawk.put(HawkConfig.LIVE_PLAY_SCALE, playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
            defaultPlayerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    private String currentCfgKey(String channelName)
    {
        return currentApi+"_"+channelName;
    }
}
