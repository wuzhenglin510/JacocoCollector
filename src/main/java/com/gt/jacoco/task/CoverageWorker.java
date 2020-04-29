package com.gt.jacoco.task;

import com.alibaba.fastjson.JSONObject;
import com.gt.jacoco.config.Config;
import com.gt.jacoco.entity.MethodReference;
import com.gt.jacoco.entity.RegisterInfo;
import com.gt.jacoco.entity.RegisterRequest;
import com.gt.jacoco.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.io.XMLWriter;
import org.eclipse.jgit.diff.DiffEntry;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gt.jacoco.utils.JacocoXmlUtils.recountCoverage;

@EnableScheduling
@Component
@Slf4j
public class CoverageWorker {

    private static final Map<String, RegisterInfo> REGISTER_TABLE = new HashMap<>();

    @Autowired
    private JacocoAgentUtils jacocoAgentUtils;

    @Autowired
    private GitUtils gitUtils;

    @Autowired
    private ASTUtils astUtils;

    @Autowired
    private Config config;

    public RegisterInfo register(RegisterRequest registerRequest) {
        RegisterInfo info = new RegisterInfo();
        BeanUtils.copyProperties(registerRequest, info);
        info.setGitDir(Paths.get(config.getGitDir(), registerRequest.getApplicationName()).toString());
        REGISTER_TABLE.put(info.getApplicationName(), info);
        return info;
    }

    @Scheduled(fixedDelay = 10000)
    public void handler() {
        log.info("任务调度: {}", REGISTER_TABLE.keySet());
        for (String applicationName : REGISTER_TABLE.keySet()) {
            RegisterInfo registerInfo = REGISTER_TABLE.get(applicationName);
            try {
                jacocoAgentUtils.fetchData(registerInfo);
                renameLatestXml(registerInfo.getApplicationName());

                String newXmlFile = Paths.get(config.getXmlDataDir(), registerInfo.getApplicationName() + ".xml").toString();
                String oldFilePath = Paths.get(config.getXmlDataDir(), applicationName + "_old.xml").toString();

                jacocoAgentUtils.generateXmlReport(registerInfo);
                Document document = JacocoXmlUtils.loadFile(newXmlFile);
                List<DiffEntry> differenceFiles = gitUtils.findDifferenceFiles(registerInfo.getGitDir(), registerInfo.getOldBranch(), registerInfo.getNewBranch());
                Map<String, List<MethodReference>> multiJavaFilesMethodChanged = astUtils.findMultiJavaFilesMethodChanged(registerInfo.getGitDir(), differenceFiles,  registerInfo.getOldBranch(), registerInfo.getNewBranch());
                JacocoXmlUtils.refactorJacocoXml(document, multiJavaFilesMethodChanged);
                File oldFile = new File(oldFilePath);
                if (oldFile.exists()) {
                    Document oldXml = JacocoXmlUtils.loadFile(oldFilePath);
                    JacocoXmlUtils.merge(oldXml, document);
                    recountCoverage(document);
                }
                try (FileWriter fileWriter = new FileWriter(newXmlFile)) {
                    XMLWriter writer = new XMLWriter(fileWriter);
                    writer.write(document);
                    writer.close();
                }
                Process exec = Runtime.getRuntime().exec(new String[]{
                        "/bin/sh", "-c", String.format("cd %s && mvn sonar:sonar -Dsonar.coverage.jacoco.xmlReportPaths=%s", registerInfo.getGitDir(), newXmlFile)
                });
                CommonUtils.printShellOutput(exec);
            } catch (Exception e) {
                registerInfo.setFailTimes(registerInfo.getFailTimes() + 1);
                REGISTER_TABLE.put(registerInfo.getApplicationName(), registerInfo);
                log.error("无法访问服务: {}", JSONObject.toJSONString(registerInfo));
                e.printStackTrace();
            }
        }
    }

    private void renameLatestXml(String applicationName) {
        String newXmlFile = Paths.get(config.getXmlDataDir(), applicationName + ".xml").toString();
        File latestFile = new File(newXmlFile);
        if (latestFile.exists()) {
            String oldFilePath = Paths.get(config.getXmlDataDir(), applicationName + "_old.xml").toString();
            File oldFile = new File(oldFilePath);
            if (oldFile.exists()) {
                oldFile.delete();
            }
            latestFile.renameTo(new File(oldFilePath));
        }
    }






}
