package org.openlca.app.editors.lcia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.components.DialogCellEditor;
import org.openlca.app.components.FormulaCellEditor;
import org.openlca.app.components.ModelSelectionDialog;
import org.openlca.app.components.UncertaintyCellEditor;
import org.openlca.app.db.Database;
import org.openlca.app.editors.ModelPage;
import org.openlca.app.editors.comments.CommentDialogModifier;
import org.openlca.app.editors.comments.CommentPaths;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.util.Actions;
import org.openlca.app.util.Labels;
import org.openlca.app.util.UI;
import org.openlca.app.util.tables.TableClipboard;
import org.openlca.app.util.tables.Tables;
import org.openlca.app.util.viewers.Viewers;
import org.openlca.app.viewers.table.modify.ComboBoxCellModifier;
import org.openlca.app.viewers.table.modify.ModifySupport;
import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.LocationDao;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.FlowPropertyFactor;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.ImpactFactor;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Uncertainty;
import org.openlca.core.model.Unit;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.io.CategoryPath;
import org.openlca.util.Strings;

class ImpactFactorPage extends ModelPage<ImpactCategory> {

	private final ImpactCategoryEditor editor;

	private boolean showFormulas = true;
	private IDatabase database = Database.get();
	private TableViewer viewer;

	ImpactFactorPage(ImpactCategoryEditor editor) {
		super(editor, "ImpactFactorPage", M.ImpactFactors);
		this.editor = editor;
		editor.getParameterSupport().afterEvaluation(() -> {
			if (viewer != null) {
				viewer.refresh();
			}
		});
		editor.onEvent(editor.FACTORS_CHANGED_EVENT, sender -> {
			if (viewer != null) {
				viewer.setInput(impact().impactFactors);
			}
		});
	}

	private ImpactCategory impact() {
		return editor.getModel();
	}

	@Override
	protected void createFormContent(IManagedForm mform) {
		ScrolledForm form = UI.formHeader(this);
		FormToolkit tk = mform.getToolkit();
		Composite body = UI.formBody(form, tk);
		Section section = UI.section(body, tk, M.ImpactFactors);
		UI.gridData(section, true, true);
		Composite client = tk.createComposite(section);
		section.setClient(client);
		UI.gridLayout(client, 1);
		render(client, section);
		List<ImpactFactor> factors = impact().impactFactors;
		sortFactors(factors);
		viewer.setInput(factors);
		form.reflow(true);
	}

	public void render(Composite parent, Section section) {
		viewer = Tables.createViewer(parent, new String[] {
				M.Flow, M.Category, M.FlowProperty,
				M.Factor, M.Unit, M.Uncertainty, M.Location,
				"" /* comment */ });
		FactorLabel label = new FactorLabel();
		Viewers.sortByLabels(viewer, label, 0, 1, 2, 4, 5, 6);
		Viewers.sortByDouble(viewer, (ImpactFactor f) -> f.value, 3);
		viewer.setLabelProvider(label);
		ModifySupport<ImpactFactor> support = new ModifySupport<>(viewer);
		support.bind(M.FlowProperty, new FlowPropertyModifier());
		support.bind(M.Unit, new UnitModifier());
		bindFactorModifier(support);
		support.bind(M.Uncertainty, new UncertaintyCellEditor(
				viewer.getTable(), editor));
		support.bind(M.Location, new LocationModifier(
				viewer.getTable(), editor));
		support.bind("", new CommentDialogModifier<ImpactFactor>(
				editor.getComments(),
				f -> CommentPaths.get(impact(), f)));
		Tables.bindColumnWidths(viewer, 0.2, 0.2, 0.11, 0.11, 0.11, 0.11, 0.11);
		bindActions(viewer, section);
		viewer.getTable().getColumns()[3].setAlignment(SWT.RIGHT);
	}

