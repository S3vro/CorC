package de.tu_bs.cs.isf.cbc.tool.features;

import org.testng.TestNG;

import org.testng.xml.XmlSuite.ParallelMode;

import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

import de.tu_bs.cs.isf.cbc.cbcclass.model.cbcclass.Field;
import de.tu_bs.cs.isf.cbc.cbcclass.model.cbcclass.ModelClass;
import de.tu_bs.cs.isf.cbc.cbcclass.model.cbcclass.Parameter;
import de.tu_bs.cs.isf.cbc.cbcmodel.AbstractStatement;
import de.tu_bs.cs.isf.cbc.cbcmodel.CbCFormula;
import de.tu_bs.cs.isf.cbc.cbcmodel.CompositionStatement;
import de.tu_bs.cs.isf.cbc.cbcmodel.Condition;
import de.tu_bs.cs.isf.cbc.cbcmodel.GlobalConditions;
import de.tu_bs.cs.isf.cbc.cbcmodel.JavaVariable;
import de.tu_bs.cs.isf.cbc.cbcmodel.JavaVariables;
import de.tu_bs.cs.isf.cbc.cbcmodel.Renaming;
import de.tu_bs.cs.isf.cbc.cbcmodel.SelectionStatement;
import de.tu_bs.cs.isf.cbc.cbcmodel.SmallRepetitionStatement;
import de.tu_bs.cs.isf.cbc.cbcmodel.VariableKind;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.AbstractStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.CompositionStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.MethodStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.OriginalStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.ReturnStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.SelectionStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.SkipStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.SmallRepetitionStatementImpl;
import de.tu_bs.cs.isf.cbc.cbcmodel.impl.StrengthWeakStatementImpl;
import de.tu_bs.cs.isf.cbc.tool.helper.BranchType;
import de.tu_bs.cs.isf.cbc.tool.helper.GetDiagramUtil;
import de.tu_bs.cs.isf.cbc.tool.helper.InputData;
import de.tu_bs.cs.isf.cbc.tool.helper.InputDataTupel;
import de.tu_bs.cs.isf.cbc.tool.helper.JavaCondition;
import de.tu_bs.cs.isf.cbc.tool.helper.PreConditionSolver;
import de.tu_bs.cs.isf.cbc.tool.helper.PreConditionSolverException;
import de.tu_bs.cs.isf.cbc.tool.helper.TestAndAssertionListener;
import de.tu_bs.cs.isf.cbc.tool.helper.TestCaseData;
import de.tu_bs.cs.isf.cbc.tool.helper.Util;
import de.tu_bs.cs.isf.cbc.tool.helper.conditionparser.ConditionParser;
import de.tu_bs.cs.isf.cbc.tool.helper.conditionparser.Node;
import de.tu_bs.cs.isf.cbc.util.Console;
import de.tu_bs.cs.isf.cbc.util.ConstructCodeBlock;
import de.tu_bs.cs.isf.cbc.util.FileUtil;
import de.tu_bs.cs.isf.cbc.util.Parser;

/**
 * The test generator.
 * @author Fynn
 */
public class TestAndAssertionGenerator extends MyAbstractAsynchronousCustomFeature{
	private static int positionIndex = 0;
	private static JavaVariable returnVariable = null;
	private URI projectPath;
	private static final String LINE = "==============================================================================================";
	private List<String> projectJavaFiles;
	private List<String> projectInternalClasses;
	private static final String INSTANCE_TOKEN = "<[";
	private static final String INSTANCE_CLOSED_TOKEN = "]>";
	public static final String ARRAY_TOKEN = "<{";
	public static final String ARRAY_CLOSED_TOKEN = "}>";
	public static final String GENERATED_CLASSNAME = "GeneratedClass";
	private final static String STATEMENT_PH = "<statement>";
	private boolean showWarnings = false;
	private static final Color red = new Color(new RGB(150, 10, 10));
		
	public TestAndAssertionGenerator(IFeatureProvider fp) {
		super(fp);
	}
	
	@Override
	public String getName() {
		return "Generate blackbox tests";
	}

	@Override
	public String getDescription() {
		return "Generates tests for the diagram.";
	}

	@Override
	public boolean canExecute(ICustomContext context) {
		return true;
	}

	@Override
	public void execute(ICustomContext context, IProgressMonitor monitor) {
		final URI uri = getDiagram().eResource().getURI();
		final List<String> globalVars = new ArrayList<String>();
		String signatureString = "";
		JavaVariables vars = null;
		CbCFormula formula = null;
		GlobalConditions globalConditions = null;
		
		setProjectPath(uri);
		Util.clearLog(uri);

		for (Shape shape : getDiagram().getChildren()) {
			Object obj = getBusinessObjectForPictogramElement(shape);
			if (obj instanceof JavaVariables) {
				vars = (JavaVariables) obj;
			} else if (obj instanceof CbCFormula) {
				formula = (CbCFormula) obj;
			} else if (obj instanceof GlobalConditions) {
				globalConditions = (GlobalConditions) obj;
			}
		}
		
		if (formula.getMethodObj() == null) {
			List<String> params = new ArrayList<String>();
			signatureString = formula.getName();
			for (var v : vars.getVariables()) {
				if (v.getKind().toString().equals("PARAM")) {
					params.add(v.getName());
				} else if (v.getKind().toString().equals("RETURN")) {
					signatureString = "public " + v.getName().split("\\s")[0] + " " + signatureString;
				}
			}
			signatureString += "(";
			if (params.size() == 0) {
				signatureString += ")";
			} else {
				for (int i = 0; i < params.size() - 1; i++) {
					signatureString += params.get(i) + ", ";
				}
				signatureString += params.get(params.size() - 1) + ")";
			}
			if (!signatureString.contains("public")) {
				signatureString = "public void " + signatureString;
			}
		} else {
			signatureString = formula.getMethodObj().getSignature();
		}
		
		JavaVariable returnVariable = null;
		int counter = 0;
		LinkedList<String> localVariables = new LinkedList<String>();
		for(int i = 0; i < vars.getVariables().size(); i++) {
			JavaVariable currentVariable = vars.getVariables().get(i);
			if(currentVariable.getKind() == VariableKind.RETURN) {
				counter++;
				if(!signatureString.substring(0, signatureString.indexOf('(')).contains(currentVariable.getName().replace("non-null", "").trim().split("\\s")[0])) {
					Console.println("Method return type and variable type does not match.");
					return;
				}
				if(counter > 1) {
					Console.println("Too much variables of kind RETURN.");
					return;
				}
				returnVariable = currentVariable;
			}else if(currentVariable.getKind() == VariableKind.LOCAL) {
				localVariables.add(currentVariable.getName().replace("non-null", ""));
			}
		}
		var tmp2 = vars.getParams();
		for (Parameter v : tmp2) {
			int a = 0;
		}
		for (Field field : vars.getFields()) {
			if (field.getName() == null) {
				continue;
			}
			globalVars.add(field.getName().replace("non-null ", ""));
		}			
		long startTime = System.nanoTime();		
		Console.clear();
		var methodToGenerate = getDiagram();			
		String code2 = genCode(methodToGenerate);		
		var className = code2.split("public\\sclass\\s", 2)[1].split("\\s", 2)[0];
		className = className.replaceAll("\\{", "");		
		var code = genCode(methodToGenerate, true);			
		List<String> classCodes; 
		try {
			classCodes = genAllDependenciesOfMethod(code, className, formula.getStatement().getPostCondition().getName());
		} catch (TestAndAssertionGeneratorException e) {
			Console.println(e.getMessage());
			e.printStackTrace();
			return;
		}
		
		try {
			if(!compileFileContents(classCodes, methodToGenerate.getName())) {
				return;
			}
		} catch (TestAndAssertionGeneratorException e) {
			Console.println(e.getMessage());
			Console.println("Execution of the test generator failed.");
			return;
		}
		// get code of class to be tested
		for (var d : classCodes) {
			if (d.trim().split("\\s", 4)[2].equals(className)) {
				code2 = d.trim();
			}
		}

		List<TestCaseData> inputs;
		String testFileContent;
		
		try {
			inputs = genInputs(parseConditions(globalConditions, formula.getStatement().getPreCondition()), vars, code2, signatureString, returnVariable);
			testFileContent = genTestCases(className, inputs, formula.getStatement().getPostCondition(), globalConditions, formula);
		} catch (TestAndAssertionGeneratorException | PreConditionSolverException | TestStatementException e) {
			Console.println(e.getMessage());
			e.printStackTrace();
			return;
		}
		writeToFile(className + "Test", testFileContent);
		Console.clear();
		Console.println("Start testing...");  
		executeTestCases("file://" + FileUtil.getProjectLocation(uri) + "/tests/", className + "Test", globalVars, inputs);
		long endTime = System.nanoTime();
		double elapsedTime = (endTime - startTime) / 1000000;
		Console.println("Total time needed: " + (int)elapsedTime + "ms");
	}
	
	//================================================================================================
	//| Start of copied code from existing methods.												 	 |
	//================================================================================================

	private static String rewriteGuardToJavaCode(String guard) {
		guard = guard.replaceAll("(?<!<|>|!|=)(\\s*=\\s*)(?!<|>|=)", " == ");
		guard = guard.replace("&", "&&");
		guard = guard.replace("|", "||");
		guard = guard.replaceAll("\\s+TRUE\\s*|TRUE\\s+", " true ");
		guard = guard.replaceAll("\\s+FALSE\\s*|FALSE\\s+", " false ");
		guard = guard.trim();
		return guard;
	}
	
