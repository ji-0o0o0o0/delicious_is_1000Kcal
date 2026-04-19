package com.kakaotracker;

import java.time.LocalDate;

public class ModifiedRow {
    private final int rowNum;
    private final LocalDate date;

    public ModifiedRow(int rowNum, LocalDate date) {
        this.rowNum = rowNum;
        this.date = date;
    }

    public int getRowNum() { return rowNum; }
    public LocalDate getDate() { return date; }
}