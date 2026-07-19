package com.dbperf.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured-output schema for a copilot chat turn. Markdown reply plus
 * suggested follow-up questions the UI renders as one-click chips.
 */
public record AiCopilotReply(
        @JsonPropertyDescription("The answer in GitHub-flavored Markdown. Use ```sql fences for any SQL. Reference actual tables, queries and numbers from the metrics context — never generic advice.")
        String reply,

        @JsonPropertyDescription("2-3 short follow-up questions the user is likely to ask next, phrased in the user's voice, e.g. 'Which index should I add first?'")
        List<String> suggestedFollowUps) {
}
