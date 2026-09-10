package com.resumeai.bot.model;

import java.util.List;

public class AnalysisResult {

    private int atsScore;

    private List<String> matchingSkills;

    private List<String> missingSkills;

    private List<String> experienceGaps;

    private List<String> learningSuggestions;

    private List<String> resumeSuggestions;

    public int getAtsScore() {
        return atsScore;
    }

    public void setAtsScore(int atsScore) {
        this.atsScore = atsScore;
    }

    public List<String> getMatchingSkills() {
        return matchingSkills;
    }

    public void setMatchingSkills(List<String> matchingSkills) {
        this.matchingSkills = matchingSkills;
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }

    public List<String> getExperienceGaps() {
        return experienceGaps;
    }

    public void setExperienceGaps(List<String> experienceGaps) {
        this.experienceGaps = experienceGaps;
    }

    public List<String> getLearningSuggestions() {
        return learningSuggestions;
    }

    public void setLearningSuggestions(List<String> learningSuggestions) {
        this.learningSuggestions = learningSuggestions;
    }

    public List<String> getResumeSuggestions() {
        return resumeSuggestions;
    }

    public void setResumeSuggestions(List<String> resumeSuggestions) {
        this.resumeSuggestions = resumeSuggestions;
    }
}