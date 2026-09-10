package com.resumeai.bot.model;

import java.util.ArrayList;
import java.util.List;

public class UserSession {

    public enum State {
        WAITING_FOR_RESUMES,
        WAITING_FOR_JOB_DESCRIPTION,
        READY_FOR_CHAT
    }

    /*
     * Stores all uploaded resumes.
     */
    private List<String> resumeTexts;

    /*
     * Stores the original file names of resumes.
     */
    private List<String> resumeFileNames;

    /*
     * Stores the job description text.
     */
    private String jobDescriptionText;

    public UserSession() {

        this.resumeTexts =
                new ArrayList<>();

        this.resumeFileNames =
                new ArrayList<>();

        this.state =
                State.WAITING_FOR_RESUMES;
    }

    private State state;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /*
     * =========================================================
     * RESUME TEXTS
     * =========================================================
     */

    public List<String> getResumeTexts() {
        return resumeTexts;
    }

    public void setResumeTexts(
            List<String> resumeTexts) {

        this.resumeTexts = resumeTexts;
    }

    public void addResumeText(
            String resumeText) {

        this.resumeTexts.add(resumeText);
    }

    /*
     * =========================================================
     * RESUME FILE NAMES
     * =========================================================
     */

    public List<String> getResumeFileNames() {
        return resumeFileNames;
    }

    public void setResumeFileNames(
            List<String> resumeFileNames) {

        this.resumeFileNames =
                resumeFileNames;
    }

    public void addResumeFileName(
            String fileName) {

        this.resumeFileNames.add(fileName);
    }

    /*
     * =========================================================
     * JOB DESCRIPTION
     * =========================================================
     */

    public String getJobDescriptionText() {
        return jobDescriptionText;
    }

    public void setJobDescriptionText(
            String jobDescriptionText) {

        this.jobDescriptionText =
                jobDescriptionText;
    }

    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset() {

        this.state =
                State.WAITING_FOR_RESUMES;

        this.resumeTexts.clear();

        this.resumeFileNames.clear();

        this.jobDescriptionText =
                null;
    }

    /*
     * =========================================================
     * RESUME COUNT
     * =========================================================
     */

    public int getResumeCount() {

        return resumeTexts.size();
    }
}