package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    private LogCallback logCallback;

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    private void log(String msg) {
        logger.info(msg);
        if (logCallback != null) logCallback.onLog(msg);
    }
    private void log(String format, Object... args) {
        String msg = String.format(format.replace("{}", "%s"), args);
        logger.info(msg);
        if (logCallback != null) logCallback.onLog(msg);
    }

    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void start() {
        int intervalHours = ConfigLoader.getInt("scheduler.interval.hours");
        logger.info("스케줄러 시작 - {}시간마다 정시에 실행", intervalHours);

        // 다음 정시까지 대기 시간 계산
        LocalTime now = LocalTime.now();
        long minutesUntilNextHour = 60 - now.getMinute();
        long secondsUntilNextHour = minutesUntilNextHour * 60 - now.getSecond();

        logger.info("다음 정시까지 {}분 대기", minutesUntilNextHour);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                run();
            } catch (Exception e) {
                logger.error("스케줄러 실행 실패: {}", e.getMessage(), e);
            }
        }, secondsUntilNextHour, intervalHours * 3600L, TimeUnit.SECONDS);
    }
    //test 용
    public void runNow() {
        run();
    }

    private void run() {
        LocalDate today = LocalDate.now();
        ImageParser parser = new ImageParser();
        SheetsUploader uploader = new SheetsUploader();

        log("===== {} {} 스케줄러 실행 =====", today, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        // 최근 7일치 이미지 체크
        String imagePathPrefix = ConfigLoader.get("image.path.prefix");

        // 처리할 파일 있는지 먼저 체크
        File imageDir = new File(imagePathPrefix);
        File[] files = imageDir.listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpg"));

        // 수정여부 Y 체크 (이미지 유무 상관없이)
        processModifications();

        if (files == null || files.length == 0) {
            log("처리할 이미지 파일 없음");
            return;
        }

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
                    uploader.upload(records,imagePath);
                    logger.info("업로드 완료 - {}", formattedDate);
                }
            } else {
                logger.info("이미지 파일 없음 - 건너뜀: {}", imagePath);
            }
        }

        // 원본기록 업로드 후 이번주 현황 업데이트
        new WeeklyCurrentUploader().uploadCurrentWeek();

        // 2. 월요일이면 지난주 통계 업로드
        if (today.getDayOfWeek().getValue() == 1) {
            logger.info("월요일 - 주간 통계 업로드 시작");
            new WeeklyStatsUploader().uploadWeeklyStats();
        }

        // 3. 프로젝트 기간 1일지나면 지난달 통계 업로드
        LocalDate monthlyEndDate = LocalDate.parse(ConfigLoader.get("monthly.end.date"));
        if (today.equals(monthlyEndDate.plusDays(1))) {
            logger.info("{}일 - 월간 통계 업로드 시작",monthlyEndDate);
            new MonthlyStatsUploader().uploadMonthlyStats();
        }
    }

    public void stop() {
        scheduler.shutdown();
        logger.info("스케줄러 종료");
    }
    private boolean isAlreadyUploaded(String date) {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<String> members = SheetsService.loadMembers();

            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, "원본기록!A:B")
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null) return false;

            // 해당 날짜에 있는 멤버 이름 수집
            Set<String> uploadedMembers = values.stream()
                    .filter(row -> row.size() >= 2 && row.get(0).toString().equals(date))
                    .map(row -> row.get(1).toString())
                    .collect(java.util.stream.Collectors.toSet());

            // 모든 멤버가 다 있으면 완료
            return uploadedMembers.containsAll(members);

        } catch (Exception e) {
            logger.error("업로드 여부 확인 실패: {}", e.getMessage(), e);
            return false;
        }
    }

    private void processModifications() {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<ModifiedRow> modifiedRows = SheetsService.getModifiedRows(service, spreadsheetId);

            if (modifiedRows.isEmpty()) {
                log("수정된 행 없음 - 건너뜀");
                return;
            }

            LocalDate monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate lastMonday = monday.minusWeeks(1);

            boolean needCurrentWeek = false;
            boolean needLastWeek = false;

            for (ModifiedRow row : modifiedRows) {
                LocalDate date = row.getDate();

                if (!date.isBefore(monday)) {
                    // 이번주
                    needCurrentWeek = true;
                    SheetsService.updateModificationStatus(service, spreadsheetId, row.getRowNum(), "D");
                } else if (!date.isBefore(lastMonday)) {
                    // 지난주
                    needLastWeek = true;
                    SheetsService.updateModificationStatus(service, spreadsheetId, row.getRowNum(), "D");
                } else {
                    // 그 이전 - 무시
                    SheetsService.updateModificationStatus(service, spreadsheetId, row.getRowNum(), "P");
                    log("지난주 이전 수정 - 무시: {}", date);
                }
            }

            if (needCurrentWeek) {
                log("이번주 수정 감지 - 이번주 현황 업데이트");
                new WeeklyCurrentUploader().uploadCurrentWeek();
            }

            if (needLastWeek) {
                log("지난주 수정 감지 - 주간 통계 업데이트");
                new WeeklyStatsUploader().uploadWeeklyStats();
            }

        } catch (Exception e) {
            logger.error("수정여부 처리 실패: {}", e.getMessage(), e);
        }
    }
}