	private void bindFactorModifier(ModifySupport<ImpactFactor> ms) {
		// factor editor with auto-completion support for parameter names
		FormulaCellEditor factorEditor = new FormulaCellEditor(viewer,
				() -> editor.getModel().parameters);
		ms.bind(M.Factor, factorEditor);
		factorEditor.onEdited((obj, factor) -> {
			if (!(obj instanceof ImpactFactor))
				return;
			ImpactFactor f = (ImpactFactor) obj;
			try {
				double value = Double.parseDouble(factor);
				f.formula = null;
				f.value = value;
			} catch (NumberFormatException ex) {
				f.formula = factor;
				editor.getParameterSupport().evaluate();
			}
			editor.setDirty(true);
			viewer.refresh();
		});
	}

	private void sortFactors(List<ImpactFactor> factors) {
		Collections.sort(factors, (o1, o2) -> {
			Flow f1 = o1.flow;
			Flow f2 = o2.flow;
			int c = Strings.compare(f1.name, f2.name);
			if (c != 0)
				return c;
			String cat1 = CategoryPath.getShort(f1.category);
			String cat2 = CategoryPath.getShort(f2.category);
			return Strings.compare(cat1, cat2);
		});
	}

	void bindActions(TableViewer viewer, Section section) {
		Action copy = TableClipboard.onCopy(viewer);
		Action add = Actions.onAdd(this::onAdd);
		Action remove = Actions.onRemove(this::onRemove);
		Actions.bind(viewer, add, remove, copy);
		Tables.onDeletePressed(viewer, (e) -> onRemove());
		Tables.onDrop(viewer, this::createFactors);
		Tables.onDoubleClick(viewer, (event) -> {
			TableItem item = Tables.getItem(viewer, event);
			if (item == null) {
				onAdd();
				return;
			}
			ImpactFactor factor = Viewers.getFirstSelected(viewer);
			if (factor != null && factor.flow != null)
				App.openEditor(factor.flow);
		});
		Action formulaSwitch = new FormulaSwitchAction();
		Actions.bind(section, add, remove, formulaSwitch);
	}

	private void onAdd() {
		BaseDescriptor[] flows = ModelSelectionDialog.multiSelect(
				ModelType.FLOW);
		if (flows != null) {
			createFactors(Arrays.asList(flows));
		}
	}

	private void createFactors(List<BaseDescriptor> flows) {
		if (flows == null || flows.isEmpty())
			return;
		for (BaseDescriptor d : flows) {
			if (d == null || d.type != ModelType.FLOW)
				continue;
			Flow flow = new FlowDao(database).getForId(d.id);
			if (flow == null)
				continue;
			impact().addFactor(flow);
		}
		viewer.setInput(impact().impactFactors);
		editor.setDirty(true);
	}

	private void onRemove() {
		List<ImpactFactor> factors = Viewers.getAllSelected(viewer);
		for (ImpactFactor factor : factors) {
			impact().impactFactors.remove(factor);
		}
		viewer.setInput(impact().impactFactors);
		editor.setDirty(true);
	}

	private class FactorLabel extends LabelProvider
			implements ITableLabelProvider {

		@Override
		public Image getColumnImage(Object o, int col) {
			if (!(o instanceof ImpactFactor))
				return null;
			ImpactFactor f = (ImpactFactor) o;
			if (col == 0)
				return Images.get(f.flow);
			if (col == 6)
				return Images.get(editor.getComments(),
						CommentPaths.get(impact(), f));
			return null;
		}

		@Override
		public String getColumnText(Object o, int col) {
			if (!(o instanceof ImpactFactor))
				return null;
			ImpactFactor f = (ImpactFactor) o;
			switch (col) {
			case 0:
				return Labels.name(f.flow);
			case 1:
				return CategoryPath.getShort(f.flow.category);
			case 2:
				if (f.flowPropertyFactor == null)
					return null;
				return Labels.name(f.flowPropertyFactor.flowProperty);
			case 3:
				if (f.formula == null || !showFormulas)
					return Double.toString(f.value);
				else
					return f.formula;
			case 4:
				return getFactorUnit(f);
			case 5:
				return Uncertainty.string(f.uncertainty);
			case 6:
				return f.location == null ? "": f.location.code != null
								? f.location.code
								: Labels.name(f.location);
			default:
				return null;
			}
		}

		private String getFactorUnit(ImpactFactor factor) {
			if (factor.unit == null)
				return null;
			String impactUnit = impact().referenceUnit;
			if (Strings.notEmpty(impactUnit))
				return impactUnit + "/" + factor.unit.name;
			else
				return "1/" + factor.unit.name;
		}

	}

