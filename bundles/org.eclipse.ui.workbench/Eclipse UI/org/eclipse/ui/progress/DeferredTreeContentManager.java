/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.progress;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.progress.IElementCollector;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.ui.internal.misc.Assert;
import org.eclipse.ui.internal.progress.PendingUpdateAdapter;
import org.eclipse.ui.internal.progress.ProgressMessages;

/**
 * The DeferredContentManager is a class that helps an ITreeContentProvider
 * get its deferred input.
 * 
 * @see IDeferredWorkbenchAdapter
 */
public class DeferredTreeContentManager {

	ITreeContentProvider contentProvider;
	AbstractTreeViewer treeViewer;

	/**
	 * Create a new instance of the receiver using the supplied content
	 * provider and viewer.
	 * @param provider
	 * @param viewer
	 */

	public DeferredTreeContentManager(
		ITreeContentProvider provider,
		AbstractTreeViewer viewer) {
		contentProvider = provider;
		treeViewer = viewer;
	}

	/**
	 * Provides an optimized lookup for determining if an element has children. This is
	 * required because elements that are populated lazilly can't answer <code>getChildren</code>
	 * just to determine the potential for children.
	 * Throw an AssertionFailedException if element is not an instance of
	 * IDeferredWorkbenchAdapter.
	 * @param element Object
	 * @return boolean 
	 */
	public boolean mayHaveChildren(Object element) {
		IDeferredWorkbenchAdapter adapter = getAdapter(element);

		Assert.isNotNull(element, ProgressMessages.getString("DeferredTreeContentManager.NotDeferred")); //$NON-NLS-1$

		return adapter.isContainer();
	}

	/**
	 * Returns the child elements of the given element, or in the case of a deferred element, returns
	 * a placeholder. If a deferred element used a job is created to fetch the children in the background.
	 * @return Object[] or <code>null</code> if parent is not an instance
	 * of IDeferredWorkbenchAdapter.
	 */
	public Object[] getChildren(final Object parent) {
		IDeferredWorkbenchAdapter element = getAdapter(parent);
		if (element == null)
			return null;

		PendingUpdateAdapter placeholder = new PendingUpdateAdapter();
		startFetchingDeferredChildren(parent, element, placeholder);
		return new Object[] { placeholder };
	}

	/**
	 * Return the IDeferredWorkbenchAdapter for element or the element
	 * if it is an instance of IDeferredWorkbenchAdapter. If it
	 * does not exist return null.
	 * @param element
	 * @return IDeferredWorkbenchAdapter or <code>null</code>
	 */
	private IDeferredWorkbenchAdapter getAdapter(Object element) {

		if (element instanceof IDeferredWorkbenchAdapter)
			return (IDeferredWorkbenchAdapter) element;

		if (!(element instanceof IAdaptable))
			return null;

		Object adapter =
			((IAdaptable) element).getAdapter(IDeferredWorkbenchAdapter.class);
		if (adapter == null)
			return null;
		else
			return (IDeferredWorkbenchAdapter) adapter;

	}

	/**
	 * Starts a job and creates a collector for fetching the children of this deferred adapter. If children
	 * are waiting to be retrieve for this parent already, that job is cancelled and another is started.
	 * @param parent. The parent object being filled in,
	 * @param adapter The adapter being used to fetch the children.
	 */
	private void startFetchingDeferredChildren(
		final Object parent,
		final IDeferredWorkbenchAdapter adapter,
		final PendingUpdateAdapter placeholder) {

		final IElementCollector collector = new IElementCollector() {
			public void add(Object element, IProgressMonitor monitor) {
				add(new Object[] { element }, monitor);
			}
			public void add(Object[] elements, IProgressMonitor monitor) {
				addChildren(parent, elements, placeholder, monitor);
			}
		};

		// Cancel any jobs currently fetching children for the same parent instance.
		Platform.getJobManager().cancel(parent);
			String jobName = ProgressMessages.format("DeferredTreeContentManager.FetchingName", //$NON-NLS-1$
	new Object[] { adapter.getLabel(parent)});
		Job job = new Job(jobName) {
			public IStatus run(IProgressMonitor monitor) {
				adapter.fetchDeferredChildren(parent, collector, monitor);
				return Status.OK_STATUS;
			}
			
			/**
			 * Check if the object is equal to parent or one 
			 * of parents children so that the job can be cancelled
			 * if the parent is refreshed.
			 */
			public boolean belongsTo(Object family) {
				if(parent.equals(family))
					return true;
				return checkParent(family);
			}
			
			/**
			 * Check if the parent of element is equal to the 
			 * parent used in this job.
			 * @return boolean
			 */
			private boolean checkParent(Object element){
				Object parent = adapter.getParent(element);
				if(parent == null)
					return false;
				if(parent.equals(element))
					return true;
				return checkParent(parent);					
			}
		};
		job.addJobChangeListener(new JobChangeAdapter() {
			/* (non-Javadoc)
			 * @see org.eclipse.core.runtime.jobs.JobChangeAdapter#done(org.eclipse.core.runtime.jobs.IJobChangeEvent)
			 */
			public void done(IJobChangeEvent event) {
				if(placeholder.isRemoved())
					return;
				//Clear the placeholder if it is still there
				UIJob clearJob = new UIJob(ProgressMessages.getString("DeferredTreeContentManager.ClearJob")) { //$NON-NLS-1$
					/* (non-Javadoc)
					 * @see org.eclipse.ui.progress.UIJob#runInUIThread(org.eclipse.core.runtime.IProgressMonitor)
					 */
					public IStatus runInUIThread(IProgressMonitor monitor) {
						clearPlaceholder(placeholder);
						return Status.OK_STATUS;
					}
				};
				clearJob.setSystem(true);
				clearJob.schedule();
				super.done(event);
			}
		});
		job.setRule(adapter.getRule());
		job.schedule();
	}

	/**
	 * Create a UIJob to add the children to the parent in the tree viewer.
	 * @param parent
	 * @param children
	 * @param monitor
	 */
	private void addChildren(
		final Object parent,
		final Object[] children,
		final PendingUpdateAdapter placeholder,
		IProgressMonitor monitor) {

			UIJob updateJob = new UIJob(ProgressMessages.getString("DeferredTreeContentManager.AddingChildren")) {//$NON-NLS-1$
			/* (non-Javadoc)
			 * @see org.eclipse.ui.progress.UIJob#runInUIThread(org.eclipse.core.runtime.IProgressMonitor)
			 */
			public IStatus runInUIThread(IProgressMonitor monitor) {

					//Cancel the job if the tree viewer got closed
				if (treeViewer.getControl().isDisposed())
					return Status.CANCEL_STATUS;

				clearPlaceholder(placeholder);
				treeViewer.add(parent, children);

				return Status.OK_STATUS;
			}
		};

		updateJob.schedule();
	}

	/**
	 * Return whether or not the element is or adapts to
	 * an IDeferredWorkbenchAdapter.
	 * @param element
	 * @return
	 */
	public boolean isDeferredAdapter(Object element) {
		return getAdapter(element) != null;
	}

	private void clearPlaceholder(PendingUpdateAdapter placeholder) {
		if (!placeholder.isRemoved()) {
			treeViewer.remove(placeholder);
			placeholder.setRemoved(true);
		}
	}
}
