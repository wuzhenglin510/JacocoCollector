package com.gt.jacoco.entity;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RegisterRequest {

    @NotNull
    private String host = "localhost";

    @NotNull
    private Integer port = 6300;

    @NotNull
    private String applicationName;

    @NotNull
    private String gitRepositoryUrl;

    private String oldBranch = "master";

    private String oldBranchCommitId;

    private String newBranch = "pre";

    private String newBranchCommitId;

}
