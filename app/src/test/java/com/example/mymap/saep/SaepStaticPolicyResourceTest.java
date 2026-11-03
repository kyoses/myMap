package com.example.mymap.saep;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 校验随包打包的静态策略能被 SAEP 规则接受。
 */
public class SaepStaticPolicyResourceTest {

    @Test
    public void packagedPolicyIsValid() throws Exception {
        String policy = readRawResource("/raw/agent_saep_policy.json");
        SaepPolicy.validatePolicy(policy);
        assertTrue(policy.contains(SaepPolicy.SCHEMA));
        assertTrue(policy.contains(SaepPolicy.PACKAGE_NAME));
    }

    private String readRawResource(String path) throws Exception {
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("未找到测试资源 " + path);
        }
        StringBuilder text = new StringBuilder();
        try (InputStream in = stream;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString().trim();
    }
}
