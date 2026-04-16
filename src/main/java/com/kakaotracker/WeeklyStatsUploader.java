package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklyStatsUploader {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyStatsUploader.class);
    private static final String WEEKLY_SHEET = "주간통계";

    public void uploadWeeklyStats() {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<String> members = SheetsService.loadMembers();

            LocalDate lastMonday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
            LocalDate lastSunday = lastMonday.plusDays(6);

            Map<String, int[]> stats = SheetsService.calculateStats(service, spreadsheetId, members, lastMonday, lastSunday);

            int weekNum = lastMonday.get(WeekFields.of(Locale.KOREA).weekOfMonth());
            String title = String.format("## %d년 %d월 %d째주 (%d.%02d~%d.%02d)",
                    lastMonday.getYear() % 100, lastMonday.getMonthValue(), weekNum,
                    lastMonday.getMonthValue(), lastMonday.getDayOfMonth(),
                    lastSunday.getMonthValue(), lastSunday.getDayOfMonth());

            List<List<Object>> insertRows = SheetsService.buildStatsRows(members, stats, 7, title, "🏆 이번주 MVP: ");

            SheetsService.ensureSheetTitle(service, spreadsheetId, WEEKLY_SHEET, "📊 주간 통계");
            SheetsService.deleteExistingStats(service, spreadsheetId, WEEKLY_SHEET, title);
            SheetsService.insertRowsAtTop(service, spreadsheetId, WEEKLY_SHEET, insertRows);

            logger.info("주간 통계 업로드 완료 - {}", title);

        } catch (Exception e) {
            logger.error("주간 통계 업로드 실패: {}", e.getMessage(), e);
        }
    }
}