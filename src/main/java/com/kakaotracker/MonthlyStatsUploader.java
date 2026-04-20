package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public class MonthlyStatsUploader {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyStatsUploader.class);
    private static final String MONTHLY_SHEET = "월간통계";

    public void uploadMonthlyStats() {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<String> members = SheetsService.loadMembers();

            LocalDate firstDay = LocalDate.parse(ConfigLoader.get("monthly.start.date"));
            LocalDate lastDay = LocalDate.parse(ConfigLoader.get("monthly.end.date"));
            int totalDays = (int) (lastDay.toEpochDay() - firstDay.toEpochDay()) + 1;

            Map<String, int[]> stats = SheetsService.calculateStats(service, spreadsheetId, members, firstDay, lastDay);

            String title = String.format("## %d.%02d.%02d ~ %d.%02d.%02d (%d일)",
                    firstDay.getYear() % 100, firstDay.getMonthValue(), firstDay.getDayOfMonth(),
                    lastDay.getYear() % 100, lastDay.getMonthValue(), lastDay.getDayOfMonth(),
                    totalDays);

            List<List<Object>> insertRows = SheetsService.buildStatsRows(members, stats, totalDays, title, "🏆 이달의 MVP: ");

            SheetsService.ensureSheetTitle(service, spreadsheetId, MONTHLY_SHEET, "📊 월간 통계");
            SheetsService.deleteExistingStats(service, spreadsheetId, MONTHLY_SHEET, title);
            SheetsService.insertRowsAtTop(service, spreadsheetId, MONTHLY_SHEET, insertRows);

            logger.info("월간 통계 업로드 완료 - {}", title);

        } catch (Exception e) {
            logger.error("월간 통계 업로드 실패: {}", e.getMessage(), e);
        }
    }
}