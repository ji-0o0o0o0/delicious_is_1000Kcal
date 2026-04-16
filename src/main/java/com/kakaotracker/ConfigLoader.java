package com.kakaotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new IllegalStateException("config.properties 파일을 찾을 수 없습니다.");
            props.load(is);
        } catch (Exception e) {
            logger.error("config.properties 로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("config.properties에 필수 값이 없습니다: " + key);
        }
        return value.trim();
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("config.properties 값이 숫자가 아닙니다: " + key);
        }
    }
}