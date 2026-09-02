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
 * libmpv 播放内核（适配 dev.jdtech.mpv:libmpv:0.5.1 静态 API）。
 * 硬解: hwdec=auto；软解: hwdec=no。
 * 对接 Doikki VideoView 的 AbstractPlayer。
 */
public class MpvMediaPlayer extends AbstractPlayer implements MPVLib.EventObserver {

    private final Context mAppContext;
    private final boolean mHardwareDecode;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private boolean mInited;
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
            // 0.5.1 为静态全局实例：create → setOption → init
            MPVLib.create(mAppContext);
            MPVLib.setOptionString("config", "no");
            MPVLib.setOptionString("terminal", "no");
            MPVLib.setOptionString("msg-level", "all=warn");
            MPVLib.setOptionString("idle", "yes");
            MPVLib.setOptionString("keep-open", "yes");
            MPVLib.setOptionString("force-window", "no");
            MPVLib.setOptionString("gpu-context", "android");
            MPVLib.setOptionString("opengl-es", "yes");
            MPVLib.setOptionString("hwdec", mHardwareDecode ? "auto" : "no");
            MPVLib.setOptionString("hwdec-codecs", "all");
            MPVLib.setOptionString("ao", "audiotrack");
            MPVLib.setOptionString("vd-lavc-o", "skip_frame=nonref");
            MPVLib.setOptionString("cache", "yes");
            MPVLib.setOptionString("demuxer-max-bytes", "64MiB");
            MPVLib.setOptionString("demuxer-max-back-bytes", "32MiB");
            MPVLib.setOptionString("network-timeout", "20");
            MPVLib.setOptionString("tls-verify", "no");
            MPVLib.setOptionString("ytdl", "no");
            MPVLib.setOptionString("sub-auto", "fuzzy");
            MPVLib.setOptionString("vo", "gpu");
            MPVLib.init();
            MPVLib.addObserver(this);
            MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE);
            MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE);
            MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG);
            MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG);
            MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG);
            MPVLib.observeProperty("video-params/w", MPVLib.MPV_FORMAT_INT64);
            MPVLib.observeProperty("video-params/h", MPVLib.MPV_FORMAT_INT64);
            MPVLib.observeProperty("track-list/count", MPVLib.MPV_FORMAT_INT64);
            mInited = true;
            LOG.i("echo-mpv-init hardware=" + mHardwareDecode);
        } catch (Throwable t) {
            LOG.i("echo-mpv-init-error:" + t.getMessage());
            mInited = false;
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
        }
    }

    @Override
    public void setOptions() {
        // options applied in initPlayer before native init
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mDataSource = path;
        mHeaders = headers;
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        if (fd != null) {
            mDataSource = "fd://" + fd.getParcelFileDescriptor().getFd();
        }
    }

    @Override
    public void prepareAsync() {
        if (!mInited || mDataSource == null) {
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
            return;
        }
        try {
            if (mHeaders != null && !mHeaders.isEmpty()) {
                StringBuilder hb = new StringBuilder();
                for (Map.Entry<String, String> e : mHeaders.entrySet()) {
                    if (hb.length() > 0) hb.append("\r\n");
                    hb.append(e.getKey()).append(": ").append(e.getValue());
                }
                MPVLib.setOptionString("http-header-fields", hb.toString());
            }
            if (mSurface != null) {
                MPVLib.attachSurface(mSurface);
                MPVLib.setPropertyString("force-window", "yes");
            }
            MPVLib.command(new String[]{"loadfile", mDataSource, "replace"});
            MPVLib.setPropertyBoolean("pause", false);
            mIsPlaying = true;
        } catch (Throwable t) {
            LOG.i("echo-mpv-prepare-error:" + t.getMessage());
            notifyError(PlayerEventListener.ERROR_TYPE_CODEC);
        }
    }

    @Override
    public void start() {
        if (!mInited) return;
        try {
            MPVLib.setPropertyBoolean("pause", false);
            mIsPlaying = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void pause() {
        if (!mInited) return;
        try {
            MPVLib.setPropertyBoolean("pause", true);
            mIsPlaying = false;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void stop() {
        if (!mInited) return;
        try {
            MPVLib.command(new String[]{"stop"});
            mIsPlaying = false;
            mIsPrepared = false;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void reset() {
        mIsPlaying = false;
        mIsPrepared = false;
        mIsBuffering = false;
        mHasVideoSize = false;
        mBufferedPercent = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        if (mInited) {
            try {
                MPVLib.command(new String[]{"stop"});
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isPlaying() {
        return mIsPlaying;
    }

    @Override
    public void seekTo(long time) {
        if (!mInited) return;
        try {
            MPVLib.command(new String[]{"seek", String.valueOf(time / 1000.0), "absolute"});
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void release() {
        mIsPlaying = false;
        mIsPrepared = false;
        if (mInited) {
            try {
                MPVLib.removeObserver(this);
                MPVLib.detachSurface();
                MPVLib.destroy();
            } catch (Throwable t) {
                LOG.i("echo-mpv-release-error:" + t.getMessage());
            }
            mInited = false;
        }
        mSurface = null;
        mMainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public long getCurrentPosition() {
        if (!mInited) return 0;
        try {
            Double pos = MPVLib.getPropertyDouble("time-pos");
            if (pos != null) return (long) (pos * 1000);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (!mInited) return 0;
        try {
            Double dur = MPVLib.getPropertyDouble("duration");
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
        if (!mInited) return;
        try {
            if (surface != null) {
                MPVLib.attachSurface(surface);
                MPVLib.setPropertyString("force-window", "yes");
            } else {
                MPVLib.detachSurface();
                MPVLib.setPropertyString("force-window", "no");
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
        if (!mInited) return;
        try {
            double vol = Math.max(0, Math.min(100, ((v1 + v2) / 2.0) * 100.0));
            MPVLib.setPropertyDouble("volume", vol);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (!mInited) return;
        try {
            MPVLib.setPropertyString("loop-file", isLooping ? "inf" : "no");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setSpeed(float speed) {
        mSpeed = speed;
        if (!mInited) return;
        try {
            MPVLib.setPropertyDouble("speed", (double) speed);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public float getSpeed() {
        return mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    private void notifyError(int type) {
        mMainHandler.post(() -> {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError();
            }
        });
    }

    // ---------- MPVLib.EventObserver (0.5.1) ----------

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
        // time-pos / duration 由 getCurrentPosition/getDuration 按需读取
    }

    @Override
    public void eventProperty(String property, boolean value) {
        if (mPlayerEventListener == null) return;
        if ("paused-for-cache".equals(property)) {
            mMainHandler.post(() -> {
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
            case MPVLib.MPV_EVENT_FILE_LOADED:
            case MPVLib.MPV_EVENT_START_FILE:
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
            case MPVLib.MPV_EVENT_END_FILE:
                break;
            case MPVLib.MPV_EVENT_VIDEO_RECONFIG:
                try {
                    Integer w = MPVLib.getPropertyInt("width");
                    Integer h = MPVLib.getPropertyInt("height");
                    if (w != null) mVideoWidth = w;
                    if (h != null) mVideoHeight = h;
                    maybeNotifyVideoSize();
                } catch (Throwable ignored) {
                }
                break;
            case MPVLib.MPV_EVENT_SHUTDOWN:
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
