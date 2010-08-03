package com.aptana.ui.internal.commands;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;

import com.aptana.core.util.ProcessUtil;

public class OpenInFinderHandler extends AbstractHandler
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		if (event == null)
		{
			return null;
		}
		Object context = event.getApplicationContext();
		if (context instanceof EvaluationContext)
		{
			EvaluationContext evContext = (EvaluationContext) event.getApplicationContext();
			@SuppressWarnings("unchecked")
			List<IResource> selectedFiles = (List<IResource>) evContext.getDefaultVariable();
			for (IResource selected : selectedFiles)
			{
				open(selected);
			}
		}
		return null;
	}

	private boolean open(IResource selected)
	{
		if (Platform.getOS().equals(Platform.OS_MACOSX))
		{
			return openInFinder(selected);
		}
		else if (Platform.getOS().equals(Platform.OS_WIN32))
		{
			return openInWindowsExplorer(selected);
		}
		return openOnLinux(selected);
	}

	private boolean openOnLinux(IResource selected)
	{
		// TODO Do we also need to try 'gnome-open' or 'dolphin' if nautilus fails?
		Map<Integer, String> result = ProcessUtil.runInBackground("nautilus", null, "\""
				+ selected.getLocation().toOSString() + "\"");
		if (result == null)
		{
			return false;
		}
		int exitCode = result.keySet().iterator().next();
		return exitCode == 0;
	}

	private boolean openInWindowsExplorer(IResource selected)
	{
		String systemRoot = System.getenv("SystemRoot");
		String explorer = systemRoot + "\\explorer.exe";
		Map<Integer, String> result = ProcessUtil.runInBackground(explorer, null, "/select,\""
				+ selected.getLocation().toOSString() + "\"");
		if (result == null)
		{
			return false;
		}
		int exitCode = result.keySet().iterator().next();
		return exitCode == 0;
	}

	private boolean openInFinder(IResource selected)
	{
		URI uri = selected.getLocationURI();
		String subcommand = "open";
		String path = uri.getPath();
		if (selected instanceof IFile)
		{
			subcommand = "reveal";
		}
		try
		{
			String appleScript = "tell application \"Finder\" to " + subcommand + " (POSIX file \"" + path + "\")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			ScriptEngineManager mgr = new ScriptEngineManager();
			ScriptEngine engine = mgr.getEngineByName("AppleScript"); //$NON-NLS-1$
			engine.eval(appleScript);
			return true;
		}
		catch (ScriptException e)
		{
			e.printStackTrace();
		}
		return false;
	}

}
