package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Interview;
import com.workctl.core.domain.Interview.ExperienceLink;
import com.workctl.core.domain.Interview.InterviewQuestion;
import com.workctl.core.model.InterviewResult;
import com.workctl.core.model.InterviewRound;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * File-backed interview service.
 *
 * Each interview is stored as a markdown file in 05_Interviews/:
 * YYYY-MM-DD-{company-slug}-{role-slug}.md
 *
 * File format:
 * # Google — SDE2
 * <!-- INTERVIEW_META: id=... company=... role=... date=... round=...
 * result=... jobUrl=... created=... -->
 *
 * ## Notes
 * Overall impressions...
 *
 * ## Experience Links
 * - [Glassdoor Review](https://glassdoor.com/...)
 * - [LeetCode Discussion](https://leetcode.com/discuss/...)
 *
 * ## Questions
 * ### DSA
 * - [ ] Two Sum (important)
 * url: https://leetcode.com/problems/two-sum/
 * > HashMap O(n) approach
 * - [x] LRU Cache
 * url: https://leetcode.com/problems/lru-cache/
 */
public class InterviewService {

    private static final String DIR_NAME = "05_Interviews";

    // ================================================================
    // PUBLIC API
    // ================================================================

    public Interview createInterview(String company, String role, LocalDateTime dateTime) {
        Interview interview = new Interview(company, role, dateTime);
        saveInterview(interview);
        return interview;
    }

    public void saveInterview(Interview interview) {
        try {
            Path file = findInterviewFile(interview.getId());
            if (file == null) {
                file = buildInterviewFilePath(interview);
            }
            Files.writeString(file, formatInterview(interview));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save interview", e);
        }
    }

    public Optional<Interview> loadInterview(String interviewId) {
        try {
            Path file = findInterviewFile(interviewId);
            if (file == null)
                return Optional.empty();
            return Optional.ofNullable(parseInterview(Files.readAllLines(file)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** All interviews sorted by date descending (newest first). */
    public List<Interview> listAllInterviews() {
        try {
            Path dir = getInterviewsDir();
            List<Interview> result = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream
                        .filter(p -> p.getFileName().toString().endsWith(".md")
                                && !p.getFileName().toString().equals("prep-topics.md"))
                        .forEach(p -> {
                            try {
                                Interview iv = parseInterview(Files.readAllLines(p));
                                if (iv != null)
                                    result.add(iv);
                            } catch (Exception ignored) {
                            }
                        });
            }
            result.sort(Comparator.comparing(
                    iv -> iv.getDateTime() != null ? iv.getDateTime() : LocalDateTime.MIN,
                    Comparator.reverseOrder()));
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    public boolean deleteInterview(String interviewId) {
        try {
            Path file = findInterviewFile(interviewId);
            if (file != null) {
                Files.deleteIfExists(file);
                return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    // ================================================================
    // FORMATTING
    // ================================================================

    private String formatInterview(Interview iv) {
        StringBuilder sb = new StringBuilder();

        // Title
        sb.append("# ").append(iv.getCompany()).append(" \u2014 ").append(iv.getRole()).append("\n\n");

        // META comment
        sb.append("<!-- INTERVIEW_META: id=").append(iv.getId());
        sb.append(" company=").append(slug(iv.getCompany()));
        sb.append(" role=").append(slug(iv.getRole()));
        sb.append(" date=").append(iv.getDateTime() != null ? iv.getDateTime() : LocalDateTime.now());
        sb.append(" status=").append(iv.getStatus() != null ? iv.getStatus().name()
                : com.workctl.core.model.InterviewStatus.SCHEDULED.name());
        sb.append(" round=").append(iv.getRound() != null ? iv.getRound().name() : InterviewRound.TECHNICAL.name());
        sb.append(" result=").append(iv.getResult() != null ? iv.getResult().name() : InterviewResult.PENDING.name());
        if (iv.getJobUrl() != null)
            sb.append(" jobUrl=").append(iv.getJobUrl());
        sb.append(" created=").append(iv.getCreatedAt() != null ? iv.getCreatedAt() : LocalDate.now());
        sb.append(" -->\n");

        // Notes
        if (iv.getNotes() != null && !iv.getNotes().isBlank()) {
            sb.append("\n## Notes\n").append(iv.getNotes().trim()).append("\n");
        }

        // Experience Links
        if (iv.getExperienceLinks() != null && !iv.getExperienceLinks().isEmpty()) {
            sb.append("\n## Experience Links\n");
            for (ExperienceLink link : iv.getExperienceLinks()) {
                if (!link.getUrl().isBlank()) {
                    sb.append(link.toMarkdownLine()).append("\n");
                }
            }
        }

        // Questions grouped by section
        if (iv.getQuestions() != null && !iv.getQuestions().isEmpty()) {
            sb.append("\n## Questions\n");

            Map<String, List<InterviewQuestion>> bySection = new LinkedHashMap<>();
            for (InterviewQuestion q : iv.getQuestions()) {
                bySection.computeIfAbsent(q.getSection(), k -> new ArrayList<>()).add(q);
            }

            for (Map.Entry<String, List<InterviewQuestion>> entry : bySection.entrySet()) {
                sb.append("\n### ").append(entry.getKey()).append("\n");
                for (InterviewQuestion q : entry.getValue()) {
                    for (String line : q.toMarkdownLines()) {
                        sb.append(line).append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    // ================================================================
    // PARSING
    // ================================================================

    private Interview parseInterview(List<String> lines) {
        if (lines == null || lines.isEmpty())
            return null;

        Interview iv = null;
        String currentSection = null;
        boolean inExperienceLinks = false;
        boolean inQuestions = false;
        InterviewQuestion lastQuestion = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // ── Title ──────────────────────────────────────────────────
            if (line.startsWith("# ") && iv == null) {
                iv = new Interview("", "", LocalDateTime.now());
                continue;
            }

            // ── META comment ──────────────────────────────────────────
            if (line.contains("INTERVIEW_META:") && iv != null) {
                parseMetaLine(line, iv);
                continue;
            }

            if (iv == null)
                continue;

            // ── Top-level section headings ─────────────────────────────
            if (line.startsWith("## ")) {
                String heading = line.substring(3).trim();
                currentSection = null;
                lastQuestion = null;
                inExperienceLinks = false;
                inQuestions = false;

                if (heading.equals("Notes")) {
                    StringBuilder notes = new StringBuilder();
                    while (i + 1 < lines.size() && !lines.get(i + 1).startsWith("#")) {
                        i++;
                        if (!lines.get(i).isBlank())
                            notes.append(lines.get(i)).append("\n");
                    }
                    iv.setNotes(notes.toString().trim());

                } else if (heading.equals("Experience Links")) {
                    inExperienceLinks = true;

                } else if (heading.equals("Questions")) {
                    inQuestions = true;
                }
                continue;
            }

            // ── Experience Links items ────────────────────────────────
            if (inExperienceLinks && line.trim().startsWith("- [")) {
                ExperienceLink link = ExperienceLink.fromLine(line);
                if (link != null)
                    iv.getExperienceLinks().add(link);
                continue;
            }

            // ── Questions sub-section headings ────────────────────────
            if (inQuestions && line.startsWith("### ")) {
                currentSection = line.substring(4).trim();
                lastQuestion = null;
                continue;
            }

            // ── Question items ────────────────────────────────────────
            if (inQuestions && currentSection != null && line.trim().startsWith("- [")) {
                lastQuestion = InterviewQuestion.fromLine(currentSection, line);
                if (lastQuestion != null)
                    iv.getQuestions().add(lastQuestion);
                continue;
            }

            // ── Question url line ─────────────────────────────────────
            if (inQuestions && lastQuestion != null && line.trim().startsWith("url: ")) {
                lastQuestion.setUrl(line.trim().substring(5).trim());
                continue;
            }

            // ── Question notes line ───────────────────────────────────
            if (inQuestions && lastQuestion != null && line.trim().startsWith("> ")) {
                lastQuestion.setNotes(line.trim().substring(2).trim());
            }
        }

        return iv;
    }

    private void parseMetaLine(String line, Interview iv) {
        Matcher idM = Pattern.compile("id=([\\w-]+)").matcher(line);
        Matcher companyM = Pattern.compile("company=([^\\s>]+)").matcher(line);
        Matcher roleM = Pattern.compile("role=([^\\s>]+)").matcher(line);
        Matcher dateM = Pattern.compile("date=([^\\s>]+)").matcher(line);
        Matcher statusM = Pattern.compile("status=([A-Z]+)").matcher(line);
        Matcher roundM = Pattern.compile("round=([A-Z_]+)").matcher(line);
        Matcher resultM = Pattern.compile("result=([A-Z]+)").matcher(line);
        Matcher jobUrlM = Pattern.compile("jobUrl=(\\S+)").matcher(line);
        Matcher creatM = Pattern.compile("created=(\\d{4}-\\d{2}-\\d{2})").matcher(line);

        if (idM.find())
            iv.setId(idM.group(1));
        if (companyM.find())
            iv.setCompany(deslug(companyM.group(1)));
        if (roleM.find())
            iv.setRole(deslug(roleM.group(1)));
        if (dateM.find()) {
            try {
                String dStr = dateM.group(1);
                if (dStr.contains("T"))
                    iv.setDateTime(LocalDateTime.parse(dStr));
                else
                    iv.setDateTime(LocalDate.parse(dStr).atStartOfDay());
            } catch (Exception ignored) {
            }
        }
        if (statusM.find()) {
            try {
                iv.setStatus(com.workctl.core.model.InterviewStatus.valueOf(statusM.group(1)));
            } catch (Exception ignored) {
            }
        }
        if (roundM.find()) {
            try {
                iv.setRound(InterviewRound.valueOf(roundM.group(1)));
            } catch (Exception ignored) {
            }
        }
        if (resultM.find()) {
            try {
                iv.setResult(InterviewResult.valueOf(resultM.group(1)));
            } catch (Exception ignored) {
            }
        }
        if (jobUrlM.find()) {
            String url = jobUrlM.group(1);
            if (!url.startsWith("created="))
                iv.setJobUrl(url);
        }
        if (creatM.find()) {
            try {
                iv.setCreatedAt(LocalDate.parse(creatM.group(1)));
            } catch (Exception ignored) {
            }
        }
    }

    // ================================================================
    // FILE UTILITIES
    // ================================================================

    private Path getInterviewsDir() throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace()).resolve(DIR_NAME);
        Files.createDirectories(dir);
        return dir;
    }

    private Path buildInterviewFilePath(Interview iv) throws IOException {
        String date = iv.getDateTime() != null ? iv.getDateTime().toLocalDate().toString() : LocalDate.now().toString();
        String company = slug40(iv.getCompany());
        String role = slug40(iv.getRole());
        String name = date + "-" + company + "-" + role + ".md";
        return getInterviewsDir().resolve(name);
    }

    private Path findInterviewFile(String interviewId) {
        if (interviewId == null)
            return null;
        try {
            Path dir = getInterviewsDir();
            try (var stream = Files.list(dir)) {
                return stream
                        .filter(p -> p.getFileName().toString().endsWith(".md")
                                && !p.getFileName().toString().equals("prep-topics.md"))
                        .filter(p -> {
                            try {
                                return Files.readString(p).contains("id=" + interviewId);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String slug(String s) {
        if (s == null || s.isBlank())
            return "unknown";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String slug40(String s) {
        String full = slug(s);
        return full.length() > 40 ? full.substring(0, 40) : full;
    }

    private static String deslug(String s) {
        if (s == null)
            return "";
        return Arrays.stream(s.split("-"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
