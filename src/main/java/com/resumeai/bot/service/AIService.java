package com.resumeai.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.bot.model.AnalysisResult;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AIService(
            ChatClient.Builder chatClientBuilder) {

        this.chatClient =
                chatClientBuilder.build();

        this.objectMapper =
                new ObjectMapper();
    }

    /*
     * =========================================================
     * ANALYZE ONE RESUME AGAINST ONE JOB DESCRIPTION
     * =========================================================
     */
    public AnalysisResult analyze(
            String resumeText,
            String jobDescriptionText)
            throws Exception {

        String prompt = """
                You are a professional AI Resume Screening
                and Job Compatibility Analysis System.

                Your task is to compare ONE candidate resume
                against ONE supplied job description.

                =====================================================
                CRITICAL RULES
                =====================================================

                1. Analyze ONLY the supplied resume and job
                   description.

                2. NEVER invent skills, experience, education,
                   projects, certifications, responsibilities,
                   achievements, or technologies.

                3. If something is not explicitly present in
                   the resume, treat it as NOT PRESENT.

                4. Do NOT assume that a technical skill means
                   product-management experience.

                Example:

                React, JavaScript, Node.js, Docker, AWS,
                Kubernetes and REST APIs are technical skills.

                They must NOT automatically be considered:

                - Product Strategy
                - Product Vision
                - User Research
                - Roadmap Planning
                - Stakeholder Management
                - Competitive Analysis
                - Business Acumen

                unless the resume explicitly demonstrates them.

                5. Do NOT use any "Expected Match", "Expected Score",
                   or similar score written inside the resume.

                Those values are NOT evidence and must be ignored.

                6. Do NOT reward unrelated skills heavily.

                7. A candidate with more years of experience does
                   NOT automatically receive a higher score.

                8. Evaluate the candidate specifically against the
                   supplied job description.

                =====================================================
                ATS COMPATIBILITY SCORING
                =====================================================

                Calculate the final ATS compatibility score from
                these categories:

                REQUIRED SKILLS = 40 points
                EXPERIENCE = 20 points
                PREFERRED SKILLS = 15 points
                JOB RESPONSIBILITIES = 10 points
                EDUCATION = 5 points
                RELEVANT TECHNICAL / DOMAIN BACKGROUND = 10 points

                TOTAL = 100 points

                =====================================================
                1. REQUIRED SKILLS - 40 POINTS
                =====================================================

                Compare every required skill in the job description
                with the resume.

                Give points ONLY for skills explicitly supported
                by the resume.

                Missing required skills must reduce the score.

                =====================================================
                2. EXPERIENCE - 20 POINTS
                =====================================================

                Compare the required experience with the candidate's
                actual experience.

                IMPORTANT:

                Years of experience alone are NOT enough.

                Also determine whether the experience is relevant
                to the position.

                For example:

                7 years of software development experience does NOT
                equal 7 years of product management experience.

                =====================================================
                3. PREFERRED SKILLS - 15 POINTS
                =====================================================

                Compare preferred skills from the JD against the
                resume.

                Give points only when explicitly supported.

                =====================================================
                4. JOB RESPONSIBILITIES - 10 POINTS
                =====================================================

                Compare the responsibilities in the JD with actual
                responsibilities demonstrated in the resume.

                Do NOT assume that the candidate performed a
                responsibility merely because they had a related job.

                =====================================================
                5. EDUCATION - 5 POINTS
                =====================================================

                Compare the candidate's education with the JD.

                If the JD requires a bachelor's degree and the resume
                explicitly contains a bachelor's degree, award the
                appropriate points.

                =====================================================
                6. TECHNICAL / DOMAIN BACKGROUND - 10 POINTS
                =====================================================

                Give credit for technical or domain knowledge that
                is genuinely relevant to the supplied position.

                Do NOT allow unrelated technologies to dominate
                the final score.

                =====================================================
                FINAL SCORE
                =====================================================

                The final atsScore MUST equal:

                requiredSkillsScore
                + experienceScore
                + preferredSkillsScore
                + responsibilitiesScore
                + educationScore
                + technicalBackgroundScore

                The final score must be between 0 and 100.

                =====================================================
                MATCHING SKILLS
                =====================================================

                List skills explicitly present in the resume that
                match requirements in the JD.

                Do NOT list unrelated skills.

                =====================================================
                MISSING SKILLS
                =====================================================

                List important JD skills that are required or
                strongly preferred but are NOT explicitly present
                in the resume.

                =====================================================
                EXPERIENCE GAPS
                =====================================================

                Identify important differences between the required
                experience and the candidate's demonstrated experience.

                Example:

                "Resume shows software development experience, but
                no explicit product management experience."

                =====================================================
                LEARNING SUGGESTIONS
                =====================================================

                Suggest learning topics based ONLY on important
                missing skills.

                Examples:

                - Product Strategy & Vision
                - User Research & Interviewing
                - Roadmap Planning
                - Stakeholder Management
                - Data Analysis

                Do not recommend technologies that are already clearly
                demonstrated in the resume unless the JD specifically
                requires deeper expertise.

                =====================================================
                RESUME SUGGESTIONS
                =====================================================

                Suggest improvements based ONLY on the supplied
                resume and JD.

                Do NOT invent achievements.

                =====================================================
                IMPORTANT PRODUCT MANAGER EXAMPLE
                =====================================================

                If the job description is for a Product Manager and
                the resume is primarily a Software Developer resume,
                do NOT give a high score simply because the candidate
                has many technical skills.

                Product Strategy, Product Vision, User Research,
                Roadmap Planning, Stakeholder Management,
                Competitive Analysis, Business Acumen and
                Communication Skills must be evaluated independently.

                =====================================================
                OUTPUT
                =====================================================

                Return ONLY valid JSON.

                Do NOT include Markdown.

                Do NOT include explanations outside JSON.

                Required JSON format:

                {
                  "atsScore": 0,

                  "requiredSkillsScore": 0,
                  "experienceScore": 0,
                  "preferredSkillsScore": 0,
                  "responsibilitiesScore": 0,
                  "educationScore": 0,
                  "technicalBackgroundScore": 0,

                  "matchingSkills": [],

                  "missingSkills": [],

                  "experienceGaps": [],

                  "learningSuggestions": [],

                  "resumeSuggestions": []
                }

                =====================================================
                RESUME
                =====================================================

                ----------------
                %s
                ----------------

                =====================================================
                JOB DESCRIPTION
                =====================================================

                ----------------
                %s
                ----------------
                """.formatted(
                resumeText,
                jobDescriptionText
        );

        String response =
                chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

        if (response == null
                || response.isBlank()) {

            throw new IllegalStateException(
                    "AI returned an empty response"
            );
        }

        /*
         * Remove Markdown code fences if Gemini
         * returns them.
         */
        response = response
                .replace("```json", "")
                .replace("```", "")
                .trim();

        AnalysisResult result =
                objectMapper.readValue(
                        response,
                        AnalysisResult.class
                );

        /*
         * =====================================================
         * VALIDATE CATEGORY SCORES
         * =====================================================
         */

        result.setRequiredSkillsScore(
                clamp(
                        result.getRequiredSkillsScore(),
                        0,
                        40
                )
        );

        result.setExperienceScore(
                clamp(
                        result.getExperienceScore(),
                        0,
                        20
                )
        );

        result.setPreferredSkillsScore(
                clamp(
                        result.getPreferredSkillsScore(),
                        0,
                        15
                )
        );

        result.setResponsibilitiesScore(
                clamp(
                        result.getResponsibilitiesScore(),
                        0,
                        10
                )
        );

        result.setEducationScore(
                clamp(
                        result.getEducationScore(),
                        0,
                        5
                )
        );

        result.setTechnicalBackgroundScore(
                clamp(
                        result.getTechnicalBackgroundScore(),
                        0,
                        10
                )
        );

        /*
         * =====================================================
         * CALCULATE FINAL SCORE OURSELVES
         * =====================================================
         *
         * Do NOT trust the AI's atsScore directly.
         *
         * This makes ranking more consistent.
         */
        int finalScore =
                result.getRequiredSkillsScore()
                        + result.getExperienceScore()
                        + result.getPreferredSkillsScore()
                        + result.getResponsibilitiesScore()
                        + result.getEducationScore()
                        + result.getTechnicalBackgroundScore();

        result.setAtsScore(
                clamp(finalScore, 0, 100)
        );

        return result;
    }


    /*
     * =========================================================
     * CLAMP VALUE
     * =========================================================
     */
    private int clamp(
            int value,
            int min,
            int max) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}