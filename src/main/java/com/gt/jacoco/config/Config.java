package com.gt.jacoco.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "system")
@Component
@Data
public class Config {

    private String mavenSettingsPath;

    private String execDataDir;

    private String xmlDataDir;

    private String gitDir;

    private String gitAccount;

    private String gitPassword;

}
