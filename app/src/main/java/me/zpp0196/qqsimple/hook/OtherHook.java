package me.zpp0196.qqsimple.hook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import me.zpp0196.qqsimple.hook.base.BaseHook;
import me.zpp0196.qqsimple.hook.util.HookUtil;
import me.zpp0196.qqsimple.hook.util.XPrefs;

import static me.zpp0196.qqsimple.Common.PREFS_KEY_CHAT_TAIL;
import static me.zpp0196.qqsimple.Common.PREFS_KEY_FONT_SIZE;
import static me.zpp0196.qqsimple.Common.PREFS_KEY_IMG_ALPHA;
import static me.zpp0196.qqsimple.Common.PREFS_VALUE_CHAT_IMG_ALPHA;
import static me.zpp0196.qqsimple.Common.PREFS_VALUE_CHAT_TAIL;
import static me.zpp0196.qqsimple.Common.PREFS_VALUE_FONT_SIZE;
import static me.zpp0196.qqsimple.hook.comm.Classes.AIOImageProviderService;
import static me.zpp0196.qqsimple.hook.comm.Classes.AbstractGalleryScene;
import static me.zpp0196.qqsimple.hook.comm.Classes.BaseChatPie;
import static me.zpp0196.qqsimple.hook.comm.Classes.Card;
import static me.zpp0196.qqsimple.hook.comm.Classes.ContactUtils;
import static me.zpp0196.qqsimple.hook.comm.Classes.Conversation;
import static me.zpp0196.qqsimple.hook.comm.Classes.CoreService;
import static me.zpp0196.qqsimple.hook.comm.Classes.CoreService$KernelService;
import static me.zpp0196.qqsimple.hook.comm.Classes.CountDownProgressBar;
import static me.zpp0196.qqsimple.hook.comm.Classes.FontSettingManager;
import static me.zpp0196.qqsimple.hook.comm.Classes.HotChatFlashPicActivity;
import static me.zpp0196.qqsimple.hook.comm.Classes.MessageRecord;
import static me.zpp0196.qqsimple.hook.comm.Classes.MessageRecordFactory;
import static me.zpp0196.qqsimple.hook.comm.Classes.QQAppInterface;
import static me.zpp0196.qqsimple.hook.comm.Classes.QQMessageFacade;
import static me.zpp0196.qqsimple.hook.comm.Classes.QQSettingSettingActivity;
import static me.zpp0196.qqsimple.hook.comm.Classes.UpgradeController;
import static me.zpp0196.qqsimple.hook.comm.Classes.UpgradeDetailWrapper;
import static me.zpp0196.qqsimple.hook.comm.Classes.XEditTextEx;
import static me.zpp0196.qqsimple.hook.comm.Maps.settingItem;
import static me.zpp0196.qqsimple.hook.util.HookUtil.isMoreThan755;

/**
 * Created by zpp0196 on 2018/3/11.
 */

class OtherHook extends BaseHook {

    @Override
    public void init() {
        hookFontSize();
        hookService();
        preventFlashDisappear();
        preventMessagesWithdrawn();
        simulateMenu();
        hookQQUpgrade();
        hookXEditTextEx();
        transBg();
        hideSettingItem();
    }

    private void hookService() {
        // 禁用 CoreService
        findAndHookMethod(CoreService, "startCoreService", boolean.class, replaceNull("disable_coreservice"));
        findAndHookMethod(CoreService, "startTempService", replaceNull("disable_coreservice"));
        findAndHookMethod(CoreService$KernelService, "onCreate", replaceNull("disable_coreservice"));
    }

