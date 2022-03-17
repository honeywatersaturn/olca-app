package org.openlca.app.editors.epds;

import org.openlca.app.tools.openepd.model.EpdDoc;
import org.openlca.app.tools.openepd.model.EpdImpactResult;
import org.openlca.app.tools.openepd.model.EpdIndicatorResult;
import org.openlca.app.tools.openepd.model.Ec3Epd;
import org.openlca.app.tools.openepd.model.EpdMeasurement;
import org.openlca.app.tools.openepd.model.EpdOrg;
import org.openlca.app.tools.openepd.model.EpdPcr;
import org.openlca.app.tools.openepd.model.EpdQuantity;
import org.openlca.app.tools.openepd.model.EpdScopeValue;
import org.openlca.core.model.Actor;
import org.openlca.core.model.Epd;
import org.openlca.core.model.Source;
import org.openlca.util.Categories;
import org.openlca.util.Pair;
import org.openlca.util.Strings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;

class EpdConverter {

	static Validation validate(Epd epd) {
		if (epd == null)
			return Validation.error("The EPD is empty.");
		if (epd.product == null || epd.product.flow == null)
			return Validation.error("The EPD has no product.");
		if (epd.product.unit == null)
			return Validation.error("The EPD has no declared unit");
		if (epd.product.amount == 0)
			return Validation.error("The product amount is 0.");

		for (var module : epd.modules) {
			var result = module.result;
			if (result == null)
				return Validation.error("The EPD contains modules without results.");
			if (result.impactMethod == null)
				return Validation.error(
					"The EPD contains module results without links to LCIA methods.");
			if (Strings.nullOrEmpty(result.impactMethod.code))
				return Validation.error(
					"The EPD contains links to LCIA methods without mapping codes.");
			for (var impact : result.impactResults) {
				if (impact.indicator == null
					|| Strings.nullOrEmpty(impact.indicator.code))
					return Validation.error(
						"The EPD contains links to LCIA categories without mapping codes.");
			}
		}
		return Validation.ok();
	}

	static Ec3Epd toEc3(Epd epd) {
		var doc = new Ec3Epd();
		doc.isDraft = true;
		doc.isPrivate = true;
		doc.name = epd.name;
		if (epd.product != null && epd.product.unit != null) {
			doc.declaredUnit = epd.product.amount + " " + epd.product.unit.name;
		}
		doc.description = epd.description;
		var today = LocalDate.now();
		doc.dateOfIssue = today;
		doc.dateValidityEnds = LocalDate.of(
			today.getYear() + 1, today.getMonth(), today.getDayOfMonth());

		doc.impactResults.addAll(convertResults(epd));
		return doc;
	}

	static EpdDoc toOpenEpd(Epd epd) {
		var doc = new EpdDoc();
		doc.isPrivate = true;
		doc.version = 1;
		doc.productName = epd.name;
		if (epd.product != null && epd.product.unit != null) {
			doc.declaredUnit = new EpdQuantity(
				epd.product.amount, epd.product.unit.name);
		}
		doc.lcaDiscussion = epd.description;
		var today = LocalDate.now();
		doc.dateOfIssue = today;
		doc.dateValidityEnds = LocalDate.of(
			today.getYear() + 1, today.getMonth(), today.getDayOfMonth());

		// category
		if (epd.category != null) {
			var path = Categories.path(epd.category);
			if (!path.isEmpty()) {
				doc.productClasses.add(
					Pair.of("io.cqd.ec3", String.join(" >> ", path)));
			}
		}

		doc.manufacturer = toOrg(epd.manufacturer);
		doc.verifier = toOrg(epd.verifier);
		doc.programOperator = toOrg(epd.programOperator);
		doc.pcr = toPcr(epd.pcr);

		doc.impactResults.addAll(convertResults(epd));
		return doc;
	}

	private static EpdOrg toOrg(Actor actor) {
		if (actor == null)
			return null;
		var org = new EpdOrg();
		org.name = actor.name;
		org.webDomain = actor.website;
		return org;
	}

	private static EpdPcr toPcr(Source source) {
		if (source == null)
			return null;
		var pcr = new EpdPcr();
		pcr.id = source.refId;
		pcr.name = source.name;
		return pcr;
	}

	private static Collection<EpdImpactResult> convertResults(Epd epd) {
		var map = new HashMap<String, EpdImpactResult>();
		for (var mod : epd.modules) {
			var result = mod.result;
			if (result == null
				|| result.impactMethod == null
				|| Strings.nullOrEmpty(result.impactMethod.code))
				continue;
			var docResult = map.computeIfAbsent(
				result.impactMethod.code,
				code -> new EpdImpactResult(code, new ArrayList<>()));

			for (var impact : result.impactResults) {
				if (impact.indicator == null
					|| Strings.nullOrEmpty(impact.indicator.code))
					continue;
				var code = impact.indicator.code;
				EpdIndicatorResult docImpact = null;
				for (var i : docResult.indicatorResults()) {
					if (Objects.equals(code, i.indicator())) {
						docImpact = i;
						break;
					}
				}
				if (docImpact == null) {
					docImpact = new EpdIndicatorResult(code, new ArrayList<>());
					docResult.indicatorResults().add(docImpact);
				}
				var value = EpdMeasurement.of(
					impact.amount, impact.indicator.referenceUnit);
				docImpact.values().add(new EpdScopeValue(mod.name, value));
			}
		}
		return map.values();
	}

}

record Validation(String error) {

	static Validation ok() {
		return new Validation(null);
	}

	static Validation error(String message) {
		return new Validation(message);
	}

	boolean hasError() {
		return error != null;
	}

}
