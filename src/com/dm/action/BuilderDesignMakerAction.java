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
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
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
        final List<ClassMember> list =  chooser.getSelectedElements();
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

    private void generateCode(Project project, Editor editor, PsiClass aClass, ClassMember[] membersChosen) {
        logger.warn("members size: " + membersChosen.length);
        //1.generate getter and setter

        //2.generate builder
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
}
