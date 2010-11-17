/**
 * This file Copyright (c) 2005-2010 Aptana, Inc. This program is
 * dual-licensed under both the Aptana Public License and the GNU General
 * Public license. You may elect to use one or the other of these licenses.
 * 
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT. Redistribution, except as permitted by whichever of
 * the GPL or APL you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or modify this
 * program under the terms of the GNU General Public License,
 * Version 3, as published by the Free Software Foundation.  You should
 * have received a copy of the GNU General Public License, Version 3 along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Aptana provides a special exception to allow redistribution of this file
 * with certain other free and open source software ("FOSS") code and certain additional terms
 * pursuant to Section 7 of the GPL. You may view the exception and these
 * terms on the web at http://www.aptana.com/legal/gpl/.
 * 
 * 2. For the Aptana Public License (APL), this program and the
 * accompanying materials are made available under the terms of the APL
 * v1.0 which accompanies this distribution, and is available at
 * http://www.aptana.com/legal/apl/.
 * 
 * You may view the GPL, Aptana's exception and additional terms, and the
 * APL in the file titled license.html at the root of the corresponding
 * plugin containing this source file.
 * 
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.scripting;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.contentobjects.jnotify.IJNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

import com.aptana.filewatcher.FileWatcher;
import com.aptana.scripting.model.AbstractElement;
import com.aptana.scripting.model.BundleChangeListener;
import com.aptana.scripting.model.BundleElement;
import com.aptana.scripting.model.BundleEntry;
import com.aptana.scripting.model.BundleManager;
import com.aptana.scripting.model.CommandContext;
import com.aptana.scripting.model.CommandElement;
import com.aptana.scripting.model.ElementChangeListener;
import com.aptana.scripting.model.TriggerType;

/**
 * FileWatcherRegistrant
 */
public class FileWatcherRegistrant implements BundleChangeListener, ElementChangeListener, JNotifyListener
{
	private static FileWatcherRegistrant INSTANCE;

	/**
	 * getInstance
	 * 
	 * @return
	 */
	public static synchronized FileWatcherRegistrant getInstance()
	{
		if (INSTANCE == null)
		{
			INSTANCE = new FileWatcherRegistrant();
			INSTANCE.setup();
		}

		return INSTANCE;
	}

	/**
	 * shutdown
	 */
	public static synchronized void shutdown()
	{
		if (INSTANCE != null)
		{
			INSTANCE.tearDown();

			// loose ref to this instance
			INSTANCE = null;
		}
	}

	private Map<File, Set<CommandElement>> _fileToCommandMap = new HashMap<File, Set<CommandElement>>();
	private Map<CommandElement, Set<File>> _commandToFilesMap = new HashMap<CommandElement, Set<File>>();
	private Map<File, Integer> _fileWatcherId = new HashMap<File, Integer>();
	private Map<Integer, File> _watcherIdFile = new HashMap<Integer, File>();

	/**
	 * FileWatcherRegistrant
	 */
	private FileWatcherRegistrant()
	{
	}

