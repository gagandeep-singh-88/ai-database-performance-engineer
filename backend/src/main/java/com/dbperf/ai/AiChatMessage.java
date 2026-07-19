package com.dbperf.ai;

/**
 * One prior turn of a copilot conversation, provider-agnostic.
 * Role is either USER or ASSISTANT; the latest user message is always
 * the last element of the history passed to the AI boundary.
 */
public record AiChatMessage(Role role, String content) {

    public enum Role { USER, ASSISTANT }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(Role.USER, content);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(Role.ASSISTANT, content);
    }
}
