package com.resumeai.bot.model;

import java.util.List;

public class AnalysisResult {

    private int atsScore;

    private int requiredSkillsScore;
    private int experienceScore;
    private int preferredSkillsScore;
    private int responsibilitiesScore;
    private int educationScore;
    private int technicalBackgroundScore;

    private List<String> matchingSkills;

    private List<String> missingSkills;

    private List<String> experienceGaps;

    private List<String> learningSuggestions;

    private List<String> resumeSuggestions;


    // =========================================================
    // ATS SCORE
    // =========================================================

    public int getAtsScore() {
        return atsScore;
    }

    public void setAtsScore(int atsScore) {
        this.atsScore = atsScore;
    }


    // =========================================================
    // CATEGORY SCORES
    // =========================================================

    public int getRequiredSkillsScore() {
        return requiredSkillsScore;
    }

    public void setRequiredSkillsScore(int requiredSkillsScore) {
        this.requiredSkillsScore = requiredSkillsScore;
    }

    public int getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(int experienceScore) {
        this.experienceScore = experienceScore;
    }

    public int getPreferredSkillsScore() {
        return preferredSkillsScore;
    }

    public void setPreferredSkillsScore(int preferredSkillsScore) {
        this.preferredSkillsScore = preferredSkillsScore;
    }

    public int getResponsibilitiesScore() {
        return responsibilitiesScore;
    }

    public void setResponsibilitiesScore(int responsibilitiesScore) {
        this.responsibilitiesScore = responsibilitiesScore;
    }

    public int getEducationScore() {
        return educationScore;
    }

    public void setEducationScore(int educationScore) {
        this.educationScore = educationScore;
    }

    public int getTechnicalBackgroundScore() {
        return technicalBackgroundScore;
    }

    public void setTechnicalBackgroundScore(int technicalBackgroundScore) {
        this.technicalBackgroundScore = technicalBackgroundScore;
    }


    // =========================================================
    // MATCHING SKILLS
    // =========================================================

    public List<String> getMatchingSkills() {
        return matchingSkills;
    }

    public void setMatchingSkills(List<String> matchingSkills) {
        this.matchingSkills = matchingSkills;
    }


    // =========================================================
    // MISSING SKILLS
    // =========================================================

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }


    // =========================================================
    // EXPERIENCE GAPS
    // =========================================================

    public List<String> getExperienceGaps() {
        return experienceGaps;
    }

    public void setExperienceGaps(List<String> experienceGaps) {
        this.experienceGaps = experienceGaps;
    }


    // =========================================================
    // LEARNING SUGGESTIONS
    // =========================================================

    public List<String> getLearningSuggestions() {
        return learningSuggestions;
    }

    public void setLearningSuggestions(List<String> learningSuggestions) {
        this.learningSuggestions = learningSuggestions;
    }


    // =========================================================
    // RESUME SUGGESTIONS
    // =========================================================

    public List<String> getResumeSuggestions() {
        return resumeSuggestions;
    }

    public void setResumeSuggestions(List<String> resumeSuggestions) {
        this.resumeSuggestions = resumeSuggestions;
    }
}