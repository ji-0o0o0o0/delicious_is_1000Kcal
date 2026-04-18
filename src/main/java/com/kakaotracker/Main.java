package com.kakaotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // 테스트 모드: java -jar kakao-tracker.jar test
        if (args.length > 0 && args[0].equals("test")) {
            logger.info("테스트 모드로 실행");
            new Scheduler().runNow();
            return;
        }
        // 주간 통계 테스트: java -jar kakao-tracker.jar weekly
        if (args.length > 0 && args[0].equals("weekly")) {
            logger.info("주간 통계 테스트 모드");
            new WeeklyStatsUploader().uploadWeeklyStats();
            return;
        }

        // 월간 통계 테스트: java -jar kakao-tracker.jar monthly
        if (args.length > 0 && args[0].equals("monthly")) {
            logger.info("월간 통계 테스트 모드");
            new MonthlyStatsUploader().uploadMonthlyStats();
            return;
        }
        // 스케줄러 모드: java -jar kakao-tracker.jar scheduler
        if (args.length > 0 && args[0].equals("scheduler")) {
            logger.info("스케줄러 모드로 실행");
            Scheduler scheduler = new Scheduler();
            scheduler.start();

            // 프로그램 종료 시 스케줄러 정리
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("프로그램 종료 - 스케줄러 정리");
                scheduler.stop();
            }));
            return;
        }

        // 수동 모드
        Scanner scanner = new Scanner(System.in);
        ImageParser parser = new ImageParser();
        SheetsUploader uploader = new SheetsUploader();

        String imagePathPrefix = ConfigLoader.get("image.path.prefix");

        System.out.print("날짜 입력 (예: 260418 또는 0418): ");
        String date = scanner.nextLine().trim();

        // 4자리면 현재 연도 앞에 추가
        if (date.length() == 4) {
            String yearPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yy"));
            date = yearPrefix + date;
        }

        String year = "20" + date.substring(0, 2);
        String month = date.substring(2, 4);
        String day = date.substring(4, 6);
        String formattedDate = year + "-" + month + "-" + day;

        String imagePng = imagePathPrefix + date + ".png";
        String imageJpg = imagePathPrefix + date + ".jpg";
        String imagePath = new File(imagePng).exists() ? imagePng : imageJpg;
        logger.info("파싱 시작 - 날짜: {}, 이미지: {}", formattedDate, imagePath);

        List<CommentRecord> records = parser.parse(imagePath, formattedDate);

        if (records.isEmpty()) {
            logger.warn("파싱된 기록이 없습니다. OCR 결과를 로그에서 확인해주세요.");
            System.out.println("파싱된 기록이 없어요. 로그 파일 확인해봐!");
            scanner.close();
            return;
        }

        logger.info("파싱 완료 - 총 {}명 기록", records.size());
        uploader.upload(records,imagePath);

        System.out.println("완료! 구글 시트 확인해봐 😊");
        scanner.close();
    }
}