	private static String constructSelection(SelectionStatement statement) {
		StringBuffer buffer = new StringBuffer();

		if (!statement.getCommands().isEmpty()) {
			String guard = statement.getGuards().get(0).getName();

			guard = rewriteGuardToJavaCode(guard);

			if(guard.trim().equals("TRUE"))
				guard = "true";
			if(guard.trim().equals("FALSE"))
				guard = "false";
			
			buffer.append("if (" + guard + ") {\n");

			positionIndex++;
			if (statement.getCommands().get(0).getRefinement() != null) {
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(0).getRefinement()));
				positionIndex--;
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append("}");
			} else {
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(0)));
				positionIndex--;
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append("}");
			}
		}

		for (int i = 1; i < statement.getCommands().size(); i++) {
			String guard = statement.getGuards().get(i).getName();
			// guard = guard.replaceAll("\\s=\\s", "==");
			guard = rewriteGuardToJavaCode(guard);
			
			if(guard.trim().equals("TRUE"))
				guard = "true";
			if(guard.trim().equals("FALSE"))
				guard = "false";
			
			buffer.append(" else if (" + guard + ") {\n");
			positionIndex++;
			if (statement.getCommands().get(i).getRefinement() != null) {
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(i).getRefinement()));
				positionIndex--;
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append("}");
			} else {
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(i)));
				positionIndex--;
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append("}");
			}

		}

		buffer.append("\n");
		return buffer.toString();
	}
	
	private static String constructComposition(CompositionStatement statement) {
		StringBuffer buffer = new StringBuffer();
		
		if (statement.getFirstStatement().getRefinement() != null) {
			buffer.append(constructCodeBlockOfChildStatement(statement.getFirstStatement().getRefinement()));
		} else {
			buffer.append(constructCodeBlockOfChildStatement(statement.getFirstStatement()));
		}

		//commented out to prevent generation of assets from intermediate condition
		/*
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		if(statement.getIntermediateCondition().getName() != "" && withAsserts) {
			buffer.append("assert " + statement.getIntermediateCondition().getName().replace("\n", " ").replace("\r", " ") + ";\n");
		}
		*/
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		
		if (statement.getSecondStatement().getRefinement() != null) {
			buffer.append(constructCodeBlockOfChildStatement(statement.getSecondStatement().getRefinement()));
		} else {
			buffer.append(constructCodeBlockOfChildStatement(statement.getSecondStatement()));
		}
		
		return buffer.toString();
	}
	
	private static String constructSmallRepetition(SmallRepetitionStatement statement) {
		StringBuffer buffer = new StringBuffer();
		/*
		if (withInvariants) {
			String invariant = statement.getInvariant().getName();
			invariant = Parser.rewriteConditionToJML(invariant);
			//invariant = useRenamingCondition(invariant);
			buffer.append("//@ loop_invariant " + invariant.replaceAll("\\r\\n", "") + ";\n");
			for (int i = 0; i < positionIndex; i++) {
				buffer.append("\t");
			}
			buffer.append("//@ decreases " + statement.getVariant().getName() + ";\n");
		}
		*/
		String guard = statement.getGuard().getName();
		// guard = guard.replaceAll("\\s=\\s", "==");
		guard = rewriteGuardToJavaCode(guard);
		/*for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}*/
		
		if(guard.trim().equals("TRUE"))
			guard = "true";
		if(guard.trim().equals("FALSE"))
			guard = "false";
		
		buffer.append("while (" + guard + ") {\n");
		positionIndex++;
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		if (statement.getLoopStatement().getRefinement() != null) {
			buffer.append(constructCodeBlockOfChildStatement(statement.getLoopStatement().getRefinement()));
		} else {
			buffer.append(constructCodeBlockOfChildStatement(statement.getLoopStatement()));
		}
		positionIndex--;
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		buffer.append("}\n");
		return buffer.toString();
	}
	
	private static String returnStatement(String variableName, String refinementName) {
		String s = "";
		if(!refinementName.trim().split(";")[0].equals(variableName)
				&& !refinementName.trim().split(";")[0].equals("this." + variableName)) {
			if(refinementName.contains("=") 
					&& refinementName.charAt(refinementName.indexOf('=') + 1) != '='
					&& refinementName.charAt(refinementName.indexOf('=') - 1) != '>'
					&& refinementName.charAt(refinementName.indexOf('=') - 1) != '<') {
				//s = variableName + refinementName.replace(refinementName.subSequence(0, refinementName.indexOf('=')), "") + "\n";
				s = refinementName + "\n";
				if(!refinementName.trim().substring(0, refinementName.indexOf('=') - 1).equals(variableName)) {
					for(int i = 0; i < positionIndex; i++) {
						s = s + "\t";
					}
					s = s + variableName + " = " + refinementName.trim().split("=")[0] + ";\n";	
				} 
			} else {
				s = variableName + " = " + refinementName + "\n";
			}
		}
		return s;
	}
	
	private static String constructCodeBlockOfChildStatement(AbstractStatement refinement) {
		if (refinement.getClass().equals(AbstractStatementImpl.class) || refinement.getClass().equals(OriginalStatementImpl.class) || refinement.getClass().equals(MethodStatementImpl.class)) {
			// behandlung von AbstractStatementImpl nur von Tobi
			String allStatements = refinement.getName().replace("\r\n", "");
			allStatements = allStatements.trim();
			allStatements = allStatements.replaceAll("\\s+", " ");
			//allStatements = allStatements.split("\\w\\=\\w")[0]+ " = " + allStatements.split("\\w\\=\\w")[1];
			allStatements = allStatements.replace("/ =", " /= ");
			allStatements = allStatements.replace("+ =", " += ");
			allStatements = allStatements.replace("- =", " -= ");
			allStatements = allStatements.replace("* =", " *= ");

			String abstractStatementSplit[] = allStatements.split(";");
			String statements;
			if (abstractStatementSplit.length > 1) {
				statements = abstractStatementSplit[0].trim() + ";\n";
				for (int i = 1; i < abstractStatementSplit.length; i++) {
					for (int j = 0; j < positionIndex; j++) {
						statements = statements + "\t";
					}
					statements = statements + (abstractStatementSplit[i].trim() + ";\n");
				}
			} else {
				statements = allStatements + "\n";
			}
			// return statements;
			return statements;
			// return refinement.getName() + "\n";
		} else if (refinement.getClass().equals(SkipStatementImpl.class)) {
			return ";\n";
		} else if (refinement.getClass().equals(ReturnStatementImpl.class)) {
			if(returnVariable != null) {//In case of void method with "return;", returnVariable will be null
				String returnString = returnStatement(returnVariable.getName().split(" ")[1], refinement.getName().trim());
				if(returnString.isEmpty()) {
					return "return " + refinement.getName() + "\n";
				}				
				for(int i = 0; i < positionIndex; i++) {
					returnString = returnString + "\t";
				}
				returnString = returnString + "return " + returnVariable.getName().split(" ")[1] + ";\n";
				return returnString; 
			}
			return "return " + refinement.getName() + "\n";
		} else if (refinement.getClass().equals(SelectionStatementImpl.class)) {
			return constructSelection((SelectionStatement) refinement);
		} else if (refinement.getClass().equals(CompositionStatementImpl.class)) {
			return constructComposition((CompositionStatement) refinement);
		} else if (refinement.getClass().equals(SmallRepetitionStatementImpl.class)) {
			return constructSmallRepetition((SmallRepetitionStatement) refinement);
		} else if (refinement.getClass().equals(StrengthWeakStatementImpl.class)) {
			if (refinement.getRefinement() != null) {
				return constructCodeBlockOfChildStatement(refinement.getRefinement());
			} else {
				return refinement.getName() + ";\n";
			}
		}
		return null;
	}
	
	private static StringBuffer insertTabs(StringBuffer s) {
		for (int i = 0; i < positionIndex; i++) {
			s.append("\t");
		}
		return s;
	}
	
	//================================================================================================
	//| End of copied code from existing methods.													 |
	//================================================================================================
	
	private static String constructCodeBlockOfChildStatement(final AbstractStatement refinement, final String id) throws TestAndAssertionGeneratorException {
		if (id == null) {
			//Console.clear();
			Console.println("Please add IDs to the diagram.");
			throw new TestAndAssertionGeneratorException("An ID was null.");
			//return "TestAndAssertionError: An ID was null.";
		}
		if (id.equals(refinement.getId())) {
			return STATEMENT_PH + "\n";
		}
		if (refinement.getClass().equals(AbstractStatementImpl.class) || refinement.getClass().equals(OriginalStatementImpl.class) || refinement.getClass().equals(MethodStatementImpl.class)) {
			// behandlung von AbstractStatementImpl nur von Tobi
			String allStatements = refinement.getName().replace("\r\n", "");
			allStatements = allStatements.trim();
			allStatements = allStatements.replaceAll("\\s+", " ");
			//allStatements = allStatements.split("\\w\\=\\w")[0]+ " = " + allStatements.split("\\w\\=\\w")[1];
			allStatements = allStatements.replace("/ =", " /= ");
			allStatements = allStatements.replace("+ =", " += ");
			allStatements = allStatements.replace("- =", " -= ");
			allStatements = allStatements.replace("* =", " *= ");

			String abstractStatementSplit[] = allStatements.split(";");
			String statements;
			if (abstractStatementSplit.length > 1) {
				statements = abstractStatementSplit[0].trim() + ";\n";
				for (int i = 1; i < abstractStatementSplit.length; i++) {
					for (int j = 0; j < positionIndex; j++) {
						statements = statements + "\t";
					}
					statements = statements + (abstractStatementSplit[i].trim() + ";\n");
				}
			} else {
				statements = allStatements + "\n";
			}
			// return statements;
			return statements;
			// return refinement.getName() + "\n";
		} else if (refinement.getClass().equals(SkipStatementImpl.class)) {
			return ";\n";
		} else if (refinement.getClass().equals(ReturnStatementImpl.class)) {
			if(returnVariable != null) {//In case of void method with "return;", returnVariable will be null
				String returnString = returnStatement(returnVariable.getName().split(" ")[1], refinement.getName().trim());
				if(returnString.isEmpty()) {
					return "return " + refinement.getName() + "\n";
				}				
				for(int i = 0; i < positionIndex; i++) {
					returnString = returnString + "\t";
				}
				returnString = returnString + "return " + returnVariable.getName().split(" ")[1] + ";\n";
				return returnString; 
			}
			return "return " + refinement.getName() + "\n";
		} else if (refinement.getClass().equals(SelectionStatementImpl.class)) {
			return constructSelection((SelectionStatement) refinement, id);
		} else if (refinement.getClass().equals(CompositionStatementImpl.class)) {
			return constructComposition((CompositionStatement) refinement, id);
		} else if (refinement.getClass().equals(SmallRepetitionStatementImpl.class)) {
			return constructSmallRepetition((SmallRepetitionStatement) refinement, id);
		} else if (refinement.getClass().equals(StrengthWeakStatementImpl.class)) {
			if (refinement.getRefinement() != null) {
				return constructCodeBlockOfChildStatement(refinement.getRefinement(), id);
			} else {
				return refinement.getName() + ";\n";
			}
		}
		return null;
	}
	
	private static String constructSelection(final SelectionStatement statement, final String id) throws TestAndAssertionGeneratorException {
		StringBuffer buffer = new StringBuffer();

		if (!statement.getCommands().isEmpty()) {
			String guard = statement.getGuards().get(0).getName();

			guard = rewriteGuardToJavaCode(guard);

			if(guard.trim().equals("TRUE"))
				guard = "true";
			if(guard.trim().equals("FALSE"))
				guard = "false";
			
			buffer.append("if (" + guard + ") {\n");

			positionIndex++;
			if (statement.getCommands().get(0).getRefinement() != null) {
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				try {
					buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(0).getRefinement(), id));
				} catch (TestAndAssertionGeneratorException e) {
					throw e;
				}
				positionIndex--;
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append("}");
			} else {
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(0), id));
				positionIndex--;
				for (int i = 0; i < positionIndex; i++) {
					buffer.append("\t");
				}
				buffer.append("}");
			}
		}

		for (int i = 1; i < statement.getCommands().size(); i++) {
			String guard = statement.getGuards().get(i).getName();
			// guard = guard.replaceAll("\\s=\\s", "==");
			guard = rewriteGuardToJavaCode(guard);
			
			if(guard.trim().equals("TRUE"))
				guard = "true";
			if(guard.trim().equals("FALSE"))
				guard = "false";
			
			buffer.append(" else if (" + guard + ") {\n");
			positionIndex++;
			if (statement.getCommands().get(i).getRefinement() != null) {
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(i).getRefinement(), id));
				positionIndex--;
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append("}");
			} else {
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append(constructCodeBlockOfChildStatement(statement.getCommands().get(i), id));
				positionIndex--;
				for (int j = 0; j < positionIndex; j++) {
					buffer.append("\t");
				}
				buffer.append("}");
			}

		}

		buffer.append("\n");
		return buffer.toString();
	}

	private static String constructComposition(final CompositionStatement statement, final String id) throws TestAndAssertionGeneratorException {
		StringBuffer buffer = new StringBuffer();
		try {
			if (statement.getFirstStatement().getRefinement() != null) {
				buffer.append(constructCodeBlockOfChildStatement(statement.getFirstStatement().getRefinement(), id));
			} else {
				buffer.append(constructCodeBlockOfChildStatement(statement.getFirstStatement(), id));
			}
	
			for (int i = 0; i < positionIndex; i++) {
				buffer.append("\t");
			}
			
			if (statement.getSecondStatement().getRefinement() != null) {
				buffer.append(constructCodeBlockOfChildStatement(statement.getSecondStatement().getRefinement(), id));
			} else {
				buffer.append(constructCodeBlockOfChildStatement(statement.getSecondStatement(), id));
			}
		} catch (TestAndAssertionGeneratorException e) {
			throw e;
		}
		return buffer.toString();
	}
	
	private static String constructSmallRepetition(final SmallRepetitionStatement statement, final String id) throws TestAndAssertionGeneratorException {
		StringBuffer buffer = new StringBuffer();

		String guard = statement.getGuard().getName();
		guard = rewriteGuardToJavaCode(guard);
		
		if(guard.trim().equals("TRUE"))
			guard = "true";
		if(guard.trim().equals("FALSE"))
			guard = "false";
		
		buffer.append("while (" + guard + ") {\n");
		positionIndex++;
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		try {
			if (statement.getLoopStatement().getRefinement() != null) {
				buffer.append(constructCodeBlockOfChildStatement(statement.getLoopStatement().getRefinement(), id));
			} else {
				buffer.append(constructCodeBlockOfChildStatement(statement.getLoopStatement(), id));
			}
		} catch (TestAndAssertionGeneratorException e) {
			throw e;
		}
		positionIndex--;
		for (int i = 0; i < positionIndex; i++) {
			buffer.append("\t");
		}
		buffer.append("}\n");
		return buffer.toString();
	}

	public static String getTabs(long num) {
		var out = "";
		for (int i = 0; i < num; i++) out+="\t";
		return out;
	}
	
	static public String genCodeUntilStatement(final CbCFormula formula, final AbstractStatement statement) throws TestAndAssertionGeneratorException {
		var code = new StringBuffer();
		String s;
		if (formula.getStatement().getRefinement() != null) {
			try {
				s = constructCodeBlockOfChildStatement(formula.getStatement().getRefinement(), statement.getId());
				code.append(s);
			} catch (TestAndAssertionGeneratorException e) {
				throw e;
			}
		} else {
			try {
				s = constructCodeBlockOfChildStatement(formula.getStatement(), statement.getId());
				code.append(s); 
			} catch (TestAndAssertionGeneratorException e) {
				Console.println(e.getMessage());
				throw e;
			}
		}
		// now remove all code that comes after the statement
		var cur = code.toString();
		if (cur.contains("TestAndAssertionError")) {
			return "";
		}
		String beforeStatement = cur.substring(0, cur.indexOf(STATEMENT_PH));
		String afterStatement;
		int lastLoopIndex = beforeStatement.lastIndexOf("while (");
		if (lastLoopIndex != -1 && countBrackets(beforeStatement, '{') > 0) {
			String helper = cur.substring(lastLoopIndex, cur.length());
			int startIndex = helper.indexOf("{");
			int endIndex = findClosingBracketIndex(cur, lastLoopIndex + startIndex, '{');
			afterStatement = cur.substring(endIndex + 1, cur.length());
			cur = cur.substring(0, endIndex + 1);
		} else {			
			afterStatement = cur.substring(cur.indexOf(STATEMENT_PH), cur.length());
			cur = cur.substring(0, cur.indexOf(STATEMENT_PH) + STATEMENT_PH.length());
		}
		long numClosing = countBrackets(afterStatement, '{');
		if (numClosing >= 0) return cur;
		numClosing *= -1;
		for (int i = 0; i < numClosing; i++) {
			cur += "\n" + getTabs(numClosing-(i+1)) + "}";
		}
		return cur;
	}
	
	public void setShowWarnings(boolean b) {
		this.showWarnings = b;
	}

	public void setProjectPath(URI projectPath) {
		this.projectPath = projectPath;
	}
	
	public String classExists(String className) {
		var dir = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests");	
		if (!dir.exists()) {
			return "";
		}
		var javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className + ".java");
		if (!javaFile.exists()){
			return "";
	    }
		try {
			var code = Files.readString(Paths.get(javaFile.getPath()));
			return removeAllComments(code);
		} catch (IOException e) {}
		return "";
	}
	
	private String removeAllComments(String code) {
		String helper;
		int curIndex = code.indexOf("/*");
		
		while (curIndex != -1) {
			if (curIndex - 1 >= 0 && code.charAt(curIndex - 1) == ' ') {
				helper = code.substring(curIndex - 1, code.length());
			} else {
				helper = code.substring(curIndex, code.length());
			}
			helper = helper.substring(0, helper.indexOf("*/") + 2);
			code = code.replace(helper, "");
			curIndex = code.indexOf("/*");
		}
		code = code.replaceAll("\\ssynchronized", "");
		return code;
	}
	
	private String generateConstructor(final String className, final List<String> gVars) {
		final StringBuffer code = new StringBuffer();
		String globalVarName;
		
		code.append("\n\t" + "public " + className + "(");
		int counter = 0;
		for (var g : gVars) {
			if (counter != gVars.size() - 1) {
				code.append(g + ", ");
			} else {
				code.append(g);
			}
			counter++;
		}
		code.append(") {\n");

		for (var g : gVars) {
			var splitter = g.split("\\s");
			if (splitter.length > 1) {
				globalVarName = g.split("\\s")[splitter.length - 1];
			} else {
				globalVarName = g.split("\\s")[0];
			}
			code.append("\t\t" + "this." + globalVarName + " = " + globalVarName + ";\n");
		}
		code.append("\t}\n\n");	
		return code.toString().trim();
	}
	
	private String insertConstructorCode(final String code, final String constructorCode) {
		// find first empty line and insert constructor there
		return code.substring(0, code.indexOf('{') + 1) + "\n" + constructorCode + code.substring(code.indexOf('{') + 1, code.length());
	}
	
	private String removeMethod(String code, String methodSignature) {
		//var split = sSplit(methodSignature, "\\s");
		//var methodName = split[split.length - 1];
		if (!code.contains(methodSignature)) {
			return code.substring(0, code.lastIndexOf("}"));
		}
		String before = code.substring(0, code.indexOf(methodSignature));
		before = before.substring(0, before.lastIndexOf("\n")).trim();
		String after = code.substring(code.indexOf(methodSignature), code.length());
		int closingBracketIndex = findClosingBracketIndex(code, code.indexOf(methodSignature) + after.indexOf('{'), '{');
		after = "\n\n\t" + code.substring(closingBracketIndex + 1, code.length()).trim();
		var output = before + after;
		return output.trim().substring(0, output.length() - 1);
	}
	
	public static String getMethodCode(final String code, final String methodSignature) {
		if (!code.contains(methodSignature)) {
			return "";
		}
		final int startIndex = code.indexOf(methodSignature);
		String methodCode = code.substring(startIndex, code.length());
		int closingBracketIndex = findClosingBracketIndex(code, startIndex + methodCode.indexOf('{'), '{');
		if (closingBracketIndex == - 1) {
			closingBracketIndex = code.length() - 1;
		}
		methodCode = code.substring(startIndex, closingBracketIndex + 1);
		return methodCode;
	}
	
	private boolean containsConstructor(String code, String constructor) {
		constructor = constructor.replaceAll("\\s", "");
		code = code.replaceAll("\\s", "");
		if (code.indexOf(constructor) != -1 && code.charAt(code.indexOf(constructor) - 1) == ']') {
			return false;
		}
		return code.contains(constructor);
	}
	
	/**
	 * Generates the code for a CbC method.
	 * @param formula
	 * @param globalConditions
	 * @param renaming
	 * @param vars
	 * @param returnVar
	 * @param signatureString
	 * @param globalVariables
	 * @param gVars
	 * @return the generated java code if the method wasn't generated before. Else it returns an empty string.
	 */
	private String generateCode(CbCFormula formula, GlobalConditions globalConditions, Renaming renaming, LinkedList<String> vars, JavaVariable returnVar, String signatureString, String globalVariables, List<String> gVars, boolean onlyMethod, String customReturnName) {	
		String existingCode = "";
		String constructorCode;
		StringBuffer code = new StringBuffer();
		final String className;
		
		if(returnVar != null) {
			returnVariable = returnVar; 
		}
			
		if (formula.getMethodObj() == null) {
			className = GENERATED_CLASSNAME;
		} else {
			className = formula.getMethodObj().getParentClass().getName();
		}
		
		// first check if the method already exists
		/*
		if (methodExists(className, signatureString)) {
			return "";
		}
		*/
		if (!onlyMethod && !(existingCode = classExists(className)).equals("")) {
			// make sure the constructor is present
			constructorCode = generateConstructor(className, gVars);
			if (!containsConstructor(existingCode, constructorCode)) {
				existingCode = insertConstructorCode(existingCode, constructorCode);
			}
			// remove method if it is already present
			existingCode = removeMethod(existingCode, signatureString);
			
			code.append(existingCode.trim());
			// only generate the method code
			onlyMethod = true;
		}
		
		if (!onlyMethod) {	
			// generate class
			code.append("public class " + className + " {\n");
			// generate global vars
			code.append(globalVariables);
			
			// generate constructor for testing purposes
			code.append(generateConstructor(className, gVars));
		}
			
		// generate method
		code.append("\n\n\t" + signatureString + " {\n");
		//System.out.println(System.getProperties());
		positionIndex = 2;//2
		code = insertTabs(code);
		
		for(String var : vars) {//declare variables
			if(!var.contains("old_")) {
				// initialize return value if present because the compiler could be
				// throwing not initialized exception when if branches are present
				/*if (var.split("\\s")[1].equals("result")) {
					var defaultValue = genDefaultInputForVar(var, null).get(0);
					var = var + " = " + defaultValue;
				}*/
				var defaultValue = genDefaultInputForVar(var, null).get(0);
				code.append(var + " = " + defaultValue + ";\n");
				code = insertTabs(code);
			}
		}

		if (returnVariable != null && !getMethodCode(code.toString(), signatureString).contains(returnVariable.getName())) {
			code.append(returnVariable.getName() + ";\n");
			code = insertTabs(code);
		}

		String s;
		if (formula.getStatement().getRefinement() != null) {
			s = constructCodeBlockOfChildStatement(formula.getStatement().getRefinement());
			if (renaming != null) 
				s = ConstructCodeBlock.useRenamingCondition(s);
			code.append(s);
		} else {
			s = constructCodeBlockOfChildStatement(formula.getStatement());
			if (renaming != null) 
				s = ConstructCodeBlock.useRenamingCondition(s);
			code.append(s); 
		}
		
		if (!customReturnName.isEmpty()) {
			var end = code.lastIndexOf("return");
			var newCode = code.toString().substring(0, end);
			newCode += "result = " +  customReturnName + ";\n" 
			+ "\t\t" + code.toString().substring(end, code.toString().length());
			code = new StringBuffer(newCode);
		}
		
		
		//Pattern void_pattern = Pattern.compile("(?<![a-zA-Z0-9])(void)(?![a-zA-Z0-9])");
		//Pattern return_pattern = Pattern.compile("(?<![a-zA-Z0-9])(return)(?![a-zA-Z0-9])");
		var methodCode = code.toString().substring(code.indexOf(signatureString), code.length());
		int closingBracket = methodCode.contains("}") ? methodCode.lastIndexOf('}') : 0;
		var lastPartOfMethod = methodCode.substring(closingBracket, methodCode.length());
		if (returnVariable != null && !lastPartOfMethod.contains("return result")) {
			//if (!void_pattern.matcher(signatureString).find() && !return_pattern.matcher(code.toString()).find()) {
			var splitter = code.toString().trim().split(";");
			if (!splitter[splitter.length - 1].contains("return")) {
				code.append("\t\treturn " + returnVariable.getName().split("\\s")[1] + ";\n");
			}
			//} else if (code.toString().)
		}
		code.append("\t}");//}
		
		//code = insertSeparator(code);
		
		if (!onlyMethod || !existingCode.equals("")) {
			// close class
			code.append("\n}");
		}

		returnVariable = null;
		return code.toString();
	}
	
	public boolean writeToFile(String className, String content) {
		try {
			var dir = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests");	
			if (!dir.exists()) {
				dir.mkdirs();
			}
			var javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className + ".java");
			if (className.contains(".xml")) {
				javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className);
			}
			if (!javaFile.exists()) {
				javaFile.createNewFile();
			} 
			Files.write(Paths.get(javaFile.getPath()), new byte[] {}, StandardOpenOption.TRUNCATE_EXISTING);
			Files.write(Paths.get(javaFile.getPath()), content.getBytes(), StandardOpenOption.WRITE);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean createFile(String className, String code) {
		try {
			var dir = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests");	
			if (!dir.exists()) {
				dir.mkdirs();
			}
			var javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className + ".java");
			if (!javaFile.exists()){
				javaFile.createNewFile();
		    }
			writeToFile(className, code);
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	private boolean deleteFile(String className) {
		try {
			var dir = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests");	
			if (!dir.exists()) {
				return false;
			}
			var javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className + ".java");
			if (!javaFile.exists()){
				return false;
		    }
			Files.delete(Paths.get(javaFile.getPath()));
			javaFile = new File(FileUtil.getProjectLocation(this.projectPath) + "\\tests\\" + className + ".class");
			if (javaFile.exists()){
				Files.delete(Paths.get(javaFile.getPath()));
		    }
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Generates exactly one instance for the given class *clazz*. 
	 * @param clazz the class to be instantiated with random default data.
	 * @return the string representation for instantiating *clazz* with generated default data as parameters.
	 */
	private String genInstanceForClass(Class<?> clazz, HashMap<String, String> generatedClasses) {
		final String fullyQualifiedName = clazz.getSimpleName();
		final String className = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf(".") + 1, fullyQualifiedName.length());
		String output = "new " + className + "(";
		if (clazz.getDeclaredFields().length == 0) {
			return output + ")";
		}
			
		if (generatedClasses.get(className) != null) {
			return generatedClasses.get(className);
		}
		for (var field : clazz.getDeclaredFields()) {
			var name = field.getName();
			var type = field.getType().getSimpleName();
			if (type.contains(";")) {
				for (int i = type.length() - 1; i > 0; i--) {
					if (Character.isUpperCase(type.charAt(i))) {
						type = type.substring(i, type.length() - 1);
						break;
					}
				}
			}
			String variable = type + " " + name;
			if (generatedClasses.get(className) == null) {
				generatedClasses.put(className, "");
			}
			output += getRandomInput(genDefaultInputForVar(variable, generatedClasses)) + ", ";
		}
		output = output.substring(0, output.length() - 2) + ")";
		if (output.contains(INSTANCE_TOKEN)) {
			var toReplace = output.substring(output.indexOf(INSTANCE_TOKEN), output.indexOf(INSTANCE_CLOSED_TOKEN) + INSTANCE_CLOSED_TOKEN.length());
			var instanceName = toReplace.substring(INSTANCE_TOKEN.length(), toReplace.length() - INSTANCE_CLOSED_TOKEN.length());
			if (!generatedClasses.get(instanceName).isEmpty()) {
				output = output.replace(toReplace, generatedClasses.get(instanceName));
			}
		}
		generatedClasses.put(className, output);
		return output;
	}
	
	/**
	 * Searches for *className* in the current project and tries to load the class.
	 * @param className the name of the class to find.
	 * @return the loaded class.
	 */
	private Class<?> loadClassFromProject(String className) {
		// TODO: this should search all subfolders of the project path instead of hardcoded subfolders
		// because projects could use different subfolder names/structure.
		className = className.replaceAll("\\[\\.*\\]", "");
		final String loaderPath = FileUtil.getProjectLocation(this.projectPath) + "/";
		Class<?> clazz;
		String path = loaderPath + "tests/" + className + ".class";
		URL[] urls = null;
		try {
			urls = new URL[]{new URL("file://" + loaderPath + "tests/")};
		} catch (MalformedURLException e) {
			Console.println(e.getMessage());
		}
		URLClassLoader cl = new URLClassLoader(urls, getClass().getClassLoader());
		
		// (1) the class was already generated
		File f = new File(path);
		if (f.exists()) {
			try {
				clazz = Class.forName(className, false, cl);
				return clazz;
			} catch (ClassNotFoundException e) {}
		}
		try {
			urls = new URL[]{new URL("file://" + loaderPath + "bin/")};
		} catch (MalformedURLException e) {
			Console.println(e.getMessage());
		}
		cl = new URLClassLoader(urls, getClass().getClassLoader());
		path = loaderPath + "bin/" + className + ".class";
		f = new File(path);
		if (f.exists()) {
			try {
				clazz = Class.forName(className, false, cl);
				return clazz;
			} catch (ClassNotFoundException e) {
				Console.println(e.getMessage());
			}
		}
		// should never happen.
		return null;
	}
	
	private List<String> genDefaultInputForVar(final String v, HashMap<String, String> generatedClasses) {
		final String actualType = v.split("\\s")[0];
		String type = actualType.toLowerCase();
		String name = v.split("\\s")[1];
		
		if (generatedClasses == null) {
			generatedClasses = new HashMap<String, String>();
		}
		if (generatedClasses.get(actualType) != null) {
			if (generatedClasses.get(actualType).isEmpty()) {
				return Arrays.asList(INSTANCE_TOKEN + actualType + INSTANCE_CLOSED_TOKEN);
			}
			return Arrays.asList(generatedClasses.get(actualType));
		}	
		
		if (type.contains("[")) {
			final InputData data = new InputData(name, actualType);
			data.setDefaultValues();
			if (data.isPrimitive()) {
				return Arrays.asList(data.getType() + ";" + data.getName() + ARRAY_TOKEN + data.getArrayValueRep() + ARRAY_CLOSED_TOKEN);	
			} else {
				return Arrays.asList(data.getArrayRep());
			}
		}
		
		if (type.contains("byte")) {
				return Arrays.asList("" + Byte.MIN_VALUE, "" + -1, "" + 0, "" + 1, "" + Byte.MAX_VALUE);
		} else if (type.contains("int")) {
				return Arrays.asList("" + Integer.MIN_VALUE, "" + -1, "" + 0, "" + 1, "" + Integer.MAX_VALUE);
		} else if (type.contains("short")) {
			return Arrays.asList("" + Short.MIN_VALUE, "" + -1, "" + 0, "" + 1, "" + Short.MAX_VALUE);
		} else if (type.contains("long")) {
			return Arrays.asList("" + Long.MIN_VALUE, "" + -1, "" + 0, "" + 1, "" + Long.MAX_VALUE);
		} else if (type.contains("boolean")) {
			if (type.contains("[")) return Arrays.asList("{false}", "{true, false}", "{0}", "{true}", "{false, true}");
			return Arrays.asList("false", "true");
		} else if (type.contains("char")) {
			if (type.contains("[")) return Arrays.asList("{\' \'}", "{\'x\'}", "{\'x\', \'y\'}", "{\'x\', \'y\', \'z\'}", "{\'x\', \'y\', \'z\', \'x\'}");
			return Arrays.asList("\' \'", "\'x\'", "\'1\'", "\'@\'", "\';\'");
		} else if (type.contains("string")) {
			if (type.contains("[")) return Arrays.asList("{\"\"}", "{\"x\"}", "{\"x\", \"y\"}", "{\"x\", \"y\"}", "{\"x\", \"y\", \"z\"}", "{\"x\", \"y\", \"z\", \"x\"}");
			return Arrays.asList("\"\"", "\"x\"", "\"xy\"", "\"1xy\"", "\"1xy@;\"");
		} else {
			// means it is a complex data type. Use reflection.
			var clazz = loadClassFromProject(actualType);
			var input = genInstanceForClass(clazz, generatedClasses);
			return Arrays.asList(input);
		}
	}
	
	private int findMethodEndIndex(String code, String signature) {
		assert(code.contains(signature));
		int bracketDiff = 1;
		int start = code.indexOf(signature) + signature.length();
		int i = 0;
		
		
		code = code.substring(start, code.length());
		start += code.indexOf("\n");
		code = code.substring(code.indexOf("\n"), code.length());
		
		while (bracketDiff != 0 && i < code.length()) {
			var curChar = code.charAt(i);
			if (curChar == '{') {
				bracketDiff++;
			} else if (curChar == '}') {
				bracketDiff--;
			}
			i++;
		}
		if (bracketDiff == 0) {
			return i + start;
		} else {
			return -1;
		}
		
	}
	
	private List<String> getUsedVars(final String code, final String signature, final JavaVariables vars) throws TestAndAssertionGeneratorException {
		assert(code.contains(signature));
		var usedVars = new ArrayList<String>();
		String relevantCode = "";
		if (code.indexOf(signature) == -1) {
			//Console.println("TestAndAssertionGeneratorError: The signature " + signature + " couldn't be found.");
			throw new TestAndAssertionGeneratorException("The signature " + signature + " couldn't be found.");
			//return null;
		}
		relevantCode = code.substring(code.indexOf(signature), code.length());
		int methodEndIndex = findMethodEndIndex(relevantCode, signature);
		relevantCode = relevantCode.substring(0, methodEndIndex);
		
		for(Field v : vars.getFields()) {
			if (relevantCode.indexOf(v.getName()) != -1) {
				usedVars.add(v.getType() + " " + v.getName());
			}
		}
		usedVars.addAll(vars.getParams()
				.stream()
				.map(v -> v.getType() + " " + v.getName())
				.toList());
		usedVars.addAll(vars.getVariables().stream()
				.filter(v -> v.getKind()
						.toString()
						.equals("PARAM"))
				.map(v -> v.getName())
				.toList());
		return usedVars;
	}
		
	private String getRandomInput(final List<String> lst) {
		if (lst == null) return ""; // happens when a none primitive data type is present
		final StringBuffer content = new StringBuffer();
		final Random rng = new Random();
		int rngNr;
		String concatLst;	
		
		
		lst.stream().forEach(s -> content.append(s + " "));
		concatLst = content.toString();
		if (concatLst.contains(INSTANCE_TOKEN)) {
			var toReplace = concatLst.substring(concatLst.indexOf(INSTANCE_TOKEN), concatLst.indexOf(INSTANCE_CLOSED_TOKEN) + INSTANCE_CLOSED_TOKEN.length());
			var instanceName = toReplace.substring(INSTANCE_TOKEN.length(), toReplace.length() - INSTANCE_CLOSED_TOKEN.length());
			return "new " + instanceName + "()";
		}
		rngNr = rng.nextInt(lst.size());
		return lst.get(rngNr);
	}
		
	private int dataContainsVar(final List<InputData> data, final String varName) {
		if (data == null) {
			return -1;
		}
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i).getName().equals(varName)) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * Generates InputDataTupels. The amount is determined by two factors. First, the amount of used variables n inside the 
	 * method under test. Second, the amount of values for each used variable k_i. Total amount of InputDataTupels and therefore test cases is sum_0^n(k_i).
	 * @param preconditions
	 * @param vars
	 * @param code
	 * @param signature
	 * @param returnVariable
	 * @return
	 * @throws TestAndAssertionGeneratorException 
	 * @throws PreConditionSolverException 
	 */
	public List<TestCaseData> genInputs(final String preconditions, final JavaVariables vars, final String code, final String signature, final JavaVariable returnVariable) throws TestAndAssertionGeneratorException, PreConditionSolverException {
		List<String> globalVarsOfClass = new ArrayList<String>();
		List<String> usedVars;
		List<String> params;
		final List<String> allVars = new ArrayList<String>();
		int counter;		
		// maps testcaseName 
		final var output = new ArrayList<TestCaseData>();
		
		// this case happens when the precondition is just 'true' and there are no invariants
		vars.getFields().stream()
		.forEach(f -> globalVarsOfClass.add(f.getType() + " " + f.getName()));
		
		params = new ArrayList<String>();
		if (vars.getParams() != null) {
			vars.getParams().stream().forEach(p -> 
			{
				params.add(p.getType() + " " + p.getName());
			});
			params.addAll(vars.getVariables().stream()
					.filter(v -> v.getKind()
							.toString()
							.equals("PARAM") &&
							!params.contains(v.getName()))
					.map(v -> v.getName())
					.toList());
		}
		
		allVars.addAll(globalVarsOfClass);
		allVars.addAll(params);
		
		// generate only all combinations of inputs for used variables inside the method for all non used just use some value out of the list
		usedVars = getUsedVars(code, signature, vars);
		
		String returnType;
		String returnVar;
		if (returnVariable == null) {
			returnType = "void";
			returnVar = ""; //TODO: set this to the last variable used in a return statement.
		}
		else {
			returnType = returnVariable.getName().split("\\s")[0];
			returnVar = "result";
		}
			

		// get data from solver for vars that have boundaries defined by preconditions
		PreConditionSolver solver = null;
		List<InputData> solverData = null;
		if (!preconditions.isEmpty()) {
			solver = new PreConditionSolver(vars);
			solver.showWarnings(false);
			try {
				solverData = solver.solve(preconditions);
			} catch (PreConditionSolverException e) {
				throw e;
			}
		}
		// for every variable that is inside solverData, use those values, for every other variable use default values.
		counter = 0;
		for(var usedVar : usedVars) {
			var varName = usedVar.split("\\s")[1];
			var usedVarData = new InputData(usedVar, usedVar.split("\\s")[0]);
			int index;
			if ((index = dataContainsVar(solverData, varName)) == -1) {
				usedVarData.setDefaultValues(); 
			} else {
				usedVarData.setValues(solverData.get(index).getValues());
			}
			for (int x = 0; x < usedVarData.getValues().length; x++) {
				TestCaseData t = new TestCaseData(getMethodName(signature), counter, returnType, returnVar);
				InputData input;
				InputDataTupel data = new InputDataTupel();
				for (var v : allVars) {
					var name = v.split("\\s")[1];
					var type = v.split("\\s")[0];
					int dimensions = (int)type.chars().filter(c -> c == '[').count();
					input = new InputData(name, type);
					if ((index = dataContainsVar(solverData, name)) == -1) {
						input.setDefaultValues();
					} else {
						input.setValues(solverData.get(index).getValues());
					}
					if (!isBuiltInType(type) && usedVars.contains(v)) {
						// need to initialize all variables	
						if (v.contains("[")) {
							if (v.lastIndexOf("]") == -1) {
								if (showWarnings) {
									Console.println("TestAndAssertionGeneratorWarning: Syntax of variable '" + v + "' is not correct.");
								}
								Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Syntax of variable '" + v + "' is not correct.");
								continue;
							}
							v = v.substring(0, v.indexOf("[")) + v.substring(v.lastIndexOf("]") + 1, v.length());
						}
						var lst = genDefaultInputForVar(v, null);
						String[] values = new String[lst.size()];
						for (int i = 0; i < lst.size(); i++) values[i] = lst.get(i);
						input.setValues(values);
					}
					if (dimensions == 0) {
						input.getRandomValue();
					} else if (input.isPrimitive()) {
						input.getPrimitiveArrayInit();
					} else {
						input.getArrayRep();
					}
					if (params.contains(type + " " + name)) {
						data.addParameter(input);
					} else {
						data.addGlobalVar(input);
					}
				}
				t.setInputDataTupel(data);
				output.add(t);
				counter++;
			}
		}
		return output;
	}
	
	public static int countBrackets(String condition, char bracket) {
		char closingBracket;
		int output = 0;
		
		if (bracket == '(') closingBracket = ')';
		else if (bracket == '[') closingBracket = ']';
		else closingBracket = '}';
		
		for (int i = 0; i < condition.length(); i++) {
			if (condition.charAt(i) == bracket) {
				output++;
			} else if (condition.charAt(i) == closingBracket) {
				output--;
			}
		}
		return output;
	}
	
	private static String replacePrimitives(String input) {
		input = input.replace("instanceof byte", "instanceof Byte");
		input = input.replace("instanceof short", "instanceof Short");
		input = input.replace("instanceof int", "instanceof Integer");
		input = input.replace("instanceof long", "instanceof Long");
		input = input.replace("instanceof char", "instanceof Character");
		input = input.replace("instanceof string", "instanceof String");
		input = input.replace("instanceof boolean", "instanceof Boolean");
		return input;
	}
	
	private static String addInstanceNameToMethod(String condition, final String instanceName, final List<String> methodLst) {
		final var methods = new ArrayList<String>();
		methods.addAll(methodLst);
		int startIndex = 0;
		int offset = 0;
		for (int m = 0; m < methods.size(); m++) {
			var curMethod = methods.get(m);
			String params = curMethod.substring(curMethod.indexOf('('), curMethod.indexOf(')') + 1);
			String methodName = curMethod.split("\\s")[2];
			methodName = methodName.substring(0, methodName.indexOf('('));
			methodName += params;
			methods.set(m, methodName.trim());
		}
		for (var method : methods) {
			Pattern p = Pattern.compile(Pattern.quote(method));
			Matcher m = p.matcher(condition);
			while (m.find()) {
				startIndex = m.start() + offset;
				if (startIndex == 0 || condition.charAt(startIndex - 1) != '.') {			
					// make sure the string is not a substring of a bigger word
					if (startIndex > 0) {
						if (Character.isAlphabetic(condition.charAt(startIndex - 1))) continue;
					}
					if (m.end() + 1 < condition.length()) {
						if (Character.isAlphabetic(condition.charAt(m.end() + 1))) continue;
					}
					var prevLen = condition.length();
					condition = condition.substring(0, startIndex) 
							+ instanceName + "." + m.group(0) + condition.substring(m.end(), condition.length());
					offset += condition.length() - prevLen;
				}				
			}
			offset = 0;
		}
		return condition;
	}
	
	private static String addInstanceNameToVar(String condition, final String instanceName, final List<InputData> globalVars) {
		for (var globalVar: globalVars) {
			Pattern p = Pattern.compile("^" + Pattern.quote(globalVar.getName()) + "\\W|\\W" + Pattern.quote(globalVar.getName()) + "$|" + "\\W" + Pattern.quote(globalVar.getName()) + "\\W");
			Matcher m = p.matcher(condition);
			int start = -1;
			
			while (m.find()) {
				if (condition.charAt(m.start()) == '.') continue;
				if (m.start() == 0) {
					if (Character.isAlphabetic(condition.charAt(m.start()))) {
						start = 0;
					} else {
						start = 1;
					}
					condition = condition.substring(0, start) + instanceName + "." + m.group(0).substring(start, m.end() - 1) + condition.substring(m.end() - 1, condition.length());
				} else {
					if (condition.substring(0, m.start() + 1).endsWith("old(")) continue;
					condition = condition.substring(0, m.start() + 1) 
							+ instanceName + "." + condition.substring(m.start() + 1, m.end() - 1) + condition.substring(m.end() - 1, condition.length());	
				}
				m = p.matcher(condition);
			}
		}
		return condition;
	}
	
	private static String replaceMathFuncs(String c) {
		Pattern p = Pattern.compile("max\\([^\\,]+\\,\\s*[^\\,]+\\,\\s*[^\\,]+\\,\\s*[^\\,\\)]+\\)");
		Matcher m = p.matcher(c);
		
		while (m.find()) {
			final String innerPart = m.group(0).substring(m.group(0).indexOf("(") + 1, m.group(0).indexOf(")"));
			final String[] params = innerPart.split("\\,");
			c = c.substring(0, m.start()) + "IntStream.of(" + params[0].trim() + ").boxed().toList().subList(" + params[1].strip() + ", " + params[2].strip() + ").stream().max(Integer::compare).get() == " 
			+ params[0].trim() 
			+ "[" + params[3].trim() 
			+ "]" + c.substring(m.end(), c.length());
			m = p.matcher(c);
		}
		
		p = Pattern.compile("appears\\([^\\,]+\\,\\s*[^\\,]+\\,\\s*[^\\,]+\\,\\s*[^\\,\\)]+\\)");
		m = p.matcher(c);		
		while (m.find()) {
			final String innerPart = m.group(0).substring(m.group(0).indexOf("(") + 1, m.group(0).indexOf(")"));
			final String[] params = innerPart.split("\\,");
			c = c.substring(0, m.start()) + "IntStream.of(" + params[0].strip() + ")" 
			+ ".boxed().toList().subList(" + params[2].strip() + ", " + params[3].strip() 
			+ ").contains(" + params[1].strip() + ")" 
			+ c.substring(m.end(), c.length());
			m = p.matcher(c);
		}
		
		p = Pattern.compile("pow\\([^\\,]+\\,\\s*[^\\,]+\\)");
		m = p.matcher(c);		
		while (m.find()) {
			if (m.start() > 0 && c.charAt(m.start() - 1) == '.') {
				continue;
			}
			final String innerPart = m.group(0).substring(m.group(0).indexOf("(") + 1, m.group(0).indexOf(")"));
			c = c.substring(0, m.start()) + "Math.pow(" + innerPart + ")" + c.substring(m.end(), c.length());
			m = p.matcher(c);
		}		
		return c;
	}
		
	public String translateCondition(final String condition, String instanceName, List<InputData> gVars, boolean replaceMathFuncs) {
		String c = condition;
		//c = c.replaceAll("[a-z]\\w*\\.", ""); 

		if (c.contains("modifiable")) {
			c = c.split("\\;", 2)[1].trim();
		}
		//c = c.replaceAll("this\\.\\s*", instanceName + "\\."); 
		c = c.replaceAll("(?<!<|>|!|=)(\\s*=\\s*)(?!<|>|=)", " == ");
		c = c.replaceAll("(?<==\\s?)TRUE", "true");
		c = c.replaceAll("(?<==\\s?)FALSE", "false");
		c = c.replaceAll(".<created>\\s*=\\s*TRUE", " != null");
		c = c.replaceAll(".<created>\\s*=\\s*FALSE", " == null");
		//c = c.replaceAll("this\\.", instanceName + ".");
		c = replacePrimitives(c);
		if (replaceMathFuncs) {
			c = replaceMathFuncs(c);
		}
		// place instanceName before each gVar and method
		if (!instanceName.isEmpty()) {
			c = c.replaceAll("(\\W)this(\\W)", "$1" + instanceName + "$2");
			c = c.replaceAll("this$", instanceName);
			c = c.replaceAll("^this", instanceName);
			c = addInstanceNameToVar(c, instanceName, gVars);
			var className = Character.toUpperCase(instanceName.charAt(0)) + instanceName.substring(1, instanceName.length());
			c = addInstanceNameToMethod(c, instanceName, getAllMethodsOfClass(className, projectPath));
		}
		return c;
	}
	
	public String translateCondition(final String condition, String instanceName, List<InputData> gVars) {
		return translateCondition(condition, instanceName, gVars, true);
	}
	
	public Node parseCondition(final String condition, String instanceName, List<InputData> gVars) {
		String c = translateCondition(condition, instanceName, gVars);
		ConditionParser parser = new ConditionParser();
		var tree = parser.parse(c);
		return tree;
	}

	/**
	 * Translates condition from JavaDL into Java and returns the resulting java condition.
	 * @param condition
	 * @param instanceName
	 * @param gVars
	 * @param projectPath
	 * @return
	 */
	public JavaCondition translateConditionToJava(String condition, String instanceName, List<InputData> gVars, final URI projectPath) {
		var tree = parseCondition(condition, instanceName, gVars);
		JavaCondition javaCondition = new JavaCondition(tree);
		return javaCondition;
	}
	
	/**
	 * Splits toSplit on delimitor *delimitorRegEx* but ignores all terms inside brackets.
	 * @param toSplit
	 * @param delimitorRegEx
	 * @return
	 */
	public static String[] sSplit(String toSplit, String delimitorRegEx) {
		final String[] output;
		final var calls = new ArrayList<String>();
		String c;
		var helper = toSplit;
		int counter = 0;
		
		
		while (helper.contains("(")) {
			if (!helper.contains(")")) {
				break;
			}
			c = helper.substring(helper.indexOf('('), helper.indexOf(')') + 1);
			helper = helper.replace(c, "");
			toSplit = toSplit.replace(c, "<$" + counter + ">");
			calls.add(c);
			counter++;
		}
		output = toSplit.split(delimitorRegEx);
		for (int i = 0; i < calls.size(); i++) {
			for (int j = 0; j < output.length; j++) {
				output[j] = output[j].replace("<$" + i + ">", calls.get(i));
			}
		}	
		return output;
	}
	
	public static String initializeParams(String methodCall, int numTabs) {
		// format of methodcall is: <methodname>(<type> <varname> = <value>, ...)
		var output = "";
		var tabs = "";
		var noBracketsStr = methodCall.substring(methodCall.indexOf("(") + 1, methodCall.length());	
		final String[] params;
		noBracketsStr = noBracketsStr.substring(0, noBracketsStr.length() - 1);	
		if (noBracketsStr.equals("")) return "";
		if (noBracketsStr.contains("(")) {
			// handle object initialization
			params = sSplit(noBracketsStr, "\\,\\s");
		} else {
			params = noBracketsStr.split("\\,\\s");
		}
		
		
		for (int i = 0; i < numTabs; i++) tabs += "\t";
		for (var param : params) {
			output += tabs + param + ";\n";
		}
		return output;
	}
	
	private String initializeOldVars(final String postCondition, String classVarStr, int numTabs, List<String> paramNames) {
		// TODO: old keyword can be used for none instance variables as well like for parameters for example. (example diagram: transfer)
		String output = "";
		var tabs = "";
		var postConditionStr = postCondition;
		String oldVarStr;
		int varEndIndex;
		int indexOfOld = postConditionStr.indexOf("old_");
		String varStr;
		
		
		for (int i = 0; i < numTabs; i++) tabs += "\t";
		
		while (indexOfOld != -1) {
			oldVarStr = postConditionStr.substring(indexOfOld, postConditionStr.length());
			varEndIndex = oldVarStr.split("[^\\w]")[0].length();
			if (varEndIndex == -1) {
				oldVarStr = oldVarStr.substring(0, oldVarStr.length());
				postConditionStr = "";
			}
			else {
				oldVarStr = oldVarStr.substring(0, varEndIndex);
				postConditionStr = postConditionStr.substring(indexOfOld + varEndIndex, postConditionStr.length());
			}
			if (!output.contains(oldVarStr)) {
				varStr = oldVarStr.split("old\\_")[1];
				if (paramNames.contains(varStr) || varStr.toLowerCase().equals(classVarStr)) {
					output += tabs + "var " + oldVarStr + " = " + varStr.toLowerCase() + ";\n";
				} else {
					output += tabs + "var " + oldVarStr + " = " + classVarStr + "." + varStr + ";\n";
				}
			}
			indexOfOld = postConditionStr.indexOf("old_");
		}
		output += "\n";
		return output;
	}
		
	public static int findClosingBracketIndex(final String code, final int bracketIndex, char bracket) {
		char closingBracket;
		int bracketCounter = 1;
		
		if (bracket == '(') closingBracket = ')';
		else if (bracket == '[') closingBracket = ']';
		else if (bracket == '{') closingBracket = '}';
		else closingBracket = ')';
		
		
		for (int i = bracketIndex + 1; i < code.length(); i++) {
			if (code.charAt(i) == bracket) {
				bracketCounter++;
			} else if (code.charAt(i) == closingBracket) {
				bracketCounter--;
			}
			if (bracketCounter == 0) {
				//var tmp = code.substring(bracketIndex, i + 1);
				return i;
			}
		}
		return -1;
	}
	
	private static List<String> getAllMethodsOfCode(final String code) {
		final var output = new ArrayList<String>();
		int startingBracketIndex;
		int closingBracketIndex;
		Pattern p = Pattern.compile("\\w+\\s\\w+\\s\\w+\\(.*\\)\\s*\\{");
		Matcher m = p.matcher(code);
		
		
		while (m.find()) {
			startingBracketIndex =  m.start() + m.group(0).indexOf('{');
			closingBracketIndex = findClosingBracketIndex(code, startingBracketIndex, '{');
			output.add(code.substring(m.start(), closingBracketIndex + 1));
		}
		return output;
	}
	
	private static List<String> getAllMethodsOfClass(final String className, final URI projectPath) {
		final var output = new ArrayList<String>();
		final var javaFile = new File(FileUtil.getProjectLocation(projectPath) + "\\tests\\" + className + ".java");
		
		
		if (!javaFile.exists()){
			return output;
	    }
		try {			
			var code = Files.readString(javaFile.toPath());
			return getAllMethodsOfCode(code);
		} catch (IOException e) {
			Console.println(e.getMessage());
		}
		return null;
	}
	
	public static List<String> getVarNames(final String assertion, final List<String> globalVarNames, final List<String> parameterVarNames, final String instanceName) {
		var output = new ArrayList<String>();
		
		for (var v : globalVarNames) {
			if (assertion.contains(v)) {
				output.add(instanceName + "." + v);
			}
		}
		
		for (var v : parameterVarNames) {
			if (assertion.contains(v)) {
				output.add(v);
			}
		}
		return output;
	}
	
	private String genTestCases(final String className, final List<TestCaseData> inputs, final Condition postCondition, final GlobalConditions conds, final CbCFormula formula) throws TestStatementException {
		var instanceName = Character.toLowerCase(className.charAt(0)) + className.substring(1, className.length());
		if (inputs.isEmpty()) {
			Console.println("TestAndAssertionInfo: No input data was generated.");
			return "";
		}
		var translatedPostCondition = translateConditionToJava(postCondition.getName().trim(), instanceName, inputs.get(0).getInputDataTupel().getGlobalVars(), this.projectPath);
		var allAttrNames = new ArrayList<String>();
				
		StringBuffer code = new StringBuffer();
		code.append("import java.util.stream.IntStream;\n");
		code.append("import org.testng.ITestContext;\n");
		code.append("import org.testng.Assert;\n");
		code.append("import org.testng.annotations.Test;\n\n");

		code.append("public class " + className + "Test {\n");
		code.append("\t" + "private " + className + " " + instanceName + ";\n\n");
		for (var test : inputs) {
			code.append("\t@Test(singleThreaded = true)\n\t" + "public void " + test.getName() + "(ITestContext context) {\n");

			// add primitive arrays needed for class instatiation
			for (InputData data : test.getInputDataTupel().getAllVars()) {
				if (data.getRep().contains(ARRAY_TOKEN)) {
					//private String handlePrimitiveArrayUses(String output, String fullVarName, String val) 
					var toAdd = handlePrimitiveArrayUses("", data.getType() + " " + data.getName(), data.getRep(), 2);
					var lines = toAdd.split("\\t\\t");
					var newRep = lines[lines.length-1].substring(lines[lines.length-1].indexOf("new"), lines[lines.length-1].lastIndexOf(";"));
					data.setRep(newRep);
					for (int i = 0; i < lines.length - 1; i++) {
						if (lines[i].isBlank()) {
							continue;
						}
						code.append(lines[i] + "\n");
					}
				}
				if (data.isPrimitive() && data.getDimensions() > 0) {
					code.append("\t\t" + data.getPrimitiveArrayInit() + ";\n");
				}
			}
			
			code.append("\t\t" + instanceName + " = new " + className + test.getInputDataTupel().getGlobalVarsRep() + ";\n");		
			
			// add variables to test for each parameter of the method
			for (InputData param : test.getInputDataTupel().getParameters()) {
				if (!param.isPrimitive() || param.getDimensions() == 0) {
					code.append("\t\t" + param.getType() + " " + param.getName() + " = " + param.getRep() + ";\n");
				}
			}

			// generate variables for old vars
			var oldVarsStr = initializeOldVars(translatedPostCondition.getRep(), instanceName, 2, test.getInputDataTupel().getParameterNames());
			if (!oldVarsStr.equals("")) {
				code.append(oldVarsStr);
			} else {
				code.append("\n");
			}
			
			// add precondition checks
			var programPreConsStr = formula.getStatement().getPreCondition().getName();
			var programPreCons = translateConditionToJava(programPreConsStr, instanceName, test.getInputDataTupel().getGlobalVars(), this.projectPath);
			try {
				code.append(TestStatement.insertPreconditionChecks("\n\n", programPreCons, 2).replaceFirst("\\n", ""));
			} catch (TestStatementException e) {
				throw e;
			}
			
			// add method call
			if (test.getReturnType().equals("void")) {
				code.append("\t\t" + instanceName + "." + test.getTesteeName() + test.getInputDataTupel().getParametersNameRep() + ";\n\n");
			} else {
				code.append("\t\t" + test.getReturnType() + " result = " + instanceName + "." + test.getTesteeName() + test.getInputDataTupel().getParametersNameRep() + ";\n");
				code.append("\t\t" + "context.setAttribute(\"" + test.getTestNumber() + "result\", result);\n\n");
				allAttrNames.add("result");
			}
				
			int branchCounter = 0;
			var branch = translatedPostCondition.getNext();
			while (branch != null) {
				if (branch.getType() == BranchType.NONE) {
					for (var assertion : branch.getAssertions()) {
						List<String> varNames = getVarNames(assertion, inputs.get(0).getInputDataTupel().getGlobalVarNames(), inputs.get(0).getInputDataTupel().getParameterNames(), instanceName);
						for (var v : varNames) {
							code.append("\t\t" + "context.setAttribute(\"" + test.getTestNumber() + v + "\", " + v + ");\n");
							allAttrNames.add(v);
						}
						code.append("\t\t" + "Assert.assertTrue(" + assertion + ");\n");
					}
				} else if (branch.getType() == BranchType.IMPL) {
					code.append("\t\t" + "if (" + branch.getBranchCondition() + ") {\n");
					code.append("\t\t\t" + "context.setAttribute(\"" + test.getTestNumber() + "branch" + branchCounter + "\", \"" + branch.getBranchCondition() + "\");\n");
					for (var assertion : branch.getAssertions()) {
						List<String> varNames = getVarNames(assertion, inputs.get(0).getInputDataTupel().getGlobalVarNames(), inputs.get(0).getInputDataTupel().getParameterNames(), instanceName);
						for (var v : varNames) {
							code.append("\t\t\t" + "context.setAttribute(\"" + test.getTestNumber() + v + "\", " + v + ");\n");
							allAttrNames.add(v);
						}
						code.append("\t\t\t" + "Assert.assertTrue(" + assertion + ");\n");
					}
					code.append("\t\t}\n");
				} else if (branch.getType() == BranchType.EQUI) {
					String otherCondition; 
					try {
						otherCondition = branch.getAssertions().stream().reduce((f, s) -> f + " && " + s).get();
					} catch(NoSuchElementException e) {
						return null;
					}
					
					code.append("\t\t" + "if (" + branch.getBranchCondition() + ") {\n");
					code.append("\t\t\t" + "context.setAttribute(\"" + test.getTestNumber() + "branch" + branchCounter + "\", \"" + branch.getBranchCondition() + "\");\n");
					for (var assertion : branch.getAssertions()) {
						List<String> varNames = getVarNames(assertion, inputs.get(0).getInputDataTupel().getGlobalVarNames(), inputs.get(0).getInputDataTupel().getParameterNames(), instanceName);
						for (var v : varNames) {
							code.append("\t\t\t" + "context.setAttribute(\"" + test.getTestNumber() + v + "\", " + v + ");\n");
							allAttrNames.add(v);
						}
						code.append("\t\t\t" + "Assert.assertTrue(" + assertion + ");\n");
					}
					code.append("\t\t}\n");
					branchCounter++;			
					code.append("\t\t" + "if (" + otherCondition + ") {\n");
					code.append("\t\t\t" + "context.setAttribute(\"" + test.getTestNumber() + "branch" + branchCounter + "\", \"" + otherCondition + "\");\n");
					code.append("\t\t\t" + "Assert.assertTrue(" + branch.getBranchCondition() + ");\n");
					code.append("\t\t}\n");
				} else if (branch.getType() == BranchType.EXISTS) {
					code.append("\t\t" + "boolean exists = false;\n");
					code.append("\t\t" + "for (" + branch.getIterType() + " " + branch.getIterName() + " = " 
							+ TestStatement.getWrapper(branch.getIterType()) + ".MIN_VALUE; " + branch.getIterName() + " < " + TestStatement.getWrapper(branch.getIterType()) + ".MAX_VALUE; " 
							+ branch.getIterName() + "++" + ")" + "{\n");
					code.append("\t\t\t" + "if (" + branch.getIterConditions().stream().reduce((f, s) -> f + " && " + s).get() + ") {\n");
					code.append(branch.getQuantorBodyConditions().stream().map(c -> "\t\t" + "\t\t" + "if(" + c + ") exists = true; break;\n").reduce((f, s) -> f + s).get());
					code.append("\t\t" + "\t" + "}\n " + "\t\t" + "}\n");
					code.append("\t\t" + "Assert.assertTrue(exists);\n");				
				} else if (branch.getType() == BranchType.FORALL) {
					code.append("\t\t" + "for (" + branch.getIterType() + " " + branch.getIterName() + " = " 
							+ TestStatement.getWrapper(branch.getIterType()) + ".MIN_VALUE; " + branch.getIterName() + " < " + TestStatement.getWrapper(branch.getIterType()) + ".MAX_VALUE; " 
							+ branch.getIterName() + "++" + ")" + "{\n");
					code.append("\t\t\t" + "if (" + branch.getIterConditions().stream().reduce((f, s) -> f + " && " + s).get() + ") {\n");
					code.append(branch.getQuantorBodyConditions().stream().map(c -> "\t\t" + "\t\t" + "Assert.assertTrue(" + c + ");\n").reduce((f, s) -> f + s).get());
					code.append("\t\t" + "\t" + "}\n " + "\t\t" + "}\n");
				}
				branchCounter++;
				branch = translatedPostCondition.getNext();
			}
			code.append("\n\t}\n\n");
		}
		/*
		// append after each method that clears all previous attributes after each method run
		code.append("\t@AfterMethod\n");
		code.append("\tpublic void clearContext(ITestContext context) {\n");
		for (int i = 0; i < inputs.size(); i++) {
			code.append("\t\tcontext.removeAttribute(\"" + inputs.get(i).getName() + "\");\n");
		}
		code.append("\t}\n"); */
		code.append("}\n");
		return code.toString(); 
	}

	public XmlSuite createXmlSuite(final String classPath, final String className) {
		final XmlSuite suite = new XmlSuite();
		URLClassLoader externalClassLoader = null;
		
		suite.setName("AutoSuite");

		try {
			URL[] urls = {new URL(classPath)};
			externalClassLoader = new URLClassLoader(urls, getClass().getClassLoader());		
		} catch (MalformedURLException e1) {
			//log(e1.getMessage());
			return null;
		}	
		 
		XmlTest test = new XmlTest(suite);
		test.setName("Test results for class " + className);
		List<XmlClass> classes = new ArrayList<XmlClass>();
		try {
			XmlClass clazz = new XmlClass(Class.forName(className, false, externalClassLoader));// using reflection here because xmlclass's loader seems to be broken
			classes.add(clazz);
		} catch (ClassNotFoundException e) {
			return null;
		}
		test.setXmlClasses(classes);
		return suite;
	}
	
	private void executeTestCases(final String classPath, final String className, final List<String> globalVars, final List<TestCaseData> inputs) {
		final XmlSuite suite;
		var pathOfPlugins = System.getProperty("osgi.syspath");
		var file = new File(pathOfPlugins);
		List<String> testNgFiles = Arrays.asList(file.list()).stream().filter(s -> s.contains("org.testng_")).toList();
		var highestVersion = testNgFiles.stream().map(s -> s.split("org.testng\\_")[1]).sorted().reduce((first, second) -> second).get();
		highestVersion = "org.testng_" + highestVersion;		
		var splitter = className.split("\\.");
		var actualClassName = splitter[splitter.length - 1];
		
		// compile test file
		var options = Arrays.asList("-cp", pathOfPlugins + "/" + highestVersion);
		if(!compileClass(actualClassName, options, false)) {
			Console.println("Stop testing...");
			return;
		}
		
		// first create the xml suite needed to run TestNG
		suite = createXmlSuite(classPath, className);
	
		
		// now start testng using the custom test listener
		var tla = new TestAndAssertionListener(projectPath, globalVars, inputs);
		List<XmlSuite> suites = new ArrayList<XmlSuite>();
		suite.setParallel(ParallelMode.NONE);
		suites.add(suite);
		TestNG tng = new TestNG();
		tng.setUseDefaultListeners(false);
		tng.setParallel(ParallelMode.NONE);
		tng.setXmlSuites(suites);
		tng.addListener(tla);
		tng.run();
	}
	
	private String genCode(Diagram diagram, boolean onlyMethod) {
		String signatureString;
		final LinkedList<String> localVariables = new LinkedList<String>();
		JavaVariable returnVariable = null;
		JavaVariables vars = null;
		Renaming renaming = null;
		CbCFormula formula = null;
		GlobalConditions globalConditions = null;
		String globalVariables = "";
		int counter = 0;

		for (Shape shape : diagram.getChildren()) {
			Object obj = getBusinessObjectForPictogramElement(shape);
			if (obj instanceof JavaVariables) {
				vars = (JavaVariables) obj;
			} else if (obj instanceof Renaming) {
				renaming = (Renaming) obj;
			} else if (obj instanceof CbCFormula) {
				formula = (CbCFormula) obj;
			} else if (obj instanceof GlobalConditions) {
				globalConditions = (GlobalConditions) obj;
			}
		}
		
		if (formula.getMethodObj() == null) {
			List<String> params = new ArrayList<String>();
			signatureString = formula.getName();
			for (var v : vars.getVariables()) {
				if (v.getKind().toString().equals("PARAM")) {
					params.add(v.getName());
				} else if (v.getKind().toString().equals("RETURN")) {
					signatureString = "public " + v.getName().split("\\s")[0] + " " + signatureString;
				}
			}
			signatureString += "(";
			if (params.size() == 0) {
				signatureString += ")";
			} else {
				for (int i = 0; i < params.size() - 1; i++) {
					signatureString += params.get(i) + ", ";
				}
				signatureString += params.get(params.size() - 1) + ")";
			}
			if (!signatureString.contains("public")) {
				signatureString = "public void " + signatureString;
			}
		} else {
			signatureString = formula.getMethodObj().getSignature();
		}
		
		// TODO: Fix RETURN Variable appearing in parameters
		var customReturnName = "";
		/*
		for (var v : vars.getParams()) {
			final var name = v.getName().replace("non-null", "");
			if (!signatureString.contains(name)) {
				// means this is the RETURN var
				localVariables.add(v.getType() + " " + name);
				if (!name.equals("result")) {
					localVariables.add(v.getType() + " result");
					customReturnName = name;
				}
			}
		}*/
		
		for(int i = 0; i < vars.getVariables().size(); i++) {
			JavaVariable currentVariable = vars.getVariables().get(i);
			if (currentVariable.getKind() == VariableKind.RETURN) {
				var varName = currentVariable.getName().replace("non-null", "").split("\\s")[1];
				var varType = currentVariable.getName().split("\\s")[0];
				if (!varName.equals("result")) {
					localVariables.add(varType + " result");
					customReturnName = varName;
				}
				localVariables.add(currentVariable.getName().replace("non-null", ""));
				counter++;
				if(!signatureString.substring(0, signatureString.indexOf('(')).contains(currentVariable.getName().replace("non-null", "").trim().split("\\s")[0])) {
					Console.println("Method return type and variable type does not match.");
					return "";
				}
				if(counter > 1) {
					Console.println("Too much variables of kind RETURN.");
					return "";
				}
				returnVariable = currentVariable;
			} else if (currentVariable.getKind() == VariableKind.LOCAL) {
				// also handle old_ annotated variables here
				var splitter = currentVariable.getName().split("\\s");
				var varName = splitter[splitter.length - 1];
				if (varName.startsWith("old_")) {
					var modifiedPostCon = formula.getStatement().getPostCondition();
					var newCon = modifiedPostCon.getName();
					var helper = varName.substring("old_".length(), varName.length());
					helper = "old_" + helper.replaceAll("\\_", "\\.");
					newCon = newCon.replaceAll(varName, helper);
					formula.getStatement().getPostCondition().setName(newCon);
				} else {	
					if (!localVariables.contains(currentVariable.getName().replace("non-null", ""))) {
						localVariables.add(currentVariable.getName().replace("non-null", ""));
					}
				}
			}
		}

		for (Field field : vars.getFields()) {
			if (field.getName() != null) {
				if (field.isIsStatic()) {
					globalVariables += ("\t" + field.getVisibility().getName().toLowerCase() + " static " + field.getType() + " " + field.getName().replace("non-null ", "") + ";\n");
				} else {
					globalVariables += ("\t" + field.getVisibility().getName().toLowerCase() + " " + field.getType() + " " + field.getName().replace("non-null ", "") + ";\n");			
				}
			}
		}
		
		return generateCode(formula, globalConditions, renaming, localVariables, returnVariable, signatureString, globalVariables, vars.getFields().stream().map(f -> f.getType() + " " + f.getName()).toList(), onlyMethod, customReturnName);
	}
	
	private String genCode(Diagram diagram) {
		return genCode(diagram, false);
	}
	
	private String insertMethod(String code, final String method, boolean intoClass) {
		if (code.isEmpty()) return "";
		if (code.charAt(code.length() - 1) != '}') return code;
		if (intoClass) {
			code = code.substring(0, code.length() - 1);
		}
		code += "\n\t" + method;
		if (intoClass) {
			code += "\n}";
		}
		return code;
	}
	
	private String insertMethod(String code, final String method) {
		return insertMethod(code, method, true);
	}
			
	/**
	 * Gets every diagram for each method name in diagramNames and creates a map which has every occurring class as key. 
	 * As values each class has a LinkedList which contains all diagrams of it's methods.
	 * @param diagramNames The map of methods mapped to their class.
	 * @return The map.
	 */
	public HashMap<String, LinkedList<Diagram>> getDiagramsFromNames(HashMap<String, String> diagramNames) {
		return getDiagramsFromNames(diagramNames, null);
	}
	
	/**
	 * Gets every diagram for each method name in diagramNames and creates a map which has every occurring class as key. 
	 * As values each class has a LinkedList which contains all diagrams of it's methods.
	 * @param diagramNames The map of methods mapped to their class.
	 * @param allDiagrams A collection of all avaiable diagrams in the project.
	 * @return The map.
	 */
	public HashMap<String, LinkedList<Diagram>> getDiagramsFromNames(HashMap<String, String> diagramNames, final Collection<Diagram> allDiagrams) {
		// method is really slow because getDiagrams takes long
		HashMap<String, LinkedList<Diagram>> output = new HashMap<String, LinkedList<Diagram>>();
		Collection<Diagram> diagrams;
		
		
		if (allDiagrams != null) {
			diagrams = allDiagrams;
		} else {
			diagrams = null;//getDiagrams();
		}	
		for (var diagram: diagrams) {
			if (diagramNames.size() == 0) { 
				return output;
			}
			if(diagramNames.containsKey(diagram.getName())) {
				var className = diagramNames.get(diagram.getName()); 
				if(!output.containsKey(className)) {
					output.put(className, new LinkedList<Diagram>());
				} 
				output.get(className).add(diagram);
				diagramNames.remove(diagram.getName());
			}
		}
		return output;
	}
	
	private boolean isVarType(String potentialType) {
		if (isBuiltInType(potentialType)) {
			return true;
		}
		// look if there is a java file that name or a cbcclass.
		if (getProjectJavaFiles().contains(potentialType)) {
			return true;
		}
		var cbcClasses = FileUtil.getCbCClasses(FileUtil.getProject(projectPath));
		for (var cbcClass : cbcClasses) {
			if (cbcClass.getClass().getName().equals(potentialType)) {
				return true;
			}
		}
		return false;
	}
	
	public String extractVarType(final String code, final String varName) {
		if (!code.contains(varName)) {
			return "";
		}
		final var firstOccurence = code.substring(0, code.indexOf(varName));
		final var typeReversed = new StringBuffer(firstOccurence).reverse().toString().trim().split("\\W", 2)[0];
		final var type = new StringBuffer(typeReversed).reverse().toString();
		if (isVarType(type)) {
			return type;
		}
		return "";
	}
	
	/**
	 * Tries finding the type of variable *varName*.
	 * @param code
	 * @param varName
	 * @param className
	 * @return The type of variable *varName* or empty string if *varName* couldn't be found.
	 */
	private String getVariableType(String code, String varName, String className) {
		// idea: find first occurence of *varName* in *code*. If there is a word x such that: 'x *varName*'
		// we know that x must be a type of x or is the class itself. If there is no such word there are two options:
		// 1. There is no word infront of *varName* => *varName* is a static class variable. Therefore
		// 		it's type is *className*.
		// 2. There is a prefix y such that 'y.x' => if y is 'this' return *className* else return y.
		String varType;
		String classCode;
		String potentialVarType;
		final Pattern p = Pattern.compile("[^\\w]" + Pattern.quote(varName) + "[^\\w]");
		final Matcher m = p.matcher(code);
		
		if (varName.equals("this") || varName.equals("result")) {
			return className;
		}
		
		if (!m.find()) {
			// try searching in the entire class
			classCode = classExists(className);
			return extractVarType(classCode, varName);
		}
		var firstOccurence = code.substring(0, m.start() + (m.end() - m.start() - 1));
		if (firstOccurence.lastIndexOf(' ') == - 1) {
			// no space infront of *varName*
		}
		var foundVarName = firstOccurence.substring(m.start() + 1, firstOccurence.length());
		/*if (foundVarName.contains("(")) {
			foundVarName = foundVarName
		}*/
		if (foundVarName.equals(varName)) {
			potentialVarType = firstOccurence.substring(0, m.start());
			final var potentialTypeReversed = new StringBuffer(potentialVarType).reverse().toString().trim().split("\\W", 2)[0];
			potentialVarType = new StringBuffer(potentialTypeReversed).reverse().toString();
			if (isVarType(potentialVarType)) {
				varType = potentialVarType;
				return varType;
			} else {
				// check if it's a class
				if (!classExists(foundVarName).isEmpty()) {
					return foundVarName;
				}
				// check if it's declared in a parameter
				if (potentialVarType.contains("(")) {
					potentialVarType = potentialVarType.substring(potentialVarType.lastIndexOf('(') + 1, potentialVarType.length());
					if (isVarType(potentialVarType)) {
						varType = potentialVarType;
						return varType;
					}
				}
				// word before *varName* is not a type, meaning *varName* is a static class variable.
				// get code of the current class.
				classCode = classExists(className);
				// find the variable
				varType = extractVarType(classCode, varName);
				if (varType.equals("")) {
					// this should never happen.
					if (showWarnings) {
						Console.println("TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					}
					Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					return "";
				} 
				return varType;
			}			
		} else {
			// this means there is no space infront of *varName* => *varName* 
			// check if there is a bracket infront of *varName*
			if (foundVarName.charAt(foundVarName.lastIndexOf(varName) - 1) == '(') {
				foundVarName = foundVarName.substring(foundVarName.lastIndexOf(varName), foundVarName.length());
				if (isVarType(foundVarName)) {
					return foundVarName;
				}
			} else if ((foundVarName.charAt(foundVarName.lastIndexOf(varName) - 1) == '<')) {
				foundVarName = foundVarName.substring(foundVarName.lastIndexOf(varName), foundVarName.length());
				if (isVarType(foundVarName)) {
					return foundVarName;
				}
			}
			// is either a static class variable or a field.
			int charIndexBefore = code.indexOf(varName) - 1;
			if (charIndexBefore == -1) {
				classCode = classExists(className);
				// static variable
				varType = extractVarType(classCode, varName);
				if (varType.equals("")) {
					// this should never happen.
					if (showWarnings) {
						Console.println("TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					}
					Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					return "";
				} 
				return varType;
			} else {
				if (code.charAt(charIndexBefore) == '.') {
					potentialVarType = code.substring(0, charIndexBefore);
					if (potentialVarType.lastIndexOf(' ') == -1) {
						if (showWarnings) {
							Console.println("TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
						}
						Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
						return "";
					}
					potentialVarType = potentialVarType.substring(potentialVarType.lastIndexOf(' ') + 1, potentialVarType.length());
					if (isVarType(potentialVarType)) {
						varType = potentialVarType;
						return varType;
					} else {
						if (potentialVarType.equals("this")) {
							varType = className;
							return varType;
						}
					}
				} else {
					// this shouldn't happen because the syntax of the code would not be correct.
					if (showWarnings) {
						Console.println("TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					}
					Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
					return "";
				}
			}
		}
		if (showWarnings) {
			Console.println("TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
		}
		Util.log(this.projectPath, "TestAndAssertionGeneratorWarning: Variable type of variable " + varName + " couldn't be found.");
		return "";
	}
	
	/**
	 * Gets the signature of method *methodName* contained in *code*.
	 * @param code The code.
	 * @param methodName Name of the method to find.
	 * @return The signature when found, else empty string.
	 */
	public static String getMethodSignature(String code, String methodName) {
		final Pattern p = Pattern.compile("\\W" + methodName + "\\(");
		final Matcher m = p.matcher(code);
		String helper;
		int startIndex;

		
		while (m.find()) {
			helper = code.substring(0, m.start() + 1);
			startIndex = helper.lastIndexOf('\n') + 1;
			helper = code.substring(startIndex, code.length());
			int endIndex = startIndex + helper.indexOf(")") + 1;
			for (int i = endIndex; i < code.length(); i++) {
				if (code.charAt(i) == '{') {
					return code.substring(startIndex, endIndex).trim();
				} else if (code.charAt(i) == ' ') 
				{
					// empty because whitespaces are valid therefore we will move on in the search for a {-bracket.				
				} else {
					break;
				}		
			}
		}
		return "";
	}
	
	private boolean codeContainsMethodDefinition(String code, String methodName) {
		final Pattern p = Pattern.compile(Pattern.quote(methodName + "("));
		final Matcher m = p.matcher(code);
		String helper;

		
		while (m.find()) {
			helper = code.substring(m.start(), code.length());
			int endIndex = m.start() + helper.indexOf(")") + 1;
			for (int i = endIndex; i < code.length(); i++) {
				if (code.charAt(i) == '{') {
					return true;
				} else if (code.charAt(i) == ' ') 
				{
					// empty because whitespaces are valid therefore we will move on in the search for a {-bracket.				
				} else {
					break;
				}		
			}
		}
		return false;
	}
	
	private boolean isBuiltInType(String type) {
		return type.contains("boolean") 
				| type.contains("byte")
				| type.contains("char") 
				| type.contains("short") 
				| type.contains("int") 
				| type.contains("long") 
				| type.contains("String");
	}
	
	/**
	 * Loads code of the given *className* in *subFolderName*.
	 * @param className
	 * @return The file name of the class with name *className*.
	 */
	private String loadFileFromClass(String className, String subFolderName) {
		final java.nio.file.Path p = Paths.get(FileUtil.getProjectLocation(projectPath) + "/" + subFolderName + "/" + className + ".java");
		final File f;
		
		try {
			f = new File(p.toString());
			if (!f.exists()) {
				return "";
			}
			var code = Files.readString(p);
			code = removeAllComments(code);
			return code;
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}
		
	/**
	 * Parses all method dependencies inside code and returns a map which maps each method to it's class.
	 * @param code
	 * @return the map.
	 */
	public HashMap<String, String> getDiagramNamesFromCalledMethods(final String code, final String className) {
		final HashMap<String, String> output = new HashMap<String, String>();
		final Pattern methodCallPattern = Pattern.compile("\\w+\\.\\w+\\(");	
		var methods = getAllMethodsOfCode(code);

		
		for (final var method : methods) {
			final var lines = method.split("\n");
			for (final var line : lines) {
				Matcher matcher = methodCallPattern.matcher(line);
				while(matcher.find()) {
					final var methodCallParts = matcher.group(0).split("\\.", 2);
					var methodName = methodCallParts[1];
					final var varName = methodCallParts[0];
					methodName = methodName.replaceAll("\\(", "");
					final var type = getVariableType(code, varName, className);
					if (!type.equals(className) || !codeContainsMethodDefinition(code, methodName)) {
						// make sure the key value pair doesn't exists already
						if (!output.keySet().contains(methodName) || !output.get(methodName).equals(type)) {
							output.put(methodName, type);
						}
					}
				}
			}

		}
		return output;
	}
			
	private void loadInternalClasses() {
		var project = FileUtil.getProject(this.projectPath);
		var cbcClasses = FileUtil.getCbCClasses(project);
		for (var cbcClass : cbcClasses) {
			var modelClass = (ModelClass)cbcClass.getContents().get(0);
			var name = modelClass.getName();
			this.projectInternalClasses.add(name);
		}
	}
	
	private List<String> getProjectInternalClasses() {
		if (this.projectInternalClasses == null) {
			this.projectInternalClasses = new ArrayList<String>();
			loadInternalClasses();
		}
		return this.projectInternalClasses;
	}
	
	private List<String> getProjectJavaFiles() {
		if (this.projectJavaFiles == null) {
			this.projectJavaFiles = new ArrayList<String>();
			loadAllJavaFilesFromProject();
		}
		return this.projectJavaFiles;
	}
	
	private boolean isClass(String className) {
		if(getProjectInternalClasses().contains(className)) {
			return true;
		}
		if(getProjectJavaFiles().contains(className)) {
			return true;
		}
		return false;
	}
	
	private boolean isExternalClass(String className) {
		if(getProjectInternalClasses().contains(className)) {
			return false;
		}
		if(!getProjectJavaFiles().contains(className)) {
			return false;
		}
		return true;
	}
		
	private String getSignature(String method) {
		method = method.substring(0, method.indexOf('{'));
		method = method.trim();
		return method;
	}
	
	private String insertMethods(List<String> methods, String target) {
		if (target.isBlank() || methods.isEmpty()) {
			return target;
		}
		
		target = target.trim();
		if (target.charAt(target.length() - 1) == '}') {
			target = target.substring(0, target.length() - 1);
		} else {
			return "";
		}

		for (var method : methods) {
			var signature = getSignature(method);
			if (!target.contains(signature)) {
				target += method;
			}
		}		
		target += "\n}";
		return target;
	}
	
	private Diagram loadDiagramFromClass(String className, String diagramName) {
		if (className.isBlank() || diagramName.isBlank()) {
			return null;
		}
		final ResourceSet rSet = new ResourceSetImpl();
		final IContainer folder = FileUtil.getProject(projectPath).getFolder(className);
		if (folder == null) {
			return null;
		}
		final IFile file = FileUtil.getProject(projectPath).getFolder("src/" + className).getFile(diagramName + ".diagram");
		if (file == null) {
			return null;
		}
		return GetDiagramUtil.getDiagramFromFile(file, rSet);
	}
	
	private List<String> findAllDistinctWords(String code, char wordDelim) {
		var output = new ArrayList<String>();
		int nextIndex = code.indexOf(wordDelim);
		int startIndex;
		String helper;
		
		
		while (nextIndex != -1) {
			helper = code.substring(nextIndex + 1, code.length());
			startIndex = nextIndex + 1;
			if (startIndex >= code.length()) {
				break;
			}
			if (!Character.isAlphabetic(code.charAt(nextIndex + 1))) {
				nextIndex += 1;
				if (nextIndex > code.length() - 1) {
					break;
				}
				continue;
			}
			while (Character.isAlphabetic(code.charAt(nextIndex + 1))) {
				nextIndex++;
			}
			if (nextIndex > startIndex + 1) {
				helper = helper.substring(0, nextIndex - startIndex + 1).trim();
				if (!output.contains(helper)) {
					output.add(helper);
				}
			}
			if (nextIndex + 1 > code.length() - 1) {
				break;
			}
			helper = code.substring(nextIndex + 1, code.length());
			nextIndex = helper.indexOf(wordDelim) + nextIndex + 1;
		}
		return output;
	}
	
	private List<String> getDependencies(final String className, List<String> output) {
		String code = classExists(className);
		
		if (output == null) {
			output = new ArrayList<String>();
		}
		var words = findAllDistinctWords(code, ' ');
		words.addAll(findAllDistinctWords(code, '('));
		words.addAll(findAllDistinctWords(code, '['));
		words.addAll(findAllDistinctWords(code, '<'));
		words.addAll(findAllDistinctWords(code, '{'));
		
		for (var word : words) {
			if (output.contains(word)) {
				continue;
			}
			if (isClass(word)) {
				output.add(word);
				var innerDependencies = getDependencies(word, output);
				for (var d : innerDependencies) {
					if (!output.contains(d)) {
						output.add(d);
					}
				}
			}
		}
		return output;
	}
		
	/**
	 * Determines all dependencies of class *className* and compiles the class using options *options*.
	 * @param className
	 * @param options
	 * @return Whether successful.
	 */
	public boolean compileClass(String className, List<String> options, boolean delete) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
		var fullClassName = FileUtil.getProjectLocation(this.projectPath) + "/tests/" + className + ".java";
		var dependenciesNames = getDependencies(className, null);
		var dependencies = new ArrayList<String>();
		for (var d : dependenciesNames) {
			d = FileUtil.getProjectLocation(this.projectPath) + "/tests/" + d + ".java";
			if (!dependencies.contains(d)) {
				dependencies.add(d);
			}
		}
		if (!dependencies.contains(fullClassName)) {
			dependencies.add(fullClassName);
		}
		
		Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(dependencies);
		JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options,
		        null, compilationUnits);
		boolean wasSuccessful = task.call();
		if (!wasSuccessful) {
			var errorMsgs = diagnostics.getDiagnostics();
			for (var errorMsg : errorMsgs) {
				Console.println();
				Console.println(" > Syntax error detected.", red);
				Console.println("\t" + errorMsg.toString());
				Console.println();
			}
			if (delete) {
				deleteFile(className);
			}
			return false;
		}
		try {
			fileManager.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean compileFileContents(List<String> fileContents, String methodToGenerate, final List<String> options) throws TestAndAssertionGeneratorException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
		List<String> dependencies = new ArrayList<String>();
		
		
		// determine all class dependencies
		for (var code : fileContents) {
			code = code.trim();
			if (code.isEmpty()) {
				throw new TestAndAssertionGeneratorException("Found an empty class code.");
			}
			var className = code.split("public\\sclass\\s")[1].split("\\s", 2)[0];
			className = className.replaceAll("\\{", "");
			var fullClassName = FileUtil.getProjectLocation(this.projectPath) + "/tests/" + className + ".java";
			createFile(className, code);
			dependencies.add(fullClassName);
		}
		
		// start compiling all needed classes.
		Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(dependencies);
		JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options,
		        null, compilationUnits);
		boolean wasSuccessful = task.call();
		if (!wasSuccessful) {
			var errorMsgs = diagnostics.getDiagnostics();
			for (var errorMsg : errorMsgs) {
				Console.println();
				Console.println(" > Syntax error detected.", red);
				Console.println("\t" + errorMsg.toString());
				Console.println();
			}
			for (var code : fileContents) {
				var className = code.split("\\s", 4)[2];
				//deleteFile(className);
			}
			return false;
		}
		try {
			fileManager.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	public boolean compileFileContents(List<String> fileContents, String methodToGenerate) throws TestAndAssertionGeneratorException {
		return compileFileContents(fileContents, methodToGenerate, null);
	}
	
	private boolean loadAllJavaFilesFromProject() {
		var projectLocation = FileUtil.getProjectLocation(this.projectPath);
		var project = FileUtil.getProject(this.projectPath);
		var javaFiles = FileUtil.getFilesFromProject(project, ".java");
		File f;
		java.nio.file.Path p;
		java.nio.file.Path source;
		
		
		for (var javaFile : javaFiles) {
			this.projectJavaFiles.add(javaFile.getName().substring(0, javaFile.getName().indexOf('.')));
			// copy file into tests folder
			p = java.nio.file.Paths.get(projectLocation + "/tests/" + javaFile.getName());
			source = Paths.get(projectLocation + "/" + javaFile.getProjectRelativePath().toOSString());
			try {
				f = new File(p.toString());
				if (!f.exists()) {
					String code = Files.readString(source);
					code = removeAllComments(code);
					f.createNewFile();
					Files.write(p, code.getBytes(), StandardOpenOption.WRITE);
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;	
	}

	/**
	 * Gets all dependencies (method calls and constructor calls) inside *methodCode*.
	 * @param methodCode The code of the method for which to get the dependencies.
	 * @param className The name of the class which contains *MethodCode*.
	 * @return Contents of the classes.
	 * @throws TestAndAssertionGeneratorException 
	 */
	public List<String> genAllDependenciesOfMethod (String methodCode, String className, String postCondition) throws TestAndAssertionGeneratorException
	{
		var innerMethod = methodCode.substring(methodCode.indexOf('{') + 1, methodCode.lastIndexOf('}'));
		// add postCondition to the code in case there are method calls in there too
		innerMethod += "\n" + postCondition;
		innerMethod = innerMethod.replaceAll("\\\\", "");
		var allDiagramNames = getCalledMethods(methodCode, innerMethod, className);//getDiagramNamesFromCalledMethods(methodCode, className);
		final var classCodes = new ArrayList<String>();
		ArrayList<String> newMethodCodes = new ArrayList<String>();
		final HashMap<String, List<String>> addedMethods = new HashMap<String, List<String>>();
		final ArrayList<String> addedClassNames = new ArrayList<String>();
		String classCode;
		methodCode = methodCode.trim();
		final var lst = new ArrayList<String>();
		String keyToRemove = null;
		String methodSigToAdd = null;
		
		while (allDiagramNames.keySet().size() > 0) {
			String curClassName;
			lst.clear();
			lst.addAll(allDiagramNames.keySet());
			curClassName = lst.get(0);
			addedClassNames.add(curClassName);
			lst.clear();
			lst.addAll(allDiagramNames.get(curClassName));
			for (var methodSig : lst) {
				if (methodSig.contains("{")) {
					keyToRemove = methodSig;
					methodSig = getSignature(methodSig);
					methodSigToAdd = methodSig;
					allDiagramNames.get(curClassName).remove(keyToRemove);
					allDiagramNames.get(curClassName).add(methodSigToAdd);
				}
				if (addedMethods.get(curClassName) != null && addedMethods.get(curClassName).contains(methodSig)) {
					continue;
				}
				// gen code of all used methods of all classes
				var innerClassesWithDep = genMethodDependencies(curClassName, methodSig, null);
				for (var innerClassName : innerClassesWithDep.keySet()) {
					if (innerClassName.equals(curClassName)) {
						// means there were more needed methods for class *curClassName*
						newMethodCodes.addAll(innerClassesWithDep.get(innerClassName));
						// mark sig ad generated
						if (addedMethods.get(curClassName) == null) {
							addedMethods.put(curClassName, new ArrayList<String>());
						}
						for (var mc : newMethodCodes) {
							addedMethods.get(curClassName).add(getSignature(mc));
						}
					} else {
						// means there are even more dependencies of other classes to be generated
						if (allDiagramNames.get(innerClassName) == null) {
							// class was not a outer dependency
							allDiagramNames.put(innerClassName, new ArrayList<String>());
						}
						allDiagramNames.get(innerClassName).addAll(innerClassesWithDep.get(innerClassName));
					}
				}
			}
			// at this point, all needed methods of class *curClassName* are inside of newMethodCodes.
			// if the class was previously added, skip it
			if (addedClassNames.stream().filter(c -> c.equals(curClassName)).count() > 1) {
				allDiagramNames.remove(curClassName);
				continue;
			}
			// insert the calling method as well
			try {
				if (curClassName.equals(className)) {
					classCode = genEmptyClass(curClassName, methodCode);
				} else {
					classCode = genEmptyClass(curClassName, "");
				}
			} catch (TestAndAssertionGeneratorException e) {
				throw e;
			}
			
			if (curClassName.equals(className)) {
				// add the original calling method here as well
				newMethodCodes.add("\n\n\t" + methodCode);
			}
			classCode = insertMethods(newMethodCodes, classCode);
			// replace any private modifier in the tests as we don't want complications with access rights
			classCode = classCode.replaceAll("private\\s", "public ");
			classCodes.add(classCode);
			newMethodCodes.clear();
			// remove all added sigs from the to generate list
			for (var clazz : addedMethods.keySet()) {
				for (var methodSig : addedMethods.get(clazz)) {
					if (allDiagramNames.get(clazz) == null) {
						continue;
					}
					while (allDiagramNames.get(clazz).contains(methodSig)) {
						allDiagramNames.get(clazz).remove(methodSig);
					}
				}
				if (allDiagramNames.get(clazz) != null && allDiagramNames.get(clazz).isEmpty()) {
					allDiagramNames.remove(clazz);
				}
			}
		}
		if (classCodes.isEmpty() || !addedClassNames.contains(className)) {
			// means the method itself has no dependencies.
			classCode = genEmptyClass(className, methodCode);
			if (!methodCode.startsWith("{")) {
				classCode = insertMethod(classCode, methodCode);
			}
			// replace any private modifier in the tests as we don't want complications with access rights
			classCode = classCode.replaceAll("private\\s", "public ");
			classCodes.add(classCode);
			addedClassNames.add(className);
		}
		// every type that is not built in the generator but is needed in a constructor of a class
		// needs to be generated too but just without any methods in them
		
		for (int i = 0; i < classCodes.size(); i++) {
			var classes = getUsedClasses(classCodes.get(i));
			for (var clazz : classes) {
				if (!addedClassNames.contains(clazz)) {
					var stubCode = genEmptyClass(clazz, "");
					if (stubCode.isEmpty()) {
						continue;
					}
					classCodes.add(genEmptyClass(clazz, ""));
					addedClassNames.add(clazz);
				}
			}
		}
	
		return classCodes;
	}
	
	/**
	 * Returns the name of every class that is used at least once in *code*.
	 * @param code
	 * @return
	 */
	private List<String> getUsedClasses(String code) {
		final List<String> classes = new ArrayList<String>();
		final Pattern p = Pattern.compile("\\W*\\w+\\W*");
		final Matcher m = p.matcher(code);
		
		while (m.find()) {
			var word = m.group(0).replaceAll("\\W", "");
			if (isClass(word) && !classes.contains(word)) {
				classes.add(word);
			}
		}
		return classes;
	}
	
	private List<String> getGvarsOfCbCClass(String className) {
		final List<String> globalVars = new ArrayList<String>();
		Collection<Resource> resources = FileUtil.getCbCClasses(FileUtil.getProject(projectPath));
		for (Resource resource : resources) {
			for (EObject object : resource.getContents()) {
				if (object instanceof ModelClass) {
					ModelClass modelClass = (ModelClass) object;
					if (modelClass.getName().equals(className)) {
						var fields = modelClass.getFields();
						for (Field field : fields) {
							if (field.getName() == null || field.getType() == null) {
								continue;
							}
							if (field.isIsStatic()) {
								globalVars.add("static " + field.getType() + " " + field.getName());
							} else {
								globalVars.add(field.getType() + " " + field.getName());
							}
						}
						return globalVars;
					}
				}
			}
		}
		return null;
	}
	
	private List<String> getGvarsOfExternalClass(String code) {
		final var output = new ArrayList<String>();
		final var cleanedOutput = new ArrayList<String>();
		code = removeAllComments(code);
		if (code.isEmpty()) {
			return null;
		}
		var firstPart = code.substring(0, code.indexOf('{'));
		var secondPart = code.substring(code.indexOf('{') + 1, code.length());
		secondPart = secondPart.trim();
		code = firstPart + "{\n" + secondPart;
		code = code.replaceAll("\r", "");
		code = code.replaceAll("\t", "");
		code = code.substring(code.indexOf('{') + 1, code.length()).trim();
		if (code.indexOf("\n\n") == -1) {
			return null;
		}
		code = code.substring(0, code.indexOf("\n\n")).trim();
		if (!code.contains(";")) {
			return output;
		}
		output.addAll(Arrays.asList(code.split(";")));
		for (var v : output) {
			v = v.replaceAll("\\sstatic", "");
			v = v.replaceAll("\\sfinal", "");
			if (v.contains("=")) {
				v = v.substring(0, v.indexOf('='));
				v = v.trim();
				var splitter = v.split("\\s");
				v = splitter[splitter.length - 2] + " " + splitter[splitter.length - 1];
			} else {
				var splitter = v.split("\\s");
				v = splitter[splitter.length - 2] + " " + splitter[splitter.length - 1];
			}
			cleanedOutput.add(v);
		}
		//cleanedOutput.add(code);
		return cleanedOutput;
	}
	
	private String genEmptyConstructor(String className) {
		return "public " + className + "(){}";
	}
	
	private String handlePrimitiveArrayUses(String output, String fullVarName, String val, int numTabs) {
		final Pattern p = Pattern.compile("[^\\s]+" + Pattern.quote(ARRAY_TOKEN));
		Matcher m = p.matcher(val);
		
		while(m.find()) {
			String helper = val.substring(m.start(), val.length());
			int start = m.start() + m.group(0).indexOf(ARRAY_TOKEN);
			int end = helper.indexOf(ARRAY_CLOSED_TOKEN) + m.start();
			String type = m.group(0).substring(0, m.group(0).indexOf(";"));
			String name = m.group(0).substring(m.group(0).indexOf(";") + 1, m.group(0).indexOf(ARRAY_TOKEN));
			String value = val.substring(start + ARRAY_TOKEN.length(), end);
			output = "\n" + getTabs(numTabs) + type + " " + name + " = " + value + ";" + output;
			final var toReplace = val.substring(m.start(), end + ARRAY_CLOSED_TOKEN.length());
			val = val.replace(toReplace, name);
			m = p.matcher(val);
		}
		return output + getTabs(numTabs) + "public " + fullVarName + " = " + val + ";\n";
	}
	
	private String initializeGvars(List<String> gVars) {
		String output = "";
		String val;
		for (var v : gVars) {
			var splitter = v.split("\\s");
			var type = splitter[splitter.length - 2];
			if (isBuiltInType(type)) {
				val = InputData.getDefaultValue(type);
				if (val.contains("[")) {
					output += "\tpublic " + v + " = " + val + ";\n";
					continue;
				}
			} else {
				val = "null";//genDefaultInputForVar(v, null).get(0);
			}
			// now handle possible primitive array uses and assign v
			output = handlePrimitiveArrayUses(output, v, val, 1);
			//output += "\t public " + v + " = " + val + ";\n";
		}
		return output;
	}
	
	private String genEmptyClass(String className, String methodCode) throws TestAndAssertionGeneratorException {
		StringBuffer code = new StringBuffer();
		String classCode;
		List<String> gVars;
		// find out how many fields the class *className* has
		if (className.equals(GENERATED_CLASSNAME)) {
			gVars = new ArrayList<String>();
		} else if (isExternalClass(className)) {
			classCode = classExists(className);
			if (classCode.isEmpty()) {
				return "";
			}
			gVars = getGvarsOfExternalClass(classCode);
		} else {
			gVars = getGvarsOfCbCClass(className);
		}
		if (gVars == null) {
			throw new TestAndAssertionGeneratorException("Couldn't get global variables of class '" + className + "'.");
		}
		// last element is the globalVariables str.
		//globalVariables = gVars.get(gVars.size() - 1);
		//gVars.remove(gVars.size() - 1);
		// generate class
		code.append("public class " + className + " {\n");
		// generate global vars and fields
		code.append(initializeGvars(gVars));
		for (int i=0;i<gVars.size();i++) 
			gVars.set(i, gVars.get(i).replaceAll("static", "")); // ;-)
																 // replace string handling with real objects
		//code.append(globalVariables);
		// generate constructor
		code.append("\n\n\t" + generateConstructor(className, gVars));
		// also generate the default constructor if the last constructor contains params
		if (gVars.size() > 0) {
			code.append("\n\n\t" + genEmptyConstructor(className));
		}
		// close class
		code.append("\n}");
		return code.toString();
	}
	
	private String getMethodNameFromSig(String sig) {
		if (sig.isBlank()) {
			return "";
		}
		final String[] splitter = sSplit(sig, "\\s");	
		return splitter[splitter.length - 1].substring(0, splitter[splitter.length - 1].indexOf('('));
	}
	
	/**
	 * Generates or gets code of *methodName* and all methods directly or indirectly referenced from all classes.
	 * @param className
	 * @param methodName
	 * @param methodCodes
	 * @return
	 */
	private HashMap<String, ArrayList<String>> genMethodDependencies(String className, String methodSig, HashMap<String, ArrayList<String>> methodCodes) {
		String methodCode;
		HashMap<String, ArrayList<String>> calledMethods;
		if (methodCodes == null) {
			methodCodes = new HashMap<String, ArrayList<String>>();
		}
		var allMethods = methodCodes.get(className);
		if (allMethods != null) {
			for (var m : allMethods) {
				var sig = getMethodName(m);
				sig = getMethodSignature(m, sig);
				if (sig.equals(methodSig)) {
					// already added the method for the class
					return null;
				}
			}
		}
		
		methodCode = getCodeOfSignature(className, methodSig);
		if (methodCode == null || methodCode.isEmpty()) {
			return methodCodes;
		}
		
		// add methodCode
		if (methodCodes.get(className) == null) {
			methodCodes.put(className, new ArrayList<String>());
		}
		methodCodes.get(className).add("\n\n\t" + methodCode.trim());
		// find out what methods are getting called inside *methodCode*.
		final var innerMethod = methodCode.substring(methodCode.indexOf('{') + 1, methodCode.lastIndexOf('}'));
		calledMethods = getCalledMethods(methodCode, innerMethod, className);
		for (var clazz : calledMethods.keySet()) {
			for (var m : calledMethods.get(clazz)) {
				genMethodDependencies(clazz, m, methodCodes);
			}
		}		
		return methodCodes;
	}
	
	private String getSignatureOfLoadedFile(String className, String methodName, int numParams) {
		// load file
		var javaFile = new File(FileUtil.getProjectLocation(projectPath) + "/src/" + className + ".java");
		var destination = new File(FileUtil.getProjectLocation(projectPath) + "/tests/" + className + ".java");
		if (!javaFile.exists()) {
			return "";
		}
		deleteFile(className);
		try {
			Files.copy(javaFile.toPath(), destination.toPath());
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}	
		
		var code = classExists(className);
		if (code.isEmpty()) {
			return "";
		}
		
		String signature = "";
		String params = "";
		// TODO: Check if this works for all possibilities
		while(code.length() > 0) {
			int start = code.indexOf(methodName);
			if (start == -1) {
				break;
			}
			int s = start - 1;
			while (s > 0 && Character.isWhitespace(code.charAt(s--)));
			if (!Character.isAlphabetic(code.charAt(++s))) {
				code = code.substring(start + methodName.length(), code.length());
				continue;
			}
			int end = code.substring(start, code.length()).indexOf('{') + start;
			if (start == -1 || end == -1) {
				return "";
			}
			start = code.substring(0, end).lastIndexOf(';');
			int cmp = code.substring(0, end).lastIndexOf('}');
			if (cmp > start) {
				start = cmp;
			}
			signature = code.substring(start, end).replaceAll("[^\\w\\s\\(\\)\\_\\,\\[\\]]", "").trim();
			// check if we found the right signature
			params = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')'));
			long actualParams = params.chars().filter(c -> c == ',').count() + 1;
			if (params.length() == 0) {
				actualParams = 0;
			}
			if (actualParams == numParams) {
				return signature;
			}
			code = code.substring(end + 1, code.length());
		}
		return signature;
		
		/*
		// parse signature
		var patternStr = methodName + "\\(";
		for (int i = 0; i < numParams; i++) {
			if (i >= 1) {
				patternStr += "\\,\\s";
			}
			patternStr += "[^\\,]+";
		}
		if (numParams > 1) {
			patternStr = patternStr.substring(0, patternStr.length() - 4);
		}
		patternStr += "\\)\\s*\\{";
		final Pattern p = Pattern.compile(patternStr);
		final Matcher m = p.matcher(code);
		
		while (m.find()) {
			var sig = code.substring(0, m.start() + m.group(0).indexOf('{'));
			sig = sig.substring(sig.lastIndexOf('\n'), sig.length()).trim();
			return sig;
		}
		return "";*/
	}
	
	/**
	 * Searches for definition of method *methodName* in class *className* which takes exactly *numParams* parameters.
	 * @param className
	 * @param methodName
	 * @param numParams
	 */
	private String getMethodSignature(String className, String methodName, int numParams) {
		Diagram diagram;
		
		if (isExternalClass(className)) {
			return getSignatureOfLoadedFile(className, methodName, numParams);
		} else {
			diagram = loadDiagramFromClass(className, methodName);
			if (diagram == null) {
				// try to load it in from java src
				var sig = getSignatureOfLoadedFile(className, methodName, numParams);
				return sig;
			}

			JavaVariables vars = null;
			CbCFormula formula = null;

			for (Shape shape : diagram.getChildren()) {
				Object obj = getBusinessObjectForPictogramElement(shape);
				if (obj instanceof JavaVariables) {
					vars = (JavaVariables) obj;
				} else if (obj instanceof CbCFormula) {
					formula = (CbCFormula) obj;
				}
			}	
			int diagramParams = 0;
			for (var v : vars.getVariables()) {
				if (v.getKind().equals(VariableKind.PARAM)) {
					diagramParams++;
				}
			}
			if (diagramParams == numParams) {
				return formula.getMethodObj().getSignature();
			}
			// this means the diagram is not the method we are looking for.
			// the method must be in code which was given in src.
			return getSignatureOfLoadedFile(className, methodName, numParams);
		}
	}
	
	private String getCodeOfSignatureOfLoadedFile(String className, String signature, boolean isConstructor) {
		// load file
		var javaFile = new File(FileUtil.getProjectLocation(projectPath) + "/src/" + className + ".java");
		var destination = new File(FileUtil.getProjectLocation(projectPath) + "/tests/" + className + ".java");
		if (!javaFile.exists()) {
			return "";
		}
		deleteFile(className);
		try {
			Files.copy(javaFile.toPath(), destination.toPath());
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}	
		
		final var code = classExists(className);
		if (code.isEmpty()) {
			return "";
		}
		final String methodCode;
		if (isConstructor) {
			methodCode = getConstructorCode(signature);	
		} else {
			methodCode = getMethodCode(code, signature);	
		}	
		return methodCode;
	}
	
	private int getNumParamsFromSig(String signature) {
		int numParams = (int)signature.chars().filter(c -> c == ',').count();
		if (numParams > 0) {
			numParams++;
		} else {
			if (signature.charAt(signature.indexOf('(') + 1) == ')') {
				numParams = 0;
			} else {
				numParams = 1;
			}
		}
		return numParams;
	}
	
	/**
	 * Given the *className* and the *signature* either generate the code for *signature* if it is from a diagram or load it from a src file which 
	 * contains the definition for *signature*.
	 * @param className
	 * @param signature
	 * @return
	 */
	private String getCodeOfSignature(String className, String signature) {
		Diagram diagram;
		boolean isConstructor = false;
		
		
		if (getMethodName(signature).equals(className)) {
			isConstructor = true;
		}
		
		if (isExternalClass(className)) {
			return getCodeOfSignatureOfLoadedFile(className, signature, isConstructor);
		} else if (isConstructor) {
			return getConstructorCode(signature);
		} else {
			diagram = loadDiagramFromClass(className, getMethodNameFromSig(signature));
			if (diagram == null) {
				// try to load the code from the java src folder
				return getCodeOfSignatureOfLoadedFile(className, signature, isConstructor);
			}
			JavaVariables vars = null;

			for (Shape shape : diagram.getChildren()) {
				Object obj = getBusinessObjectForPictogramElement(shape);
				if (obj instanceof JavaVariables) {
					vars = (JavaVariables) obj;
				}
			}	

			int diagramParams = 0;
			// TODO: Check if this works for all cases
			if (vars != null) {
				for (var v : vars.getVariables()) {
					if (v.getKind().equals(VariableKind.PARAM)) {
						diagramParams++;
					}
				}
				if (diagramParams == getNumParamsFromSig(signature)) {
					return genCode(diagram, true);
				}
			}
			// this means the diagram is not the method we are looking for.
			// the method must be in code which was given in src.
			return getCodeOfSignatureOfLoadedFile(className, signature, isConstructor);
		}
	}

	private String getConstructorCode(String signature) {
		final Pattern p = Pattern.compile(Pattern.quote(signature));
		var code = loadFileFromClass(getMethodName(signature), "src");
		final Matcher m = p.matcher(code);
		
		while (m.find()) {
			var helper = code.substring(m.start(), code.length());
			int startBracket = m.start() + helper.indexOf("{");
			int endBracket = findClosingBracketIndex(code, startBracket, '{');
			if (endBracket == -1) {
				return null;
			}
			return code.substring(m.start(), endBracket + 1);
		}
		// just return the default constructor when no constructor was found
		return "public " + getMethodName(signature) + "(){}";
	}

	private String getConstructorSig(String code, String className, int numParams) {
		final Pattern p = Pattern.compile(className + "\\([^\\)]*\\)\\s*\\{");
		final Matcher m = p.matcher(code);
		String cCode;
		String params;
		
		while (m.find()) {
			params = m.group(0).substring(m.group(0).indexOf('(') + 1, m.group(0).lastIndexOf(')'));
			if (sSplit(params, ",").length == numParams) {
				// we found the right constructor
				cCode = "public " + code.substring(m.start(), m.start() + m.group(0).indexOf('{')).trim();
				return cCode;
			}
		}
		// just return the default constructor sig when numParams is 0 and no constructor was found
		if (numParams == 0) {
			return "public " + className + "()";
		}
		return null;
	}
	
	/**
	 * Collects all method/constructor calls made in *matchee*. *matchee* can be any arbitrary string.
	 * @param methodCode
	 * @param innerMethod
	 * @param className
	 * @return a map of containing every class that has at least one called method. 
	 * Map contains mapping from the classes to their called methods.
	 */
	private HashMap<String, ArrayList<String>> getCalledMethods(String code, String matchee, String className) {
		final HashMap<String, ArrayList<String>> output = new HashMap<String, ArrayList<String>>();
		int start = matchee.indexOf("(");
		while (start != -1) {
			if (start != 0 && Character.isAlphabetic(matchee.charAt(start-1))) {
				int end = findClosingBracketIndex(matchee, start, '(');
				if (end == -1) {
					matchee = matchee.replaceFirst("\\(", "");
					start = matchee.indexOf("(");
					continue;
				}
				// get identifiers infront of the method call
				int fullId = start - 1;
				while (fullId-- > 0 && (Character.isAlphabetic(matchee.charAt(fullId)) || Arrays.asList('.', '_' /*, '[', ']'*/).contains(matchee.charAt(fullId))));
				fullId++;
				final String methodCallStr = matchee.substring(fullId, end+1);
				final String fullIdStr = matchee.substring(fullId, start);
				final String paramsStr = matchee.substring(start + 1, end);
				long numDots = fullIdStr.chars().filter(c -> c == '.').count();
				if (numDots == 2) {
					final var methodCallParts = methodCallStr.split("\\.", 3);
					var methodName = methodCallParts[2].substring(0, methodCallParts[2].indexOf('('));
					final var varName = methodCallParts[1];
					//final var curClassName = methodCallParts[0];
					final var type = getVariableType(code, varName, className);
					if (type.isEmpty()) {
						if (showWarnings) {
							Console.println("TestAndAssertionWarning: Couldn't get type of variable '" + varName + "'.");
						}
						Util.log(this.projectPath, "TestAndAssertionWarning: Couldn't get type of variable '" + varName + "'.");
						matchee = matchee.replaceFirst("\\(", "");
						start = matchee.indexOf("(");
						continue;
					}
					if (output.get(type) == null) {
						output.put(type, new ArrayList<String>());
					}
					int numParams = getNumParamsFromSig(methodCallStr);
					final var methodSig = getMethodSignature(type, methodName, numParams);
					
					if (!output.get(type).contains(methodSig)) {
						output.get(type).add(methodSig);
					}
				} else if (numDots == 1) {
					final var methodCallParts = methodCallStr.split("\\.", 2);
					var methodName = methodCallParts[1].substring(0, methodCallParts[1].indexOf('('));
					if (methodName.equals("setAttribute")) {
						// testng context, skip
						matchee = matchee.replaceFirst("\\(", "");
						start = matchee.indexOf("(");
						continue;
					}
					final var varName = methodCallParts[0];
					final var type = getVariableType(code, varName, className);
					if (type.isEmpty()) {
						if (showWarnings) {
							Console.println("TestAndAssertionWarning: Couldn't get type of variable '" + varName + "'.");
						}
						Util.log(this.projectPath, "TestAndAssertionWarning: Couldn't get type of variable '" + varName + "'.");
						matchee = matchee.replaceFirst("\\(", "");
						start = matchee.indexOf("(");
						continue;
					}
					if (output.get(type) == null) {
						output.put(type, new ArrayList<String>());
					}
					int numParams = getNumParamsFromSig(methodCallStr);
					final var methodSig = getMethodSignature(type, methodName, numParams);
					
					if (!output.get(type).contains(methodSig)) {
						output.get(type).add(methodSig);
					}
				} else if (numDots == 0) {
					final var type = className;
					var methodName = methodCallStr.split("\\(", 2)[0].trim();
					// idea: as it is a constructor, we know it has no diagram and must have been
					// been provided by the user in the src.
					String classCode;
					if (!(classCode = loadFileFromClass(methodName, "src")).isEmpty()) { // TODO: make sure every loaded class has the custom constructor with all gvars initializable
						// means it is indeed a constructor call
						// determine amount of parameters.
						var call = methodCallStr
								.substring(methodCallStr.indexOf('(') + 1, methodCallStr.lastIndexOf(')'));
						int numParams;
						if (call.isEmpty()) {
							numParams = 0;
						} else {
							numParams = sSplit(call, ",").length;
							if (numParams == 0) numParams = 1;
						}
						// get sig of the constructor
						var cSig = getConstructorSig(classCode, methodName, numParams); 
						if (cSig == null) {
							if (showWarnings) {
								Console.println("TestAndAssertionWarning: Couldn't get the signature of the constructor '" + methodName + "' which has " + numParams + " parameters.");
							}
							Util.log(this.projectPath, "TestAndAssertionWarning: Couldn't get the signature of the constructor '" + methodName + "' which has " + numParams + " parameters.");
							matchee = matchee.replaceFirst("\\(", "");
							start = matchee.indexOf("(");
							continue;
						}
						if (output.get(methodName) == null) {
							output.put(methodName, new ArrayList<String>());
						}
						output.get(methodName).add(cSig);
						matchee = matchee.replaceFirst("\\(", "");
						start = matchee.indexOf("(");
						continue;
					}
					int numParams = getNumParamsFromSig(methodCallStr);
					final var methodSig = getMethodSignature(type, methodName, numParams);
					if (methodSig.isEmpty()) {
						if (showWarnings) {
							Console.println("TestAndAssertionWarning: Couldn't find signature for method '" + methodName + "'.");
						}
						Util.log(this.projectPath, "TestAndAssertionWarning: Couldn't find signature for method '" + methodName + "'.");
						matchee = matchee.replaceFirst("\\(", "");
						start = matchee.indexOf("(");
						continue;
					}
					if (output.get(type) == null) {
						output.put(type, new ArrayList<String>());
					}
					if (!output.get(type).contains(methodSig)) {
						output.get(type).add(methodSig);
					}
				}
			}	
			matchee = matchee.replaceFirst("\\(", "");
			start = matchee.indexOf("(");
		}
		return output;
	}
			
	private String getMethodName(String methodCode) {
		if (methodCode.indexOf('(') == - 1) {
			return "";
		}
		if (methodCode.indexOf('{') != - 1 && methodCode.indexOf('(') > methodCode.indexOf('{')) {
			return "";
		}
		methodCode = methodCode.substring(0, methodCode.indexOf('('));
		methodCode = methodCode.substring(methodCode.lastIndexOf(' '), methodCode.length());
		return methodCode.trim();
	}
	
	/**
	 * Returns a string of all preconditions in JavaDL syntax or empty string, when there is no precondition.
	 * @param globalConditions
	 * @param formula
	 * @return
	 */
	public static String parseConditions(final GlobalConditions globalConditions, final Condition preCondition) {
		// By definition we know that the root formula does contain the strongest precondition,
		// therefore we only need to parse it's preconditions as any following refinement
		// can't have more (=stronger) requirements.
		var preCon = preCondition.getName().trim();
		if (preCon.equals("true")) {
			preCon = "";
		}
		// add invariants 
		List<String> invariants = globalConditions == null ? new ArrayList<String>() : globalConditions.getConditions().stream()
				.map(c -> c.getName())
				.toList();
		String invariantsStr = "";
		// non-null conditions can be discarded because the generator always generates values for complex data types.
		final Pattern p = Pattern.compile("[A-Z]\\w*\\.\\w+");
		int counter = 0;
		for (int i = 0; i < invariants.size(); i++) {
			if (!invariants.get(i).contains("null") && !invariants.get(i).contains("self")) {
				Matcher m = p.matcher(invariants.get(i));
				String con = invariants.get(i);
				while (m.find()) {
					return "";
					
					//con = con.substring(0, m.start()) + con.substring(m.start() + m.group(0).indexOf('.') + 1, con.length());
				}
				if (counter > 0) {
					invariantsStr += " & " + con;
				} else {
					invariantsStr += con;
				}
				counter++;
			}
		}
		//invariantsStr = invariantsStr.replaceAll("this\\.", "");
		if (preCon.isEmpty()) {
			return invariantsStr;
		} else {	
			if (invariantsStr.isEmpty()) {
				return preCon;
			} else {
				return preCon + " & " + invariantsStr;
			}
		}
	}
	
	public static String parseConditions2(final GlobalConditions globalConditions, final Condition preCondition) {
		// By definition we know that the root formula does contain the strongest precondition,
		// therefore we only need to parse it's preconditions as any following refinement
		// can't have more (=stronger) requirements.
		var preCon = preCondition.getName().trim();
		if (preCon.equals("true")) {
			preCon = "";
		}
		// add invariants 
		List<String> invariants = globalConditions.getConditions().stream()
				.map(c -> c.getName())
				.toList();
		String invariantsStr = "";
		int counter = 0;
		for (int i = 0; i < invariants.size(); i++) {
			if (!invariants.get(i).contains("null") && !invariants.get(i).contains("self")) {
				String con = invariants.get(i);
				if (counter > 0) {
					invariantsStr += " & " + con;
				} else {
					invariantsStr += con;
				}
				counter++;
			}
		}
		if (preCon.isEmpty()) {
			return invariantsStr;
		} else {	
			if (invariantsStr.isEmpty()) {
				return preCon;
			} else {
				return preCon + " & " + invariantsStr;
			}
		}
	}
}