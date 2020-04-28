package com.gt.jacoco.utils;


import com.gt.jacoco.entity.MethodReference;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class UtilsTest {

    @Autowired
    private GitUtils gitUtils;

    @Autowired
    private ASTUtils astUtils;

    @Test
    public void textFindDifferenceFiles() {
        List<DiffEntry> differenceFiles = gitUtils.findDifferenceFiles("/Users/leo/projects/redisson_demo", "pre", "master");
        if (!differenceFiles.isEmpty()) {
            for (DiffEntry diffEntry : differenceFiles) {
                log.info("修改文件路径: {}  ------  改动类型: {}", diffEntry.getNewPath(), diffEntry.getChangeType());
            }
        }
        assert !differenceFiles.isEmpty() : "对比分支需要有变动的文件";
    }

    @Test
    public void testFindChangeClass() {
        List<DiffEntry> differenceFiles = gitUtils.findDifferenceFiles("/Users/leo/projects/redisson_demo", "pre", "master");
        assert !differenceFiles.isEmpty() : "对比分支需要有变动的文件";
        for (DiffEntry diffEntry : differenceFiles) {
            String oldContent = gitUtils.getFile("/Users/leo/projects/redisson_demo", diffEntry.getNewPath(), "master");
            String newContent = gitUtils.getFile("/Users/leo/projects/redisson_demo", diffEntry.getNewPath(), "pre");
            Map<String, List<MethodReference>> singleJavaFileMethodChanged = astUtils.findSingleJavaFileMethodChanged(oldContent, newContent);
            assert !singleJavaFileMethodChanged.keySet().isEmpty() : "需要有文件变动";
            for (String className : singleJavaFileMethodChanged.keySet()) {
                log.info("修改的class文件: {}", className);
                for (MethodReference methodReference : singleJavaFileMethodChanged.get(className)) {
                    log.info("修改了方法: {}", methodReference.getMethodNameWithParams());
                }
            }
        }
    }

    @Test
    public void testJacocoXmlFilter() {
        long start = System.currentTimeMillis();
        String oldBranch = "master";
        String newBranch = "pre";
        Document document = JacocoXmlUtils.loadFile("/Users/leo/share/jacoco1_new.xml");
        log.info("load document 耗时：" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        List<DiffEntry> differenceFiles = gitUtils.findDifferenceFiles("/Users/leo/projects/redisson_demo", oldBranch, newBranch);
        log.info("git diff 耗时：" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        Map<String, List<MethodReference>> multiJavaFilesMethodChanged = astUtils.findMultiJavaFilesMethodChanged("/Users/leo/projects/redisson_demo", differenceFiles, oldBranch, newBranch);
        log.info("ast diff 耗时：" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        JacocoXmlUtils.refactorJacocoXml(document, multiJavaFilesMethodChanged);
        log.info("rebuild xml 耗时：" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        log.info(document.asXML());
        log.info("输出 xml 耗时：" + (System.currentTimeMillis() - start));
    }

    @Test
    public void testJacocoXmlMerge() {
        long start = System.currentTimeMillis();
        Document oldXml = JacocoXmlUtils.loadFile("/Users/leo/share/jacoco1_old.xml");
        Document newXml = JacocoXmlUtils.loadFile("/Users/leo/share/jacoco1_new.xml");
        JacocoXmlUtils.merge(oldXml, newXml);
        log.info("合并 xml 耗时：" + (System.currentTimeMillis() - start));
        log.info(newXml.asXML());
    }


}