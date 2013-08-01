/*
 * Copyright (C) 2007, 2008 Quadduc <quadduc@gmail.com>
 * 
 * This file is part of LateralGM.
 * 
 * LateralGM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * LateralGM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License (COPYING) for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ircbot;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class Messages
	{
	private static final String BUNDLE_NAME = "org.ircbot.messages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private Messages()
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
			return '!' + key + '!';
			}
		}

	public static String format(String key, Object...arguments)
		{
		try
			{
			String p = RESOURCE_BUNDLE.getString(key);
			return MessageFormat.format(p,arguments);
			}
		catch (MissingResourceException e)
			{
			return '!' + key + '!';
			}
		}

	public static void error(Exception e)
		{
		e.printStackTrace();
		}

	public static String formatTime(long time)
		{
		return formatTime(new Date(time),time,false);
		}

	public static String formatTime(Date date)
		{
		return formatTime(date,false);
		}

	public static String formatTime(Date date, boolean hideDiff)
		{
		if (date == null) return "<unknown>";
		return formatTime(date,date.getTime(),hideDiff);
		}

	public static final DateFormat TIMESTAMP = new SimpleDateFormat("HH:mm:ss");
	public static final DateFormat DATETIME = new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss");

	private static String formatTime(Date date, long time, boolean hideDiff)
		{
		Ago ago = getAgo(time);
		if (ago == null) return DATETIME.format(date);
		String iso = ago.type == Ago.Type.DAY ? DATETIME.format(date) : TIMESTAMP.format(date);
		if (hideDiff) return iso;
		return ago.toString() + " ago: " + iso;
		}

	public static class Ago
		{
		public int num;
		public Type type;

		public enum Type
			{
			SECOND,MINUTE,HOUR,DAY
			}

		public Ago(int num, Type type)
			{
			this.num = num;
			this.type = type;
			}

		public String toString()
			{
			return String.format("%d %s",num,type.name().toLowerCase() + (num != 1 ? "s" : ""));
			}
		}

	public static Ago getAgo(long time)
		{
		int diff = (int) ((System.currentTimeMillis() - time) / 60000);
		if (diff < 60) return new Ago(diff,Ago.Type.MINUTE);
		diff /= 60;
		if (diff < 24) return new Ago(diff,Ago.Type.HOUR);
		diff /= 24;
		if (diff < 28) return new Ago(diff,Ago.Type.DAY);
		return null;
		}
	}