    /**
     * 防止闪照消失
     */
    private void preventFlashDisappear() {
        findAndHookMethod(CountDownProgressBar, "a", replaceNull("prevent_flash_disappear"));
        findAndHookMethod(HotChatFlashPicActivity, "onTouch", View.class, MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (getBool("prevent_flash_disappear")) {
                    param.args[1] = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
                }
            }
        });
        Method method = findMethodIfExists(AIOImageProviderService, void.class, "a", long.class);
        hookMethod(method, replaceNull("prevent_flash_disappear"));

        // 隐藏倒计时
        findAndHookMethod(CountDownProgressBar, "onDraw", Canvas.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                hideView((View) param.thisObject, "prevent_flash_disappear");
            }
        });

        blockSecureFlag();
    }

    /**
     * 允许闪照截图
     */
    private void blockSecureFlag() {
        if (!getBool("prevent_flash_disappear")) {
            return;
        }
        findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Integer.parseInt(param.args[0].toString()) ==
                    WindowManager.LayoutParams.FLAG_SECURE) {
                    param.setResult(null);
                }
            }
        });
        findAndHookMethod(SurfaceView.class, "setSecure", boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = false;
            }
        });
    }

    /**
     * 防止消息撤回
     */
    private void preventMessagesWithdrawn() {
        if (!getBool("prevent_messages_withdrawn"))
            return;
        findAndHookMethod(QQMessageFacade, "a", ArrayList.class, boolean.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                ArrayList arrayList = (ArrayList) param.args[0];
                if (arrayList == null || arrayList.isEmpty() ||
                    HookUtil.isCallingFrom("C2CMessageProcessor")) {
                    return null;
                }
                Object revokeMsgInfo = arrayList.get(0);

                String friendUin = getObject(revokeMsgInfo, String.class, "a");
                String fromUin = getObject(revokeMsgInfo, String.class, "b");
                int isTroop = getObject(revokeMsgInfo, int.class, "a");
                long msgUid = getObject(revokeMsgInfo, long.class, "b");
                long shmsgseq = getObject(revokeMsgInfo, long.class, "a");
                long time = getObject(revokeMsgInfo, long.class, "c");

                Object qqAppInterface = getObject(param.thisObject, QQAppInterface, "a");
                String selfUin = (String) XposedHelpers.callMethod(qqAppInterface, "getCurrentAccountUin");

                if (selfUin.equals(fromUin)) {
                    return null;
                }

                int msgType = (int) XposedHelpers.getStaticObjectField(MessageRecord, "MSG_TYPE_REVOKE_GRAY_TIPS");
                List tip = getRevokeTip(qqAppInterface, selfUin, friendUin, fromUin, msgUid, shmsgseq,
                        time + 1, msgType, isTroop);
                if (tip != null && !tip.isEmpty()) {
                    try {
                        XposedHelpers.callMethod(param.thisObject, "a", tip, selfUin);
                    } catch (Throwable ignored) {

                    }
                }
                return null;
            }
        });
    }

    /**
     * 撤回提示
     */
    private List getRevokeTip(Object qqAppInterface, String selfUin, String friendUin, String fromUin, long msgUid, long shmsgseq, long time, int msgType, int isTroop) {
        Object messageRecord = XposedHelpers.callStaticMethod(MessageRecordFactory, "a", msgType);

        String name;
        if (isTroop == 0) {
            name = "对方";
        } else {
            name = (String) XposedHelpers.callStaticMethod(ContactUtils, "a", qqAppInterface, fromUin, friendUin,
                    isTroop == 1 ? 1 : 2, 0);
        }

        XposedHelpers.callMethod(messageRecord, "init", selfUin,
                isTroop == 0 ? fromUin : friendUin, fromUin,
                name + "尝试撤回一条消息", time, msgType, isTroop, time);

        XposedHelpers.setObjectField(messageRecord, "msgUid",
                msgUid == 0 ? 0 : msgUid + new Random().nextInt());
        XposedHelpers.setObjectField(messageRecord, "shmsgseq", shmsgseq);
        XposedHelpers.setObjectField(messageRecord, "isread", true);

        List<Object> list = new ArrayList<>();
        list.add(messageRecord);
        return list;
    }

    /**
     * 模拟菜单
     */
    private void simulateMenu() {
        if (!getBool("simulate_menu"))
            return;
        findAndHookMethod(Conversation, "D", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                RelativeLayout relativeLayout = getObject(param.thisObject, RelativeLayout.class, "b");
                relativeLayout.setOnClickListener(v -> new Thread(() -> {
                    try {
                        Instrumentation inst = new Instrumentation();
                        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start());
            }
        });
    }

    /**
     * 隐藏QQ更新提示
     */
    private void hookQQUpgrade() {
        // FIXME
        Method method = findMethodIfExists(UpgradeController, UpgradeDetailWrapper, "a");
        hookMethod(method, replaceNull("hook_qq_upgrade"));
    }

    /**
     * 自定义字体大小
     */
    private void hookFontSize() {
        if (!getBool("hook_font_size")) {
            return;
        }
        float fontSize = Float.parseFloat(XPrefs.getPref()
                .getString(PREFS_KEY_FONT_SIZE, PREFS_VALUE_FONT_SIZE));
        findAndHookMethod(FontSettingManager, "a", float.class, replaceObj(true, "hook_font_size"));
        findAndHookMethod(FontSettingManager, "a", Context.class, float.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[1] = fontSize;
            }
        });
    }

    /**
     * 聊天小尾巴
     */
    private void hookXEditTextEx() {
        if (!getBool("hook_chat_tail")) {
            return;
        }
        String tail = XPrefs.getPref()
                .getString(PREFS_KEY_CHAT_TAIL, PREFS_VALUE_CHAT_TAIL);
        String[] keyWords = XPrefs.getPref()
                .getString("chat_tail_keywords", "")
                .split(" ");
        findAndHookMethod(BaseChatPie, isMoreThan755() ? "ak" : "ai", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                EditText editText = getObject(param.thisObject, XEditTextEx, "a");
                Editable editable = editText.getText();
                if (isAddTail(editable.toString())) {
                    editText.setText(editable.append(tail));
                }
            }

            private boolean isAddTail(String text) {
                if (text.endsWith(tail))
                    return false;
                for (String str : keyWords) {
                    if (!str.isEmpty() && text.contains(str))
                        return false;
                }
                return true;
            }
        });
    }

    /**
     * 聊天图片背景透明
     */
    private void transBg() {
        if (!getBool("transparent_chat_img_background")) {
            return;
        }
        float range = Float.parseFloat(XPrefs.getPref()
                .getString(PREFS_KEY_IMG_ALPHA, PREFS_VALUE_CHAT_IMG_ALPHA));
        findAndHookMethod(AbstractGalleryScene, "a", ViewGroup.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View d = getObject(param.thisObject, View.class, "d");
                if (d != null) {
                    d.setAlpha(range);
                }
                RelativeLayout root = getObject(param.thisObject, RelativeLayout.class, "a");
                View view = root.getChildAt(root.getChildCount() - 1);
                if (view != null) {
                    view.setAlpha(range);
                }
            }
        });
    }

    /**
     * 隐藏设置界面内容
     */
    private void hideSettingItem() {
        findAndHookMethod(QQSettingSettingActivity, "a", int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                int viewId = Integer.parseInt(param.args[0].toString());
                int strId = Integer.parseInt(param.args[1].toString());
                String str = activity.getString(strId);
                for (String key : settingItem.keySet()) {
                    String value = settingItem.get(key);
                    if (str.contains(value)) {
                        hideView(activity.findViewById(viewId), key);
                    }
                }
            }
        });
        // 隐藏达人
        findAndHookMethod(QQSettingSettingActivity, "c", Card, hideView(RelativeLayout.class, "a", "hide_setting_newXman"));
        // 隐藏设置免流量特权
        findAndHookMethod(QQSettingSettingActivity, "a", replaceNull("hide_setting_kingCard"));
    }
}