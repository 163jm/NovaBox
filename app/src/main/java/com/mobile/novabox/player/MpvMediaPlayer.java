package com.mobile.novabox.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.mobile.novabox.util.LOG;

import java.util.Map;

import dev.jdtech.mpv.MPVLib;
import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * libmpv 播放内核，支持硬解(hwdec=auto/mediacodec)与软解(hwdec=no)。
 * 对接 Doikki VideoView 的 AbstractPlayer 接口。
 */
public class MpvMediaPlayer extends AbstractPlayer implements MPVLib.EventObserver {

    private final Context mAppContext;
    private final boolean mHardwareDecode;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private MPVLib mMpv;
    private Surface mSurface;
    private String mDataSource;
    private Map<String, String> mHeaders;
    private boolean mIsPlaying;
    private boolean mIsPrepared;
    private boolean mIsBuffering;
    private boolean mHasVideoSize;
    private float mSpeed = 1.0f;
    private int mBufferedPercent;
    private int mVideoWidth;
    private int mVideoHeight;

    public MpvMediaPlayer(Context context, boolean hardwareDecode) {
        mAppContext = context.getApplicationContext();
        mHardwareDecode = hardwareDecode;
    }

    @Override
    public void initPlayer() {
        try {
            mMpv = MPVLib.create(mAppContext);
            if (mMpv == null) {
                LOG.i("echo-mpv-create-failed");
                notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
                return;
            }
            mMpv.setOptionString("config", "no");
            mMpv.setOptionString("terminal", "no");
            mMpv.setOptionString("msg-level", "all=warn");
            mMpv.setOptionString("idle", "yes");
            mMpv.setOptionString("keep-open", "yes");
            mMpv.setOptionString("force-window", "no");
            mMpv.setOptionString("gpu-context", "android");
            mMpv.setOptionString("opengl-es", "yes");
            mMpv.setOptionString("hwdec", mHardwareDecode ? "auto" : "no");
            mMpv.setOptionString("hwdec-codecs", "all");
            mMpv.setOptionString("ao", "audiotrack");
            mMpv.setOptionString("vd-lavc-o", "skip_frame=nonref");
            mMpv.setOptionString("cache", "yes");
            mMpv.setOptionString("demuxer-max-bytes", "64MiB");
            mMpv.setOptionString("demuxer-max-back-bytes", "32MiB");
            mMpv.setOptionString("network-timeout", "20");
            mMpv.setOptionString("tls-verify", "no");
            mMpv.setOptionString("ytdl", "no");
            mMpv.setOptionString("sub-auto", "fuzzy");
            mMpv.setOptionString("vo", "gpu");
            mMpv.init();
            mMpv.addObserver(this);
            mMpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
            mMpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
            mMpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            mMpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            mMpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            mMpv.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            mMpv.observeProperty("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            mMpv.observeProperty("track-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            LOG.i("echo-mpv-init hardware=" + mHardwareDecode);
        } catch (Throwable t) {
            LOG.i("echo-mpv-init-error:" + t.getMessage());
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
        }
    }

    @Override
    public void setOptions() {
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mDataSource = path;
        mHeaders = headers;
        mIsPrepared = false;
        mHasVideoSize = false;
        mBufferedPercent = 0;
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
    }

    @Override
    public void prepareAsync() {
        if (mMpv == null || mDataSource == null) {
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
            return;
        }
        try {
            applyHeaders();
            if (mSurface != null) {
                mMpv.attachSurface(mSurface);
                mMpv.setPropertyString("force-window", "yes");
            }
            mMpv.command(new String[]{"loadfile", mDataSource, "replace"});
            mMpv.setPropertyBoolean("pause", false);
            mIsPlaying = true;
        } catch (Throwable t) {
            LOG.i("echo-mpv-prepare-error:" + t.getMessage());
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
        }
    }

    private void applyHeaders() {
        if (mHeaders == null || mHeaders.isEmpty() || mMpv == null) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mHeaders.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (sb.length() > 0) sb.append("\r\n");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        if (sb.length() > 0) {
            mMpv.setOptionString("http-header-fields", sb.toString());
            mMpv.setPropertyString("http-header-fields", sb.toString());
        }
        String ua = mHeaders.get("User-Agent");
        if (ua == null) ua = mHeaders.get("user-agent");
        if (ua != null) {
            mMpv.setOptionString("user-agent", ua);
            mMpv.setPropertyString("user-agent", ua);
        }
    }

    @Override
    public void start() {
        if (mMpv == null) return;
        try {
            mMpv.setPropertyBoolean("pause", false);
            mIsPlaying = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void pause() {
        if (mMpv == null) return;
        try {
            mMpv.setPropertyBoolean("pause", true);
            mIsPlaying = false;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void stop() {
        if (mMpv == null) return;
        try {
            mMpv.command(new String[]{"stop"});
            mIsPlaying = false;
            mIsPrepared = false;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void reset() {
        stop();
        mDataSource = null;
        mHeaders = null;
        mBufferedPercent = 0;
        mHasVideoSize = false;
    }

    @Override
    public boolean isPlaying() {
        return mIsPlaying;
    }

    @Override
    public void seekTo(long time) {
        if (mMpv == null) return;
        try {
            mMpv.command(new String[]{"seek", String.valueOf(time / 1000.0), "absolute"});
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void release() {
        mIsPlaying = false;
        mIsPrepared = false;
        if (mMpv != null) {
            try {
                mMpv.removeObserver(this);
                mMpv.detachSurface();
                mMpv.destroy();
            } catch (Throwable t) {
                LOG.i("echo-mpv-release-error:" + t.getMessage());
            }
            mMpv = null;
        }
        mSurface = null;
        mMainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public long getCurrentPosition() {
        if (mMpv == null) return 0;
        try {
            Double pos = mMpv.getPropertyDouble("time-pos");
            if (pos != null) return (long) (pos * 1000);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mMpv == null) return 0;
        try {
            Double dur = mMpv.getPropertyDouble("duration");
            if (dur != null) return (long) (dur * 1000);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @Override
    public int getBufferedPercentage() {
        return mBufferedPercent;
    }

    @Override
    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mMpv == null) return;
        try {
            if (surface != null) {
                mMpv.attachSurface(surface);
                mMpv.setPropertyInt("android-surface-size", 0);
                mMpv.setPropertyString("force-window", "yes");
            } else {
                mMpv.detachSurface();
                mMpv.setPropertyString("force-window", "no");
            }
        } catch (Throwable t) {
            LOG.i("echo-mpv-setSurface-error:" + t.getMessage());
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder != null ? holder.getSurface() : null);
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (mMpv == null) return;
        try {
            double vol = Math.max(0, Math.min(100, ((v1 + v2) / 2.0) * 100.0));
            mMpv.setPropertyDouble("volume", vol);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mMpv == null) return;
        try {
            mMpv.setPropertyString("loop-file", isLooping ? "inf" : "no");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setSpeed(float speed) {
        mSpeed = speed;
        if (mMpv == null) return;
        try {
            mMpv.setPropertyDouble("speed", speed);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public float getSpeed() {
        if (mMpv != null) {
            try {
                Double s = mMpv.getPropertyDouble("speed");
                if (s != null) return s.floatValue();
            } catch (Throwable ignored) {
            }
        }
        return mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    private void notifyError(int type) {
        mMainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError(type);
            }
        });
    }

    @Override
    public void eventProperty(String property) {
    }

    @Override
    public void eventProperty(String property, long value) {
        if ("video-params/w".equals(property)) {
            mVideoWidth = (int) value;
            maybeNotifyVideoSize();
        } else if ("video-params/h".equals(property)) {
            mVideoHeight = (int) value;
            maybeNotifyVideoSize();
        }
    }

    @Override
    public void eventProperty(String property, double value) {
    }

    @Override
    public void eventProperty(String property, boolean value) {
        if ("paused-for-cache".equals(property)) {
            mMainHandler.post(() -> {
                if (mPlayerEventListener == null) return;
                if (value && !mIsBuffering) {
                    mIsBuffering = true;
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                } else if (!value && mIsBuffering) {
                    mIsBuffering = false;
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 0);
                }
            });
        } else if ("eof-reached".equals(property) && value) {
            mIsPlaying = false;
            mMainHandler.post(() -> {
                if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
            });
        } else if ("pause".equals(property)) {
            mIsPlaying = !value;
        }
    }

    @Override
    public void eventProperty(String property, String value) {
    }

    @Override
    public void event(int eventId) {
        switch (eventId) {
            case MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED:
            case MPVLib.MpvEvent.MPV_EVENT_START_FILE:
                if (!mIsPrepared) {
                    mIsPrepared = true;
                    mMainHandler.post(() -> {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onPrepared();
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                    });
                }
                break;
            case MPVLib.MpvEvent.MPV_EVENT_END_FILE:
                break;
            case MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG:
                try {
                    if (mMpv != null) {
                        Integer w = mMpv.getPropertyInt("width");
                        Integer h = mMpv.getPropertyInt("height");
                        if (w != null) mVideoWidth = w;
                        if (h != null) mVideoHeight = h;
                        maybeNotifyVideoSize();
                    }
                } catch (Throwable ignored) {
                }
                break;
            case MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN:
                break;
            default:
                break;
        }
    }

    private void maybeNotifyVideoSize() {
        if (mVideoWidth > 0 && mVideoHeight > 0 && !mHasVideoSize) {
            mHasVideoSize = true;
            final int w = mVideoWidth;
            final int h = mVideoHeight;
            mMainHandler.post(() -> {
                if (mPlayerEventListener != null) {
                    mPlayerEventListener.onVideoSizeChanged(w, h);
                }
            });
        }
    }
}
