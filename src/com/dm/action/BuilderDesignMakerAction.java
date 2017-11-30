package com.dm.action;

import com.dm.generation.DMBuilderPsiFieldMember;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;

import java.util.ArrayList;
import java.util.List;

public class BuilderDesignMakerAction extends AnAction {

    private Logger logger = Logger.getInstance(this.getClass());

    @Override
    public void actionPerformed(AnActionEvent e) {

        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (psiFile == null) {
            return;
        }
        final PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, psiFile, false);
        if (aClass == null || aClass.isInterface() || aClass.isEnum()) {
            return;
        }

        if (aClass.getLanguage() != StdLanguages.JAVA) return;
        final List<EncapsulatableClassMember> members = new ArrayList<EncapsulatableClassMember>();
        for (PsiField field : aClass.getAllFields()) {
            members.add(new DMBuilderPsiFieldMember(field));
        }

        // show dialog to choose members
        ClassMember[] allOriginalMembers = members.toArray(new ClassMember[members.size()]);
        MemberChooser<ClassMember> chooser = createMembersChooser(allOriginalMembers, true, false, project);
        chooser.show();
        final List<ClassMember> list = chooser.getSelectedElements();
        if (list != null) {
            final ClassMember[] membersChosen = list.toArray(new ClassMember[list.size()]);
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            generateCode(project, editor, aClass, membersChosen);
                        }
                    });
                }
            }, "dm builder", "dm");
        }
    }

    private void generateCode(Project project, Editor editor, PsiClass mainClass, ClassMember[] membersChosen) {
        logger.warn("members size: " + membersChosen.length);
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        //generate builder
        PsiClass builderClass = factory.createClass("Builder");
        PsiModifierList builderClassModifier = builderClass.getModifierList();
        if (builderClassModifier != null) {
            builderClassModifier.setModifierProperty(PsiModifier.PUBLIC, true);
            builderClassModifier.setModifierProperty(PsiModifier.STATIC, true);
        }

        PsiMethod buildMethod = factory.createMethod("build", PsiType.getTypeByName(mainClass.getName(), project,
                GlobalSearchScope
                        .allScope(project)));
        PsiCodeBlock body = buildMethod.getBody();
        assert body != null;

        String clazzTypeInstanceName = "builder" +  mainClass
                .getName();

        PsiStatement newClassTypeStatement = factory.createStatementFromText(mainClass.getName() + " " + clazzTypeInstanceName + "= new " + mainClass.getName() + "();",
                body);
        body.add(newClassTypeStatement);

        for (ClassMember member : membersChosen) {
            String[] memberTexts = member.getText().split(":");
            String fieldName = memberTexts[0], fieldType = memberTexts[1];
            String capturedFieldName = captureName(fieldName);

            //generate getter and setter in mainclass
            PsiMethod methodGet = factory.createMethodFromText("public " + fieldType + " get"
                    + capturedFieldName + "(){return " + fieldName + ";}", mainClass);

            PsiMethod methodSet = factory.createMethodFromText("public void set"
                    + capturedFieldName + "(" + fieldType + " " + fieldName + "){this." + fieldName + "="
                    + fieldName + ";}", mainClass);
            mainClass.add(methodGet);
            mainClass.add(methodSet);

            //generate fields in builder
            PsiField builderField = factory.createFieldFromText("private " + fieldType + " " + fieldName + ";",
                    builderClass);
            builderClass.add(builderField);

            //generate setter in builder
            PsiMethod methodSetInBuilder = factory.createMethodFromText("public Builder set" + capturedFieldName
                    + "(" + fieldType + " " + fieldName + "){this." + fieldName + "=" + fieldName + ";return this;}", builderClass);
            builderClass.add(methodSetInBuilder);

            //generate build method in builder
            PsiStatement setStatement = factory.createStatementFromText(clazzTypeInstanceName + ".set" + capturedFieldName + "(this." + fieldName + ");", body);
            body.add(setStatement);
        }

        PsiStatement returnStatement = factory.createStatementFromText("return " + clazzTypeInstanceName + ";", body);
        body.add(returnStatement);

        builderClass.add(buildMethod);
        mainClass.add(builderClass);


        CodeStyleManager.getInstance(project).reformat(mainClass);
    }


    protected MemberChooser<ClassMember> createMembersChooser(ClassMember[] members,
                                                              boolean allowEmptySelection,
                                                              boolean copyJavadocCheckbox,
                                                              Project project) {
        MemberChooser<ClassMember> chooser = new MemberChooser<ClassMember>(members, allowEmptySelection, true, project, null, null);
        chooser.setTitle("design maker generate builder");
        chooser.setCopyJavadocVisible(copyJavadocCheckbox);
        return chooser;
    }

    @Override
    public boolean startInTransaction() {
        return true;
    }

    /**
     * e.g. name -> Name
     * @param name
     * @return
     */
    private String captureName(String name) {
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;

    }
}
