package org.openlca.app.results.analysis.sankey.model;

import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.swt.SWT;
import org.openlca.app.db.Database;
import org.openlca.app.tools.graphics.model.Component;
import org.openlca.app.util.CostResultDescriptor;
import org.openlca.app.util.Labels;
import org.openlca.core.matrix.index.EnviFlow;
import org.openlca.core.matrix.index.TechFlow;
import org.openlca.core.model.Flow;
import org.openlca.core.model.descriptors.ImpactDescriptor;
import org.openlca.core.results.Sankey;
import org.openlca.util.Strings;

import static org.openlca.app.results.analysis.sankey.layouts.SankeyLayout.DEFAULT_LOCATION;

public class SankeyNode extends Component {

	public static final Dimension DEFAULT_SIZE = new Dimension(280, SWT.DEFAULT);

	public final Sankey.Node node;
	public final TechFlow product;
	public final double directShare;
	public final String unit;

	public SankeyNode(Sankey.Node node, Sankey<?> sankey) {
		this.node = node;
		product = node.product;
		directShare = sankey.root.total != 0
				? Math.abs(node.direct / sankey.root.total) + 0.0
				: 0;

		var db = Database.get();

		if (sankey.reference instanceof EnviFlow enviFlow) {
			var flow = db.get(Flow.class, enviFlow.flow().id);
			unit = flow.getReferenceUnit().name;
		}
		else if (sankey.reference instanceof ImpactDescriptor impact)
			unit = impact.referenceUnit;
		else if (sankey.reference instanceof CostResultDescriptor cost)
			unit = cost.name;
		else unit = "";

		setLocation(DEFAULT_LOCATION);
		setSize(DEFAULT_SIZE);
	}

	public Diagram getDiagram() {
		return (Diagram) getParent();
	}

	public boolean isReference() {
		return getDiagram().isReferenceNode(this);
	}

	@Override
	public int compareTo(Component other) {
		if (other instanceof SankeyNode n)
			return Strings.compare(getComparisonLabel(), n.getComparisonLabel());
		else return 0;
	}

	@Override
	public String getComparisonLabel() {
		if (product == null)
			return "";
		return Labels.name(product.provider());
	}

	public String toString() {
		return "Node: "+ Labels.name(node.product.provider());
	}

}
