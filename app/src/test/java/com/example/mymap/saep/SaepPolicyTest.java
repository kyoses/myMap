package com.example.mymap.saep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * SAEP 策略构造与校验的单元测试。
 */
public class SaepPolicyTest {

    @Test
    public void defaultConfigProducesValidPolicy() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String policy = SaepPolicy.createDynamicPolicy(config);
        SaepPolicy.validatePolicy(policy);
        assertTrue(policy.contains(SaepPolicy.SCHEMA));
        assertTrue(policy.contains(SaepPolicy.PACKAGE_NAME));
    }

    @Test
    public void policyVersionMustBePositive() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        config.version = 0;
        try {
            SaepPolicy.createDynamicPolicy(config);
            fail("policy_version 为 0 时应当抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("policy_version"));
        }
    }

    @Test
    public void wrongPackageIsRejected() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String policy = SaepPolicy.createDynamicPolicy(config)
                .replace(SaepPolicy.PACKAGE_NAME, "com.example.other");
        try {
            SaepPolicy.validatePolicy(policy);
            fail("包名不匹配时应当抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("package"));
        }
    }

    @Test
    public void wrongSchemaIsRejected() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String policy = SaepPolicy.createDynamicPolicy(config)
                .replace(SaepPolicy.SCHEMA, "AGRP-Policy/9.9");
        try {
            SaepPolicy.validatePolicy(policy);
            fail("schema 不匹配时应当抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("schema"));
        }
    }

    @Test
    public void missingAgentIntentIsRejected() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String policy = SaepPolicy.createDynamicPolicy(config)
                .replace("\"account_incentive\":false", "\"other_intent\":false");
        try {
            SaepPolicy.validatePolicy(policy);
            fail("缺少 agent intent 时应当抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("required field"));
        }
    }

    @Test
    public void oversizedPolicyIsRejected() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        config.activityScreenshotDisable = true;
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < SaepPolicy.MAX_POLICY_BYTES; i++) {
            padding.append('x');
        }
        String policy = SaepPolicy.createDynamicPolicy(config)
                .replace("\"name\":\"MyMap main screen\"",
                        "\"name\":\"MyMap main screen" + padding + "\"");
        try {
            SaepPolicy.validatePolicy(policy);
            fail("超过 10 KiB 的策略应当被拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("10 KiB"));
        }
    }

    @Test
    public void updatedAtMustBeMillisecondTimestamp() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        try {
            SaepPolicy.buildPolicy(config, "not-a-number");
            fail("updated_at 非法时应当抛异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("updated_at"));
        }
    }

    @Test
    public void summaryReportsPackageAndSize() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String summary = SaepPolicy.summarizePolicy(SaepPolicy.createDynamicPolicy(config));
        assertTrue(summary.contains(SaepPolicy.PACKAGE_NAME));
        assertTrue(summary.contains("bytes"));
    }

    @Test
    public void policyStaysWithinSizeLimit() {
        SaepPolicy.Config config = new SaepPolicy.Config();
        String policy = SaepPolicy.createDynamicPolicy(config);
        assertTrue(policy.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= SaepPolicy.MAX_POLICY_BYTES);
        assertEquals(SaepPolicy.SCHEMA, SaepPolicy.SCHEMA);
    }
}
