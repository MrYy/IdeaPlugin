package com.dm.generation;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

public class DMBuilderPsiFieldMember extends PsiFieldMember {

    public DMBuilderPsiFieldMember(@NotNull PsiField field) {
        super(field);
    }
}
