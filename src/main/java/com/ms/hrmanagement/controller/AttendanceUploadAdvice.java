package com.ms.hrmanagement.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class AttendanceUploadAdvice {
    // Multipart limits can fail before a controller has been selected.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String photoTooLarge(RedirectAttributes flash) {
        flash.addFlashAttribute("attendanceError", "Photo exceeds the 5 MB limit. Choose a smaller JPEG or PNG photo.");
        return "redirect:/attendance/add";
    }
}
