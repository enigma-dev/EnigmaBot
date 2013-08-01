package org.ircbot;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class NetCmds
	{
	public static String command(int auth, String sender, String cmd, String arg)
		{
		if (auth == IrcBot.AUTH_IGNORE) return null;
		if (cmd.equals("define"))
			{
			if (arg.trim().isEmpty()) return "Define what?";
			return define(arg);
			}
		if (cmd.equals("defineurl") || cmd.equals("defineweb")) return defineUrl(arg);
		if (cmd.equals("spell"))
			{
			if (arg.trim().isEmpty()) return "Spell what?";
			return spell(arg);
			}
		if (cmd.equals("get") || cmd.equals("wiki"))
			{
			if (arg.trim().isEmpty())
				return cmd.equals("get") ? "Get what EDL function?" : "http://enigma-dev.org/docs/Wiki";
			return getWiki(arg,cmd.equals("get") ? 1 : 0);
			}
		if (cmd.equals("vps") || cmd.equals("sms")) return sms(auth,sender,arg);

		return null;
		}

	public static String defineMW(String phrase)
		{
		String uphrase = phrase;
		try
			{
			uphrase = URLEncoder.encode(uphrase,"UTF-8");
			}
		catch (UnsupportedEncodingException e)
			{
			}
		String url = "http://www.merriam-webster.com/dictionary/" + uphrase;
		try
			{
			HttpURLConnection conn = (HttpURLConnection) (new URL(url).openConnection());

			// Set up a request.
			conn.setConnectTimeout(5000); // 10 sec
			conn.setReadTimeout(5000); // 10 sec
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("User-agent","spider");

			// Send the request.
			conn.connect();
			InputStream is = conn.getInputStream();
			Scanner sc = new Scanner(is);
			sc.useDelimiter("<strong>:</strong>");
			sc.next();
			sc.useDelimiter("<(?:br/|/p|/div)>");
			String def = sc.next();
			sc.close();
			def = def.replaceAll("<strong>:</strong>",":").replaceAll("\n"," ");
			def = def.replaceAll("<.*?>","--").replaceAll("\\&.*?;","").trim();
			return phrase + def;
			}
		catch (NoSuchElementException e)
			{
			return "I don't have a definition for " + phrase;
			}
		catch (SocketTimeoutException e)
			{
			return "That definition is taking too long.";
			}
		catch (IOException e)
			{
			Messages.error(e);
			return "I've encountered an internet error.";
			}
		}

	public static String defineUrl(String arg)
		{
		String phrase = arg.split("\\|")[0];
		phrase = urlPathEncode(phrase);
		if (phrase.trim().isEmpty()) return "http://www.wordnik.com";
		return "http://www.wordnik.com/words/" + phrase;
		}

	public static String define(String args)
		{
		String[] arg = args.split("\\|");

		String phrase = arg[0];
		phrase = urlPathEncode(phrase);
		String key = "20136bcc66cf6016b300a01f4b402a36245b857cb95c5737c";
		String url = "http://api.wordnik.com/v4/word.xml/" + phrase + "/definitions?api_key=" + key;
		int ind = 0;
		if (arg.length > 1)
			{
			String pos = arg[1];
			if (arg[1].matches("[0-9]+"))
				{
				ind = Integer.parseInt(arg[1]);
				pos = arg.length > 2 ? arg[2] : null;
				}
			else if (arg.length > 2 && arg[2].matches("[0-9]+")) ind = Integer.parseInt(arg[2]);

			String abbr[] = { "n","vt","vi","v","adj","adv" };
			String full[] = { "noun","verb-transitive","verb-intransitive","verb","adjective","adverb" };
			int i = Arrays.asList(abbr).indexOf(pos);
			if (i != -1) pos = full[i];

			if (pos != null)
				{
				try
					{
					pos = URLEncoder.encode(pos,"UTF-8");
					}
				catch (UnsupportedEncodingException e)
					{ //should never happen
					}
				url += "&partOfSpeech=" + pos;
				}
			}

		Document dom = null;

		try
			{
			HttpURLConnection conn = (HttpURLConnection) (new URL(url).openConnection());

			// Set up a request.
			conn.setConnectTimeout(5000); // 10 sec
			conn.setReadTimeout(5000); // 10 sec
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("User-agent","spider");

			// Send the request.
			conn.connect();
			InputStream is = conn.getInputStream();

			dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			}
		catch (ParserConfigurationException e)
			{
			Messages.error(e);
			return "Apparently a \"serious configuration error\" occurred...";
			}
		catch (SAXException e)
			{
			Messages.error(e);
			return "My dictionary is scrambled!";
			}
		catch (SocketTimeoutException e)
			{
			return "That definition is taking too long.";
			}
		catch (IOException e)
			{
			Messages.error(e);
			return "I've encountered an internet error.";
			}

		Map<String,Integer> speech = new TreeMap<String,Integer>();
		NodeList list = dom.getDocumentElement().getChildNodes();
		int s = list.getLength();
		if (s == 0) return "No definitions found. Try !spell <word>";
		for (int i = 0; i < s; i++)
			{
			NodeList children = list.item(i).getChildNodes();
			for (int j = 0; j < children.getLength(); j++)
				{
				Node n = children.item(j);
				if (n.getNodeName().equals("partOfSpeech"))
					{
					String pos = n.getTextContent();
					Integer num = speech.get(pos);
					speech.put(pos,num == null ? 1 : ++num);
					continue;
					}
				}
			}
		if (ind >= s) return "I don't have that many definitions for that word.";

		Element e = (Element) list.item(ind);
		String word = null, pos = null, def = null;
		list = e.getChildNodes();
		s = list.getLength();
		for (int i = 0; i < s; i++)
			{
			Node n = list.item(i);
			if (n.getNodeName().equals("word")) word = n.getTextContent();
			if (n.getNodeName().equals("partOfSpeech")) pos = n.getTextContent();
			if (n.getNodeName().equals("text")) def = n.getTextContent();
			}

		if (word == null || pos == null || def == null)
			return "Someone spilled something over that definition...";

		return String.format("%s (%s): %s %s",word,pos,def.trim(),speech.toString());
		}

	public static String spell(String word)
		{
		try
			{
			Process p = Runtime.getRuntime().exec(new String[] { "aspell","-a" });
			BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
			out.readLine();
			PrintStream in = new PrintStream(new BufferedOutputStream(p.getOutputStream()),true);
			in.println(word);
			in.close();
			String line = out.readLine();
			if (line == null) return "No spelling suggestions for '" + word + "'";
			while (line.equals("*"))
				line = out.readLine();
			if (line.isEmpty()) return "'" + word + "' is spelled correctly";
			if (line.startsWith("#")) return "No spelling suggestions for '" + word + "'";
			int i = line.indexOf(":");
			if (i != -1) return line.substring(i + 1);
			return line;
			}
		catch (IOException e)
			{
			Messages.error(e);
			return "Unable to access spelling dictionary.";
			}
		}

	/**
	 * Encodes URL Path components by escaping any invalid path
	 * characters with their hex %xx equivalent.<p>
	 * Valid characters are: <code>a-z A-Z 0-9 ./:-_?=;@&%#</code><p>
	 * For example, "a /" escapes to "a%20/".
	 * @param part The part of the URL you wish to encode
	 * @return The encoded form of the argument.
	 */
	public static String urlPathEncode(String part)
		{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < part.length(); i++)
			{
			char c = part.charAt(i);
			String valid = "./:-_?=;@&%#";
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
					|| valid.indexOf(c) > -1)
				sb.append(c);
			else
				{
				sb.append('%');
				if (c < 16) sb.append('0');
				sb.append(Integer.toHexString(c));
				}
			}
		return sb.toString();
		}

	/** level is one of { 0 = page link, 1 = notation/description } */
	public static String getWiki(String oarg, int level)
		{
		String arg = Character.toUpperCase(oarg.charAt(0)) + oarg.substring(1);

		arg = arg.replaceAll("\\s","_");
		arg = urlPathEncode(arg);

		String url = "http://enigma-dev.org/docs/Wiki/" + arg;
		String def = null, not = null;

		Scanner sc = null;
		HttpURLConnection conn = null;
		try
			{
			conn = (HttpURLConnection) (new URL(url).openConnection());

			// Set up a request.
			conn.setConnectTimeout(5000); // 5 sec
			conn.setReadTimeout(5000); // 5 sec
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("User-agent","spider");

			// Send the request.
			conn.connect();
			InputStream is = conn.getInputStream();
			if (level == 0)
				{
				is.close();
				conn.disconnect();
				return url;
				}
			sc = new Scanner(is);
			sc.useDelimiter("id=\"(Descrip|Nota)tion\"");

			sc.next();
			String which = sc.nextLine();
			if (which.contains("Nota"))
				not = sc.nextLine();
			else
				def = sc.nextLine();

			sc.next();
			which = sc.nextLine();
			if (which.contains("Nota"))
				not = sc.nextLine();
			else
				def = sc.nextLine();
			}
		catch (NoSuchElementException e)
			{
			}
		catch (SocketTimeoutException e)
			{
			return "Server timeout. Try again later.";
			}
		catch (IOException e)
			{
			if (level == 0) return url + " (Page doesn't exist)";
			}
		finally
			{
			if (sc != null) sc.close();
			if (conn != null) conn.disconnect();
			}

		if (level == 0) return url + " <<OH GOD WUT>>";

		String bad1 = "Sample description.";
		String bad2 = "Description of the function and it's arguments.";
		String bad3 = "General description of the function.";

		if (def != null)
			{
			if (def.startsWith("<p>")) def = def.substring(3);
			def = def.replaceAll("</?[ib]>","*");
			def = def.replaceAll("<.*?>","--").replaceAll("\\&.*?;","").trim();
			if (def.isEmpty() || def.startsWith(bad1) || def.startsWith(bad2) || def.startsWith(bad3))
				def = null;
			}
		if (not != null)
			{
			not = not.replaceAll("<.*?>","").replaceAll("\\&.*?;","").trim();
			if (not.isEmpty()) not = null;
			}

		if (not == null) return "Unknown EDL function: " + oarg;
		if (def == null) return (not + " (YYG) " + getGmWiki(arg));
		return not + ' ' + def;
		}

	public static String getGmWiki(String arg)
		{
		String url = "http://wiki.yoyogames.com/index.php/" + arg;
		String def = null;

		Scanner sc = null;
		HttpURLConnection conn = null;
		try
			{
			conn = (HttpURLConnection) (new URL(url).openConnection());

			// Set up a request.
			conn.setConnectTimeout(5000); // 5 sec
			conn.setReadTimeout(5000); // 5 sec
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("User-agent","spider");

			// Send the request.
			conn.connect();
			InputStream is = conn.getInputStream();

			sc = new Scanner(is);
			sc.useDelimiter("id=\"Description\"");

			sc.next();
			sc.nextLine();
			def = sc.nextLine();
			}
		catch (NoSuchElementException e)
			{
			}
		catch (SocketTimeoutException e)
			{
			return "Server timeout. Try again later.";
			}
		catch (IOException e)
			{
			}
		finally
			{
			if (sc != null) sc.close();
			if (conn != null) conn.disconnect();
			}

		if (def != null)
			{
			if (def.startsWith("<p>")) def = def.substring(3);
			def = def.replaceAll("</?[ib]>","*");
			def = def.replaceAll("<.*?>","--").replaceAll("\\&.*?;","").trim();
			if (def.isEmpty()) def = null;
			}

		return def;
		}

	public static String sms(int auth, String sender, String msg)
		{
		if (auth < IrcBot.AUTH_OP) return null;
		URLConnection conn = null;
		try
			{
			String url = "http://enigma-dev.org/sms.php";
			conn = new URL(url).openConnection();
			conn.setConnectTimeout(5000); // 5 sec
			conn.setDoOutput(true);

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter dos = new OutputStreamWriter(os);
			dos.write("name=" + URLEncoder.encode(sender,"UTF-8"));
			dos.write("&msg=" + URLEncoder.encode(msg,"UTF-8"));
			dos.close();

			InputStream is = conn.getInputStream();
			is.read();
			is.close();
			}
		catch (MalformedURLException e)
			{
			return e.getMessage();
			}
		catch (IOException e)
			{
			return e.getMessage();
			}

		return "Your message has been sent.";
		}
	}