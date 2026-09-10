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

        /*
         * Create ObjectMapper directly.
         * This avoids requiring an ObjectMapper bean.
         */
        this.objectMapper =
                new ObjectMapper();
    }

    /*
     * =========================================================
     * ANALYZE ONE RESUME
     * =========================================================
     */
    public AnalysisResult analyze(
            String resumeText,
            String jobDescriptionText)
            throws Exception {

        String prompt = """
                You are an AI resume analysis assistant.

                Analyze ONLY the information provided in the
                resume and job description below.

                Do not invent experience, skills, education,
                projects, certifications, or technologies.

                If something is not explicitly mentioned in the
                resume, say that it is not explicitly mentioned.

                Give an AI-ESTIMATED ATS compatibility score
                from 0 to 100.

                This is an estimate, NOT an actual commercial
                ATS score.

                IMPORTANT:
                Evaluate the candidate only against the supplied
                job description.

                Consider:

                1. Skills match
                2. Technical skills
                3. Experience
                4. Education
                5. Projects
                6. Certifications
                7. Job requirements
                8. Relevant technologies

                Return ONLY valid JSON.

                Required JSON format:

                {
                  "atsScore": 0,
                  "matchingSkills": [],
                  "missingSkills": [],
                  "experienceGaps": [],
                  "learningSuggestions": [],
                  "resumeSuggestions": []
                }

                RESUME:
                ----------------
                %s

                JOB DESCRIPTION:
                ----------------
                %s
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
         * Keep score between 0 and 100.
         */
        if (result.getAtsScore() < 0) {
            result.setAtsScore(0);
        }

        if (result.getAtsScore() > 100) {
            result.setAtsScore(100);
        }

        return result;
    }
}