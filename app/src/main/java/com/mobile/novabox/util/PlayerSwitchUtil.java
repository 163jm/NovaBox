package com.mobile.novabox.util;

import java.util.Set;

/**
 * 播放内核自动切换工具。
 *
 * 4 档 PLAY_TYPE:0=EXO硬解, 1=EXO软解, 2=MPV硬解, 3=MPV软解。
 * 播放失败时按固定顺序 0→1→2→3 逐个尝试其余内核,跳过当前与已尝试过的,
 * 四个播放场景(点播/直播/本地/OpenList)共用同一套逻辑。
 */
public class PlayerSwitchUtil {

    public static final int MAX_BUILTIN_TYPE = 3;

    /**
     * 计算下一个待尝试的内核档位。
     *
     * @param currentType 当前正在使用的内核档位(0~3)
     * @param tried       已尝试过的内核档位集合(方法内会把当前内核加入其中)
     * @return 下一个要尝试的内核档位;返回 -1 表示其余内核都试过了
     */
    public static int nextPlayerType(int currentType, Set<Integer> tried) {
        if (currentType < 0 || currentType > MAX_BUILTIN_TYPE) currentType = 0;
        tried.add(currentType);
        for (int i = 0; i <= MAX_BUILTIN_TYPE; i++) {
            if (!tried.contains(i)) return i;
        }
        return -1;
    }

    /**
     * 归一化 PLAY_TYPE 编码,超出 4 档范围(含历史遗留值)一律回退到 EXO硬解。
     */
    public static int normalizePlayType(int playType) {
        if (playType < 0 || playType > MAX_BUILTIN_TYPE) return 0;
        return playType;
    }
}
