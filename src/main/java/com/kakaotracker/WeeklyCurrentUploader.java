package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklyCurrentUploader {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyCurrentUploader.class);
    private static final String CURRENT_SHEET = "이번주현황";

    public void uploadCurrentWeek() {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<String> members = SheetsService.loadMembers();

            // 이번주 월~오늘까지
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate lastDate = SheetsService.getLastRecordedDate(service, spreadsheetId, monday);

            int daysPassed = (int) (lastDate.toEpochDay() - monday.toEpochDay()) + 1;

            Map<String, int[]> stats = SheetsService.calculateStats(service, spreadsheetId, members, monday, lastDate);

            int weekNum = monday.get(WeekFields.of(Locale.KOREA).weekOfMonth());
            String title = String.format("## %d년 %d월 %d째주 현황 (%d.%02d~%d.%02d)",
                    monday.getYear() % 100, monday.getMonthValue(), weekNum,
                    monday.getMonthValue(), monday.getDayOfMonth(),
                    lastDate.getMonthValue(), lastDate.getDayOfMonth());

            List<List<Object>> rows = SheetsService.buildStatsRows(members, stats, daysPassed, title, "🏆 현재 1위: ");

            // 시트 전체 초기화 후 덮어쓰기
            SheetsService.ensureSheetTitle(service, spreadsheetId, CURRENT_SHEET, "📊 이번주 현황");

            // 기존 내용 지우고 새로 쓰기
            service.spreadsheets().values()
                    .clear(spreadsheetId, CURRENT_SHEET + "!A2:Z1000", new com.google.api.services.sheets.v4.model.ClearValuesRequest())
                    .execute();

            service.spreadsheets().values()
                    .update(spreadsheetId, CURRENT_SHEET + "!A2", new ValueRange().setValues(rows))
                    .setValueInputOption("RAW")
                    .execute();

            logger.info("이번주 현황 업로드 완료 - {}", title);

        } catch (Exception e) {
            logger.error("이번주 현황 업로드 실패: {}", e.getMessage(), e);
        }
    }
}