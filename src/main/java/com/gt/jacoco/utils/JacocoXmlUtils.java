package com.gt.jacoco.utils;

import com.gt.jacoco.entity.MethodReference;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JacocoXmlUtils {

    /**
     * 重构jacoco统计文档，标记那些没有改动的统计节点，没有改动的节点设置覆盖率为100%，并且重新计算覆盖统计数据
     *
     * @param document
     * @param classMethodChanged
     */
    public static void refactorJacocoXml(Document document, Map<String, List<MethodReference>> classMethodChanged) {
        processForPackage(document, classMethodChanged);
        setAllMethodEndLine(document);
        processForUnchangedLine(document);
    }

    /**
     * 合并两个jacoco xml覆盖率数据
     * 说明：
     * 1、如果新的class节点在旧的存在， 则新的、相同的method节点将替换老的， 但sourcefile会按照一定的逻辑合并，而不是简单替换。
     * 2、如果新的class节点在旧但不存在， 则整个class节点插入
     *
     * @param oldXml
     * @param newXml
     */
    public static void merge(Document oldXml, Document newXml) {
        List<Node> newChangeMethodNodes = newXml.selectNodes("//method[@mark='changed']");
        for (Node newChangeMethodNode : newChangeMethodNodes) {
            Element newChangeMethodElement = (Element) newChangeMethodNode;
            Element newClassNode = newChangeMethodNode.getParent();
            String className = newClassNode.attributeValue("name");
            Node oldClassNode = oldXml.selectSingleNode(String.format("//class[@name='%s']", className));
            if (oldClassNode != null) {
                Element oldClassElement = (Element) oldClassNode;
                Node oldNode = oldClassNode.selectSingleNode(String.format("//method[@name='%s' and @desc='%s' and @hash='%s']"
                        , newChangeMethodElement.attributeValue("name")
                        , newChangeMethodElement.attributeValue("desc")
                        , newChangeMethodElement.attributeValue("hash"))
                );
                if (oldNode != null) {
                    Element oldElement = (Element) oldNode;
                    Element newPackageNode = newClassNode.getParent();
                    Node newSourceFileNode = newPackageNode.selectSingleNode(String.format("sourcefile[@name='%s']", newClassNode.attributeValue("sourcefilename")));
                    Element oldPackageNode = oldClassElement.getParent();
                    Node oldSourceFileNode = oldPackageNode.selectSingleNode(String.format("sourcefile[@name='%s']", oldClassElement.attributeValue("sourcefilename")));

                    int newStartLine = Integer.parseInt(newChangeMethodElement.attributeValue("line"));
                    int newEndLine = Integer.parseInt(newChangeMethodElement.attributeValue("endLine"));
                    int oldStartLine = Integer.parseInt(oldElement.attributeValue("line"));
                    int oldEndLine = Integer.parseInt(oldElement.attributeValue("endLine"));

                    List<Node> newLines = newSourceFileNode.selectNodes("line");
                    List<Node> oldLines = oldSourceFileNode.selectNodes("line");

                    int newLineOffset = findLineIndex(newStartLine, newLines);
                    int oldLineOffset = findLineIndex(oldStartLine, oldLines);

                    Element newLine = (Element) newLines.get(newLineOffset);
                    Element oldLine = (Element) oldLines.get(oldLineOffset);

                    int miTotal = 0;
                    int ciTotal = 0;
                    int mbTotal = 0;
                    int cbTotal = 0;
                    while (newStartLine <= newEndLine && oldStartLine <= oldEndLine && newLineOffset < newLines.size() - 1 && oldLineOffset < oldLines.size() - 1) {
                        int nmi = Integer.parseInt(newLine.attributeValue("mi"));
                        int omi = Integer.parseInt(oldLine.attributeValue("mi"));
                        int nci = Integer.parseInt(newLine.attributeValue("ci"));
                        int oci = Integer.parseInt(oldLine.attributeValue("ci"));
                        int nmb = Integer.parseInt(newLine.attributeValue("mb"));
                        int omb = Integer.parseInt(oldLine.attributeValue("mb"));
                        int ncb = Integer.parseInt(newLine.attributeValue("cb"));
                        int ocb = Integer.parseInt(oldLine.attributeValue("cb"));
                        newLine.attribute("mi").setValue(nmi < omi ? String.valueOf(nmi) : String.valueOf(omi));
                        newLine.attribute("ci").setValue(nci > oci ? String.valueOf(nci) : String.valueOf(oci));
                        newLine.attribute("mb").setValue(nmb < omb ? String.valueOf(nmb) : String.valueOf(omb));
                        newLine.attribute("cb").setValue(ncb > ocb ? String.valueOf(ncb) : String.valueOf(ocb));
                        newLine.addAttribute("mark", "changed");
                        newLineOffset++;
                        oldLineOffset++;
                        newLine = (Element) newLines.get(newLineOffset);
                        oldLine = (Element) oldLines.get(oldLineOffset);
                        newStartLine = Integer.parseInt(newLine.attributeValue("nr"));
                        oldStartLine = Integer.parseInt(oldLine.attributeValue("nr"));
                        miTotal += Integer.parseInt(newLine.attributeValue("mi"));
                        ciTotal += Integer.parseInt(newLine.attributeValue("ci"));
                        mbTotal += Integer.parseInt(newLine.attributeValue("mb"));
                        cbTotal += Integer.parseInt(newLine.attributeValue("cb"));
                    }
                    Element instructionCountNode = (Element) newChangeMethodNode.selectSingleNode("counter[@type='INSTRUCTION']");
                    if (instructionCountNode != null) {
                        instructionCountNode.attribute("missed").setValue(String.valueOf(miTotal));
                        instructionCountNode.attribute("covered").setValue(String.valueOf(ciTotal));
                    }
                    Element branchCountNode = (Element) newChangeMethodNode.selectSingleNode("counter[@type='BRANCH']");
                    if (branchCountNode != null) {
                        branchCountNode.attribute("missed").setValue(String.valueOf(mbTotal));
                        branchCountNode.attribute("covered").setValue(String.valueOf(cbTotal));
                    }
                }
            }
        }
    }


    private static int findLineIndex(int lineNumber, List<Node> nodes) {
        for (int idx = 0; idx < nodes.size(); idx++) {
            Element element = (Element) nodes.get(idx);
            if (Integer.parseInt(element.attributeValue("nr")) == lineNumber) {
                return idx;
            }
        }
        return -1;
    }


    /**
     * 加载xml Dom
     *
     * @param fileName
     * @return
     */
    public static Document loadFile(String fileName) {
        try (FileReader fileReader = new FileReader(new File(fileName))) {
            SAXReader reader = new SAXReader();
            return reader.read(fileReader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 重新计算覆盖率统计数据
     *
     * @param document
     */
    public static void recountCoverage(Document document) {
        Node report = document.selectSingleNode("report");
        Element reportInstructionCountNode = (Element) report.selectSingleNode("counter[@type='INSTRUCTION']");
        Element reportBranchCountNode = (Element) report.selectSingleNode("counter[@type='BRANCH']");
        Element reportLineCountNode = (Element) report.selectSingleNode("counter[@type='LINE']");
        Element reportComplexityCountNode = (Element) report.selectSingleNode("counter[@type='COMPLEXITY']");
        Element reportMethodCountNode = (Element) report.selectSingleNode("counter[@type='METHOD']");
        Element reportClassCountNode = (Element) report.selectSingleNode("counter[@type='CLASS']");
        //set root report coverage count to zero
        if (reportInstructionCountNode != null) {
            reportInstructionCountNode.attribute("missed").setValue("0");
            reportInstructionCountNode.attribute("covered").setValue("0");
        }
        if (reportBranchCountNode != null) {
            reportBranchCountNode.attribute("missed").setValue("0");
            reportBranchCountNode.attribute("covered").setValue("0");
        }
        if (reportLineCountNode != null) {
            reportLineCountNode.attribute("missed").setValue("0");
            reportLineCountNode.attribute("covered").setValue("0");
        }
        if (reportComplexityCountNode != null) {
            reportComplexityCountNode.attribute("missed").setValue("0");
            reportComplexityCountNode.attribute("covered").setValue("0");
        }
        if (reportMethodCountNode != null) {
            reportMethodCountNode.attribute("missed").setValue("0");
            reportMethodCountNode.attribute("covered").setValue("0");
        }
        if (reportClassCountNode != null) {
            reportClassCountNode.attribute("missed").setValue("0");
            reportClassCountNode.attribute("covered").setValue("0");
        }
        List<Node> packageNodes = report.selectNodes("package");
        for (Node packageNode : packageNodes) {
            Element packageInstructionCountNode = (Element) packageNode.selectSingleNode("counter[@type='INSTRUCTION']");
            Element packageBranchCountNode = (Element) packageNode.selectSingleNode("counter[@type='BRANCH']");
            Element packageLineCountNode = (Element) packageNode.selectSingleNode("counter[@type='LINE']");
            Element packageComplexityCountNode = (Element) packageNode.selectSingleNode("counter[@type='COMPLEXITY']");
            Element packageMethodCountNode = (Element) packageNode.selectSingleNode("counter[@type='METHOD']");
            Element packageClassCountNode = (Element) packageNode.selectSingleNode("counter[@type='CLASS']");
            //set package coverage count to zero
            if (packageInstructionCountNode != null) {
                packageInstructionCountNode.attribute("missed").setValue("0");
                packageInstructionCountNode.attribute("covered").setValue("0");
            }
            if (packageBranchCountNode != null) {
                packageBranchCountNode.attribute("missed").setValue("0");
                packageBranchCountNode.attribute("covered").setValue("0");
            }
            if (packageLineCountNode != null) {
                packageLineCountNode.attribute("missed").setValue("0");
                packageLineCountNode.attribute("covered").setValue("0");
            }
            if (packageComplexityCountNode != null) {
                packageComplexityCountNode.attribute("missed").setValue("0");
                packageComplexityCountNode.attribute("covered").setValue("0");
            }
            if (packageMethodCountNode != null) {
                packageMethodCountNode.attribute("missed").setValue("0");
                packageMethodCountNode.attribute("covered").setValue("0");
            }
            if (packageClassCountNode != null) {
                packageClassCountNode.attribute("missed").setValue("0");
                packageClassCountNode.attribute("covered").setValue("0");
            }
            List<Node> classNodes = packageNode.selectNodes("class");
            for (Node classNode : classNodes) {
                //set class coverage count to zero
                Element classInstructionCountNode = (Element) classNode.selectSingleNode("counter[@type='INSTRUCTION']");
                Element classBranchCountNode = (Element) classNode.selectSingleNode("counter[@type='BRANCH']");
                Element classLineCountNode = (Element) classNode.selectSingleNode("counter[@type='LINE']");
                Element classComplexityCountNode = (Element) classNode.selectSingleNode("counter[@type='COMPLEXITY']");
                Element classMethodCountNode = (Element) classNode.selectSingleNode("counter[@type='METHOD']");
                Element classClassCountNode = (Element) classNode.selectSingleNode("counter[@type='CLASS']");
                if (classInstructionCountNode != null) {
                    classInstructionCountNode.attribute("missed").setValue("0");
                    classInstructionCountNode.attribute("covered").setValue("0");
                }
                if (classBranchCountNode != null) {
                    classBranchCountNode.attribute("missed").setValue("0");
                    classBranchCountNode.attribute("covered").setValue("0");
                }
                if (classLineCountNode != null) {
                    classLineCountNode.attribute("missed").setValue("0");
                    classLineCountNode.attribute("covered").setValue("0");
                }
                if (classComplexityCountNode != null) {
                    classComplexityCountNode.attribute("missed").setValue("0");
                    classComplexityCountNode.attribute("covered").setValue("0");
                }
                if (classMethodCountNode != null) {
                    classMethodCountNode.attribute("missed").setValue("0");
                    classMethodCountNode.attribute("covered").setValue("0");
                }
                //recount class coverage
                List<Node> methodNodes = classNode.selectNodes("method");
                for (Node methodNode : methodNodes) {
                    Element subInstructionCountNode = (Element) methodNode.selectSingleNode("counter[@type='INSTRUCTION']");
                    Element subBranchCountNode = (Element) methodNode.selectSingleNode("counter[@type='BRANCH']");
                    Element subLineCountNode = (Element) methodNode.selectSingleNode("counter[@type='LINE']");
                    Element subComplexityCountNode = (Element) methodNode.selectSingleNode("counter[@type='COMPLEXITY']");
                    Element subMethodCountNode = (Element) methodNode.selectSingleNode("counter[@type='METHOD']");
                    if (classInstructionCountNode != null && subInstructionCountNode != null) {
                        classInstructionCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(classInstructionCountNode.attributeValue("missed")) + Integer.parseInt(subInstructionCountNode.attributeValue("missed"))));
                        classInstructionCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(classInstructionCountNode.attributeValue("covered")) + Integer.parseInt(subInstructionCountNode.attributeValue("covered"))));
                    }
                    if (classBranchCountNode != null && subBranchCountNode != null) {
                        classBranchCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(classBranchCountNode.attributeValue("missed")) + Integer.parseInt(subBranchCountNode.attributeValue("missed"))));
                        classBranchCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(classBranchCountNode.attributeValue("covered")) + Integer.parseInt(subBranchCountNode.attributeValue("covered"))));
                    }
                    if (classLineCountNode != null && subLineCountNode != null) {
                        classLineCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(classLineCountNode.attributeValue("missed")) + Integer.parseInt(subLineCountNode.attributeValue("missed"))));
                        classLineCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(classLineCountNode.attributeValue("covered")) + Integer.parseInt(subLineCountNode.attributeValue("covered"))));
                    }
                    if (classComplexityCountNode != null && subComplexityCountNode != null) {
                        classComplexityCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(classComplexityCountNode.attributeValue("missed")) + Integer.parseInt(subComplexityCountNode.attributeValue("missed"))));
                        classComplexityCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(classComplexityCountNode.attributeValue("covered")) + Integer.parseInt(subComplexityCountNode.attributeValue("covered"))));
                    }
                    if (classMethodCountNode != null && subMethodCountNode != null) {
                        classMethodCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(classMethodCountNode.attributeValue("missed")) + Integer.parseInt(subMethodCountNode.attributeValue("missed"))));
                        classMethodCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(classMethodCountNode.attributeValue("covered")) + Integer.parseInt(subMethodCountNode.attributeValue("covered"))));
                    }
                }
                if (packageInstructionCountNode != null && classInstructionCountNode != null) {
                    packageInstructionCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageInstructionCountNode.attributeValue("missed")) + Integer.parseInt(classInstructionCountNode.attributeValue("missed"))));
                    packageInstructionCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageInstructionCountNode.attributeValue("covered")) + Integer.parseInt(classInstructionCountNode.attributeValue("covered"))));
                }
                if (packageBranchCountNode != null && classBranchCountNode != null) {
                    packageBranchCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageBranchCountNode.attributeValue("missed")) + Integer.parseInt(classBranchCountNode.attributeValue("missed"))));
                    packageBranchCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageBranchCountNode.attributeValue("covered")) + Integer.parseInt(classBranchCountNode.attributeValue("covered"))));
                }
                if (packageLineCountNode != null && classLineCountNode != null) {
                    packageLineCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageLineCountNode.attributeValue("missed")) + Integer.parseInt(classLineCountNode.attributeValue("missed"))));
                    packageLineCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageLineCountNode.attributeValue("covered")) + Integer.parseInt(classLineCountNode.attributeValue("covered"))));
                }
                if (packageComplexityCountNode != null && classComplexityCountNode != null) {
                    packageComplexityCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageComplexityCountNode.attributeValue("missed")) + Integer.parseInt(classComplexityCountNode.attributeValue("missed"))));
                    packageComplexityCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageComplexityCountNode.attributeValue("covered")) + Integer.parseInt(classComplexityCountNode.attributeValue("covered"))));
                }
                if (packageMethodCountNode != null && classMethodCountNode != null) {
                    packageMethodCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageMethodCountNode.attributeValue("missed")) + Integer.parseInt(classMethodCountNode.attributeValue("missed"))));
                    packageMethodCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageMethodCountNode.attributeValue("covered")) + Integer.parseInt(classMethodCountNode.attributeValue("covered"))));
                }
                if (packageClassCountNode != null && classClassCountNode != null) {
                    packageClassCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(packageClassCountNode.attributeValue("missed")) + Integer.parseInt(classClassCountNode.attributeValue("missed"))));
                    packageClassCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(packageClassCountNode.attributeValue("covered")) + Integer.parseInt(classClassCountNode.attributeValue("covered"))));
                }
            }
            if (reportInstructionCountNode != null && packageInstructionCountNode != null) {
                reportInstructionCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportInstructionCountNode.attributeValue("missed")) + Integer.parseInt(packageInstructionCountNode.attributeValue("missed"))));
                reportInstructionCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportInstructionCountNode.attributeValue("covered")) + Integer.parseInt(packageInstructionCountNode.attributeValue("covered"))));
            }
            if (reportBranchCountNode != null && packageBranchCountNode != null) {
                reportBranchCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportBranchCountNode.attributeValue("missed")) + Integer.parseInt(packageBranchCountNode.attributeValue("missed"))));
                reportBranchCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportBranchCountNode.attributeValue("covered")) + Integer.parseInt(packageBranchCountNode.attributeValue("covered"))));
            }
            if (reportLineCountNode != null && packageLineCountNode != null) {
                reportLineCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportLineCountNode.attributeValue("missed")) + Integer.parseInt(packageLineCountNode.attributeValue("missed"))));
                reportLineCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportLineCountNode.attributeValue("covered")) + Integer.parseInt(packageLineCountNode.attributeValue("covered"))));
            }
            if (reportComplexityCountNode != null && packageComplexityCountNode != null) {
                reportComplexityCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportComplexityCountNode.attributeValue("missed")) + Integer.parseInt(packageComplexityCountNode.attributeValue("missed"))));
                reportComplexityCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportComplexityCountNode.attributeValue("covered")) + Integer.parseInt(packageComplexityCountNode.attributeValue("covered"))));
            }
            if (reportMethodCountNode != null && packageMethodCountNode != null) {
                reportMethodCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportMethodCountNode.attributeValue("missed")) + Integer.parseInt(packageMethodCountNode.attributeValue("missed"))));
                reportMethodCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportMethodCountNode.attributeValue("covered")) + Integer.parseInt(packageMethodCountNode.attributeValue("covered"))));
            }
            if (reportClassCountNode != null && packageClassCountNode != null) {
                reportClassCountNode.attribute("missed").setValue(String.valueOf(Integer.parseInt(reportClassCountNode.attributeValue("missed")) + Integer.parseInt(packageClassCountNode.attributeValue("missed"))));
                reportClassCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(reportClassCountNode.attributeValue("covered")) + Integer.parseInt(packageClassCountNode.attributeValue("covered"))));
            }
        }

    }

    /**
     * 将被标记为为改动的 method 节点全部改成100%覆盖
     *
     * @param node
     */
    private static void setUnchangedNode100CoverageStepInMethodRecursive(Node node) {
        switch (node.getName()) {
            case "package": {
                List<Node> classNodes = node.selectNodes("class");
                for (Node classNode : classNodes) {
                    setUnchangedNode100CoverageStepInMethodRecursive(classNode);
                }
                break;
            }
            case "class": {
                List<Node> methodNodes = node.selectNodes("method");
                for (Node methodNode : methodNodes) {
                    setUnchangedNode100CoverageStepInMethodRecursive(methodNode);
                }
                break;
            }
            case "method": {
                Element instructionCountNode = (Element) node.selectSingleNode("counter[@type='INSTRUCTION']");
                Element branchCountNode = (Element) node.selectSingleNode("counter[@type='BRANCH']");
                Element lineCountNode = (Element) node.selectSingleNode("counter[@type='LINE']");
                Element complexityCountNode = (Element) node.selectSingleNode("counter[@type='COMPLEXITY']");
                Element methodCountNode = (Element) node.selectSingleNode("counter[@type='METHOD']");
                if (instructionCountNode != null) {
                    instructionCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(instructionCountNode.attributeValue("covered")) + Integer.parseInt(instructionCountNode.attributeValue("missed"))));
                    instructionCountNode.attribute("missed").setValue("0");
                }
                if (branchCountNode != null) {
                    branchCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(branchCountNode.attributeValue("covered")) + Integer.parseInt(branchCountNode.attributeValue("missed"))));
                    branchCountNode.attribute("missed").setValue("0");
                }
                if (lineCountNode != null) {
                    lineCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(lineCountNode.attributeValue("covered")) + Integer.parseInt(lineCountNode.attributeValue("missed"))));
                    lineCountNode.attribute("missed").setValue("0");
                }
                if (complexityCountNode != null) {
                    complexityCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(complexityCountNode.attributeValue("covered")) + Integer.parseInt(complexityCountNode.attributeValue("missed"))));
                    complexityCountNode.attribute("missed").setValue("0");
                }
                if (methodCountNode != null) {
                    methodCountNode.attribute("covered").setValue(String.valueOf(Integer.parseInt(methodCountNode.attributeValue("covered")) + Integer.parseInt(methodCountNode.attributeValue("missed"))));
                    methodCountNode.attribute("missed").setValue("0");
                }
                break;
            }
            default: {
            }
        }
    }

    /**
     * 标记没改动的package节点
     *
     * @param document
     * @param classMethodChanged
     */
    private static void processForPackage(Document document, Map<String, List<MethodReference>> classMethodChanged) {
        Set<String> shouldRemainPackage = classMethodChanged.keySet()
                .stream()
                .map(className -> className.substring(0, className.lastIndexOf("/")))
                .collect(Collectors.toSet());
        List<Node> packageNodes = document.selectNodes("report/package");
        for (Node packageNode : packageNodes) {
            String packageName = packageNode.valueOf("@name");
            if (packageName != null) {
                if (!shouldRemainPackage.contains(packageName)) {
                    ((Element) packageNode).addAttribute("mark", "unchanged");
                    setUnchangedNode100CoverageStepInMethodRecursive(packageNode);
                } else {
                    processForClass(packageNode, classMethodChanged);
                }
            }
        }
    }

    private static void processForClass(Node packageNode, Map<String, List<MethodReference>> classMethodChanged) {
        Set<String> shouldRemainClass = classMethodChanged.keySet();
        List<Node> classNodes = packageNode.selectNodes("class");
        for (Node classNode : classNodes) {
            String className = classNode.valueOf("@name");
            if (!shouldRemainClass.contains(className)) {
                ((Element) classNode).addAttribute("mark", "unchanged");
                setUnchangedNode100CoverageStepInMethodRecursive(classNode);
            } else {
                processForMethod(classNode, classMethodChanged.get(className));
            }
        }
    }


    /**
     * 标记没改动的method节点，并且对改动过的 method 节点设置body hash
     *
     * @param classNode
     * @param methodChangedList
     */
    private static void processForMethod(Node classNode, List<MethodReference> methodChangedList) {
        Set<String> changedMethodNames = methodChangedList.stream().map(MethodReference::getMethodNameWithParams).collect(Collectors.toSet());
        Map<String, MethodReference> map = new HashMap<>();
        for (MethodReference methodReference : methodChangedList) {
            map.put(methodReference.getMethodNameWithParams(), methodReference);
        }
        List<Node> methodNodes = classNode.selectNodes("method");
        for (Node methodNode : methodNodes) {
            String methodName = methodNode.valueOf("@name") + methodNode.valueOf("@desc");
            boolean isThisMethodChange = false;
            for (String changedMethodName : changedMethodNames) {
                if (methodName.startsWith(changedMethodName)) {
                    isThisMethodChange = true;
                    Element methodElement = (Element) methodNode;
                    methodElement.addAttribute("hash", map.get(changedMethodName).getMethodBodyHash());
                    methodElement.addAttribute("mark", "changed");
                    Element packageNode = methodNode.getParent().getParent();
                    packageNode.addAttribute("mark", "changed");
                    break;
                }
            }
            if (!isThisMethodChange) {
                ((Element) methodNode).addAttribute("mark", "unchanged");
                setUnchangedNode100CoverageStepInMethodRecursive(methodNode);
            }
        }
    }


    private static void processForUnchangedLine(Document xml) {
        List<Node> unChangeMethodNodes = xml.selectNodes("//method[@mark='unchanged']");
        for (Node node : unChangeMethodNodes) {
            Element methodElement = (Element) node;
            Element newClassNode = node.getParent();
            Element newPackageNode = newClassNode.getParent();

            int startLine = Integer.parseInt(methodElement.attributeValue("line"));
            int endLine = Integer.parseInt(methodElement.attributeValue("endLine"));

            Node newSourceFileNode = newPackageNode.selectSingleNode(String.format("sourcefile[@name='%s']", newClassNode.attributeValue("sourcefilename")));

            List<Node> lines = newSourceFileNode.selectNodes("line");
            int lineOffset = findLineIndex(startLine, lines);
            Element line = (Element) lines.get(lineOffset);

            while (startLine <= endLine && lineOffset < lines.size() - 1) {
                int mi = Integer.parseInt(line.attributeValue("mi"));
                int ci = Integer.parseInt(line.attributeValue("ci"));
                int mb = Integer.parseInt(line.attributeValue("mb"));
                int cb = Integer.parseInt(line.attributeValue("cb"));
                line.attribute("mi").setValue("0");
                line.attribute("ci").setValue(String.valueOf(mi + ci));
                line.attribute("mb").setValue("0");
                line.attribute("cb").setValue(String.valueOf(mb + cb));
                line.addAttribute("mark", "unchanged");
                lineOffset++;
                line = (Element) lines.get(lineOffset);
                startLine = Integer.parseInt(line.attributeValue("nr"));
            }
        }

    }

    private static void setAllMethodEndLine(Document xml) {
        List<Node> nodes = xml.selectNodes("//method");
        for (Node node : nodes) {
            Element methodElement = (Element) node;
            Element reportLineCountNode = (Element) node.selectSingleNode("counter[@type='LINE']");
            if (reportLineCountNode != null) {
                int lines = Integer.parseInt(reportLineCountNode.attributeValue("missed")) + Integer.parseInt(reportLineCountNode.attributeValue("covered"));
                methodElement.addAttribute("endLine", String.valueOf(Integer.parseInt(methodElement.attributeValue("line")) + lines + 1));
            } else {
                methodElement.addAttribute("endLine", methodElement.attributeValue("line"));
            }
        }
    }

}
