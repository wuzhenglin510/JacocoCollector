package com.gt.jacoco.utils;


import com.gt.jacoco.config.Config;
import com.gt.jacoco.entity.RegisterInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.xml.XMLFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Paths;

@Slf4j
@Component
public class JacocoAgentUtils {

    @Autowired
    private Config config;

    @Autowired
    private GitUtils gitUtils;

    public void fetchData(RegisterInfo registerInfo) throws IOException {
        final FileOutputStream localFile = new FileOutputStream(Paths.get(config.getExecDataDir(), registerInfo.getApplicationName() + ".exec").toString());
        final ExecutionDataWriter localWriter = new ExecutionDataWriter(localFile);
        final Socket socket = new Socket(InetAddress.getByName(registerInfo.getHost()), registerInfo.getPort());
        final RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
        final RemoteControlReader reader = new RemoteControlReader(socket.getInputStream());
        reader.setSessionInfoVisitor(localWriter);
        reader.setExecutionDataVisitor(localWriter);
        writer.visitDumpCommand(true, false);
        if (!reader.read()) {
            throw new IOException("Socket closed unexpectedly.");
        }
        socket.close();
        localFile.close();
    }

    public String generateXmlReport(RegisterInfo registerInfo) throws IOException, GitAPIException, InterruptedException {
        File applicationDir = new File(config.getGitDir(), registerInfo.getApplicationName());
        if (!applicationDir.exists()) {
            gitUtils.cloneRepository(registerInfo);
        }
        gitUtils.checkoutBranch(registerInfo, registerInfo.getOldBranch());
        gitUtils.pull(registerInfo, registerInfo.getOldBranch());
        gitUtils.checkoutBranch(registerInfo, registerInfo.getNewBranch());
        gitUtils.pull(registerInfo, registerInfo.getNewBranch());

        Process exec = Runtime.getRuntime().exec(new String[]{
                "/bin/sh", "-c", String.format("cd %s && mvn --settings %s compile", registerInfo.getGitDir(), config.getMavenSettingsPath())
        });
        CommonUtils.printShellOutput(exec);

        ExecFileLoader execFileLoader = new ExecFileLoader();
        File executionDataFile = new File(config.getExecDataDir(), registerInfo.getApplicationName() + ".exec");
        execFileLoader.load(executionDataFile);
        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
        analyzer.analyzeAll(new File(registerInfo.getGitDir()));
        final IBundleCoverage bundleCoverage = coverageBuilder.getBundle(registerInfo.getApplicationName());
        final XMLFormatter xmlFormatter = new XMLFormatter();
        final IReportVisitor visitor = xmlFormatter.createVisitor(new FileOutputStream(new File(config.getXmlDataDir(), registerInfo.getApplicationName() + ".xml")));
        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(), execFileLoader.getExecutionDataStore().getContents());
        visitor.visitBundle(bundleCoverage, new DirectorySourceFileLocator(new File(registerInfo.getGitDir(), "src/main/java"), "utf-8", 4));
        visitor.visitEnd();
        return Paths.get(config.getXmlDataDir(), registerInfo.getApplicationName() + ".xml").toString();
    }

}