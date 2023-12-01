package de.tu_bs.cs.isf.cbc.tool.propertiesview;

import java.awt.GridBagConstraints;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.tu_bs.cs.isf.cbc.util.FileUtil;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.platform.GFPropertySection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertyConstants;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import de.tu_bs.cs.isf.cbc.cbcmodel.Condition;
import de.tu_bs.cs.isf.cbc.mutation.feature.CbCMutator;
import de.tu_bs.cs.isf.cbc.mutation.feature.ImplMutator;
import de.tu_bs.cs.isf.cbc.mutation.feature.Mutator;
import de.tu_bs.cs.isf.cbc.tool.diagram.CbCDiagramTypeProvider;
import de.tu_bs.cs.isf.cbc.tool.helper.Colors;

/**
 * Class for the Mutation-Tab of the Properties-View.
 * 
 * @author Fynn Demmler
 */
public class MutationSection extends GFPropertySection implements ITabbedPropertyConstants {
	final List<Button> buttons = new ArrayList<Button>();
	// Defining the logical properties
	private Composite parent;
	private TabbedPropertySheetPage tabbedPropertySheetPage;
	
	private Composite composite;
	
	private Group cbcGroup;
	private Group implGroup;
	private Group contractGroup;
	private Button generateBtn;
	
	
	private Condition selectedCondition;
	
	private Device device = Display.getCurrent ();
	private Color white = new Color (device, 255, 255, 255);
	
	private final int NUM_GROUPS = 3;
	// Impl Mutation Operators
	private final String[] implOperators = new String[] {
			"AORB | a + b  →  M₁: a - b  M₂: a * b  M₃: a / b",
			"AORS | i++  →  M₁: i--  M₂: ++i  M₃: --i",
			"AOIU | a < b  →  M₁: -a < b  M₂: a < -b ...",
			"AOIS | a  →  M₁: --a  M₂: ++a  M₃: a++  M₄: a--",
			"AODU | -e  →  M₁: e",
			"AODS | a++  →  M₁: i",
			"ROR | a < b → M₁: a <= b  M₂: a >= b ...",
			"COR | a || b → M₁: a && b  M₂: a & b ...",
			"COD | !a → M₁: a",
			"COI | a → M₁: !a",
			"SOR | a >> b → M₁: a << b  M₂: a >>>> b",
			"LOR | a | b →  M₁: a & b  M₂: a ^ b",
			"LOI | a →  M₁: ˜a",
			"LOD | ˜a →  M₁: a",
			"ASRS | a += b → M₁: a -= b  M₂: a *= b ..."
	};
	
	// Contract Mutation Operators
	private final String[] contractOperators = new String[] {
			Mutator.PW_RENAME + " | ==, >, <, >=, <=, &&, forall",
			Mutator.PS_RENAME + " | >=, <=, !=, >, <, ||, exists"
	};
	
	// CbC Mutation Operators
	private final String[] cbcOperators = new String[] {
			"CAORB | Apply AORB to selected condition.",
			"CROR | Apply ROR to selected condition.",
			"CCOR | Apply COR to selected condition.",
			"CCOD | Apply COD to selected condition.",
			"CLOR | Apply LOR to selected condition.",
	};
	// TODO: Adjust Mutator to support more contract mutation operators.

