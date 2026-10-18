package com.ms.hrmanagement.model;

public class Intern {

    private String id;
    private String internCode;
    private String fullName;
    private String email;
    private String phone;
    private String university;
    private String major;
    private String department;
    private String mentor;
    private String startDate;
    private String endDate;
    private String status;

    // Empty constructor required by Firebase Firestore
    public Intern() {
    }

    public Intern(String id, String internCode, String fullName,
                   String email, String phone, String university,
                   String major, String department, String mentor,
                   String startDate, String endDate, String status) {

        this.id = id;
        this.internCode = internCode;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.university = university;
        this.major = major;
        this.department = department;
        this.mentor = mentor;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInternCode() {
        return internCode;
    }

    public void setInternCode(String internCode) {
        this.internCode = internCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getMentor() {
        return mentor;
    }

    public void setMentor(String mentor) {
        this.mentor = mentor;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}