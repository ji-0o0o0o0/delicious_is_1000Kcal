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

            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            LocalDate firstDay = lastMonth.atDay(1);
            LocalDate lastDay = lastMonth.atEndOfMonth();
            int totalDays = lastMonth.lengthOfMonth();

            Map<String, int[]> stats = SheetsService.calculateStats(service, spreadsheetId, members, firstDay, lastDay);

            String title = String.format("## %d년 %d월", lastMonth.getYear() % 100, lastMonth.getMonthValue());

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