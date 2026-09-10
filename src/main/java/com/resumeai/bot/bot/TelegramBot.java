package com.resumeai.bot.bot;

import com.resumeai.bot.model.AnalysisResult;
import com.resumeai.bot.model.UserSession;
import com.resumeai.bot.service.AIService;
import com.resumeai.bot.service.ChatService;
import com.resumeai.bot.service.DocumentService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelegramBot implements SpringLongPollingBot {

    private final TelegramClient telegramClient;

    private final String botToken;

    private final DocumentService documentService;

    private final AIService aiService;

    private final ChatService chatService;

    /*
     * Stores one session for each Telegram chat/user.
     */
    private final Map<Long, UserSession> userSessions =
            new ConcurrentHashMap<>();

    /*
     * Stores the latest analysis results for each user.
     *
     * This allows /result and follow-up questions
     * after the analysis.
     */
    private final Map<Long, List<CandidateAnalysis>>
            analysisResults =
            new ConcurrentHashMap<>();

    public TelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            DocumentService documentService,
            AIService aiService,
            ChatService chatService) {

        this.botToken = botToken;

        this.telegramClient =
                new OkHttpTelegramClient(botToken);

        this.documentService =
                documentService;

        this.aiService =
                aiService;

        this.chatService =
                chatService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer
    getUpdatesConsumer() {

        return updates -> {

            for (Update update : updates) {

                /*
                 * Ignore updates without messages.
                 */
                if (!update.hasMessage()) {
                    continue;
                }

                Long chatId =
                        update.getMessage()
                                .getChatId();

                /*
                 * Get or create user session.
                 */
                UserSession session =
                        userSessions.computeIfAbsent(
                                chatId,
                                id -> new UserSession()
                        );

                /*
                 * =====================================================
                 * DOCUMENT PROCESSING
                 * =====================================================
                 */
                if (update.getMessage().hasDocument()) {

                    processDocument(
                            update,
                            chatId,
                            session
                    );

                    continue;
                }

                /*
                 * Ignore non-text messages.
                 */
                if (!update.getMessage().hasText()) {
                    continue;
                }

                String text =
                        update.getMessage()
                                .getText()
                                .trim();

                /*
                 * =====================================================
                 * /start
                 * =====================================================
                 */
                if (text.equals("/start")) {

                    session.reset();

                    analysisResults.remove(chatId);

                    sendMessage(
                            SendMessage.builder()
                                    .chatId(chatId)
                                    .text("""
                                            👋 Welcome to ResumeAI!

                                            I can analyze MULTIPLE resumes against one job description.

                                            🎯 AI-Estimated ATS Compatibility Score
                                            ✅ Matching Skills
                                            ❌ Missing Skills
                                            📊 Experience Gaps
                                            📚 Learning Suggestions
                                            📝 Resume Suggestions
                                            🏆 Candidate Ranking

                                            📄 Supported formats:
                                            • PDF
                                            • DOCX

                                            STEP 1:
                                            Upload one or more resumes.

                                            STEP 2:
                                            Send /done when all resumes
                                            have been uploaded.

                                            STEP 3:
                                            Upload the job description.

                                            Use /help for more information.
                                            """)
                                    .build()
                    );
                }

                /*
                 * =====================================================
                 * /help
                 * =====================================================
                 */
                else if (text.equals("/help")) {

                    sendMessage(
                            SendMessage.builder()
                                    .chatId(chatId)
                                    .text("""
                                            📚 ResumeAI Help

                                            Commands:

                                            /start
                                            Start a new analysis.

                                            /analyze
                                            Start a new multi-resume analysis.

                                            /done
                                            Finish uploading resumes and
                                            move to the job description.

                                            /result
                                            Show the analysis results.

                                            /reset
                                            Clear the current session.

                                            /help
                                            Show this help.

                                            📋 How to use:

                                            1️⃣ Upload Resume 1
                                            2️⃣ Upload Resume 2
                                            3️⃣ Upload Resume 3
                                            4️⃣ Upload as many resumes as needed
                                            5️⃣ Send /done
                                            6️⃣ Upload the Job Description
                                            7️⃣ ResumeAI analyzes every candidate
                                            8️⃣ Candidates are ranked by score

                                            📄 Supported:
                                            • PDF
                                            • DOCX
                                            """)
                                    .build()
                    );
                }

                /*
                 * =====================================================
                 * /done
                 * =====================================================
                 */
                else if (text.equals("/done")) {

                    handleDone(
                            chatId,
                            session
                    );
                }

                /*
                 * =====================================================
                 * /reset
                 * =====================================================
                 */
                else if (text.equals("/reset")) {

                    session.reset();

                    analysisResults.remove(chatId);

                    sendMessage(
                            SendMessage.builder()
                                    .chatId(chatId)
                                    .text("""
                                            🔄 Session reset successfully.

                                            Please upload your resumes.

                                            You can upload multiple
                                            PDF or DOCX files.

                                            Send /done when finished.
                                            """)
                                    .build()
                    );
                }

                /*
                 * =====================================================
                 * /analyze
                 * =====================================================
                 */
                else if (text.equals("/analyze")) {

                    session.reset();

                    analysisResults.remove(chatId);

                    sendMessage(
                            SendMessage.builder()
                                    .chatId(chatId)
                                    .text("""
                                            🎯 New multi-resume analysis started.

                                            Upload your first resume.

                                            You can upload multiple
                                            PDF or DOCX resumes.

                                            When finished, send:

                                            /done
                                            """)
                                    .build()
                    );
                }

                /*
                 * =====================================================
                 * /result
                 * =====================================================
                 */
                else if (text.equals("/result")) {

                    showResults(
                            chatId,
                            session
                    );
                }

                /*
                 * =====================================================
                 * NORMAL CHAT
                 * =====================================================
                 */
                else {

                    handleNormalChat(
                            chatId,
                            session,
                            text
                    );
                }
            }
        };
    }

    /*
     * =============================================================
     * PROCESS DOCUMENT
     * =============================================================
     */
    private void processDocument(
            Update update,
            Long chatId,
            UserSession session) {

        Document document =
                update.getMessage()
                        .getDocument();

        String fileName =
                document.getFileName();

        /*
         * Check format.
         */
        if (!isSupportedDocument(fileName)) {

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ❌ Unsupported file format.

                                    Please upload:
                                    📄 PDF
                                    📝 DOCX
                                    """)
                            .build()
            );

            return;
        }

        /*
         * =========================================================
         * RESUME UPLOAD
         * =========================================================
         */
        if (session.getState()
                == UserSession.State.WAITING_FOR_RESUMES) {

            processResume(
                    document,
                    fileName,
                    chatId,
                    session
            );

            return;
        }

        /*
         * =========================================================
         * JOB DESCRIPTION UPLOAD
         * =========================================================
         */
        if (session.getState()
                == UserSession.State.WAITING_FOR_JOB_DESCRIPTION) {

            processJobDescription(
                    document,
                    fileName,
                    chatId,
                    session
            );

            return;
        }

        /*
         * =========================================================
         * DOCUMENT AFTER ANALYSIS
         * =========================================================
         */
        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text("""
                                ℹ️ Analysis is already completed.

                                Use /reset to start a new analysis.
                                """)
                        .build()
        );
    }

    /*
     * =============================================================
     * PROCESS ONE RESUME
     * =============================================================
     */
    private void processResume(
            Document document,
            String fileName,
            Long chatId,
            UserSession session) {

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(
                                "📄 "
                                        + fileName
                                        + " received.\n\n"
                                        + "⏳ Processing resume..."
                        )
                        .build()
        );

        File tempFile = null;

        try {

            /*
             * Get Telegram file.
             */
            GetFile getFile =
                    new GetFile(
                            document.getFileId()
                    );

            var telegramFile =
                    telegramClient.execute(
                            getFile
                    );

            String filePath =
                    telegramFile.getFilePath();

            /*
             * Build download URL.
             */
            String downloadUrl =
                    "https://api.telegram.org/file/bot"
                            + botToken
                            + "/"
                            + filePath;

            /*
             * Create temporary file.
             */
            String extension =
                    getFileExtension(fileName);

            tempFile =
                    File.createTempFile(
                            "resumeai-",
                            extension
                    );

            /*
             * Download.
             */
            try (
                    InputStream inputStream =
                            URI.create(downloadUrl)
                                    .toURL()
                                    .openStream();

                    FileOutputStream outputStream =
                            new FileOutputStream(
                                    tempFile
                            )
            ) {

                inputStream.transferTo(
                        outputStream
                );
            }

            /*
             * Extract text.
             */
            String extractedText =
                    documentService.extractText(
                            tempFile,
                            fileName
                    );

            if (extractedText == null
                    || extractedText.isBlank()) {

                throw new IllegalStateException(
                        "No readable text found in resume."
                );
            }

            /*
             * Store resume.
             */
            session.addResumeText(
                    extractedText
            );

            session.addResumeFileName(
                    fileName
            );

            int count =
                    session.getResumeCount();

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(
                                    """
                                    ✅ Resume added successfully!

                                    📄 File: %s
                                    👥 Resumes uploaded: %d

                                    You can upload another resume.

                                    When all resumes are uploaded,
                                    send:

                                    /done

                                    Then I will ask for the
                                    job description.
                                    """.formatted(
                                            fileName,
                                            count
                                    )
                            )
                            .build()
            );

        } catch (Exception e) {

            System.err.println(
                    "========================================"
            );

            System.err.println(
                    "RESUME PROCESSING ERROR"
            );

            System.err.println(
                    "========================================"
            );

            e.printStackTrace();

            System.err.println(
                    "========================================"
            );

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ❌ Could not process this resume.

                                    Please make sure the file is:

                                    • Valid PDF or DOCX
                                    • Not corrupted
                                    • Contains readable text

                                    You can upload the file again.
                                    """)
                            .build()
            );

        } finally {

            deleteTempFile(
                    tempFile
            );
        }
    }

    /*
     * =============================================================
     * HANDLE /DONE
     * =============================================================
     */
    private void handleDone(
            Long chatId,
            UserSession session) {

        /*
         * No resumes uploaded.
         */
        if (session.getResumeCount() == 0) {

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ⚠️ No resumes uploaded yet.

                                    Please upload at least one
                                    PDF or DOCX resume.
                                    """)
                            .build()
            );

            return;
        }

        /*
         * Move to job description.
         */
        session.setState(
                UserSession.State.WAITING_FOR_JOB_DESCRIPTION
        );

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(
                                """
                                ✅ Resume upload completed!

                                👥 Total resumes: %d

                                Now upload the JOB DESCRIPTION
                                as PDF or DOCX.

                                🤖 After receiving it, I will analyze
                                every candidate against the job.
                                """.formatted(
                                        session.getResumeCount()
                                )
                        )
                        .build()
        );
    }

    /*
     * =============================================================
     * PROCESS JOB DESCRIPTION
     * =============================================================
     */
    private void processJobDescription(
            Document document,
            String fileName,
            Long chatId,
            UserSession session) {

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(
                                "📋 "
                                        + fileName
                                        + " received.\n\n"
                                        + "⏳ Processing job description..."
                        )
                        .build()
        );

        File tempFile = null;

        try {

            /*
             * Get Telegram file.
             */
            GetFile getFile =
                    new GetFile(
                            document.getFileId()
                    );

            var telegramFile =
                    telegramClient.execute(
                            getFile
                    );

            String filePath =
                    telegramFile.getFilePath();

            /*
             * Download URL.
             */
            String downloadUrl =
                    "https://api.telegram.org/file/bot"
                            + botToken
                            + "/"
                            + filePath;

            /*
             * Temporary file.
             */
            String extension =
                    getFileExtension(fileName);

            tempFile =
                    File.createTempFile(
                            "resumeai-jd-",
                            extension
                    );

            /*
             * Download.
             */
            try (
                    InputStream inputStream =
                            URI.create(downloadUrl)
                                    .toURL()
                                    .openStream();

                    FileOutputStream outputStream =
                            new FileOutputStream(
                                    tempFile
                            )
            ) {

                inputStream.transferTo(
                        outputStream
                );
            }

            /*
             * Extract JD text.
             */
            String jobDescriptionText =
                    documentService.extractText(
                            tempFile,
                            fileName
                    );

            if (jobDescriptionText == null
                    || jobDescriptionText.isBlank()) {

                throw new IllegalStateException(
                        "No readable text found in job description."
                );
            }

            session.setJobDescriptionText(
                    jobDescriptionText
            );

            /*
             * Analyze all resumes.
             */
            analyzeAllResumes(
                    chatId,
                    session
            );

        } catch (Exception e) {

            System.err.println(
                    "========================================"
            );

            System.err.println(
                    "JOB DESCRIPTION PROCESSING ERROR"
            );

            System.err.println(
                    "========================================"
            );

            e.printStackTrace();

            System.err.println(
                    "========================================"
            );

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ❌ Could not process the job description.

                                    Please upload a valid PDF or DOCX
                                    job description.
                                    """)
                            .build()
            );

        } finally {

            deleteTempFile(
                    tempFile
            );
        }
    }

    /*
     * =============================================================
     * ANALYZE ALL RESUMES
     * =============================================================
     */
    private void analyzeAllResumes(
            Long chatId,
            UserSession session) {

        List<String> resumeTexts =
                session.getResumeTexts();

        List<String> fileNames =
                session.getResumeFileNames();

        String jobDescription =
                session.getJobDescriptionText();

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(
                                """
                                🤖 Starting AI analysis...

                                👥 Candidates: %d

                                ⏳ I will analyze each resume
                                against the job description.

                                Please wait...
                                """.formatted(
                                        resumeTexts.size()
                                )
                        )
                        .build()
        );

        List<CandidateAnalysis> results =
                new ArrayList<>();

        /*
         * Analyze every resume.
         */
        for (int i = 0;
             i < resumeTexts.size();
             i++) {

            String fileName =
                    fileNames.get(i);

            String resumeText =
                    resumeTexts.get(i);

            try {

                sendMessage(
                        SendMessage.builder()
                                .chatId(chatId)
                                .text(
                                        "🤖 Analyzing candidate "
                                                + (i + 1)
                                                + "/"
                                                + resumeTexts.size()
                                                + "\n\n📄 "
                                                + fileName
                                )
                                .build()
                );

                AnalysisResult result =
                        aiService.analyze(
                                resumeText,
                                jobDescription
                        );

                results.add(
                        new CandidateAnalysis(
                                fileName,
                                result
                        )
                );

            } catch (Exception e) {

                System.err.println(
                        "========================================"
                );

                System.err.println(
                        "AI ANALYSIS ERROR FOR: "
                                + fileName
                );

                System.err.println(
                        "========================================"
                );

                e.printStackTrace();

                System.err.println(
                        "========================================"
                );

                sendMessage(
                        SendMessage.builder()
                                .chatId(chatId)
                                .text(
                                        "❌ AI analysis failed for:\n"
                                                + fileName
                                                + "\n\nSkipping this candidate."
                                )
                                .build()
                );
            }
        }

        /*
         * Sort candidates from highest score
         * to lowest score.
         */
        results.sort(
                Comparator.comparingInt(
                        (CandidateAnalysis candidate) ->
                                candidate.result()
                                        .getAtsScore()
                ).reversed()
        );

        /*
         * Save results.
         */
        analysisResults.put(
                chatId,
                results
        );

        /*
         * Analysis completed.
         */
        session.setState(
                UserSession.State.READY_FOR_CHAT
        );

        /*
         * Send ranking.
         */
        sendRanking(
                chatId,
                results
        );

        /*
         * Send follow-up information.
         */
        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text("""
                                💬 Analysis completed!

                                You can now ask questions about
                                the candidates and job description.

                                Examples:

                                • Which candidate is the best?
                                • What skills does the top candidate have?
                                • Which candidate needs the least training?
                                • What are the missing skills of candidate 2?
                                • How can candidate 3 improve?

                                Use /result to see the ranking again.
                                """)
                        .build()
        );
    }

    /*
     * =============================================================
     * SEND RANKING
     * =============================================================
     */
    private void sendRanking(
            Long chatId,
            List<CandidateAnalysis> results) {

        StringBuilder message =
                new StringBuilder();

        message.append(
                "🏆 RESUME ANALYSIS RANKING\n\n"
        );

        if (results.isEmpty()) {

            message.append(
                    "❌ No candidates could be analyzed."
            );

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(message.toString())
                            .build()
            );

            return;
        }

        for (int i = 0;
             i < results.size();
             i++) {

            CandidateAnalysis candidate =
                    results.get(i);

            message.append(
                    getRankEmoji(i + 1)
            );

            message.append(" ")
                    .append(i + 1)
                    .append(". ")
                    .append(candidate.fileName())
                    .append("\n");

            message.append(
                    "   🎯 Score: "
            )
                    .append(
                            candidate.result()
                                    .getAtsScore()
                    )
                    .append("/100\n\n");
        }

        message.append(
                "⚠️ Scores are AI-estimated compatibility "
                        + "scores, not actual commercial ATS scores."
        );

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(message.toString())
                        .build()
        );

        /*
         * Send detailed result for each candidate.
         */
        for (int i = 0;
             i < results.size();
             i++) {

            CandidateAnalysis candidate =
                    results.get(i);

            sendCandidateDetailedResult(
                    chatId,
                    i + 1,
                    candidate
            );
        }
    }

    /*
     * =============================================================
     * SEND CANDIDATE DETAILED RESULT
     * =============================================================
     */
    private void sendCandidateDetailedResult(
            Long chatId,
            int rank,
            CandidateAnalysis candidate) {

        AnalysisResult result =
                candidate.result();

        StringBuilder message =
                new StringBuilder();

        message.append(
                "👤 CANDIDATE #"
        )
                .append(rank)
                .append("\n\n");

        message.append(
                "📄 Resume: "
        )
                .append(candidate.fileName())
                .append("\n");

        message.append(
                "🎯 AI-Estimated ATS Compatibility Score: "
        )
                .append(result.getAtsScore())
                .append("/100\n\n");

        /*
         * Matching skills.
         */
        message.append(
                "✅ MATCHING SKILLS\n"
        );

        appendList(
                message,
                result.getMatchingSkills(),
                "None identified"
        );

        /*
         * Missing skills.
         */
        message.append(
                "\n❌ MISSING SKILLS\n"
        );

        appendList(
                message,
                result.getMissingSkills(),
                "None identified"
        );

        /*
         * Experience gaps.
         */
        message.append(
                "\n📊 EXPERIENCE GAPS\n"
        );

        appendList(
                message,
                result.getExperienceGaps(),
                "No major gaps identified"
        );

        /*
         * Learning section heading.
         */
        message.append(
                "\n📚 LEARNING SUGGESTIONS"
        );

        /*
         * Resume suggestions.
         */
        message.append(
                "\n\n📝 RESUME SUGGESTIONS\n"
        );

        appendList(
                message,
                result.getResumeSuggestions(),
                "None identified"
        );

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(message.toString())
                        .build()
        );

        /*
         * Send each learning suggestion separately
         * with its own course button directly below it.
         */
        sendLearningSuggestions(
                chatId,
                result.getLearningSuggestions()
        );
    }

    /*
     * =============================================================
     * SEND LEARNING SUGGESTIONS
     * =============================================================
     */
    private void sendLearningSuggestions(
            Long chatId,
            List<String> suggestions) {

        if (suggestions == null
                || suggestions.isEmpty()) {

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(
                                    "• None identified"
                            )
                            .build()
            );

            return;
        }

        for (String suggestion : suggestions) {

            if (suggestion == null
                    || suggestion.isBlank()) {
                continue;
            }

            CourseResource course =
                    findCourseForSuggestion(
                            suggestion
                    );

            String text =
                    "🔹 "
                            + suggestion.trim();

            if (course == null) {

                sendMessage(
                        SendMessage.builder()
                                .chatId(chatId)
                                .text(text)
                                .build()
                );

                continue;
            }

            InlineKeyboardButton button =
                    InlineKeyboardButton.builder()
                            .text(course.buttonText())
                            .url(course.url())
                            .build();

            InlineKeyboardRow row =
                    new InlineKeyboardRow(button);

            InlineKeyboardMarkup keyboard =
                    InlineKeyboardMarkup.builder()
                            .keyboardRow(row)
                            .build();

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .replyMarkup(keyboard)
                            .build()
            );
        }
    }

    /*
     * =============================================================
     * FIND COURSE FOR SUGGESTION
     * =============================================================
     */
    private CourseResource findCourseForSuggestion(
            String suggestion) {

        if (suggestion == null
                || suggestion.isBlank()) {

            return null;
        }

        String text =
                suggestion
                        .toLowerCase()
                        .trim();

        /*
         * =========================================================
         * PYTHON
         * =========================================================
         */
        if (text.contains("python")) {

            return new CourseResource(
                    "🐍 Open Python Course",
                    "https://www.coursera.org/specializations/python"
            );
        }

        /*
         * =========================================================
         * MACHINE LEARNING
         * =========================================================
         */
        if (text.contains("machine learning")
                || text.contains("machine-learning")) {

            return new CourseResource(
                    "🤖 Open Machine Learning Course",
                    "https://www.coursera.org/learn/machine-learning"
            );
        }

        /*
         * =========================================================
         * STATISTICAL ANALYSIS
         * =========================================================
         */
        if (text.contains("statistical analysis")
                || text.contains("statistics")
                || text.contains("statistical")) {

            return new CourseResource(
                    "📊 Open Statistics Course",
                    "https://www.coursera.org/specializations/statistics-with-python"
            );
        }

        /*
         * =========================================================
         * SQL
         * =========================================================
         */
        if (text.contains("sql")
                || text.contains("structured query language")) {

            return new CourseResource(
                    "🗄️ Open SQL Course",
                    "https://www.coursera.org/learn/sql-for-data-science"
            );
        }

        /*
         * =========================================================
         * PANDAS
         * =========================================================
         */
        if (text.contains("pandas")) {

            return new CourseResource(
                    "🐼 Open Pandas Course",
                    "https://www.coursera.org/learn/data-analysis-with-python"
            );
        }

        /*
         * =========================================================
         * NUMPY
         * =========================================================
         */
        if (text.contains("numpy")) {

            return new CourseResource(
                    "🔢 Open NumPy Course",
                    "https://www.coursera.org/learn/python-data-analysis"
            );
        }

        /*
         * =========================================================
         * SCIKIT-LEARN
         * =========================================================
         */
        if (text.contains("scikit-learn")
                || text.contains("scikit learn")
                || text.contains("sklearn")) {

            return new CourseResource(
                    "🧠 Open Scikit-learn Course",
                    "https://www.coursera.org/learn/machine-learning"
            );
        }

        /*
         * =========================================================
         * DATA VISUALIZATION
         * =========================================================
         */
        if (text.contains("data visualization")
                || text.contains("data visualisation")
                || text.contains("visualization")
                || text.contains("visualisation")) {

            return new CourseResource(
                    "📈 Open Data Visualization Course",
                    "https://www.coursera.org/learn/data-visualization"
            );
        }

        /*
         * =========================================================
         * TENSORFLOW
         * =========================================================
         */
        if (text.contains("tensorflow")) {

            return new CourseResource(
                    "🧠 Open TensorFlow Course",
                    "https://www.coursera.org/specializations/tensorflow-in-practice"
            );
        }

        /*
         * =========================================================
         * PYTORCH
         * =========================================================
         */
        if (text.contains("pytorch")
                || text.contains("torch")) {

            return new CourseResource(
                    "🔥 Open PyTorch Course",
                    "https://www.coursera.org/learn/deep-learning-with-pytorch"
            );
        }

        /*
         * =========================================================
         * JAVASCRIPT
         * =========================================================
         */
        if (text.contains("javascript")
                || text.contains("java script")) {

            return new CourseResource(
                    "🟨 Open JavaScript Course",
                    "https://www.coursera.org/specializations/javascript-beginner"
            );
        }

        /*
         * =========================================================
         * REACT
         * =========================================================
         */
        if (text.contains("react")
                || text.contains("react.js")
                || text.contains("reactjs")) {

            return new CourseResource(
                    "⚛️ Open React Course",
                    "https://www.coursera.org/learn/react-basics"
            );
        }

        /*
         * =========================================================
         * NODE.JS
         * =========================================================
         */
        if (text.contains("node.js")
                || text.contains("nodejs")
                || text.contains("node js")) {

            return new CourseResource(
                    "🟢 Open Node.js Course",
                    "https://www.coursera.org/learn/server-side-javascript-with-nodejs"
            );
        }

        /*
         * =========================================================
         * AWS
         * =========================================================
         */
        if (text.contains("aws")
                || text.contains("amazon web services")) {

            return new CourseResource(
                    "☁️ Open AWS Developer Learning",
                    "https://aws.amazon.com/training/learn-about/developer/"
            );
        }

        /*
         * =========================================================
         * DOCKER
         * =========================================================
         */
        if (text.contains("docker")
                || text.contains("container")
                || text.contains("containerization")
                || text.contains("containerisation")) {

            return new CourseResource(
                    "🐳 Open Docker Course",
                    "https://www.coursera.org/learn/docker-for-the-absolute-beginner"
            );
        }

        /*
         * =========================================================
         * POSTGRESQL
         * =========================================================
         */
        if (text.contains("postgresql")
                || text.contains("postgres")) {

            return new CourseResource(
                    "🐘 Open PostgreSQL Course",
                    "https://www.coursera.org/learn/postgresql-for-everybody"
            );
        }

        /*
         * =========================================================
         * MONGODB
         * =========================================================
         */
        if (text.contains("mongodb")
                || text.contains("mongo db")) {

            return new CourseResource(
                    "🍃 Open MongoDB Course",
                    "https://www.coursera.org/learn/introduction-to-mongodb"
            );
        }

        /*
         * =========================================================
         * GIT
         * =========================================================
         */
        if (text.contains("git")
                || text.contains("github")
                || text.contains("version control")) {

            return new CourseResource(
                    "🌿 Open Git Course",
                    "https://www.coursera.org/learn/introduction-git-github"
            );
        }

        /*
         * =========================================================
         * REST API
         * =========================================================
         */
        if (text.contains("rest api")
                || text.contains("restful api")
                || text.contains("rest services")
                || text.contains("restful services")) {

            return new CourseResource(
                    "🔗 Open REST API Course",
                    "https://www.coursera.org/learn/restful-api-with-spring-boot"
            );
        }

        /*
         * =========================================================
         * SPRING BOOT
         * =========================================================
         */
        if (text.contains("spring boot")
                || text.contains("springboot")) {

            return new CourseResource(
                    "🌱 Open Spring Boot Course",
                    "https://www.coursera.org/learn/java-spring-boot"
            );
        }

        /*
         * =========================================================
         * MICROSERVICES
         * =========================================================
         */
        if (text.contains("microservice")
                || text.contains("microservices")) {

            return new CourseResource(
                    "🔧 Open Microservices Course",
                    "https://www.coursera.org/learn/java-microservices-spring-boot"
            );
        }

        /*
         * =========================================================
         * JAVA
         * =========================================================
         */
        if (text.contains("java")
                || text.contains("core java")
                || text.contains("java programming")) {

            return new CourseResource(
                    "☕ Open Java Course",
                    "https://www.coursera.org/learn/java-programming"
            );
        }

        /*
         * =========================================================
         * PRODUCT STRATEGY
         * =========================================================
         */
        if (text.contains("product strategy")
                || text.contains("product vision")
                || text.contains("product strategy & vision")
                || text.contains("product strategy and vision")) {

            return new CourseResource(
                    "🎯 Open Product Strategy Course",
                    "https://www.coursera.org/learn/strategic-product-management"
            );
        }

        /*
         * =========================================================
         * USER RESEARCH
         * =========================================================
         */
        if (text.contains("user research")
                || text.contains("user interviewing")
                || text.contains("user interview")
                || text.contains("interviewing users")) {

            return new CourseResource(
                    "🔎 Open User Research Course",
                    "https://www.coursera.org/learn/user-research"
            );
        }

        /*
         * =========================================================
         * DATA ANALYSIS
         * =========================================================
         */
        if (text.contains("data analysis")
                || text.contains("data analytics")
                || text.contains("analyze data")
                || text.contains("analysing data")) {

            return new CourseResource(
                    "📊 Open Data Analysis Course",
                    "https://www.coursera.org/learn/data-analysis-with-python"
            );
        }

        /*
         * =========================================================
         * ROADMAP PLANNING
         * =========================================================
         */
        if (text.contains("roadmap planning")
                || text.contains("product roadmap")
                || text.contains("roadmap")) {

            return new CourseResource(
                    "🗺️ Open Product Roadmap Course",
                    "https://www.coursera.org/learn/microsoft-product-strategy-and-roadmapping"
            );
        }

        /*
         * =========================================================
         * STAKEHOLDER MANAGEMENT
         * =========================================================
         */
        if (text.contains("stakeholder management")
                || text.contains("stakeholder communication")
                || text.contains("stakeholder")) {

            return new CourseResource(
                    "🤝 Open Stakeholder Management Course",
                    "https://www.coursera.org/learn/microsoft-product-strategy-and-roadmapping"
            );
        }

        /*
         * =========================================================
         * COMPETITIVE ANALYSIS
         * =========================================================
         */
        if (text.contains("competitive analysis")
                || text.contains("competitor analysis")
                || text.contains("competitive research")) {

            return new CourseResource(
                    "🏆 Open Competitive Analysis Course",
                    "https://www.coursera.org/learn/strategic-product-management"
            );
        }

        /*
         * =========================================================
         * BUSINESS ACUMEN
         * =========================================================
         */
        if (text.contains("business acumen")
                || text.contains("business strategy")
                || text.contains("business knowledge")) {

            return new CourseResource(
                    "💼 Open Business Strategy Course",
                    "https://www.coursera.org/learn/business-strategy"
            );
        }

        /*
         * =========================================================
         * COMMUNICATION SKILLS
         * =========================================================
         */
        if (text.contains("communication skills")
                || text.contains("communication")
                || text.contains("professional communication")) {

            return new CourseResource(
                    "💬 Open Communication Course",
                    "https://www.coursera.org/learn/wharton-communication-skills"
            );
        }

        /*
         * No matching course.
         */
        return null;
    }

    /*
     * =============================================================
     * APPEND LIST
     * =============================================================
     */
    private void appendList(
            StringBuilder message,
            List<String> items,
            String emptyMessage) {

        if (items != null
                && !items.isEmpty()) {

            for (String item : items) {

                message.append("• ")
                        .append(item)
                        .append("\n");
            }

        } else {

            message.append(
                    "• "
                            + emptyMessage
                            + "\n"
            );
        }
    }

    /*
     * =============================================================
     * RANK EMOJI
     * =============================================================
     */
    private String getRankEmoji(
            int rank) {

        return switch (rank) {

            case 1 -> "🥇";

            case 2 -> "🥈";

            case 3 -> "🥉";

            default -> "🏅";
        };
    }

    /*
     * =============================================================
     * SHOW RESULTS
     * =============================================================
     */
    private void showResults(
            Long chatId,
            UserSession session) {

        List<CandidateAnalysis> results =
                analysisResults.get(chatId);

        if (results == null
                || results.isEmpty()) {

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ℹ️ No completed analysis is available.

                                    Upload multiple resumes,
                                    send /done, and then upload
                                    the job description.
                                    """)
                            .build()
            );

            return;
        }

        sendRanking(
                chatId,
                results
        );
    }

    /*
     * =============================================================
     * NORMAL CHAT
     * =============================================================
     */
    private void handleNormalChat(
            Long chatId,
            UserSession session,
            String text) {

        if (session.getState()
                != UserSession.State.READY_FOR_CHAT) {

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    📄 Please complete the analysis first.

                                    1️⃣ Upload multiple resumes
                                    2️⃣ Send /done
                                    3️⃣ Upload the job description
                                    """)
                            .build()
            );

            return;
        }

        sendMessage(
                SendMessage.builder()
                        .chatId(chatId)
                        .text("🤖 Thinking...")
                        .build()
        );

        try {

            /*
             * For now ChatService uses the first resume.
             *
             * We will improve this next if you want
             * candidate-specific conversations.
             */
            String resumeText =
                    session.getResumeTexts().get(0);

            String answer =
                    chatService.ask(
                            resumeText,
                            session.getJobDescriptionText(),
                            text
                    );

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(
                                    "🤖 ResumeAI\n\n"
                                            + answer
                            )
                            .build()
            );

        } catch (Exception e) {

            System.err.println(
                    "========================================"
            );

            System.err.println(
                    "CHAT AI ERROR"
            );

            System.err.println(
                    "========================================"
            );

            e.printStackTrace();

            sendMessage(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("""
                                    ❌ Sorry, I couldn't answer that.

                                    Please try again.
                                    """)
                            .build()
            );
        }
    }

    /*
     * =============================================================
     * SUPPORTED DOCUMENT
     * =============================================================
     */
    private boolean isSupportedDocument(
            String fileName) {

        if (fileName == null) {
            return false;
        }

        String lowerName =
                fileName.toLowerCase();

        return lowerName.endsWith(".pdf")
                || lowerName.endsWith(".docx");
    }

    /*
     * =============================================================
     * FILE EXTENSION
     * =============================================================
     */
    private String getFileExtension(
            String fileName) {

        if (fileName == null) {
            return ".tmp";
        }

        String lowerName =
                fileName.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return ".pdf";
        }

        if (lowerName.endsWith(".docx")) {
            return ".docx";
        }

        return ".tmp";
    }

    /*
     * =============================================================
     * DELETE TEMP FILE
     * =============================================================
     */
    private void deleteTempFile(
            File tempFile) {

        if (tempFile != null
                && tempFile.exists()) {

            if (!tempFile.delete()) {

                System.err.println(
                        "Warning: temporary file could not be deleted."
                );
            }
        }
    }

    /*
     * =============================================================
     * SEND TELEGRAM MESSAGE
     * =============================================================
     */
    private void sendMessage(
            SendMessage message) {

        try {

            telegramClient.execute(
                    message
            );

        } catch (TelegramApiException e) {

            System.err.println(
                    "Failed to send Telegram message: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    /*
     * =============================================================
     * COURSE RESOURCE RECORD
     * =============================================================
     */
    private record CourseResource(
            String buttonText,
            String url
    ) {
    }

    /*
     * =============================================================
     * CANDIDATE ANALYSIS RECORD
     * =============================================================
     */
    private record CandidateAnalysis(
            String fileName,
            AnalysisResult result
    ) {
    }
}