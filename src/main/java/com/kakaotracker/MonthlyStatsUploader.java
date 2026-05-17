package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static com.kakaotracker.SheetsService.getMvpText;

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

            // 전체 기간 통계
            Map<String, int[]> totalStats = SheetsService.calculateStats(service, spreadsheetId, members, firstDay, lastDay);

            // 주차별 치팅 보너스 + 부상 주 유효일수 계산
            Map<String, Integer> cheatBonus = new LinkedHashMap<>();
            Map<String, Integer> injuryDays = new LinkedHashMap<>();
            for (String member : members) {
                cheatBonus.put(member, 0);
                injuryDays.put(member, 0);
            }


            LocalDate weekStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!weekStart.isAfter(lastDay)) {
                LocalDate weekEnd = weekStart.plusDays(6);
                LocalDate effectiveStart = weekStart.isBefore(firstDay) ? firstDay : weekStart;
                LocalDate effectiveEnd = weekEnd.isAfter(lastDay) ? lastDay : weekEnd;
                int weekDays = (int)(effectiveEnd.toEpochDay() - effectiveStart.toEpochDay()) + 1;

                Map<String, int[]> weekStats = SheetsService.calculateStats(service, spreadsheetId, members, effectiveStart, effectiveEnd);

                // 제외기간 읽기
                Map<String, List<String>> weekExclusions = SheetsService.getExclusionReasons(service, spreadsheetId, effectiveStart, effectiveEnd);

                for (String member : members) {
                    int weekCheat = Math.min(weekStats.get(member)[3], 1);
                    boolean weekExclusion = !weekExclusions.getOrDefault(member, new ArrayList<>()).isEmpty();

                    if (weekExclusion) {
                        injuryDays.put(member, injuryDays.get(member) + weekDays);
                    } else {
                        cheatBonus.put(member, cheatBonus.get(member) + weekCheat);
                    }
                }
                weekStart = weekStart.plusWeeks(1);
            }

            // 달성률 계산
            List<String[]> resultRows = new ArrayList<>();
            Map<String, List<String>> exclusions = SheetsService.getExclusionReasons(service, spreadsheetId, firstDay, lastDay);

            for (String member : members) {
                int[] s = totalStats.get(member);
                int bonus = cheatBonus.get(member);
                int effectiveTotalDays = totalDays - injuryDays.get(member);
                double rate = effectiveTotalDays == 0 ? 0 : (Math.min(s[2] + bonus, effectiveTotalDays) / (double) effectiveTotalDays) * 100;
                String cheatStatus = bonus == 0 ? "미사용" : bonus + "회";

                // 비고
                List<String> reasons = exclusions.getOrDefault(member, new ArrayList<>());
                Map<String, Integer> reasonCount = new LinkedHashMap<>();
                for (String r : reasons) {
                    reasonCount.put(r, reasonCount.getOrDefault(r, 0) + 1);
                }
                List<String> displayList = new ArrayList<>();
                for (Map.Entry<String, Integer> e : reasonCount.entrySet()) {
                    displayList.add(e.getValue() > 1 ? e.getKey() + " " + e.getValue() + "주" : e.getKey());
                }
                String reasonText = String.join(", ", displayList);

                resultRows.add(new String[]{
                        member,
                        s[0] + "/" + totalDays + "일",
                        s[1] + "/" + totalDays + "일",
                        s[2] + "/" + effectiveTotalDays + "일",
                        cheatStatus,
                        String.format("%.0f%%", rate),
                        reasonText
                });
            }

            resultRows.sort((a, b) -> Integer.parseInt(b[5].replace("%", "")) - Integer.parseInt(a[5].replace("%", "")));

            // 공동 1등 처리
            String topRate = resultRows.get(0)[5];
            String mvpText = getMvpText(resultRows, 5);

            String title = String.format("## %d.%02d.%02d ~ %d.%02d.%02d (%d일)",
                    firstDay.getYear() % 100, firstDay.getMonthValue(), firstDay.getDayOfMonth(),
                    lastDay.getYear() % 100, lastDay.getMonthValue(), lastDay.getDayOfMonth(),
                    totalDays);

            // 운동짱/식단짱 계산
            List<String> exerciseChamps = new ArrayList<>();
            List<String> dietChamps = new ArrayList<>();
            int maxExercise = 0;
            int maxDiet = 0;

            for (String member : members) {
                int[] s = totalStats.get(member);
                if (s[0] > maxExercise) {
                    maxExercise = s[0];
                    exerciseChamps.clear();
                    exerciseChamps.add(member);
                } else if (s[0] == maxExercise) {
                    exerciseChamps.add(member);
                }
                if (s[1] > maxDiet) {
                    maxDiet = s[1];
                    dietChamps.clear();
                    dietChamps.add(member);
                } else if (s[1] == maxDiet) {
                    dietChamps.add(member);
                }
            }

            String exerciseChamp = String.join(", ", exerciseChamps);
            String dietChamp = String.join(", ", dietChamps);

            List<List<Object>> insertRows = new ArrayList<>();
            insertRows.add(Arrays.asList("", "", "", "", "", "", "", "", ""));
            insertRows.add(Arrays.asList(title, "", "", "", "", "", "", "", ""));
            insertRows.add(Arrays.asList("🏆 MVP: " + mvpText + " (" + topRate + ")", "", "", "", "", "", "", "", ""));
            insertRows.add(Arrays.asList("💪 운동짱: " + exerciseChamp + " (" + maxExercise + "일) | 🥗 식단짱: " + dietChamp + " (" + maxDiet + "일)", "", "", "", "", "", "", "", ""));
            insertRows.add(Arrays.asList("이름", "운동 달성", "식단 달성", "둘다 달성", "치팅횟수", "달성률", "순위", "비고", ""));

            int rank = 1;
            for (int i = 0; i < resultRows.size(); i++) {
                if (i > 0) {
                    int prevRate = Integer.parseInt(resultRows.get(i-1)[5].replace("%", ""));
                    int currRate = Integer.parseInt(resultRows.get(i)[5].replace("%", ""));
                    if (currRate < prevRate) rank = i + 1;
                }
                String[] r = resultRows.get(i);
                List<Object> row = new ArrayList<>(Arrays.asList(r[0], r[1], r[2], r[3], r[4], r[5], rank + "위", r[6], ""));
                insertRows.add(row);
            }

            insertRows.add(Arrays.asList("", "", "", "", "", "", "", "", ""));

            SheetsService.ensureSheetTitle(service, spreadsheetId, MONTHLY_SHEET, "📊 월간 통계");
            SheetsService.deleteExistingStats(service, spreadsheetId, MONTHLY_SHEET, title);
            SheetsService.insertRowsAtTop(service, spreadsheetId, MONTHLY_SHEET, insertRows);

            logger.info("월간 통계 업로드 완료 - {}, MVP: {}", title, mvpText);

        } catch (Exception e) {
            logger.error("월간 통계 업로드 실패: {}", e.getMessage(), e);
        }
    }
}