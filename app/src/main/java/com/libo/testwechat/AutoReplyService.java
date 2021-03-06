package com.libo.testwechat;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.libo.testwechat.http.Apis;
import com.libo.testwechat.http.MyCallback;
import com.libo.testwechat.util.PreferenceUtil;
import com.libo.testwechat.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import static com.libo.testwechat.Constant.PROJECT;

public class AutoReplyService extends AccessibilityService {
    private final static String MM_LAUNCHERUI = "com.tencent.mm.ui.LauncherUI";
    boolean hasAction = false;
    boolean hasActionForSend = false;
    int count = 0;

    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:// 通知栏事件
                List<CharSequence> texts = event.getText();
                if (texts.isEmpty()) break;
                for (CharSequence text : texts) {
                    if (TextUtils.isEmpty(text.toString())) break;

                    Intent intent = new Intent("new_message");
                    intent.putExtra("msg", "收到新消息：" + text.toString());
                    sendBroadcast(intent);

                    if (!App.getInstance().isLogin()) break;
                    if (!getSharedPreferences().getString(Constant.STATUS, "").equals("1")) break;

                    if (text.toString().contains("近")
                            && text.toString().contains("期")
                            && text.toString().contains("----")) {
                        //提交开奖结果
                        hasAction = true;
                        count++;
                        if (count == 2)
                            openWechatByNotification(event);
                        end(text.toString());

                    } else if (text.toString().contains("在线")
                            && text.toString().contains("总分")
                            && text.toString().contains("欢迎你")) {
                        //查账单-并判断是否下注进行下注
                        hasAction = true;
                        count++;
                        if (count == 2)
                            openWechatByNotification(event);
                    } else if (!TextUtils.isEmpty(getSharedPreferences().getString(Constant.MESSAGE_FOR_WECHAT, ""))) {
                        if (Utils.isScreenLocked(this))
                            wakeAndUnlock();
                        hasAction = true;
                        hasActionForSend = true;
                        openWechatByNotification(event);
                    }
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                if (!App.getInstance().isLogin()) break;
                if (!getSharedPreferences().getString(Constant.STATUS, "").equals("1")) break;
                if (!hasAction) break;
                String className = event.getClassName().toString();
                if (!MM_LAUNCHERUI.equals(className)) break;

                if (hasActionForSend) {
                    if (fill(getSharedPreferences().getString(Constant.MESSAGE_FOR_WECHAT, "空"))) {
                        getSharedPreferences().edit().putString(Constant.MESSAGE_FOR_WECHAT, "").commit();
                        send();

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        back2MeRightNow();
                        hasAction = false;
                        hasActionForSend = false;
                    }
                } else {
                    refreshUserInfo();
                }
                break;
        }

    }

    @SuppressLint("NewApi")
    private boolean fill(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            return findEditText(rootNode, text);
        }
        return false;
    }

    @SuppressLint("NewApi")
    private void send() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo
                    .findAccessibilityNodeInfosByText("发送");
            if (list == null || list.size() == 0)
                list = nodeInfo.findAccessibilityNodeInfosByText("Send");

            if (list != null && list.size() > 0) {
                for (AccessibilityNodeInfo n : list) {
                    if (n.getClassName().equals("android.widget.Button") && n.isEnabled()) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                }
            }
        }
    }

    private void openWechatByNotification(AccessibilityEvent event) {
        count = 0;
        if (event.getParcelableData() != null
                && event.getParcelableData() instanceof Notification) {
            Notification notification = (Notification) event
                    .getParcelableData();
            PendingIntent pendingIntent = notification.contentIntent;
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    private boolean findEditText(AccessibilityNodeInfo rootNode, String content) {
        int count = rootNode.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo nodeInfo = rootNode.getChild(i);
            if (nodeInfo == null) {
                continue;
            }
            if ("android.widget.EditText".equals(nodeInfo.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
                arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                        true);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                        arguments);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                ClipData clip = ClipData.newPlainText("label", content);
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(clip);
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                return true;
            }

            if (findEditText(nodeInfo, content)) {
                return true;
            }
        }

        return false;
    }

    public void back2Me() {
        try {
            Thread.sleep(12000);
            back2MeRightNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
            back2MeRightNow();
        }
    }

    public void back2MeRightNow() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ComponentName cn = new ComponentName("com.libo.testwechat", "com.libo.testwechat.HomeActivity");
            intent.setComponent(cn);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.libo.testwechat");
            intent.putExtra("autoLoginAction", true);
            if (intent != null)
                startActivity(intent);
        }
    }

    private void wakeAndUnlock() {
        //获取电源管理器对象
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        //获取PowerManager.WakeLock对象，后面的参数|表示同时传入两个值，最后的是调试用的Tag
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");

        //点亮屏幕
        wl.acquire(1000);

        //得到键盘锁管理器对象
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unLock");

        //解锁
        kl.disableKeyguard();

    }

    public String findLastChatMessage() {
        String returnStr = "";
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo
                    .findAccessibilityNodeInfosByViewId("com.tencent.mm:id/if");
            System.out.println("判断" + (list != null && list.size() > 0));
            if (list != null && list.size() > 0) {
                System.out.println("list.size()=" + list.size());
                for (int i = list.size() - 1; i > -1; i--) {
                    AccessibilityNodeInfo lastNode = list.get(i);
                    String str = lastNode.getText().toString();
                    if (str.contains("总分") && str.contains("在线") && str.contains("欢迎你")) {
                        returnStr = str;
                        break;
                    }
                }
            }
        }
        return returnStr;
    }

    public void refreshUserInfo() {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) {
            back2Me();
            return;
        }
        Apis.getInstance().getUserInfo(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                try {
                    JSONObject jsonObject = new JSONObject(body);
                    String billName = jsonObject.optString("billname");
                    getSharedPreferences().edit()
                            .putString(Constant.BILL_NAME, billName)
                            .putString(Constant.STATUS, jsonObject.optString("status"))
                            .putString(Constant.BALANCE, jsonObject.optString("balance"))
                            .commit();
                    postBill(billName);
                } catch (JSONException e) {
                    e.printStackTrace();
                    hasAction = false;
                    back2Me();
                }
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                hasAction = false;
                back2Me();
            }
        });
    }

    public void postBill(String billName) {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) {
            back2Me();
            return;
        }
        String lastMessage = findLastChatMessage();
        System.out.println("======lastMsg====" + lastMessage);
        if (TextUtils.isEmpty(lastMessage)) {
            back2MeRightNow();
            return;
        }
        final String bill = Utils.findBill(billName, lastMessage);
        System.out.println("=======bill====" + bill);
        Apis.getInstance().postBill(uid, bill, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                getSharedPreferences().edit().putString(Constant.BALANCE, bill)
                        .putBoolean(Constant.NEED_SOUND, false)
                        .commit();
                start();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                hasAction = false;
                if (responseStatus == 666) {
                    getSharedPreferences().edit()
                            .putInt(Constant.CURRENT, responseStatus)
                            .putString(Constant.CURRENT_TIP, errMsg)
                            .putBoolean(Constant.NEED_SOUND, true)
                            .commit();
                }
                back2Me();
            }
        });
    }

    public void start() {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) return;
        Apis.getInstance().start(uid, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
                hasAction = false;
                if (fill(body)) {
                    try {
                        Thread.sleep(2000);
                        send();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                back2Me();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
                hasAction = false;
                if (responseStatus == 400) {
                }
                back2Me();
            }
        });
    }

    public void end(String msg) {
        String uid = PreferenceUtil.getInstance().getString(Constant.UID, "");
        if (TextUtils.isEmpty(uid)) return;
        String last = Utils.findLastWord(msg);
        String phase = Utils.findPhase(msg);
        System.out.println("==期==phase" + phase);
        System.out.println("======last" + last);
        Apis.getInstance().end(uid, phase, last, new MyCallback() {
            @Override
            public void responeData(String body, JSONObject json) {
//                hasAction = false;
//                back2Me();
            }

            @Override
            public void responeDataFail(int responseStatus, String errMsg) {
//                hasAction = false;
                if (responseStatus == 400) {
                }
//                back2Me();
            }
        });
    }

    SharedPreferences mSharedPreferences;

    public SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null)
            mSharedPreferences = this.getSharedPreferences(PROJECT,
                    MODE_PRIVATE);
        return mSharedPreferences;
    }

}
