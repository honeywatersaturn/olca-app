package org.openlca.app.collaboration.viewers.diff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeItem;
import org.openlca.app.collaboration.viewers.json.label.Direction;
import org.openlca.app.util.UI;
import org.openlca.git.model.DiffType;
import org.openlca.git.util.TypeRefIdSet;

public class CommitViewer extends DiffNodeViewer {

	private List<DiffNode> selected = new ArrayList<>();
	// The option fixNewElements will prevent the user to uncheck "NEW"
	// elements, used in ReferencesResultDialog
	private boolean lockNewElements;

	public CommitViewer(Composite parent, boolean lockNewElements) {
		super(parent, false);
		super.setDirection(Direction.LEFT_TO_RIGHT);
		this.lockNewElements = lockNewElements;
	}

	@Override
	public final void setDirection(Direction direction) {
		throw new UnsupportedOperationException("Can't change commit direction");
	}

	public void setSelection(TypeRefIdSet initialSelection) {
		selected = findNodes(initialSelection, root);
		var expanded = new HashSet<String>();
		var tree = getViewer().getTree();
		for (var node : selected) {
			if (!node.isModelNode())
				continue;
			var result = node.contentAsDiffResult();
			var path = result.type.name() + "/" + result.category;
			if (expanded.contains(path))
				continue;
			expanded.add(path);
			getViewer().reveal(node);
		}
		tree.setRedraw(false);
		setChecked(initialSelection, tree.getItems());
		tree.setRedraw(true);
	}

	// can't use setChecked(Object[]) for performance reasons. Original method
	// reveals path internally for all elements, which is unnecessary because
	// this is already done in a more efficient way in setInitialSelection
	private void setChecked(TypeRefIdSet models, TreeItem[] items) {
		for (var item : items) {
			var node = (DiffNode) item.getData();
			if (node != null && node.isModelNode()) {
				var result = node.contentAsDiffResult();
				// null is used as hack to select all
				if (models == null || models.contains(result.type, result.refId))
					item.setChecked(true);
			}
			setChecked(models, item.getItems());
		}
	}

	public void selectAll() {
		setSelection(null);
	}

	private List<DiffNode> findNodes(TypeRefIdSet models, DiffNode node) {
		var elements = new ArrayList<DiffNode>();
		for (var child : node.children) {
			if (child.isModelNode() && child.hasChanged()) {
				// TODO && child.getContent().local.tracked
				var result = child.contentAsDiffResult();
				// null is used as hack to select all
				if (models == null || models.contains(result.type, result.refId))
					elements.add(child);
			}
			elements.addAll(findNodes(models, child));
		}
		return elements;
	}

	@Override
	protected TreeViewer createViewer(Composite parent) {
		var viewer = new CheckboxTreeViewer(parent, SWT.BORDER);
		configureViewer(viewer, true);
		viewer.addCheckStateListener((e) -> setChecked(viewer, (DiffNode) e.getElement(), e.getChecked()));
		UI.gridData(viewer.getTree(), true, true);
		return viewer;
	}

	private void setChecked(CheckboxTreeViewer viewer, DiffNode node, boolean value) {
		var result = node.contentAsDiffResult();
		if (result == null || result.noAction()) {
			// TODO || !result.local.tracked
			viewer.setChecked(node, false);
		} else if (value) {
			selected.add(node);
		} else if (lockNewElements && result.leftDiffType == DiffType.ADDED) {
			viewer.setChecked(node, true);
		} else {
			selected.remove(node);
		}
	}

	@Override
	public CheckboxTreeViewer getViewer() {
		return (CheckboxTreeViewer) super.getViewer();
	}

	public List<DiffNode> getChecked() {
		return selected;
	}

	public boolean hasChecked() {
		return !selected.isEmpty();
	}

}
