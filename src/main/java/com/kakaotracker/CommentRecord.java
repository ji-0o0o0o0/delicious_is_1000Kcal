package com.kakaotracker;

public class CommentRecord {
    private final String date;
    private final String name;
    private final boolean exercise;
    private final boolean diet;
    private final boolean cheat;
    private final boolean injury;

    public CommentRecord(String date, String name, boolean exercise, boolean diet, boolean cheat, boolean injury) {
        this.date = date;
        this.name = name;
        this.exercise = exercise;
        this.diet = diet;
        this.cheat = cheat;
        this.injury = injury;
    }

    public String getDate() { return date; }
    public String getName() { return name; }
    public boolean isExercise() { return exercise; }
    public boolean isDiet() { return diet; }
    public boolean isCheat() { return cheat; }
    public boolean isInjury() { return injury; }

    public String getStatus() {
        if (cheat) return "치팅";
        if (injury) return "부상";
        if (exercise && diet) return "완료";
        if (exercise) return "운동만";
        if (diet) return "식단만";
        return "미완료";
    }
}