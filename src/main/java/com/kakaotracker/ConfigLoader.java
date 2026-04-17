package com.kakaotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Properties props = new Properties();

    static {
        try {
            // 외부 config.properties 먼저 찾기
            String jarDir = new File(ConfigLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            String externalPath = jarDir + "/config.properties";
            logger.info("외부 config 탐색 경로: {}", externalPath);
            java.io.File externalFile = new java.io.File(externalPath);

            if (externalFile.exists()) {
                try (InputStream is = new FileInputStream(externalFile)) {
                    props.load(is);
                    logger.info("외부 config.properties 로드: {}", externalPath);
                }
            } else {
                // 없으면 jar 내부 리소스에서 읽기
                try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
                    if (is == null) throw new IllegalStateException("config.properties 파일을 찾을 수 없습니다.");
                    props.load(is);
                    logger.info("내부 config.properties 로드");
                }
            }
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