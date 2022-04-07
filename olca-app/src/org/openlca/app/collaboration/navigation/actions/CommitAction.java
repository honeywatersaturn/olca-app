package org.openlca.app.collaboration.navigation.actions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.openlca.app.M;
import org.openlca.app.collaboration.dialogs.CommitDialog;
import org.openlca.app.collaboration.dialogs.HistoryDialog;
import org.openlca.app.collaboration.dialogs.LibraryRestrictionDialog;
import org.openlca.app.collaboration.navigation.RepositoryLabel;
import org.openlca.app.collaboration.preferences.CollaborationPreference;
import org.openlca.app.collaboration.util.WebRequests.WebRequestException;
import org.openlca.app.collaboration.viewers.diff.DiffNodeBuilder;
import org.openlca.app.collaboration.viewers.diff.DiffResult;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.MsgBox;
import org.openlca.git.actions.GitCommit;
import org.openlca.git.actions.GitPush;
import org.openlca.git.model.Change;
import org.openlca.git.util.DiffEntries;

public class CommitAction extends Action implements INavigationAction {

	private List<INavigationElement<?>> selection;

	@Override
	public String getText() {
		return M.Commit + "...";
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return Icon.COMMIT.descriptor();
	}

	@Override
	public boolean isEnabled() {
		return RepositoryLabel.hasChanged(Navigator.findElement(Database.getActiveConfiguration()));
	}

	@Override
	public void run() {
		try {
			var committer = Repository.get().personIdent();
			if (committer == null)
				return;
			var changes = getWorkspaceChanges();
			var dialog = createCommitDialog(changes);
			if (dialog == null)
				return;
			var dialogResult = dialog.open();
			if (dialogResult == CommitDialog.CANCEL)
				return;
			var withReferences = dialog.getSelected();
			// new ReferenceCheck(Database.get()).run(dialog.getSelected(),
			// changes);
			if (withReferences == null)
				return;
			if (!checkLibraries(withReferences))
				return;
			GitCommit.from(Database.get())
					.to(Repository.get().git)
					.changes(withReferences)
					.withMessage(dialog.getMessage())
					.as(committer)
					.update(Repository.get().workspaceIds)
					.run();
			if (dialogResult != CommitDialog.COMMIT_AND_PUSH)
				return;
			var result = Actions.run(GitPush
					.to(Repository.get().git)
					.authorizeWith(Actions.credentialsProvider()));
			if (result.status() == Status.REJECTED_NONFASTFORWARD) {
				MsgBox.error("Rejected - Not up to date - Please merge remote changes to continue");
			} else {
				Collections.reverse(result.newCommits());
				new HistoryDialog("Pushed commits", result.newCommits()).open();
			}
		} catch (IOException | GitAPIException | InvocationTargetException | InterruptedException e) {
			Actions.handleException("Error during commit", e);
		} finally {
			Actions.refresh();
		}
	}

	private List<Change> getWorkspaceChanges() throws IOException {
		var commit = Repository.get().commits.head();
		return DiffEntries.workspace(Repository.get().toConfig(), commit).stream()
				.map(Change::new)
				.toList();
	}

	private CommitDialog createCommitDialog(List<Change> changes) {
		var differences = changes.stream()
				.map(DiffResult::new)
				.toList();
		var node = new DiffNodeBuilder(Database.get()).build(differences);
		if (node == null) {
			MsgBox.info("No changes to commit");
			return null;
		}
		var dialog = new CommitDialog(node);
		dialog.setInitialSelection(selection);
		return dialog;
	}

	private boolean checkLibraries(List<Change> changes) {
		if (!CollaborationPreference.checkAgainstLibraries())
			return true;
		if (!Repository.get().isCollaborationServer())
			return true;
		try {
			var restricted = Repository.get().client.performLibraryCheck(changes);
			if (restricted.isEmpty())
				return true;
			var code = new LibraryRestrictionDialog(restricted).open();
			return code == LibraryRestrictionDialog.OK;
		} catch (WebRequestException e) {
			Actions.handleException("Error performing library check", e);
			return false;
		}
	}

	@Override
	public boolean accept(List<INavigationElement<?>> selection) {
		if (!Repository.isConnected())
			return false;
		this.selection = selection;
		return true;
	}

}