	private class FlowPropertyModifier extends
			ComboBoxCellModifier<ImpactFactor, FlowProperty> {

		@Override
		protected FlowProperty[] getItems(ImpactFactor element) {
			List<FlowProperty> items = new ArrayList<>();
			for (FlowPropertyFactor factor : element.flow.flowPropertyFactors)
				items.add(factor.flowProperty);
			return items.toArray(new FlowProperty[items.size()]);
		}

		@Override
		protected FlowProperty getItem(ImpactFactor element) {
			if (element.flowPropertyFactor == null)
				return null;
			return element.flowPropertyFactor.flowProperty;
		}

		@Override
		protected String getText(FlowProperty value) {
			return value.name;
		}

		@Override
		protected void setItem(ImpactFactor f, FlowProperty prop) {
			if (f.flowPropertyFactor == null
					|| !Objects.equals(prop, f.flowPropertyFactor.flowProperty)) {
				FlowPropertyFactor factor = f.flow.getFactor(prop);
				f.flowPropertyFactor = factor;
				editor.setDirty(true);
			}
		}
	}

	private class UnitModifier extends ComboBoxCellModifier<ImpactFactor, Unit> {

		@Override
		protected Unit[] getItems(ImpactFactor f) {
			if (f.flowPropertyFactor == null)
				return new Unit[0];
			if (f.flowPropertyFactor.flowProperty == null)
				return new Unit[0];
			if (f.flowPropertyFactor.flowProperty.unitGroup == null)
				return new Unit[0];
			List<Unit> items = new ArrayList<>();
			for (Unit unit : f.flowPropertyFactor.flowProperty.unitGroup.units)
				items.add(unit);
			return items.toArray(new Unit[items.size()]);
		}

		@Override
		protected Unit getItem(ImpactFactor f) {
			return f.unit;
		}

		@Override
		protected String getText(Unit value) {
			return value.name;
		}

		@Override
		protected void setItem(ImpactFactor f, Unit u) {
			if (!Objects.equals(u, f.unit)) {
				f.unit = u;
				editor.setDirty(true);
			}
		}
	}

	private static class LocationModifier extends DialogCellEditor {

		private final ImpactCategoryEditor editor;
		private ImpactFactor factor;

		LocationModifier(Composite parent, ImpactCategoryEditor editor) {
			super(parent);
			this.editor = editor;
		}

		@Override
		protected void doSetValue(Object value) {
			if (!(value instanceof ImpactFactor)) {
				super.doSetValue("");
				factor = null;
				return;
			}
			factor = (ImpactFactor) value;
			String s = Labels.name(factor.location);
			super.doSetValue(s == null ? "" : s);
		}

		@Override
		protected Object openDialogBox(Control control) {
			if (factor == null)
				return null;
			ModelSelectionDialog dialog = new ModelSelectionDialog(
					ModelType.LOCATION);
			dialog.isEmptyOk = true;
			if (dialog.open() != Window.OK)
				return null;

			CategorizedDescriptor loc = dialog.first();

			// clear the location
			if (loc == null) {
				if (factor.location == null)
					return null;
				// delete the location
				factor.location = null;
				editor.setDirty(true);
				return factor;
			}

			// the same location was selected again
			if (factor.location != null
					&& factor.location.id == loc.id)
				return null;

			// a new location was selected
			LocationDao dao = new LocationDao(Database.get());
			factor.location = dao.getForId(loc.id);
			editor.setDirty(true);
			return factor;
		}
	}

	private class FormulaSwitchAction extends Action {
		public FormulaSwitchAction() {
			setImageDescriptor(Icon.NUMBER.descriptor());
			setText(M.ShowValues);
		}

		@Override
		public void run() {
			showFormulas = !showFormulas;
			if (showFormulas) {
				setImageDescriptor(Icon.NUMBER.descriptor());
				setText(M.ShowValues);
			} else {
				setImageDescriptor(Icon.FORMULA.descriptor());
				setText(M.ShowFormulas);
			}
			viewer.refresh();
		}
	}

}