	@Override
	public void createControls(Composite parent, TabbedPropertySheetPage tabbedPropertySheetPage) {
		super.createControls(parent, tabbedPropertySheetPage);

		this.parent = parent;
		this.tabbedPropertySheetPage = tabbedPropertySheetPage;
		TabbedPropertySheetWidgetFactory factory = getWidgetFactory();
		
		composite = factory.createFlatFormComposite(parent);
		
		// Defining GridLayout for properties-view
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = NUM_GROUPS;
		gridLayout.verticalSpacing = 5;
		
		composite.setLayout(gridLayout);
		composite.setBackground(new Color(254, 250, 224));
		composite.setForeground(new Color(212, 163, 115));
		
		implGroup = createButtonGroup(composite, "Implementation Mutation Operators");
		for (var implOperator : implOperators) {
			createCheckbox(implGroup, implOperator);
		}
		
		contractGroup = createButtonGroup(composite, "Contract Mutation Operators");
		for (var contractOperator : contractOperators) {
			createCheckbox(contractGroup, contractOperator);
		}

		cbcGroup = createButtonGroup(composite, "CbC Mutation Operators");
		for (var cbcOperator : cbcOperators) {
			createCheckbox(cbcGroup, cbcOperator);
		}
		this.cbcGroup.setLocation(5, 5);

		
		this.generateBtn = createButton(composite, "Generate Mutants");
		
		addListener(generateBtn);
	}
	
	@Override
	public void refresh() {
		this.cbcGroup.setLocation(5, 5);
		CbCDiagramTypeProvider diagramProvider = new CbCDiagramTypeProvider();
		PictogramElement pe = getSelectedPictogramElement();
		var bo = diagramProvider.getFeatureProvider().getBusinessObjectForPictogramElement(pe);
		if(bo instanceof Condition) {
			this.selectedCondition = (Condition)bo;
			this.cbcGroup.setVisible(true);
			this.generateBtn.setLocation(this.cbcGroup.getLocation().x, this.cbcGroup.getLocation().y + this.cbcGroup.getSize().y + 5);
			this.contractGroup.setVisible(false);
			this.implGroup.setVisible(false);
		} else {
			this.generateBtn.setLocation(this.implGroup.getLocation().x, this.implGroup.getLocation().y + this.implGroup.getSize().y + 5);
			this.selectedCondition = null;
			this.cbcGroup.setVisible(false);
			this.contractGroup.setVisible(true);
			this.implGroup.setVisible(true);
		}
	}
	
	private Group createButtonGroup(Composite parent, String name) {
		Group buttonGroup = new Group(parent, SWT.PUSH);
		buttonGroup.setText(name);
		GridLayout buttonGroupLayout = new GridLayout();
		buttonGroupLayout.numColumns = 1;
		buttonGroup.setLayout(buttonGroupLayout);
		buttonGroup.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		buttonGroup.setBackground(parent.getBackground());
		buttonGroup.setForeground(parent.getForeground());
		return buttonGroup;
	}
	
	private Button createCheckbox(Composite buttonGroup, String name) {
		Button newButton = new Button(buttonGroup, SWT.CHECK);
		newButton.setText(name);
		newButton.setForeground(buttonGroup.getForeground());
		newButton.setBackground(buttonGroup.getBackground());
		buttons.add(newButton);
		return newButton;
	}
	
	private Button createButton(Composite buttonGroup, String name) {
		Button newButton = new Button(buttonGroup, SWT.PUSH);
		newButton.setText(name);
		newButton.setForeground(buttonGroup.getForeground());
		newButton.setBackground(buttonGroup.getBackground());
		buttons.add(newButton);
		return newButton;
	}
	
	private void addListener(Button btn) {
		btn.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				var ops = new ArrayList<String>();
				addSelectedOperators(btn, ops);
				try {
					generateMutatedDiagrams(ops);
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		});
	}
	
	private void addSelectedOperators(Button btn, List<String> ops) {
		for (var b : buttons) {
			if (b.equals(btn)) {
				continue;
			}
			if (b.getSelection()) {
				ops.add(getOperator(b));
			}
		}
	}
	
	private void generateMutatedDiagrams(List<String> ops) throws Exception {
		ImplMutator implMutator = new ImplMutator(ops);
		implMutator.mutate(getDiagram(), null);
		if (this.selectedCondition != null) {
			CbCMutator cbcMutator = new CbCMutator(ops);
			cbcMutator.mutate(getDiagram(), this.selectedCondition);
		}
	}

	private String getOperator(Button btn) {
		return btn.getText().split("\\s\\|")[0];
	}
}

