package com.dbperf.settings.dto;

public record AboutResponse(
        String appName,
        String appVersion,
        String buildVersion,
        String javaVersion,
        String springBootVersion,
        String aiProvider,
        String aiModel,
        String cloudPlatform,
        Links links) {

    public record Links(String support, String documentation, String license) {
    }
}
