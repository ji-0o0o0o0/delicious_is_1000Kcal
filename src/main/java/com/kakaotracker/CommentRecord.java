package com.kakaotracker;

public class CommentRecord {
    private String date;
    private String name;
    private boolean exercise;
    private boolean diet;

    public CommentRecord(String date, String name, boolean exercise, boolean diet) {
        this.date = date;
        this.name = name;
        this.exercise = exercise;
        this.diet = diet;
    }

    public String getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public boolean isExercise() {
        return exercise;
    }
    public boolean isDiet() {return diet;}

    public String getStatus(){
        if(exercise && diet)return "완료";
        if(exercise) return "운동만";
        if(diet) return "식단만";
        return "미완료";
    }
}
