package com.mobile.novabox.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mobile.novabox.R;
import com.mobile.novabox.ui.activity.HomeActivity;
import com.mobile.novabox.ui.activity.LivePlayActivity;
import com.mobile.novabox.ui.activity.MyActivity;

/**
 * 首页 / 直播 / 我的 三个页面共用的主导航栏。
 *
 * 结构:本组件只负责选中态与点击路由;具体样式(手机版底部横条 /
 * 平板 sw600dp 左侧竖栏)由 view_main_nav_bar 的布局限定符各留一份,
 * 页面布局里不再重复手写导航栏。
 *
 * 用法:
 * <pre>
 * &lt;com.mobile.novabox.ui.widget.MainNavBar
 *     android:id="@+id/bottomNavLayout"
 *     app:navSelected="home|live|my" ... /&gt;
 * </pre>
 *
 * 点击路由在组件内部统一处理:跳转目标页,栈内已存在时用
 * CLEAR_TOP|SINGLE_TOP 复用并清掉其上的页面;再次点击当前已选中的
 * tab 时回调 {@link #setOnTabReselectListener}(如首页滚回顶部)。
 */
public class MainNavBar extends FrameLayout {
    public static final int TAB_HOME = 0;
    public static final int TAB_LIVE = 1;
    public static final int TAB_MY = 2;

    private static final int COLOR_SELECTED = 0xFF0CADE2;
    private static final int COLOR_UNSELECTED = 0x80000000;
    private static final int COLOR_ICON_UNSELECTED = 0xFF000000;

    private int selectedTab = TAB_HOME;
    private Runnable reselectListener;

    public MainNavBar(Context context) {
        this(context, null);
    }

    public MainNavBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainNavBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 同一个布局名,手机/平板各自的表达由资源限定符(view_main_nav_bar / -sw600dp)选择
        inflate(context, R.layout.view_main_nav_bar, this);
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MainNavBar);
            selectedTab = ta.getInt(R.styleable.MainNavBar_navSelected, TAB_HOME);
            ta.recycle();
        }
        applySelection();
        wireClicks();
    }

    /** 再次点击"当前已选中"的 tab 时回调(例如首页滚回顶部);不需要可不设置 */
    public void setOnTabReselectListener(Runnable listener) {
        this.reselectListener = listener;
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    private void wireClicks() {
        findViewById(R.id.navHome).setOnClickListener(v -> openTab(TAB_HOME));
        findViewById(R.id.navLive).setOnClickListener(v -> openTab(TAB_LIVE));
        findViewById(R.id.navSetting).setOnClickListener(v -> openTab(TAB_MY));
    }

    private void openTab(int tab) {
        if (tab == selectedTab) {
            if (reselectListener != null) reselectListener.run();
            return;
        }
        Class<?> target;
        if (tab == TAB_LIVE) {
            target = LivePlayActivity.class;
        } else if (tab == TAB_MY) {
            target = MyActivity.class;
        } else {
            target = HomeActivity.class;
        }
        Intent intent = new Intent(getContext(), target);
        // 栈内已有目标页时复用并清掉其上的页面(等效原直播页 finish+CLEAR_TOP 的行为)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getContext().startActivity(intent);
    }

    private void applySelection() {
        bindItem(R.id.navHomeIcon, R.id.navHomeText, TAB_HOME == selectedTab);
        bindItem(R.id.navLiveIcon, R.id.navLiveText, TAB_LIVE == selectedTab);
        bindItem(R.id.navSettingIcon, R.id.navSettingText, TAB_MY == selectedTab);
    }

    private void bindItem(int iconId, int textId, boolean selected) {
        ImageView icon = findViewById(iconId);
        if (icon != null) {
            icon.setAlpha(selected ? 1.0f : 0.5f);
            icon.setColorFilter(selected ? COLOR_SELECTED : COLOR_ICON_UNSELECTED);
        }
        TextView text = findViewById(textId);
        if (text != null) {
            text.setTextColor(selected ? COLOR_SELECTED : COLOR_UNSELECTED);
        }
    }
}
