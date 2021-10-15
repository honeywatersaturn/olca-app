package org.openlca.app.editors.results.openepd.input;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.openlca.app.editors.results.openepd.model.Ec3Epd;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.FlowPropertyFactor;
import org.openlca.core.model.FlowType;
import org.openlca.core.model.ResultFlow;
import org.openlca.core.model.ResultOrigin;
import org.openlca.core.model.Unit;

class Util {

	private Util() {
	}

	static ResultFlow initQuantitativeReference(Ec3Epd epd, IDatabase db) {
		var f = new ResultFlow();
		f.flow = new Flow();
		f.isInput = false;
		f.flow.flowType = FlowType.PRODUCT_FLOW;
		f.flow.name = epd.name;
		f.origin = ResultOrigin.IMPORTED;
		var quantity = Quantity.detect(epd, db);
		f.amount = quantity.amount();
		if (quantity.hasUnit()) {
			f.unit = quantity.unit();
			setFlowProperty(f, quantity.property());
		}
		return f;
	}

	static void setFlowProperty(ResultFlow resultFlow, FlowProperty property) {
		if (resultFlow == null || resultFlow.flow == null)
			return;
		var f = resultFlow.flow;
		f.flowPropertyFactors.clear();
		f.referenceFlowProperty = null;
		if (property == null)
			return;

		var factor = new FlowPropertyFactor();
		factor.conversionFactor = 1.0;
		factor.flowProperty = property;
		f.flowPropertyFactors.add(factor);
		f.referenceFlowProperty = property;
	}

	static List<Unit> unitsOf(FlowProperty property) {
		if (property == null || property.unitGroup == null)
			return Collections.emptyList();
		var units = property.unitGroup.units;
		units.sort(Comparator.comparing(u -> u.name));
		return units;
	}

}
