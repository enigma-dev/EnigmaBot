package org.ircbot;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class BotProps
	{
	private static final String BUNDLE_NAME = "org.ircbot.bot"; //$NON-NLS-1$
	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private BotProps()
		{
		}

	public static String getString(String key)
		{
		try
			{
			return RESOURCE_BUNDLE.getString(key);
			}
		catch (MissingResourceException e)
			{
			return null;
			}
		}
	}
