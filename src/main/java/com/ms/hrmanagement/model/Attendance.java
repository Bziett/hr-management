package com.ms.hrmanagement.model;

public class Attendance {

    private String id;
    private String employeeId;
    private String employeeName;
    private String department;
    private String date;
    private String checkIn;
    private String checkOut;
    private String status;
    private String submittedByRole;
    private String reviewStatus;
    private String note;
    private boolean photoSubmitted;
    private String photoSubmittedAt;
    private String summarySubmittedBy;
    private String summarySubmittedAt;
    private boolean manualEntry;
    private String correctedBy;
    private String correctedAt;
    private String correctionReason;

    public boolean isPhotoSubmitted() { return photoSubmitted; }
    public void setPhotoSubmitted(boolean value) { photoSubmitted = value; }
    public String getPhotoSubmittedAt() { return photoSubmittedAt; }
    public void setPhotoSubmittedAt(String value) { photoSubmittedAt = value; }
    public String getSummarySubmittedBy() { return summarySubmittedBy; }
    public void setSummarySubmittedBy(String value) { summarySubmittedBy = value; }
    public String getSummarySubmittedAt() { return summarySubmittedAt; }
    public void setSummarySubmittedAt(String value) { summarySubmittedAt = value; }
    public boolean isManualEntry() { return manualEntry; }
    public void setManualEntry(boolean value) { manualEntry = value; }
    public String getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(String value) { correctedBy = value; }
    public String getCorrectedAt() { return correctedAt; }
    public void setCorrectedAt(String value) { correctedAt = value; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String value) { correctionReason = value; }

    // Empty constructor required by Firebase Firestore
    public Attendance() {
    }

    public Attendance(String id, String employeeId, String employeeName,
                      String department, String date, String checkIn, String checkOut,
                      String status, String submittedByRole, String reviewStatus, String note) {

        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.date = date;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
        this.submittedByRole = submittedByRole;
        this.reviewStatus = reviewStatus;
        this.note = note;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(String checkIn) {
        this.checkIn = checkIn;
    }

    public String getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(String checkOut) {
        this.checkOut = checkOut;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmittedByRole() {
        return submittedByRole;
    }

    public void setSubmittedByRole(String submittedByRole) {
        this.submittedByRole = submittedByRole;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
