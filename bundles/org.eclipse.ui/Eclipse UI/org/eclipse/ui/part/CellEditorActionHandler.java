package org.eclipse.ui.part;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import org.eclipse.ui.*;
import org.eclipse.ui.internal.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.util.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Handles the redirection of the global actions Cut, Copy, Paste,
 * Delete, Select All, Find, Undo and Redo to either the current
 * inline cell editor or the part's supplied action handler.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p><p>
 * Example usage:
 * <pre>
 * actionHandler = new CellEditorActionHandler(this.getViewSite().getActionBars());
 * actionHandler.addCellEditor(textCellEditor1);
 * actionHandler.addCellEditor(textCellEditor2);
 * actionHandler.setSelectAllAction(selectAllAction);
 * </pre>
 * </p>
 */
public class CellEditorActionHandler {
	private CutActionHandler cellCutAction = new CutActionHandler();
	private CopyActionHandler cellCopyAction = new CopyActionHandler();
	private PasteActionHandler cellPasteAction = new PasteActionHandler();
	private DeleteActionHandler cellDeleteAction = new DeleteActionHandler();
	private SelectAllActionHandler cellSelectAllAction = new SelectAllActionHandler();
	private FindActionHandler cellFindAction = new FindActionHandler();
	private UndoActionHandler cellUndoAction = new UndoActionHandler();
	private RedoActionHandler cellRedoAction = new RedoActionHandler();
	
	private IAction cutAction;
	private IAction copyAction;
	private IAction pasteAction;
	private IAction deleteAction;
	private IAction selectAllAction;
	private IAction findAction;
	private IAction undoAction;
	private IAction redoAction;
	
	private IPropertyChangeListener cutActionListener = new ActionEnabledChangeListener(cellCutAction);
	private IPropertyChangeListener copyActionListener = new ActionEnabledChangeListener(cellCopyAction);
	private IPropertyChangeListener pasteActionListener = new ActionEnabledChangeListener(cellPasteAction);
	private IPropertyChangeListener deleteActionListener = new ActionEnabledChangeListener(cellDeleteAction);
	private IPropertyChangeListener selectAllActionListener = new ActionEnabledChangeListener(cellSelectAllAction);
	private IPropertyChangeListener findActionListener = new ActionEnabledChangeListener(cellFindAction);
	private IPropertyChangeListener undoActionListener = new ActionEnabledChangeListener(cellRedoAction);
	private IPropertyChangeListener redoActionListener = new ActionEnabledChangeListener(cellUndoAction);
	
	private CellEditor activeEditor;
	private IPropertyChangeListener cellListener = new CellChangeListener();
	private Listener controlListener = new ControlListener();
	private HashMap controlToEditor = new HashMap();

	
	private class ControlListener implements Listener {
		public void handleEvent(Event event) {
			switch (event.type) {
				case SWT.Activate:
					activeEditor = (CellEditor)controlToEditor.get(event.widget);
					if (activeEditor != null)
						activeEditor.addPropertyChangeListener(cellListener);
					updateActionsEnableState();
					break;
				case SWT.Deactivate:
					if (activeEditor != null)
						activeEditor.removePropertyChangeListener(cellListener);
					activeEditor = null;
					updateActionsEnableState();
					break;
				default:
					break;
			}
		}
	}
	
	private class ActionEnabledChangeListener implements IPropertyChangeListener {
		private IAction actionHandler;
		protected ActionEnabledChangeListener(IAction actionHandler) {
			super();
			this.actionHandler = actionHandler;
		}
		public void propertyChange(PropertyChangeEvent event) {
			if (activeEditor != null)
				return;
			if (event.getProperty().equals(IAction.ENABLED)) {
				Boolean bool = (Boolean) event.getNewValue();
				actionHandler.setEnabled(bool.booleanValue());
				return;
			}
		}
	};
	
	private class CellChangeListener implements IPropertyChangeListener {
		public void propertyChange(PropertyChangeEvent event) {
			if (activeEditor == null)
				return;
			if (event.getProperty().equals(CellEditor.CUT)) {
				cellCutAction.setEnabled(activeEditor.isCutEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.COPY)) {
				cellCopyAction.setEnabled(activeEditor.isCopyEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.PASTE)) {
				cellPasteAction.setEnabled(activeEditor.isPasteEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.DELETE)) {
				cellDeleteAction.setEnabled(activeEditor.isDeleteEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.SELECT_ALL)) {
				cellSelectAllAction.setEnabled(activeEditor.isSelectAllEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.FIND)) {
				cellFindAction.setEnabled(activeEditor.isFindEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.UNDO)) {
				cellUndoAction.setEnabled(activeEditor.isUndoEnabled());
				return;
			}
			if (event.getProperty().equals(CellEditor.REDO)) {
				cellRedoAction.setEnabled(activeEditor.isRedoEnabled());
				return;
			}
		}
	};
	
