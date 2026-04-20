package com.kakaotracker;

public class CommentRecord {
    private String date;
    private String name;
    private boolean exercise;
    private boolean diet;
    private boolean cheat;

    public CommentRecord(String date, String name, boolean exercise, boolean diet, boolean cheat) {
        this.date = date;
        this.name = name;
        this.exercise = exercise;
        this.diet = diet;
        this.cheat = cheat;
    }

    public String getDate() { return date; }
    public String getName() { return name; }
    public boolean isExercise() { return exercise; }
    public boolean isDiet() { return diet; }
    public boolean isCheat() { return cheat; }

    public String getStatus() {
        if (cheat) return "치팅";
        if (exercise && diet) return "완료";
        if (exercise) return "운동만";
        if (diet) return "식단만";
        return "미완료";
    }
}