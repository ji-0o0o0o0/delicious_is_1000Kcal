package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void start() {
        int targetHour = ConfigLoader.getInt("scheduler.hour");
        int targetMinute = ConfigLoader.getInt("scheduler.minute");
        logger.info("스케줄러 시작 - 매일 {}:{}에 실행", targetHour, targetMinute);

        long initialDelay = calculateInitialDelay(targetHour,targetMinute);
        logger.info("첫 실행까지 {}분 대기", initialDelay / 60);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                run();
            } catch (Exception e) {
                logger.error("스케줄러 실행 실패: {}", e.getMessage(), e);
            }
        }, initialDelay, 86400, TimeUnit.SECONDS);
    }
    //test 용
    public void runNow() {
        run();
    }

    private void run() {
        LocalDate today = LocalDate.now();
        ImageParser parser = new ImageParser();
        SheetsUploader uploader = new SheetsUploader();

        // 최근 7일치 이미지 체크
        String imagePathPrefix = ConfigLoader.get("image.path.prefix");

        for (int i = 0; i < 7; i++) {
            LocalDate targetDate = today.minusDays(i);
            String dateStr = targetDate.format(FILE_FMT);
            String formattedDate = targetDate.format(DATE_FMT);
            String imagePng = imagePathPrefix + dateStr  + ".png";
            String imageJpg = imagePathPrefix + dateStr  + ".jpg";
            String imagePath = new File(imagePng).exists() ? imagePng : imageJpg;
            File imageFile = new File(imagePath);

            if (imageFile.exists()) {
                // 원본기록에 이미 있는지 확인
                if (isAlreadyUploaded(formattedDate)) {
                    logger.info("이미 업로드됨 - 건너뜀: {}", formattedDate);
                    continue;
                }
                logger.info("이미지 파일 발견 - {}", imagePath);
                var records = parser.parse(imagePath, formattedDate);
                if (!records.isEmpty()) {
                    uploader.upload(records);
                    logger.info("업로드 완료 - {}", formattedDate);
                }
            } else {
                logger.info("이미지 파일 없음 - 건너뜀: {}", imagePath);
            }
        }

        // 2. 월요일이면 지난주 통계 업로드
        if (today.getDayOfWeek().getValue() == 1) {
            logger.info("월요일 - 주간 통계 업로드 시작");
            new WeeklyStatsUploader().uploadWeeklyStats();
        }

        // 3. 1일이면 지난달 통계 업로드
        if (today.getDayOfMonth() == 1) {
            logger.info("1일 - 월간 통계 업로드 시작");
            new MonthlyStatsUploader().uploadMonthlyStats();
        }
    }

    private long calculateInitialDelay(int targetHour, int targetMinute) {
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(targetHour, targetMinute);
        long secondsUntilTarget = now.until(target, ChronoUnit.SECONDS);
        if (secondsUntilTarget < 0) secondsUntilTarget += 86400;
        return secondsUntilTarget;
    }

    public void stop() {
        scheduler.shutdown();
        logger.info("스케줄러 종료");
    }
    private boolean isAlreadyUploaded(String date) {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, "원본기록!A:A")
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null) return false;

            return values.stream()
                    .anyMatch(row -> !row.isEmpty() && row.get(0).toString().equals(date));
        } catch (Exception e) {
            logger.error("업로드 여부 확인 실패: {}", e.getMessage(), e);
            return false;
        }
    }
}