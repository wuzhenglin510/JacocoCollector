package com.gt.jacoco.utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.gt.jacoco.entity.MethodReference;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ASTUtils {

    private static final JavaParser JAVA_PARSER;

    @Autowired
    private GitUtils gitUtils;

    static {
        JAVA_PARSER = new JavaParser();
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        JAVA_PARSER.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    /**
     * 在多个变动的源码文件中寻找有改动的方法
     *
     * @param differenceFiles
     * @return
     */
    public Map<String, List<MethodReference>> findMultiJavaFilesMethodChanged(String rootPath, List<DiffEntry> differenceFiles, String oldBranch, String newBranch) {
        Map<String, List<MethodReference>> classChangedMethods = new HashMap<>();
        for (DiffEntry diffEntry : differenceFiles) {
            switch (diffEntry.getChangeType()) {
                case MODIFY: {
                    String oldContent = gitUtils.getFile(rootPath, diffEntry.getNewPath(), oldBranch);
                    String newContent = gitUtils.getFile(rootPath, diffEntry.getNewPath(), newBranch);
                    Map<String, List<MethodReference>> singleJavaFileMethodChanged = findSingleJavaFileMethodChanged(oldContent, newContent);
                    classChangedMethods.putAll(singleJavaFileMethodChanged);
                    break;
                }
                case ADD: {
                    String newContent = gitUtils.getFile(rootPath, diffEntry.getNewPath(), newBranch);
                    Map<String, List<MethodReference>> singleJavaFileMethod = collectClassesAndMethodOfSingleJavaFile(newContent);
                    classChangedMethods.putAll(singleJavaFileMethod);
                    break;
                }
            }
        }
        return classChangedMethods;
    }

    /**
     * 在一个源码文件中寻找有改动的方法以及找出使用lombok注解的类
     * eg: Map<类名, 修改过的方法列表>
     *
     * @param oldJavaContent
     * @param newJavaContent
     * @return
     */
    public Map<String, List<MethodReference>> findSingleJavaFileMethodChanged(String oldJavaContent, String newJavaContent) {
        Map<String, List<MethodReference>> classChangedMethods = new HashMap<>();
        try {
            ParseResult<CompilationUnit> oldJavaResult = JAVA_PARSER.parse(oldJavaContent);
            ParseResult<CompilationUnit> newJavaResult = JAVA_PARSER.parse(newJavaContent);
            if (oldJavaResult.getResult().isPresent() && newJavaResult.getResult().isPresent()) {
                CompilationUnit oldCompilationUnit = oldJavaResult.getResult().get();
                CompilationUnit newCompilationUnit = newJavaResult.getResult().get();
                Map<String, ClassOrInterfaceDeclaration> classDefinitionsInOldFile = getClassDefinitionInCurrentFile(oldCompilationUnit);
                Map<String, ClassOrInterfaceDeclaration> classDefinitionsInNewFile = getClassDefinitionInCurrentFile(newCompilationUnit);
                for (String classAbsoluteNameInNewFile : classDefinitionsInNewFile.keySet()) {
                    if (classDefinitionsInOldFile.containsKey(classAbsoluteNameInNewFile)) {
                        Map<String, MethodDeclaration> functionDefinitionInNewClass = getFunctionDefinitionInClass(classDefinitionsInNewFile.get(classAbsoluteNameInNewFile));
                        Map<String, MethodDeclaration> functionDefinitionInOldClass = getFunctionDefinitionInClass(classDefinitionsInOldFile.get(classAbsoluteNameInNewFile));
                        List<MethodReference> methodChangeList = findChangeMethodInClass(functionDefinitionInOldClass, functionDefinitionInNewClass);
                        classChangedMethods.put(classAbsoluteNameInNewFile, methodChangeList);
                    } else {
                        Map<String, MethodDeclaration> allFunctionAreNewAdd = getFunctionDefinitionInClass(classDefinitionsInNewFile.get(classAbsoluteNameInNewFile));
                        classChangedMethods.put(classAbsoluteNameInNewFile, allFunctionAreNewAdd.keySet()
                                .stream()
                                .map(functionName -> new MethodReference(functionName, allFunctionAreNewAdd.get(functionName), methodBodyHash(allFunctionAreNewAdd.get(functionName))))
                                .collect(Collectors.toList()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
        return classChangedMethods;
    }

    /**
     * 获取一个文件里面所有的类和方法
     * eg: Map<类名, 修改过的方法列表>
     *
     * @param javContent
     * @return
     */
    public Map<String, List<MethodReference>> collectClassesAndMethodOfSingleJavaFile(String javContent) {
        Map<String, List<MethodReference>> classChangedMethods = new HashMap<>();
        try {
            ParseResult<CompilationUnit> javaResult = JAVA_PARSER.parse(javContent);
            if (javaResult.getResult().isPresent()) {
                Map<String, ClassOrInterfaceDeclaration> classDefinitionsInNewFile = getClassDefinitionInCurrentFile(javaResult.getResult().get());
                for (String classAbsoluteName : classDefinitionsInNewFile.keySet()) {
                    Map<String, MethodDeclaration> allFunctionAreNewAdd = getFunctionDefinitionInClass(classDefinitionsInNewFile.get(classAbsoluteName));
                    classChangedMethods.put(classAbsoluteName, allFunctionAreNewAdd.keySet()
                            .stream()
                            .map(functionName -> new MethodReference(functionName, allFunctionAreNewAdd.get(functionName), methodBodyHash(allFunctionAreNewAdd.get(functionName))))
                            .collect(Collectors.toList()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
        return classChangedMethods;
    }

    /**
     * 查找两个类之间的差异方法
     *
     * @param functionDefinitionInOldClass
     * @param functionDefinitionInNewClass
     * @return
     */
    private List<MethodReference> findChangeMethodInClass(Map<String, MethodDeclaration> functionDefinitionInOldClass
            , Map<String, MethodDeclaration> functionDefinitionInNewClass) {
        List<MethodReference> methodChangeList = new ArrayList<>();
        for (String functionName : functionDefinitionInNewClass.keySet()) {
            if (!functionDefinitionInOldClass.containsKey(functionName)) {
                methodChangeList.add(new MethodReference(functionName, functionDefinitionInNewClass.get(functionName), methodBodyHash(functionDefinitionInNewClass.get(functionName))));
            } else {
                MethodDeclaration methodDeclarationInNew = functionDefinitionInNewClass.get(functionName);
                MethodDeclaration methodDeclarationInOld = functionDefinitionInOldClass.get(functionName);
                if (methodDeclarationInNew.getBody().isPresent() ^ methodDeclarationInOld.getBody().isPresent()) {
                    methodChangeList.add(new MethodReference(functionName, functionDefinitionInNewClass.get(functionName), methodBodyHash(functionDefinitionInNewClass.get(functionName))));
                } else if (methodDeclarationInNew.getBody().isPresent() & methodDeclarationInOld.getBody().isPresent()) {
                    String newBody = methodDeclarationInNew.getBody().get().toString().replaceAll("\r|\n|\\s", "");
                    String oldBody = methodDeclarationInOld.getBody().get().toString().replaceAll("\r|\n|\\s", "");
                    if (!newBody.equals(oldBody)) {
                        methodChangeList.add(new MethodReference(functionName, functionDefinitionInNewClass.get(functionName), methodBodyHash(functionDefinitionInNewClass.get(functionName))));
                    }
                }
            }
        }
        return methodChangeList;
    }

    private String methodBodyHash(MethodDeclaration methodDeclaration) {
        if (methodDeclaration.getBody().isPresent()) {
            return String.valueOf(methodDeclaration.getBody().get().toString().replaceAll("\r|\n|\\s", "").hashCode());
        } else {
            return "0";
        }
    }


    /**
     * 获取一个源码文件中的所有类定义
     *
     * @param compilationUnit
     * @return
     */
    private Map<String, ClassOrInterfaceDeclaration> getClassDefinitionInCurrentFile(CompilationUnit compilationUnit) {
        Map<String, ClassOrInterfaceDeclaration> classDefinitionInCurrentFile = new HashMap<>();
        List<Node> childNodes = compilationUnit.getChildNodes();
        for (Node node : childNodes) {
            if (node instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDefinition = (ClassOrInterfaceDeclaration) node;
                classDefinitionInCurrentFile.put(getAbsoluteClassName(classDefinition), classDefinition);
                getClassDefinitionInCurrentFileRecursive((ClassOrInterfaceDeclaration) node, classDefinitionInCurrentFile);
            }
        }
        return classDefinitionInCurrentFile;
    }

    /**
     * 在一个源码文件中递归获取类定义（适应内部类）
     *
     * @param declaration
     * @param collector
     */
    private void getClassDefinitionInCurrentFileRecursive(ClassOrInterfaceDeclaration declaration, Map<String, ClassOrInterfaceDeclaration> collector) {
        List<Node> childNodes = declaration.getChildNodes();
        for (Node node : childNodes) {
            if (node instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDefinition = (ClassOrInterfaceDeclaration) node;
                collector.put(getAbsoluteClassName(classDefinition), classDefinition);
                for (Node subNode : node.getChildNodes()) {
                    if (subNode instanceof ClassOrInterfaceDeclaration) {
                        ClassOrInterfaceDeclaration subClassDefinition = (ClassOrInterfaceDeclaration) subNode;
                        collector.put(getAbsoluteClassName(subClassDefinition), subClassDefinition);
                        getClassDefinitionInCurrentFileRecursive(subClassDefinition, collector);
                    }
                }
            }
        }
    }

    /**
     * 获取类全路径名
     * eg-1: com/leo/TestController$InnerClassHere
     * eg-2: com/leo/TestController
     *
     * @param node
     * @return
     */
    private String getAbsoluteClassName(Node node) {
        StringBuilder absoluteClassName = new StringBuilder();
        absoluteClassName.insert(0, ((ClassOrInterfaceDeclaration) node).getNameAsString());
        while (node.getParentNode().isPresent()) {
            Node parent = node.getParentNode().get();
            if (parent instanceof ClassOrInterfaceDeclaration) {
                absoluteClassName.insert(0, "$");
                absoluteClassName.insert(0, ((ClassOrInterfaceDeclaration) parent).getNameAsString());
            } else if (parent instanceof CompilationUnit) {
                CompilationUnit topParent = (CompilationUnit) parent;
                if (topParent.getPackageDeclaration().isPresent()) {
                    absoluteClassName.insert(0, ".");
                    absoluteClassName.insert(0, topParent.getPackageDeclaration().get().getNameAsString());
                }
            }
            node = parent;
        }
        return absoluteClassName.toString().replaceAll("\\.", "/");
    }

    /**
     * 获取方法声明（包含方法名跟参数全路径）
     * eg: saySomethingToWorld(Ljava/lang/String;)
     *
     * @param methodDeclaration
     * @return
     */
    private String getMethodDefinitionName(MethodDeclaration methodDeclaration) {
        StringBuilder expression = new StringBuilder();
        expression.insert(0, "()");
        NodeList<Parameter> parameters = methodDeclaration.getParameters();
        for (Parameter parameter : parameters) {
            try {
                String describe = parameter.getType().resolve().describe();
                expression.insert(expression.length() - 1, "L");
                expression.insert(expression.length() - 1, describe.replaceAll("\\.", "/"));
                expression.insert(expression.length() - 1, ";");
            } catch (UnsolvedSymbolException e) {
                String unresolvedClassName = e.getName();
                StringBuilder importTips = new StringBuilder();
                if (unresolvedClassName.contains(".")) {
                    int firstIdx = unresolvedClassName.indexOf(".");
                    importTips.append(findImportTips(unresolvedClassName.substring(0, firstIdx), methodDeclaration));
                    importTips.append(unresolvedClassName.substring(firstIdx));
                } else {
                    importTips.append(findImportTips(unresolvedClassName, methodDeclaration));
                }
                expression.insert(expression.length() - 1, "L");
                expression.insert(expression.length() - 1, importTips.toString().replaceAll("\\.", "/"));
                expression.insert(expression.length() - 1, ";");
            }
        }
        expression.insert(0, methodDeclaration.getNameAsString());
        return expression.toString();
    }

    /**
     * 解析类名，获取全路径名
     * eg: String  -->  java.lang.String
     *
     * @param className
     * @param node
     * @return
     */
    private String findImportTips(String className, Node node) {
        while (!(node instanceof CompilationUnit)) {
            if (!node.getParentNode().isPresent()) {
                return className;
            } else {
                node = node.getParentNode().get();
            }
        }
        NodeList<ImportDeclaration> imports = ((CompilationUnit) node).getImports();
        for (ImportDeclaration importDeclaration : imports) {
            if (importDeclaration.getName().getIdentifier().equals(className)) {
                if (importDeclaration.getName().getQualifier().isPresent()) {
                    return importDeclaration.getName().getQualifier().get().asString() + "." + importDeclaration.getName().getIdentifier();
                } else {
                    return importDeclaration.getName().getIdentifier();
                }
            }
        }
        return className;
    }

    /**
     * 获取一个类里面的方法定义
     *
     * @param classOrInterfaceDeclaration
     * @return
     */
    private Map<String, MethodDeclaration> getFunctionDefinitionInClass(ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
        Map<String, MethodDeclaration> methodDeclarations = new HashMap<>();
        List<Node> childNodes = classOrInterfaceDeclaration.getChildNodes();
        for (Node node : childNodes) {
            if (node instanceof MethodDeclaration) {
                methodDeclarations.put(getMethodDefinitionName((MethodDeclaration) node), (MethodDeclaration) node);
            }
        }
        return methodDeclarations;
    }

}