	/**
	 * addCommand
	 * 
	 * @param file
	 * @param element
	 */
	private void addCommand(File file, CommandElement element)
	{
		Set<File> files = this._commandToFilesMap.get(element);

		if (files == null)
		{
			files = new HashSet<File>();

			this._commandToFilesMap.put(element, files);
		}

		files.add(file);
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.BundleChangeListener#added(com.aptana.scripting.model.BundleElement)
	 */
	public void bundleAdded(BundleElement bundle)
	{
	}

	/**
	 * addFile
	 * 
	 * @param file
	 * @param element
	 */
	private void addFile(File file, CommandElement element)
	{
		Set<CommandElement> commands = this._fileToCommandMap.get(file);

		if (commands == null)
		{
			commands = new HashSet<CommandElement>();

			this._fileToCommandMap.put(file, commands);
		}

		commands.add(element);
	}

	/**
	 * addWatcher
	 * 
	 * @param command
	 */
	protected void addWatcher(CommandElement command)
	{
		// grab the list of files that this command wants to track
		String[] filenames = command.getTriggerTypeValues(TriggerType.FILE_WATCHER);

		if (filenames != null && filenames.length > 0)
		{
			// create a copy of all files we're tracking already
			Set<File> beforeFiles = new HashSet<File>(this._fileToCommandMap.keySet());
			// update our file/command graph

			for (String filename : filenames)
			{
				File file = new File(filename).getAbsoluteFile();

				addFile(file, command);
				addCommand(file, command);
			}

			// create a copy of the new list of files we're tracking
			Set<File> afterFiles = new HashSet<File>(this._fileToCommandMap.keySet());

			// remove the original list from the new list so we know what was added
			afterFiles.removeAll(beforeFiles);

			// create file watchers for each new file we're supposed to track
			for (File file : afterFiles)
			{
				try
				{
					int id = FileWatcher.addWatch(file.getAbsolutePath(), IJNotify.FILE_ANY, true, this);

					this._fileWatcherId.put(file, id);
					this._watcherIdFile.put(id, file);
				}
				catch (JNotifyException e)
				{
					Activator.logError(e.getMessage(), e);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.BundleChangeListener#becameHidden(com.aptana.scripting.model.BundleEntry)
	 */
	public void bundlesBecameHidden(BundleEntry entry)
	{
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.BundleChangeListener#becameVisible(com.aptana.scripting.model.BundleEntry)
	 */
	public void bundlesBecameVisible(BundleEntry entry)
	{
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.BundleChangeListener#deleted(com.aptana.scripting.model.BundleElement)
	 */
	public void bundleDeleted(BundleElement bundle)
	{
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.ElementChangeListener#elementAdded(com.aptana.scripting.model.AbstractElement)
	 */
	public void elementAdded(AbstractElement element)
	{
		if (element instanceof CommandElement)
		{
			this.addWatcher((CommandElement) element);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.scripting.model.ElementChangeListener#elementDeleted(com.aptana.scripting.model.AbstractElement)
	 */
	public void elementDeleted(AbstractElement element)
	{
		if (element instanceof CommandElement)
		{
			this.removeWatcher((CommandElement) element);
		}
	}

	private void execute(int wd, String type, String... properties)
	{
		// create property map
		Map<String, String> propertyMap = new HashMap<String, String>();
		
		// add type
		propertyMap.put("type", type);
		
		// add optional key/values
		int length = properties.length & ~0x01;

		for (int i = 0; i < length; i += 2)
		{
			String name = properties[i];
			String value = properties[i + 1];

			propertyMap.put(name, value);
		}

		// get commands for this watch id
		Set<CommandElement> commands = this.getCommandsByWatchId(wd);

		// and execute each
		for (CommandElement command : commands)
		{
			CommandContext context = command.createCommandContext();

			context.put(TriggerType.FILE_WATCHER.getName(), propertyMap);
			command.execute(context);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see net.contentobjects.jnotify.JNotifyListener#fileCreated(int, java.lang.String, java.lang.String)
	 */
	public void fileCreated(int wd, String rootPath, String name)
	{
		this.execute( //
			wd, //
			"created", //
			"rootPath", rootPath, //
			"name", name //
		);
	}

	/*
	 * (non-Javadoc)
	 * @see net.contentobjects.jnotify.JNotifyListener#fileDeleted(int, java.lang.String, java.lang.String)
	 */
	public void fileDeleted(int wd, String rootPath, String name)
	{
		this.execute( //
			wd, //
			"deleted", //
			"rootPath", rootPath, //
			"name", name //
		);
	}

	/*
	 * (non-Javadoc)
	 * @see net.contentobjects.jnotify.JNotifyListener#fileModified(int, java.lang.String, java.lang.String)
	 */
	public void fileModified(int wd, String rootPath, String name)
	{
		this.execute( //
			wd, //
			"modified", //
			"rootPath", rootPath, //
			"name", name //
		);
	}

	/*
	 * (non-Javadoc)
	 * @see net.contentobjects.jnotify.JNotifyListener#fileRenamed(int, java.lang.String, java.lang.String,
	 * java.lang.String)
	 */
	public void fileRenamed(int wd, String rootPath, String oldName, String newName)
	{
		this.execute( //
			wd, //
			"deleted", //
			"rootPath", rootPath, //
			"oldName", oldName, //
			"newName", newName //
		);
	}

	/**
	 * getCommandsByWatchId
	 * 
	 * @param wd
	 * @return
	 */
	private Set<CommandElement> getCommandsByWatchId(int wd)
	{
		File file = this._watcherIdFile.get(wd);
		Set<CommandElement> result;

		if (file != null)
		{
			result = this._fileToCommandMap.get(file);
		}
		else
		{
			result = Collections.emptySet();
		}

		return result;
	}

	/**
	 * removeWatcher
	 * 
	 * @param command
	 */
	protected void removeWatcher(CommandElement command)
	{
		if (this._commandToFilesMap.containsKey(command))
		{
			for (File file : this._commandToFilesMap.get(command))
			{
				if (this._fileToCommandMap.containsKey(file))
				{
					Set<CommandElement> commands = this._fileToCommandMap.get(file);

					commands.remove(command);

					if (commands.size() == 0)
					{
						// no more commands watching this file, so remove the watch
						if (this._fileWatcherId.containsKey(file))
						{
							int id = this._fileWatcherId.remove(file);
							this._watcherIdFile.remove(id);

							try
							{
								FileWatcher.removeWatch(id);
							}
							catch (JNotifyException e)
							{
								Activator.logError(e.getMessage(), e);
							}
						}

						// remove hash entry as well
						this._fileToCommandMap.remove(file);
					}
				}
			}

			// remove hash entry for this command
			this._commandToFilesMap.remove(command);
		}
	}

	private void setup()
	{
		BundleManager manager = BundleManager.getInstance();

		manager.addBundleChangeListener(this);
		manager.addElementChangeListener(this);
	}

	/**
	 * tearDown
	 */
	private void tearDown()
	{
		BundleManager manager = BundleManager.getInstance();

		manager.removeBundleChangeListener(this);
		manager.removeElementChangeListener(this);

		// remove all watches
		for (int id : this._watcherIdFile.keySet())
		{
			try
			{
				FileWatcher.removeWatch(id);
			}
			catch (JNotifyException e)
			{
				Activator.logError(e.getMessage(), e);
			}
		}

		// drop all references
		this._commandToFilesMap.clear();
		this._fileToCommandMap.clear();
		this._fileWatcherId.clear();
		this._watcherIdFile.clear();
	}
}
