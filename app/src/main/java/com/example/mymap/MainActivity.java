package com.example.mymap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.mymap.map.MapActivity;
import com.example.mymap.saep.SaepFrameworkClient;
import com.example.mymap.saep.SaepPolicy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 主界面：地图导航入口 + SAEP 协议的管理入口。
 */
public final class MainActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SaepFrameworkClient saepClient;
    private LinearLayout content;
    private TextView output;
    private String currentScreen = "home";
    private volatile int screenGeneration;
    private boolean callInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        saepClient = new SaepFrameworkClient(this);
        showHome();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!"home".equals(currentScreen)) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void showHome() {
        currentScreen = "home";
        beginScreen("MyMap", "百度地图导航 + SAEP 协议接入");
        addNavButton("地图与导航", view -> {
            startActivity(new Intent(this, MapActivity.class));
        });
        addNavButton("SAEP 开关", view -> showSwitch());
        addNavButton("静态策略", view -> showStaticPolicy());
        addNavButton("动态策略", view -> showDynamicPolicy());
        addNavButton("本地日志查询", view -> showLocalLogs());
        addNavButton("可信 Agent", view -> showTrustedAgents());
        setOutput("选择一个功能。所有 SAEP 调用都通过反射访问平台 Stub，"
                + "平台不支持时会明确提示而不会崩溃。");
    }

    private void showSwitch() {
        currentScreen = "switch";
        beginScreen("SAEP 开关", "查询框架级 SAEP 启用状态");
        addBackButton();
        Button read = addActionButton("读取开关");
        read.setOnClickListener(view -> runSaepCall("正在读取开关",
                () -> saepClient.queryProtocolEnabled()));
        setOutput(getString(R.string.saep_status_unknown));
    }

    private void showStaticPolicy() {
        currentScreen = "static";
        beginScreen("静态策略", "Manifest 元数据指向应用内打包的策略");
        addBackButton();
        Button preview = addActionButton("预览静态策略");
        preview.setOnClickListener(view -> setOutput(saepClient.readStaticPolicyPreview()));
        setOutput(saepClient.readStaticPolicyPreview());
    }

    private void showDynamicPolicy() {
        currentScreen = "dynamic";
        beginScreen("动态策略", "更新 SAEP 策略 provider");
        addBackButton();

        final EditText versionInput = addEditText("策略版本号", "2", true);
        addSectionLabel("应用级规则（勾选表示禁用）");
        final CheckBox appGlobal = addCheckBox("禁用全部 Agent 操作", false);
        final CheckBox appScreenshot = addCheckBox("禁用截图", false);
        final CheckBox appInput = addCheckBox("禁用输入", false);

        addSectionLabel("页面级规则（勾选表示禁用）");
        final CheckBox activityGlobal = addCheckBox("禁用全部 Agent 操作", false);
        final CheckBox activityScreenshot = addCheckBox("禁用截图", false);
        final CheckBox activityInput = addCheckBox("禁用输入", false);

        addSectionLabel("Agent intent（勾选表示禁用）");
        final CheckBox modifyContent = addCheckBox("修改内容", false);
        final CheckBox postContent = addCheckBox("发布内容", false);
        final CheckBox deleteContent = addCheckBox("删除内容", false);
        final CheckBox accountIncentive = addCheckBox("账号激励", false);

        View.OnClickListener previewListener = view -> {
            try {
                SaepPolicy.Config config = readPolicyConfig(versionInput,
                        appGlobal, appScreenshot, appInput,
                        activityGlobal, activityScreenshot, activityInput,
                        modifyContent, postContent, deleteContent, accountIncentive);
                setOutput(SaepPolicy.summarizePolicy(SaepPolicy.createDynamicPolicy(config)));
            } catch (IllegalArgumentException e) {
                setOutput("策略无效: " + e.getMessage());
            }
        };
        Button preview = addActionButton("预览动态策略");
        preview.setOnClickListener(previewListener);

        Button update = addActionButton("更新动态策略");
        update.setOnClickListener(view -> {
            final SaepPolicy.Config config;
            try {
                config = readPolicyConfig(versionInput,
                        appGlobal, appScreenshot, appInput,
                        activityGlobal, activityScreenshot, activityInput,
                        modifyContent, postContent, deleteContent, accountIncentive);
            } catch (IllegalArgumentException e) {
                setOutput("策略无效: " + e.getMessage());
                return;
            }
            runSaepCall("正在更新策略", () -> saepClient.updateDynamicPolicy(config));
        });
        preview.performClick();
    }

    private void showLocalLogs() {
        currentScreen = "logs";
        beginScreen("本地日志查询", "按可选过滤条件读取审计记录");
        addBackButton();
        final EditText startTime = addEditText("开始时间戳（毫秒，0 表示不限）", "0", true);
        final EditText endTime = addEditText("结束时间戳（毫秒，0 表示不限）", "0", true);
        final EditText targetIntent = addEditText("目标 intent（可选）", "", false);
        final EditText targetActivity = addEditText("目标 Activity（可选）", "", false);
        Button query = addActionButton("查询日志");
        query.setOnClickListener(view -> {
            final long startTimeMs;
            final long endTimeMs;
            try {
                startTimeMs = readTimestamp(startTime, "开始时间");
                endTimeMs = readTimestamp(endTime, "结束时间");
            } catch (IllegalArgumentException e) {
                setOutput("查询参数无效: " + e.getMessage());
                return;
            }
            final String intent = targetIntent.getText().toString().trim();
            final String activity = targetActivity.getText().toString().trim();
            runSaepCall("正在查询日志",
                    () -> saepClient.queryLogs(startTimeMs, endTimeMs, intent, activity));
        });
        setOutput("尚未查询。");
    }

    private void showTrustedAgents() {
        currentScreen = "agents";
        beginScreen("可信 Agent", "列出可信 Agent 并按 UUID 查询详情");
        addBackButton();
        Button list = addActionButton("列出可信 Agent");
        final EditText uuidInput = addEditText("Agent UUID", "", false);
        Button detail = addActionButton("查询 Agent 详情");
        list.setOnClickListener(view -> runSaepCall("正在列出可信 Agent",
                () -> saepClient.listRegisteredAgents()));
        detail.setOnClickListener(view -> {
            final String agentUuid = uuidInput.getText().toString().trim();
            runSaepCall("正在查询 Agent " + agentUuid,
                    () -> saepClient.getAgentInfo(agentUuid));
        });
        setOutput("先列出 Agent，再填 UUID 查询详情。");
    }

    private void beginScreen(String title, String subtitle) {
        screenGeneration++;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F6F8);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(0xFF1A5FB4);
        titleView.setTextSize(30);
        titleView.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        root.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(0xFF6C757D);
        subtitleView.setTextSize(15);
        subtitleView.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        output = new TextView(this);
        output.setTextColor(0xFF212529);
        output.setTextSize(14);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(0, dp(16), 0, 0);
        content.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void addNavButton(String label, View.OnClickListener listener) {
        Button button = addActionButton(label);
        button.setOnClickListener(listener);
    }

    private void addBackButton() {
        Button button = makeButton("返回", false);
        button.setOnClickListener(view -> showHome());
        content.addView(button, 0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private Button addActionButton(String label) {
        Button button = makeButton(label, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        content.addView(button, Math.max(0, content.getChildCount() - 1), params);
        return button;
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(primary ? 0xFFFFFFFF : 0xFF1A5FB4);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(primary ? 0xFF1A5FB4 : 0xFFE3E8F0);
        button.setMinHeight(dp(48));
        return button;
    }

    private CheckBox addCheckBox(String label, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setChecked(checked);
        checkBox.setTextColor(0xFF212529);
        checkBox.setTextSize(15);
        content.addView(checkBox, Math.max(0, content.getChildCount() - 1),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return checkBox;
    }

    private EditText addEditText(String hint, String value, boolean numeric) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT);
        editText.setText(value);
        editText.setTextColor(0xFF212529);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        content.addView(editText, Math.max(0, content.getChildCount() - 1), params);
        return editText;
    }

    private void addSectionLabel(String label) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(0xFF1A5FB4);
        textView.setTextSize(16);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setPadding(0, dp(12), 0, dp(4));
        content.addView(textView, Math.max(0, content.getChildCount() - 1),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void runSaepCall(String inProgress, SaepTask task) {
        if (callInProgress) {
            setOutput("已有 SAEP 请求正在执行。");
            return;
        }
        callInProgress = true;
        setOutput(inProgress + "...");
        final int generation = screenGeneration;
        final TextView targetOutput = output;
        try {
            executor.submit(() -> {
                if (generation != screenGeneration) {
                    finishSaepCall(generation, targetOutput, null);
                    return;
                }
                SaepFrameworkClient.CallResult result;
                try {
                    result = task.run();
                } catch (RuntimeException e) {
                    result = SaepFrameworkClient.CallResult.failure(
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                finishSaepCall(generation, targetOutput, result);
            });
        } catch (RejectedExecutionException e) {
            callInProgress = false;
            setOutput("无法启动 SAEP 请求。");
        }
    }

    private void finishSaepCall(final int generation, final TextView targetOutput,
            final SaepFrameworkClient.CallResult result) {
        runOnUiThread(() -> {
            callInProgress = false;
            if (result == null || isDestroyed() || generation != screenGeneration
                    || output != targetOutput) {
                return;
            }
            setOutput((result.success ? "成功\n\n" : "不可用或失败\n\n") + result.message);
        });
    }

    private void setOutput(String text) {
        output.setText(text);
    }

    private SaepPolicy.Config readPolicyConfig(EditText versionInput,
            CheckBox appGlobal, CheckBox appScreenshot, CheckBox appInput,
            CheckBox activityGlobal, CheckBox activityScreenshot, CheckBox activityInput,
            CheckBox modifyContent, CheckBox postContent, CheckBox deleteContent,
            CheckBox accountIncentive) {
        SaepPolicy.Config config = new SaepPolicy.Config();
        config.version = readPolicyVersion(versionInput);
        config.appGlobalDisable = appGlobal.isChecked();
        config.appScreenshotDisable = appScreenshot.isChecked();
        config.appInputDisable = appInput.isChecked();
        config.activityGlobalDisable = activityGlobal.isChecked();
        config.activityScreenshotDisable = activityScreenshot.isChecked();
        config.activityInputDisable = activityInput.isChecked();
        config.modifyContentDisable = modifyContent.isChecked();
        config.postContentDisable = postContent.isChecked();
        config.deleteContentDisable = deleteContent.isChecked();
        config.accountIncentiveDisable = accountIncentive.isChecked();
        return config;
    }

    private static int readPolicyVersion(EditText versionInput) {
        String value = versionInput.getText().toString().trim();
        if (value.length() == 0) {
            throw new IllegalArgumentException("policy_version 必须为正整数");
        }
        try {
            int version = Integer.parseInt(value);
            if (version <= 0) {
                throw new IllegalArgumentException("policy_version 必须为正整数");
            }
            return version;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("policy_version 必须为正整数");
        }
    }

    private static long readTimestamp(EditText input, String label) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " 必须为非负整数");
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface SaepTask {
        SaepFrameworkClient.CallResult run();
    }
}
