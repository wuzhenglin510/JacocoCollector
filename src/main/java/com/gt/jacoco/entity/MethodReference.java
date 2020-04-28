package com.gt.jacoco.entity;

import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MethodReference {

    /**
     * eg:  saySomethingToWorld(Ljava/lang/String;)
     */
    private String methodNameWithParams;

    private MethodDeclaration methodDeclaration;

    /**
     * use to decide should replace new jacoco method coverage data with old
     * discard method body not changed
     */
    private String methodBodyHash;

}
