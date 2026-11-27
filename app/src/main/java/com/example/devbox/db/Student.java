package com.example.devbox.db;

public class Student {
    private String id;
    private String email;
    private String fullName;
    private long birthDate;
    private String school;

    public Student() {

    }

    public Student(String id, String email, String fullName, long birthDate, String school) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.school = school;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public long getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(long birthDate) {
        this.birthDate = birthDate;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }


}
