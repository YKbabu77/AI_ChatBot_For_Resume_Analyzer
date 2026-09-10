package com.resumeai.bot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String ask(
            String resumeText,
            String jobDescriptionText,
            String question) {

        String prompt = """
                You are ResumeAI, an AI career assistant.

                Answer the user's question using ONLY the supplied
                resume and job description.

                Do not invent information.

                If the information is not available in the documents,
                clearly say:
                "This is not explicitly mentioned in the provided documents."

                Give practical and concise advice.

                RESUME:
                ----------------
                %s

                JOB DESCRIPTION:
                ----------------
                %s

                USER QUESTION:
                ----------------
                %s
                """.formatted(
                resumeText,
                jobDescriptionText,
                question
        );

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}