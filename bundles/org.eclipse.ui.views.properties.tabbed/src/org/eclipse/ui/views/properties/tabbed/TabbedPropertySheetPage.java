/*******************************************************************************
 * Copyright (c) 2001, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.views.properties.tabbed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.internal.views.properties.tabbed.view.Tab;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabDescriptor;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabListContentProvider;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyComposite;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyRegistry;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyRegistryFactory;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyTitle;
import org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyViewer;
import org.eclipse.ui.part.IContributedContentsView;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.PropertySheet;

/**
 * A property sheet page that provides a tabbed UI.
 * 
 * @author Anthony Hunter
 */
public class TabbedPropertySheetPage
	extends Page
	implements IPropertySheetPage, ILabelProviderListener {

	// widgets
	private TabbedPropertyComposite tabbedPropertyComposite;

	private TabbedPropertySheetWidgetFactory widgetFactory;

	private ITabbedPropertySheetPageContributor contributor;

	private TabbedPropertyRegistry registry;

	private ITabbedPropertySheetPageContributor selectionContributor = null;

	/**
	 * The currently active contributor id, which may not match the contributor
	 * id from the workbench part that created this instance.
	 */
	private String currentContributorId;

	protected IStructuredContentProvider tabListContentProvider;

	private ISelection currentSelection;

	private boolean activePropertySheet;

	private TabbedPropertyViewer tabbedPropertyViewer;

	private Tab currentTab;

	private Map descriptorToTab;

	private Map tabToComposite;

	private List selectionQueue;

	private boolean selectionQueueLocked;

	private List tabSelectionListeners;

	private IWorkbenchWindow cachedWorkbenchWindow;

	private boolean hasTitleBar;

	/**
	 * a listener that is interested in part activation events.
	 */
	private IPartListener partActivationListener = new IPartListener() {

		public void partActivated(IWorkbenchPart part) {
			handlePartActivated(part);
		}

		public void partBroughtToTop(IWorkbenchPart part) {
		}

		public void partClosed(IWorkbenchPart part) {
		}

		public void partDeactivated(IWorkbenchPart part) {
		}

		public void partOpened(IWorkbenchPart part) {
		}
	};

	private class TabbedPropertySheetPageContributorFromSelection
		implements ITabbedPropertySheetPageContributor {

		private String contributorId;

		/**
		 * Constructor that takes in a contributor id taken from a selection.
		 * 
		 * @param contributorId
		 *            the contributor id.
		 */
		public TabbedPropertySheetPageContributorFromSelection(
				String contributorId) {
			super();
			this.contributorId = contributorId;
		}

		/**
		 * @see org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor#getContributorId()
		 */
		public String getContributorId() {
			return contributorId;
		}

	}

	/**
	 * Label provider for the ListViewer.
	 */
	class TabbedPropertySheetPageLabelProvider
		extends LabelProvider {

		public String getText(Object element) {
			if (element instanceof TabDescriptor) {
				return ((TabDescriptor) element).getLabel();
			}
			return null;
		}
	}

	/**
	 * SelectionChangedListener for the ListViewer.
	 */
	class SelectionChangedListener
		implements ISelectionChangedListener {

		/**
		 * Shows the tab associated with the selection.
		 */
		public void selectionChanged(SelectionChangedEvent event) {
			IStructuredSelection selection = (IStructuredSelection) event
				.getSelection();
			Tab tab = null;
			TabDescriptor descriptor = (TabDescriptor) selection
				.getFirstElement();

			if (descriptor == null) {
				// pretend the tab is empty.
				hideTab(currentTab);
			} else {
				// create tab if necessary
				// can not cache based on the id - tabs may have the same id,
				// but different section depending on the selection
				tab = (Tab) descriptorToTab.get(descriptor);

				if (tab != currentTab) {
					hideTab(currentTab);
				}

				Composite tabComposite = (Composite) tabToComposite.get(tab);
				if (tabComposite == null) {
					tabComposite = createTabComposite();
					tab.createControls(tabComposite,
						TabbedPropertySheetPage.this);
					// tabAreaComposite.layout(true);
					tabToComposite.put(tab, tabComposite);
				}
				// force widgets to be resized
				tab.setInput(tabbedPropertyViewer.getWorkbenchPart(),
					(ISelection) tabbedPropertyViewer.getInput());

				// store tab selection
				storeCurrentTabSelection(descriptor.getLabel());

				if (tab != currentTab) {
					showTab(tab);
				}

				tab.refresh();
			}
			tabbedPropertyComposite.getTabComposite().layout(true);
			currentTab = tab;
			resizeScrolledComposite();

			if (descriptor != null) {
				handleTabSelection(descriptor);
			}
		}

		/**
		 * Shows the given tab.
		 */
		private void showTab(Tab target) {
			if (target != null) {
				Composite tabComposite = (Composite) tabToComposite.get(target);
				if (tabComposite != null) {
					/**
					 * the following method call order is important - do not
					 * change it or the widgets might be drawn incorrectly
					 */
					tabComposite.moveAbove(null);
					target.aboutToBeShown();
					tabComposite.setVisible(true);
				}
			}
		}

		/**
		 * Hides the given tab.
		 */
		private void hideTab(Tab target) {
			if (target != null) {
				Composite tabComposite = (Composite) tabToComposite.get(target);
				if (tabComposite != null) {
					target.aboutToBeHidden();
					tabComposite.setVisible(false);
				}
			}
		}

	}

	/**
	 * create a new tabbed property sheet page.
	 * 
	 * @param tabbedPropertySheetPageContributor
	 *            the tabbed property sheet page contributor.
	 */
	public TabbedPropertySheetPage(
			ITabbedPropertySheetPageContributor tabbedPropertySheetPageContributor) {
		contributor = tabbedPropertySheetPageContributor;
		tabToComposite = new HashMap();
		selectionQueue = new ArrayList(10);
		tabSelectionListeners = new ArrayList();
		initContributor(contributor.getContributorId());
	}

	/**
	 * Handle the part activated event.
	 * 
	 * @param part
	 *            the new activated part.
	 */
	protected void handlePartActivated(IWorkbenchPart part) {
		/*
		 * The properties view has been activated and the current page is this
		 * instance of TabbedPropertySheetPage
		 */
		boolean thisActivated = part instanceof PropertySheet
			&& ((PropertySheet) part).getCurrentPage() == this;

		/*
		 * When the active part changes and the part does not provide a
		 * selection that affects this property sheet page, the PropertySheet
		 * does not send us a selectionChanged() event. We need to be informed
		 * of these events since we want to send aboutToBeHidden() and
		 * aboutToBeShown() when the property sheet is hidden or shown.
		 */
		if (!thisActivated && !part.equals(contributor)) {
			/*
			 * Is the part is a IContributedContentsView for the contributor,
			 * for example, outline view.
			 */
			IContributedContentsView view = (IContributedContentsView) part
				.getAdapter(IContributedContentsView.class);
			if (view == null
				|| (view.getContributingPart() != null && !view
					.getContributingPart().equals(contributor))) {
				if (activePropertySheet) {
					if (currentTab != null) {
						currentTab.aboutToBeHidden();
					}
					activePropertySheet = false;
				}
				return;
			}
		}
		if (!activePropertySheet && currentTab != null) {
			currentTab.aboutToBeShown();
			currentTab.refresh();
		}
		activePropertySheet = true;
	}

	/**
	 * @see org.eclipse.ui.part.IPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		widgetFactory = new TabbedPropertySheetWidgetFactory();
		tabbedPropertyComposite = new TabbedPropertyComposite(parent,
			widgetFactory, hasTitleBar);
		widgetFactory.paintBordersFor(tabbedPropertyComposite);
		tabbedPropertyComposite.setLayout(new FormLayout());
		FormData formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(0, 0);
		formData.bottom = new FormAttachment(100, 0);
		tabbedPropertyComposite.setLayoutData(formData);

		tabbedPropertyViewer = new TabbedPropertyViewer(tabbedPropertyComposite
			.getList());
		tabbedPropertyViewer.setContentProvider(tabListContentProvider);
		tabbedPropertyViewer
			.setLabelProvider(new TabbedPropertySheetPageLabelProvider());
		tabbedPropertyViewer
			.addSelectionChangedListener(new SelectionChangedListener());

		tabbedPropertyComposite.getScrolledComposite().addControlListener(
			new ControlAdapter() {

				public void controlResized(ControlEvent e) {
					resizeScrolledComposite();
				}
			});

		/**
		 * Add a part activation listener.
		 */
		cachedWorkbenchWindow = getSite().getWorkbenchWindow();
		cachedWorkbenchWindow.getPartService().addPartListener(
			partActivationListener);

		/**
		 * Add a label provider change listener.
		 */
		if (hasTitleBar) {
			registry.getLabelProvider().addListener(this);
		}
	}

	/**
	 * Initialize the contributor with the provided contributor id.
	 * 
	 * @param contributorId
	 *            the contributor id.
	 */
	private void initContributor(String contributorId) {
		descriptorToTab = new HashMap();
		if (contributor.getContributorId().equals(contributorId)) {
			/**
			 * default contributor from the workbench part.
			 */
			registry = TabbedPropertyRegistryFactory.getInstance()
				.createRegistry(contributor);
		} else {
			/**
			 * selection contributor.
			 */
			selectionContributor = new TabbedPropertySheetPageContributorFromSelection(
				contributorId);
			registry = TabbedPropertyRegistryFactory.getInstance()
				.createRegistry(selectionContributor);
		}
		currentContributorId = contributorId;
		tabListContentProvider = getTabListContentProvider();
		hasTitleBar = registry.getLabelProvider() != null;

		if (tabbedPropertyViewer != null) {
			tabbedPropertyViewer.setContentProvider(tabListContentProvider);
		}

		/**
		 * Add a label provider change listener.
		 */
		if (hasTitleBar) {
			registry.getLabelProvider().addListener(this);
		}

	}

	/**
	 * Gets the tab list content provider for the contributor.
	 * 
	 * @return the tab list content provider for the contributor.
	 */
	protected IStructuredContentProvider getTabListContentProvider() {
		return new TabListContentProvider(registry);
	}

	/**
	 * Dispose the contributor with the provided contributor id. This happens on
	 * part close as well as when contributiors switch between the workbench
	 * part and contributor from a selection.
	 * 
	 * @param contributorId
	 *            the contributor id.
	 */
	private void disposeContributor() {
		/**
		 * If the current tab is about to be disposed we have to call
		 * aboutToBeHidden
		 */
		if (currentTab != null) {
			currentTab.aboutToBeHidden();
			currentTab = null;
		}

		disposeTabs(descriptorToTab.values());
		descriptorToTab = new HashMap();

		/**
		 * Remove the label provider change listener.
		 */
		if (hasTitleBar && registry != null) {
			registry.getLabelProvider().removeListener(this);
		}

		if (selectionContributor != null) {
			/**
			 * remove the selection contributed registry.
			 */
			TabbedPropertyRegistryFactory.getInstance().disposeRegistry(
				selectionContributor);
			selectionContributor = null;
		}
	}

	/**
	 * @see org.eclipse.ui.part.IPage#dispose()
	 */
	public void dispose() {

		disposeContributor();

		if (widgetFactory != null) {
			widgetFactory.dispose();
			widgetFactory = null;
		}
		/**
		 * Remove the part activation listener.
		 */
		if (cachedWorkbenchWindow != null) {
			cachedWorkbenchWindow.getPartService().removePartListener(
				partActivationListener);
			cachedWorkbenchWindow = null;
		}

		if (registry != null) {
			TabbedPropertyRegistryFactory.getInstance().disposeRegistry(
				contributor);
			registry = null;
		}
	}

	/**
	 * @see org.eclipse.ui.part.IPage#getControl()
	 */
	public Control getControl() {
		return tabbedPropertyComposite;
	}

	/**
	 * @see org.eclipse.ui.part.IPage#setActionBars(org.eclipse.ui.IActionBars)
	 */
	public void setActionBars(IActionBars actionBars) {
		// Override the undo and redo global action handlers
		// to use the contributor action handlers
		if (contributor instanceof IEditorPart) {
			IEditorPart editorPart = (IEditorPart) contributor;
			IActionBars editorActionBars = editorPart.getEditorSite()
				.getActionBars();
			actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(),
				editorActionBars.getGlobalActionHandler(ActionFactory.UNDO
					.getId()));
			actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(),
				editorActionBars.getGlobalActionHandler(ActionFactory.REDO
					.getId()));
		}
	}

	/**
	 * @see org.eclipse.ui.part.IPage#setFocus()
	 */
	public void setFocus() {
		getControl().setFocus();
	}

	/**
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart,
	 *      org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		setInput(part, selection);
	}

	/**
	 * Stores the current tab label in the selection queue. Tab labels are used
	 * to carry the tab context from one input object to another. The queue
	 * specifies the selection priority. So if the first tab in the queue is not
	 * available for the input we try the second tab and so on. If none of the
	 * tabs are available we default to the first tab available for the input.
	 */
	private void storeCurrentTabSelection(String label) {
		if (!selectionQueueLocked) {
			selectionQueue.remove(label);
			selectionQueue.add(0, label);
		}
	}

	private void resizeScrolledComposite() {
		Point currentTabSize = new Point(0, 0);
		if (currentTab != null) {
			Composite sizeReference = (Composite) tabToComposite
				.get(currentTab);
			currentTabSize.y = (sizeReference != null) ? sizeReference
				.computeSize(SWT.DEFAULT, SWT.DEFAULT).y
				: 0;
			currentTabSize.x = (sizeReference != null) ? sizeReference
				.computeSize(SWT.DEFAULT, SWT.DEFAULT).x
				: 0;
		}
		if (hasTitleBar) {
			int titleHeight = tabbedPropertyComposite.getTitle().computeSize(
				SWT.DEFAULT, SWT.DEFAULT).y;
			currentTabSize.y += titleHeight;
		}
		tabbedPropertyComposite.getScrolledComposite().setMinSize(
			currentTabSize.x, currentTabSize.y);
	}

	private void disposeTabs(Collection tabs) {
		for (Iterator iter = tabs.iterator(); iter.hasNext();) {
			Tab tab = (Tab) iter.next();
			Composite composite = (Composite) tabToComposite.remove(tab);
			tab.dispose();
			if (composite != null) {
				composite.dispose();
			}
		}
	}

	/**
	 * Returns the last known selected tab for the given input.
	 */
	private int getLastTabSelection(IWorkbenchPart part, ISelection input) {
		TabDescriptor[] descriptors = registry.getTabDescriptors(part, input);
		if (descriptors.length != 0) {
			for (Iterator iter = selectionQueue.iterator(); iter.hasNext();) {
				String text = (String) iter.next();
				for (int i = 0; i < descriptors.length; i++) {
					if (text.equals(descriptors[i].getLabel())) {
						return i;
					}
				}
			}
		}
		return 0;
	}

	/**
	 * Update the current tabs to represent the given input object. When tabs
	 * apply for both the old and new input they are reused otherwise they are
	 * disposed. If the current visible tab will not be reused (i.e. will be
	 * disposed) we have to send it an aboutToBeHidden() message.
	 */
	protected void updateTabs(TabDescriptor[] descriptors) {
		Map newTabs = new HashMap(descriptors.length * 2);
		boolean disposingCurrentTab = (currentTab != null);
		for (int i = 0; i < descriptors.length; i++) {
			Tab tab = (Tab) descriptorToTab.remove(descriptors[i]);

			if (tab != null && tab.controlsHaveBeenCreated()) {
				if (tab == currentTab) {
					disposingCurrentTab = false;
				}
			} else {
				tab = (descriptors[i]).createTab();
			}

			newTabs.put(descriptors[i], tab);
		}
		if (disposingCurrentTab) {
			/**
			 * If the current tab is about to be disposed we have to call
			 * aboutToBeHidden
			 */
			currentTab.aboutToBeHidden();
			currentTab = null;
		}
		disposeTabs(descriptorToTab.values());
		descriptorToTab = newTabs;
	}

	/**
	 * Helper method for creating property tab composites.
	 */
	private Composite createTabComposite() {
		Composite result = widgetFactory.createComposite(
			tabbedPropertyComposite.getTabComposite(), SWT.NO_FOCUS);
		result.setVisible(false);
		result.setLayout(new FillLayout());
		FormData data = new FormData();
		if (hasTitleBar) {
			data.top = new FormAttachment(tabbedPropertyComposite.getTitle(), 0);
		} else {
			data.top = new FormAttachment(0, 0);
		}
		data.bottom = new FormAttachment(100, 0);
		data.left = new FormAttachment(0, 0);
		data.right = new FormAttachment(100, 0);
		result.setLayoutData(data);
		return result;
	}

	private void setInput(IWorkbenchPart part, ISelection selection) {
		if (selection.equals(currentSelection)) {
			return;
		}

		this.currentSelection = selection;

		// see if the selection provides a new contributor
		validateRegistry(selection);
		TabDescriptor[] descriptors = registry.getTabDescriptors(part,
			currentSelection);
		// If there are no descriptors for the given input we do not need to
		// touch the tab objects. We might reuse them for the next valid
		// input.
		if (descriptors.length > 0) {
			updateTabs(descriptors);
		}
		// update tabs list
		tabbedPropertyViewer.setInput(part, currentSelection);
		int lastTabSelectionIndex = getLastTabSelection(part, currentSelection);
		Object selectedTab = tabbedPropertyViewer
			.getElementAt(lastTabSelectionIndex);
		selectionQueueLocked = true;
		try {
			if (selectedTab == null) {
				tabbedPropertyViewer.setSelection(null);
			} else {
				tabbedPropertyViewer.setSelection(new StructuredSelection(
					selectedTab));
			}
		} finally {
			selectionQueueLocked = false;
		}
		refreshTitleBar();
	}

	/**
	 * Refresh the currently active tab.
	 */
	public void refresh() {
		currentTab.refresh();
	}

	/**
	 * Get the currently active tab.
	 * 
	 * @return the currently active tab.
	 */
	public Tab getCurrentTab() {
		return currentTab;
	}

	/**
	 * Handle the tab selected change event.
	 * 
	 * @param tabDescriptor
	 *            the new selected tab.
	 */
	private void handleTabSelection(TabDescriptor tabDescriptor) {
		if (selectionQueueLocked) {
			/*
			 * don't send tab selection events for non user changes.
			 */
			return;
		}
		for (Iterator i = tabSelectionListeners.iterator(); i.hasNext();) {
			ITabSelectionListener listener = (ITabSelectionListener) i.next();
			listener.tabSelected(tabDescriptor);
		}
	}

	/**
	 * Add a tab selection listener.
	 * 
	 * @param listener
	 *            a tab selection listener.
	 */
	public void addTabSelectionListener(ITabSelectionListener listener) {
		tabSelectionListeners.add(listener);
	}

	/**
	 * Remove a tab selection listener.
	 * 
	 * @param listener
	 *            a tab selection listener.
	 */
	public void removeTabSelectionListener(ITabSelectionListener listener) {
		tabSelectionListeners.remove(listener);
	}

	/**
	 * Get the widget factory.
	 * 
	 * @return the widget factory.
	 */
	public TabbedPropertySheetWidgetFactory getWidgetFactory() {
		return widgetFactory;
	}

	/**
	 * Update the title bar of the contributor has a label provider.
	 */
	private void refreshTitleBar() {
		if (hasTitleBar) {
			TabbedPropertyTitle title = tabbedPropertyComposite.getTitle();
			if (currentTab == null) {
				/**
				 * No tabs are shown so hide the title bar, otherwise you see
				 * "No properties available" and a title bar for the selection.
				 */
				title.setTitle(null, null);
			} else {
				String text = registry.getLabelProvider().getText(
					currentSelection);
				Image image = registry.getLabelProvider().getImage(
					currentSelection);
				title.setTitle(text, image);
			}
		}
	}

	/**
	 * @see org.eclipse.jface.viewers.ILabelProviderListener#labelProviderChanged(org.eclipse.jface.viewers.LabelProviderChangedEvent)
	 */
	public void labelProviderChanged(LabelProviderChangedEvent event) {
		refreshTitleBar();
	}

    /**
     * Retrieve the contributor from the selection.
     * 
     * @param object -
     *            the selected element
     * @return the TabbedPropertySheetPageContributor or null if not applicable
     */
    private ITabbedPropertySheetPageContributor getTabbedPropertySheetPageContributor(
            Object object) {
        Object element = object instanceof IAdaptable ? ((IAdaptable) object)
            .getAdapter(ITabbedPropertySheetPageContributor.class)
            : object;
        return element instanceof ITabbedPropertySheetPageContributor ? (ITabbedPropertySheetPageContributor) element
            : null;
    }

	/**
	 * The workbench part creates this instance of the TabbedPropertySheetPage
	 * and implements ITabbedPropertySheetPageContributor which is unique
	 * contributor id. This unique contributor id is used to load a registry
	 * with the extension point This id matches the registry.
	 * <p>
	 * It is possible for elements in a selection to implement
	 * ITabbedPropertySheetPageContributor to provide a different contributor id
	 * and thus a differenent registry.
	 * 
	 * @param selection
	 *            the current selection in the active workbench part.
	 */
	private void validateRegistry(ISelection selection) {
		if (selection == null) {
			return;
		}

		if (!(selection instanceof IStructuredSelection)) {
			return;
		}

		IStructuredSelection structuredSelection = (IStructuredSelection) selection;
		if (structuredSelection.size() == 0) {
			return;
		}

        ITabbedPropertySheetPageContributor newContributor = getTabbedPropertySheetPageContributor(structuredSelection.getFirstElement());
        
		if (newContributor == null) {
			/**
			 * selection does not implement or adapt ITabbedPropertySheetPageContributor.
			 */
			return;
		}
		
        String selectionContributorId = newContributor.getContributorId();
		if (selectionContributorId.equals(currentContributorId)) {
			/**
			 * selection has the same contributor id as current, so leave
			 * existing registry.
			 */
			return;
		}

		/**
		 * Selection implements ITabbedPropertySheetPageContributor different
		 * than current contributor id, so make sure all elements implement the
		 * new id. If all contributor id do not match, then fall back to default
		 * contributor from the workbench part.
		 */
		Iterator i = structuredSelection.iterator();
        i.next();
		while (i.hasNext()) {
            newContributor = getTabbedPropertySheetPageContributor(i.next());
			if (newContributor == null || !newContributor.getContributorId().equals(selectionContributorId)) {
				/**
				 * fall back to use the default contributor id from the
				 * workbench part.
				 */
				if (selectionContributor != null) {
					disposeContributor();
					currentContributorId = contributor.getContributorId();
					initContributor(currentContributorId);
				}
				return;
			}
		}

		/**
		 * All the elements in the selection implement a new contributor id, so
		 * use that id.
		 */
		disposeContributor();
		currentContributorId = selectionContributorId;
		initContributor(currentContributorId);
	}

}