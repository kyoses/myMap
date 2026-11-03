package com.example.mymap.saep;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * SAEP 策略（AGRP-Policy/1.0）的构造与校验。
 *
 * <p>协议只约束四类 Agent intent：modify_content、post_content、delete_content、
 * account_incentive；动作规则分为 global_disable / screenshot_disable / input_disable。
 */
public final class SaepPolicy {

    public static final String SCHEMA = "AGRP-Policy/1.0";
    public static final String PACKAGE_NAME = "com.example.mymap";
    public static final String MAIN_ACTIVITY = PACKAGE_NAME + ".MainActivity";
    public static final String MAP_ACTIVITY = PACKAGE_NAME + ".map.MapActivity";
    public static final int STATIC_POLICY_VERSION = 1;
    public static final int MAX_POLICY_BYTES = 10 * 1024;

    private SaepPolicy() {
    }

    public static String createDynamicPolicy(Config config) {
        return buildPolicy(config, String.valueOf(System.currentTimeMillis()));
    }

    public static String buildPolicy(Config config, String updatedAt) {
        if (config == null) {
            throw new IllegalArgumentException("Policy configuration is required");
        }
        if (config.version <= 0) {
            throw new IllegalArgumentException("policy_version must be a positive integer");
        }
        if (updatedAt == null || updatedAt.trim().isEmpty()) {
            throw new IllegalArgumentException("updated_at is required");
        }
        try {
            if (Long.parseLong(updatedAt) < 0L) {
                throw new IllegalArgumentException("updated_at must be a millisecond timestamp");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("updated_at must be a millisecond timestamp", e);
        }

        try {
            JSONObject policy = new JSONObject();
            policy.put("schema", SCHEMA);
            policy.put("policy_version", config.version);
            policy.put("package", PACKAGE_NAME);
            policy.put("updated_at", updatedAt);

            JSONObject defaultPolicy = new JSONObject();
            defaultPolicy.put("app", buildActionRules(
                    config.appGlobalDisable,
                    config.appScreenshotDisable,
                    config.appInputDisable));
            policy.put("default_policy", defaultPolicy);

            JSONObject scope = new JSONObject();
            scope.put("app", buildActionRules(
                    config.appGlobalDisable,
                    config.appScreenshotDisable,
                    config.appInputDisable));

            JSONObject activities = new JSONObject();
            activities.put(MAIN_ACTIVITY, buildActivity(
                    "MyMap main screen",
                    config.activityGlobalDisable,
                    config.activityScreenshotDisable,
                    config.activityInputDisable));
            activities.put(MAP_ACTIVITY, buildActivity(
                    "MyMap navigation screen",
                    config.activityGlobalDisable,
                    config.activityScreenshotDisable,
                    config.activityInputDisable));
            scope.put("activities", activities);

            JSONObject agentIntents = new JSONObject();
            agentIntents.put("modify_content", config.modifyContentDisable);
            agentIntents.put("post_content", config.postContentDisable);
            agentIntents.put("delete_content", config.deleteContentDisable);
            agentIntents.put("account_incentive", config.accountIncentiveDisable);
            scope.put("agent_intents", agentIntents);
            policy.put("scope", scope);

            String json = policy.toString();
            validatePolicy(json);
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to create policy JSON", e);
        }
    }

    public static void validatePolicy(String policyJson) {
        if (policyJson == null) {
            throw new IllegalArgumentException("Policy JSON is required");
        }
        if (policyJson.getBytes(StandardCharsets.UTF_8).length > MAX_POLICY_BYTES) {
            throw new IllegalArgumentException("Policy JSON exceeds the 10 KiB limit");
        }

        try {
            JSONObject policy = new JSONObject(policyJson);
            requireEquals(policy, "schema", SCHEMA);
            requireEquals(policy, "package", PACKAGE_NAME);
            if (policy.getInt("policy_version") <= 0) {
                throw new IllegalArgumentException("policy_version must be positive");
            }
            String updatedAt = policy.getString("updated_at");
            try {
                if (Long.parseLong(updatedAt) < 0L) {
                    throw new IllegalArgumentException(
                            "updated_at must be a millisecond timestamp");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "updated_at must be a millisecond timestamp", e);
            }

            JSONObject defaultPolicy = policy.getJSONObject("default_policy");
            validateActionRules(defaultPolicy.getJSONObject("app"), "default_policy.app");

            JSONObject scope = policy.getJSONObject("scope");
            validateActionRules(scope.getJSONObject("app"), "scope.app");
            JSONObject activities = scope.getJSONObject("activities");
            validateActivity(activities.getJSONObject(MAIN_ACTIVITY));
            validateActivity(activities.getJSONObject(MAP_ACTIVITY));

            JSONObject intents = scope.getJSONObject("agent_intents");
            requireBoolean(intents, "modify_content", "scope.agent_intents");
            requireBoolean(intents, "post_content", "scope.agent_intents");
            requireBoolean(intents, "delete_content", "scope.agent_intents");
            requireBoolean(intents, "account_incentive", "scope.agent_intents");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Policy JSON is missing a required field", e);
        }
    }

    public static String summarizePolicy(String policyJson) {
        if (policyJson == null || policyJson.isEmpty()) {
            return "No policy available.";
        }
        String formatted = policyJson;
        try {
            formatted = new JSONObject(policyJson).toString(2);
        } catch (JSONException ignored) {
            // 校验流程会单独报告非法的 JSON，这里保留原文便于排查。
        }
        return String.format(Locale.US, "Package: %s%nSize: %d / %d bytes%n%n%s",
                PACKAGE_NAME,
                policyJson.getBytes(StandardCharsets.UTF_8).length,
                MAX_POLICY_BYTES,
                formatted);
    }

    private static JSONObject buildActivity(String name, boolean globalDisable,
            boolean screenshotDisable, boolean inputDisable) throws JSONException {
        JSONObject activity = new JSONObject();
        activity.put("name", name);
        activity.put("page_scope", buildActionRules(
                globalDisable, screenshotDisable, inputDisable));
        return activity;
    }

    private static JSONObject buildActionRules(boolean globalDisable,
            boolean screenshotDisable, boolean inputDisable) throws JSONException {
        JSONObject rules = new JSONObject();
        rules.put("global_disable", globalDisable);
        rules.put("screenshot_disable", screenshotDisable);
        rules.put("input_disable", inputDisable);
        return rules;
    }

    private static void validateActivity(JSONObject activity) throws JSONException {
        if (activity.getString("name").trim().isEmpty()) {
            throw new IllegalArgumentException("Activity name must not be empty");
        }
        validateActionRules(activity.getJSONObject("page_scope"), "scope.activities[].page_scope");
    }

    private static void validateActionRules(JSONObject rules, String path) throws JSONException {
        requireBoolean(rules, "global_disable", path);
        requireBoolean(rules, "screenshot_disable", path);
        requireBoolean(rules, "input_disable", path);
    }

    private static void requireBoolean(JSONObject object, String key, String path)
            throws JSONException {
        if (!(object.get(key) instanceof Boolean)) {
            throw new IllegalArgumentException(path + "." + key + " must be boolean");
        }
    }

    private static void requireEquals(JSONObject object, String key, String expected)
            throws JSONException {
        if (!expected.equals(object.getString(key))) {
            throw new IllegalArgumentException(key + " must be " + expected);
        }
    }

    public static final class Config {
        public int version = STATIC_POLICY_VERSION + 1;
        public boolean appGlobalDisable;
        public boolean appScreenshotDisable;
        public boolean appInputDisable;
        public boolean activityGlobalDisable;
        public boolean activityScreenshotDisable;
        public boolean activityInputDisable;
        public boolean modifyContentDisable;
        public boolean postContentDisable;
        public boolean deleteContentDisable;
        public boolean accountIncentiveDisable;
    }
}
