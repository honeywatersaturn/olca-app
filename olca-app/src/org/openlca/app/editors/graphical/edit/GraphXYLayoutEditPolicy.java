package org.openlca.app.editors.graphical.edit;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.PrecisionRectangle;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.geometry.Translatable;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.gef.editpolicies.ResizableEditPolicy;
import org.eclipse.gef.editpolicies.XYLayoutEditPolicy;
import org.eclipse.gef.requests.ChangeBoundsRequest;
import org.eclipse.gef.requests.CreateRequest;
import org.openlca.app.editors.graphical.GraphConfig;
import org.openlca.app.editors.graphical.model.Graph;
import org.openlca.app.editors.graphical.model.commands.CreateNodeCommand;
import org.openlca.app.editors.graphical.model.commands.CreateStickyNoteCommand;
import org.openlca.app.editors.graphical.model.commands.EditConfigCommand;
import org.openlca.app.editors.graphical.model.commands.GraphLayoutCommand;
import org.openlca.app.editors.graphical.requests.GraphRequest;
import org.openlca.app.tools.graphics.model.Component;
import org.openlca.app.tools.graphics.model.commands.ComponentSetConstraintCommand;
import org.openlca.core.model.descriptors.RootDescriptor;

import static org.openlca.app.editors.graphical.GraphConfig.CONFIG_PROP;
import static org.openlca.app.editors.graphical.requests.GraphRequestConstants.REQ_LAYOUT;
import static org.openlca.app.editors.graphical.requests.GraphRequestConstants.REQ_EDIT_CONFIG;

public class GraphXYLayoutEditPolicy extends XYLayoutEditPolicy {

	@Override
	public Command getCommand(Request request) {
		if (REQ_LAYOUT.equals(request.getType()))
			return new GraphLayoutCommand((GraphEditPart) getHost());
		if (REQ_CREATE.equals(request.getType()))
			return getCreateCommand((GraphRequest) request);
		if (REQ_EDIT_CONFIG.equals(request.getType()))
			return getEditConfigCommand(request);
		return super.getCommand(request);
	}

	@Override
	protected Command getCreateCommand(CreateRequest request) {
		return null;
	}

	protected Command getCreateCommand(GraphRequest request) {
		var command = new CompoundCommand();
		command.setDebugLabel("Create in GraphXYLayoutEditPolicy");

		var descriptors = request.getDescriptors();

		if (descriptors == null)
			command.add(createCreateStickyNoteCommand(
					(Rectangle) getConstraintFor(request)));
		else for (var descriptor : descriptors)
			command.add(createCreateNodeCommand(descriptor,
				(Rectangle) getConstraintFor(request)));

		return command.unwrap();
	}

	protected Command createCreateNodeCommand(RootDescriptor descriptor,
																						Rectangle constraint) {
		var graph = (Graph) getHost().getModel();
		return new CreateNodeCommand(graph, descriptor, constraint);
	}

	protected Command createCreateStickyNoteCommand(Rectangle constraint) {
		var graph = (Graph) getHost().getModel();
		return new CreateStickyNoteCommand(graph, constraint);
	}

	@Override
	protected Command createChangeConstraintCommand(
		ChangeBoundsRequest request, EditPart child, Object constraint) {
		if (child instanceof AbstractVertexEditPart
				&& constraint instanceof Rectangle) {
			return new ComponentSetConstraintCommand((Component) child.getModel(),
				request, (Rectangle) constraint);
		}
		return super.createChangeConstraintCommand(request, child,
			constraint);
	}

	@Override
	protected EditPolicy createChildEditPolicy(EditPart child) {
		var policy = new ResizableEditPolicy();
		if (child instanceof NodeEditPart)
			policy.setResizeDirections(
					PositionConstants.EAST | PositionConstants.WEST);
		return policy;
	}

	/**
	 * Generates a draw2d constraint for the given <code>GraphRequest</code>.
	 *
	 * If the GraphRequest has a size, is used during size-on-drop creation, a
	 * Rectangle of the request's location and size is passed with the
	 * delegation. Otherwise, a rectangle with the request's location and an
	 * empty size (0,0) is passed over.
	 * <P>
	 * The GraphRequest's location is relative to the Viewer. The location is
	 * made layout-relative by using
	 * {@link #translateFromAbsoluteToLayoutRelative(Translatable)} before
	 * calling {@link #getConstraintFor(Request, GraphicalEditPart, Rectangle)}.
	 *
	 * @param request
	 *            the GraphRequest
	 * @return a draw2d constraint
	 */
	protected Object getConstraintFor(GraphRequest request) {
		var locationAndSize = (request.getSize() == null
			|| request.getSize().isEmpty())
			? new PrecisionRectangle(request.getLocation(), UNSPECIFIED_SIZE)
			: new PrecisionRectangle(request.getLocation(), request.getSize());

		translateFromAbsoluteToLayoutRelative(locationAndSize);
		return getConstraintFor(request, null, locationAndSize);
	}

	private Command getEditConfigCommand(Request request) {
		var newConfig = (GraphConfig) request.getExtendedData().get(CONFIG_PROP);
		return new EditConfigCommand((Graph) getHost().getModel(), newConfig);
	}

}