	private class CutActionHandler extends Action {
		protected CutActionHandler() {
			super(WorkbenchMessages.getString("Cut")); //$NON-NLS-1$
			setId("CellEditorCutActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL |'x');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performCut();
				return;
			}
			if (cutAction != null) {
				cutAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isCutEnabled());
				return;
			}
			if (cutAction != null) {
				setEnabled(cutAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
	
	private class CopyActionHandler extends Action {
		protected CopyActionHandler() {
			super(WorkbenchMessages.getString("Copy")); //$NON-NLS-1$
			setId("CellEditorCopyActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL |'c');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performCopy();
				return;
			}
			if (copyAction != null) {
				copyAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isCopyEnabled());
				return;
			}
			if (copyAction != null) {
				setEnabled(copyAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
	
	private class PasteActionHandler extends Action {
		protected PasteActionHandler() {
			super(WorkbenchMessages.getString("Paste")); //$NON-NLS-1$
			setId("CellEditorPasteActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL |'v');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performPaste();
				return;
			}
			if (pasteAction != null) {
				pasteAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isPasteEnabled());
				return;
			}
			if (pasteAction != null) {
				setEnabled(pasteAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
	
	private class DeleteActionHandler extends Action {
		protected DeleteActionHandler() {
			super(WorkbenchMessages.getString("Delete")); //$NON-NLS-1$
			setId("CellEditorDeleteActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.DEL);
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performDelete();
				return;
			}
			if (deleteAction != null) {
				deleteAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isDeleteEnabled());
				return;
			}
			if (deleteAction != null) {
				setEnabled(deleteAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
	
	private class SelectAllActionHandler extends Action {
		protected SelectAllActionHandler() {
			super(WorkbenchMessages.getString("TextAction.selectAll")); //$NON-NLS-1$
			setId("CellEditorSelectAllActionHandler");//$NON-NLS-1$
			setEnabled(false);
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performSelectAll();
				return;
			}
			if (selectAllAction != null) {
				selectAllAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isSelectAllEnabled());
				return;
			}
			if (selectAllAction != null) {
				setEnabled(selectAllAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}

	private class FindActionHandler extends Action {
		protected FindActionHandler() {
			super(WorkbenchMessages.getString("Workbench.findReplace")); //$NON-NLS-1$
			setId("CellEditorFindActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL |'f');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performFind();
				return;
			}
			if (findAction != null) {
				findAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isFindEnabled());
				return;
			}
			if (findAction != null) {
				setEnabled(findAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}

	private class UndoActionHandler extends Action {
		protected UndoActionHandler() {
			super(WorkbenchMessages.getString("Workbench.undo")); //$NON-NLS-1$
			setId("CellEditorUndoActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL | 'z');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performUndo();
				return;
			}
			if (undoAction != null) {
				undoAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isUndoEnabled());
				return;
			}
			if (undoAction != null) {
				setEnabled(undoAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
	
	private class RedoActionHandler extends Action {
		protected RedoActionHandler() {
			super(WorkbenchMessages.getString("Workbench.redo")); //$NON-NLS-1$
			setId("CellEditorRedoActionHandler");//$NON-NLS-1$
			setEnabled(false);
			setAccelerator(SWT.CTRL |'y');
		}
		public void run() {
			if (activeEditor != null) {
				activeEditor.performRedo();
				return;
			}
			if (redoAction != null) {
				redoAction.run();
				return;
			}
		}
		public void updateEnabledState() {
			if (activeEditor != null) {
				setEnabled(activeEditor.isRedoEnabled());
				return;
			}
			if (redoAction != null) {
				setEnabled(redoAction.isEnabled());
				return;
			}
			setEnabled(false);
		}
	}
/**
 * Creates a <code>CellEditor</code> action handler
 * for the global Cut, Copy, Paste, Delete, Select All,
 * Find, Undo, and Redo of the action bar.
 *
 * @param actionBar the action bar to register global
 *    action handlers.
 */
public CellEditorActionHandler(IActionBars actionBar) {
	super();
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.CUT, cellCutAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.COPY, cellCopyAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.PASTE, cellPasteAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.DELETE, cellDeleteAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.SELECT_ALL, cellSelectAllAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.FIND, cellFindAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.UNDO, cellUndoAction);
	actionBar.setGlobalActionHandler(IWorkbenchActionConstants.REDO, cellRedoAction);
}
/**
 * Adds a <code>CellEditor</code> to the handler so that the
 * Cut, Copy, Paste, Delete, Select All, Find, Undo, and Redo
 * actions are redirected to it when active.
 *
 * @param editor the <code>CellEditor</code>
 */
public void addCellEditor(CellEditor editor) {
	if (editor == null)
		return;

	Control control = editor.getControl();
	controlToEditor.put(control, editor);
	control.addListener(SWT.Activate, controlListener);
	control.addListener(SWT.Deactivate, controlListener);

	if (control.isFocusControl()) {
		activeEditor = editor;
		editor.addPropertyChangeListener(cellListener);
		updateActionsEnableState();
	}
}
/**
 * Disposes of this action handler
 */
public void dispose() {
	setCutAction(null);
	setCopyAction(null);
	setPasteAction(null);
	setDeleteAction(null);
	setSelectAllAction(null);
	setFindAction(null);
	setUndoAction(null);
	setRedoAction(null);

	Iterator enum = controlToEditor.keySet().iterator();
	while (enum.hasNext()) {
		Control control = (Control)enum.next();
		if (!control.isDisposed()) {
			control.removeListener(SWT.Activate, controlListener);
			control.removeListener(SWT.Deactivate, controlListener);
		}
	}
	controlToEditor.clear();

	if (activeEditor != null)
		activeEditor.removePropertyChangeListener(cellListener);
	activeEditor = null;

}
/**
 * Removes a <code>CellEditor</code> from the handler
 * so that the Cut, Copy, Paste, Delete, Select All, Find
 * Undo, and Redo actions are no longer redirected to it.
 *
 * @param editor the <code>CellEditor</code>
 */
public void removeCellEditor(CellEditor editor) {
	if (editor == null)
		return;

	if (activeEditor == editor) {
		activeEditor.removePropertyChangeListener(cellListener);
		activeEditor = null;
	}
	
	Control control = editor.getControl();
	controlToEditor.remove(control);
	if (!control.isDisposed()) {
		control.removeListener(SWT.Activate, controlListener);
		control.removeListener(SWT.Deactivate, controlListener);
	}
}
/**
 * Sets the default <code>IAction</code> handler for the Copy
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Copy action, or <code>null</null> if not interested.
 */
public void setCopyAction(IAction action) {
	if (copyAction == action)
		return;

	if (copyAction != null)
		copyAction.removePropertyChangeListener(copyActionListener);
		
	copyAction = action;

	if (copyAction != null)
		copyAction.addPropertyChangeListener(copyActionListener);

	cellCopyAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Cut
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Cut action, or <code>null</null> if not interested.
 */
public void setCutAction(IAction action) {
	if (cutAction == action)
		return;

	if (cutAction != null)
		cutAction.removePropertyChangeListener(cutActionListener);
		
	cutAction = action;

	if (cutAction != null)
		cutAction.addPropertyChangeListener(cutActionListener);

	cellCutAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Delete
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Delete action, or <code>null</null> if not interested.
 */
public void setDeleteAction(IAction action) {
	if (deleteAction == action)
		return;

	if (deleteAction != null)
		deleteAction.removePropertyChangeListener(deleteActionListener);
		
	deleteAction = action;

	if (deleteAction != null)
		deleteAction.addPropertyChangeListener(deleteActionListener);

	cellDeleteAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Find
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Find action, or <code>null</null> if not interested.
 */
public void setFindAction(IAction action) {
	if (findAction == action)
		return;

	if (findAction != null)
		findAction.removePropertyChangeListener(findActionListener);
		
	findAction = action;

	if (findAction != null)
		findAction.addPropertyChangeListener(findActionListener);

	cellFindAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Paste
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Paste action, or <code>null</null> if not interested.
 */
public void setPasteAction(IAction action) {
	if (pasteAction == action)
		return;

	if (pasteAction != null)
		pasteAction.removePropertyChangeListener(pasteActionListener);
		
	pasteAction = action;

	if (pasteAction != null)
		pasteAction.addPropertyChangeListener(pasteActionListener);

	cellPasteAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Redo
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Redo action, or <code>null</null> if not interested.
 */
public void setRedoAction(IAction action) {
	if (redoAction == action)
		return;

	if (redoAction != null)
		redoAction.removePropertyChangeListener(redoActionListener);
		
	redoAction = action;

	if (redoAction != null)
		redoAction.addPropertyChangeListener(redoActionListener);

	cellRedoAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Select All
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Select All action, or <code>null</null> if not interested.
 */
public void setSelectAllAction(IAction action) {
	if (selectAllAction == action)
		return;

	if (selectAllAction != null)
		selectAllAction.removePropertyChangeListener(selectAllActionListener);
		
	selectAllAction = action;

	if (selectAllAction != null)
		selectAllAction.addPropertyChangeListener(selectAllActionListener);

	cellSelectAllAction.updateEnabledState();
}
/**
 * Sets the default <code>IAction</code> handler for the Undo
 * action. This <code>IAction</code> is run only if no active
 * cell editor control.
 *
 * @param action the <code>IAction</code> to run for the
 *    Undo action, or <code>null</null> if not interested.
 */
public void setUndoAction(IAction action) {
	if (undoAction == action)
		return;

	if (undoAction != null)
		undoAction.removePropertyChangeListener(undoActionListener);
		
	undoAction = action;

	if (undoAction != null)
		undoAction.addPropertyChangeListener(undoActionListener);

	cellUndoAction.updateEnabledState();
}
/**
 * Updates the enable state of the Cut, Copy,
 * Paste, Delete, Select All, Find, Undo, and
 * Redo action handlers
 */
private void updateActionsEnableState() {
	cellCutAction.updateEnabledState();
	cellCopyAction.updateEnabledState();
	cellPasteAction.updateEnabledState();
	cellDeleteAction.updateEnabledState();
	cellSelectAllAction.updateEnabledState();
	cellFindAction.updateEnabledState();
	cellUndoAction.updateEnabledState();
	cellRedoAction.updateEnabledState();
}
}
