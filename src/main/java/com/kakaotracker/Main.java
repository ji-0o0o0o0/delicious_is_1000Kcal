package com.kakaotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ImageParser parser = new ImageParser();
        SheetsUploader uploader = new SheetsUploader();

        // config.properties 에서 이미지 경로 prefix 읽기
        String imagePathPrefix = "";
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new IllegalStateException("config.properties 파일을 찾을 수 없습니다.");
            }
            Properties props = new Properties();
            props.load(is);
            imagePathPrefix = props.getProperty("image.path.prefix", "");
        } catch (Exception e) {
            logger.error("config.properties 읽기 실패: {}", e.getMessage(), e);
        }

        System.out.print("날짜 입력 (예: 260414): ");
        String date = scanner.nextLine().trim();
        String year = "20" + date.substring(0, 2);
        String month = date.substring(2, 4);
        String day = date.substring(4, 6);
        String formattedDate = year + "-" + month + "-" + day;

        String imagePath = imagePathPrefix + date + ".png";
        logger.info("파싱 시작 - 날짜: {}, 이미지: {}", date, imagePath);

        List<CommentRecord> records = parser.parse(imagePath, formattedDate);

        if (records.isEmpty()) {
            logger.warn("파싱된 기록이 없습니다. OCR 결과를 로그에서 확인해주세요.");
            System.out.println("파싱된 기록이 없어요. 로그 파일 확인해봐!");
            scanner.close();
            return;
        }

        logger.info("파싱 완료 - 총 {}명 기록", records.size());
        uploader.upload(records);

        System.out.println("완료! 구글 시트 확인해봐 😊");
        scanner.close();
    }
}