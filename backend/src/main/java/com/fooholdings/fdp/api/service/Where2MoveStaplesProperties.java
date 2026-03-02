package com.fooholdings.fdp.api.service;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "where2move.staples")
public record Where2MoveStaplesProperties(List<String> upcs) {}