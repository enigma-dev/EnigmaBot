package org.ircbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public final class GitCmds {
	static class Project {
		static final File WCDIR = new File(BotProps.getString("GIT.WCDIR"));

		final File wc;
		final String url;
		Git git;
		Repository repo;
		boolean ready = false;

		/** Every project must define a GIT.URL.[name] external string. */
		public Project(String name) {
			wc = new File(WCDIR, name.toLowerCase());
			url = BotProps.getString("GIT.URL." + name);

			new Thread() {
				public void run() {
					git = setup("https://" + url + ".git");
					if (git != null)
						repo = git.getRepository();
					ready = true;
				}
			}.start();
		}

		public Git setup(String url) {
			try {
				if (wc.exists()) {
					Git g = Git.open(wc);
					try {
						g.pull().call();
					} catch (Exception e) {
						System.err.println(url);
						e.printStackTrace();
					}
					return g;
				}
				return Git.cloneRepository().setURI(url).setDirectory(wc)
						.call();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	static Map<String, Project> projects = new HashMap<String, Project>();

	static {
		projects.put("e", new Project("ENIGMA"));
		projects.put("l", new Project("LGM"));
		projects.put("jdi", new Project("JDI"));
		projects.put("je", new Project("JE"));
		projects.put("jeie", new Project("JEIE"));
		projects.put("js", new Project("JS"));
	}

	private GitCmds() {
	}

	public static void init() {
	}

	public static String command(final String cmd, String arg) {
		if (cmd.endsWith("rev") || cmd.endsWith("msg") || cmd.endsWith("age")
				|| cmd.endsWith("svn")) {
			Project p = projects.get(cmd.substring(0, cmd.length() - 3));
			if (p == null)
				return null;
			if (!p.ready)
				return Messages.getString("GIT_NOT_READY");
			if (p.git == null || p.repo == null)
				return Messages.getString("GIT_PRIOR_FAIL");
			try {
				p.git.pull().call();
				if (cmd.endsWith("age")) {
					if (arg.trim().isEmpty())
						return Messages.getString("HELP_AGE");
					return getAgeString(p.repo, arg);
				}
				if (cmd.endsWith("svn")) {
					if (arg.trim().isEmpty())
						return Messages.getString("HELP_SVN");
					try {
						return svnToHash(p.repo, Integer.parseInt(arg));
					} catch (NumberFormatException e) {
						return Messages.getString("GIT_SVN_BAD_NUMBER");
					}
				}
				return getRevInfo(p.repo, arg.trim().isEmpty() ? "HEAD" : arg);
			} catch (IOException e) {
				e.printStackTrace();
				return "Owe. " + e.getClass();
			} catch (GitAPIException e) {
				e.printStackTrace();
				return "Owe. " + e.getClass();
			}
		}

		if (cmd.endsWith("diff")) {
			Project p = projects.get(cmd.substring(0, cmd.length() - 4));
			if (p == null)
				return null;
			try {
				arg = URLEncoder.encode(arg, "UTF-8");
			} catch (UnsupportedEncodingException e) { // should never happen
			}
			return "https://" + p.url + "/commit/" + arg;
		}

		if (cmd.endsWith("grep") || cmd.endsWith("grepi")) {
			boolean i = cmd.endsWith("grepi");
			Project p = projects.get(cmd.substring(0, cmd.length()
					- (i ? 5 : 4)));
			if (p == null)
				return null;
			if (!p.ready)
				return Messages.getString("GIT_NOT_READY");
			if (p.git == null || p.repo == null)
				return Messages.getString("GIT_PRIOR_FAIL");
			return grep(p.repo, arg, i);
		}

		if (cmd.equals("grepnext"))
			return grepNext();

		if (cmd.endsWith("url") || cmd.endsWith("log")) {
			boolean log = cmd.endsWith("log");
			String start = cmd.substring(0, cmd.length() - 3);
			boolean grep = start.endsWith("grep");
			if (grep)
				start = start.substring(0, start.length() - 4);
			Project p = projects.get(start);
			if (p == null)
				return null;
			String url = "https://" + p.url;
			if (!grep && arg.trim().isEmpty()) {
				if (log)
					url += "/commits/master";
				return Messages.format("HELP_FILE", cmd, url);
			}
			if (!p.ready)
				return Messages.getString("GIT_NOT_READY");
			if (p.git == null || p.repo == null)
				return Messages.getString("GIT_PRIOR_FAIL");
			String r;
			if (grep) {
				r = grepLast == null ? null : grepLast.split(":")[0]
						.substring(2);
			} else
				try {
					r = findFile(p.wc, arg);
				} catch (PatternSyntaxException e) {
					return Messages.getString("GIT_FILE_BAD_REGEX");
				}
			if (r == null)
				return Messages.getString("GIT_FILE_NOT_FOUND");
			return url + (log ? "/commits/master/" : "/blob/master/") + r;
		}

		// not consumed
		return null;
	}

	// Revisions

	public static String getRevInfo(Repository r, String revstr)
			throws IOException {
		try {
			ObjectId oid = r.resolve(revstr);
			if (oid == null)
				return Messages.getString("GIT_RESOLVE_NULL");
			return getRevInfo(r, oid);
		} catch (AmbiguousObjectException e) {
			return getAmbiguousRevisionError(r, e.getCandidates());
		} catch (IncorrectObjectTypeException e) {
			return Messages.format("GIT_INCORRECT_OBJECT_TYPE", e.getClass(),
					e.getLocalizedMessage());
		} catch (RevisionSyntaxException e) {
			return Messages.format("GIT_REVISION_SYNTAX", e.getClass(),
					e.getLocalizedMessage());
		}
	}

	private static CharSequence collapseCandidates(Repository r,
			Collection<? extends ObjectId> cans, String glue)
			throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		ObjectReader or = r.newObjectReader();
		for (ObjectId oid : cans) {
			if (!first)
				sb.append(glue);
			else
				first = false;
			sb.append(or.abbreviate(oid).name());
		}
		return sb;
	}

	private static String getRevInfo(Repository r, ObjectId oid)
			throws IOException {
		RevWalk rw = new RevWalk(r);
		try {
			return formatCommit("%s [%s]: %s (%s)", rw, rw.parseCommit(oid));
		} finally {
			rw.release();
		}
	}

	private static String formatCommit(String fmt, RevWalk rw, RevCommit rc)
			throws IOException {
		String id = rw.getObjectReader().abbreviate(rc).name();
		String auth = rc.getAuthorIdent().getName();
		String mess = rc.getFullMessage().split("[\\r\\n]")[0];
		return String.format(fmt, id, auth, mess,
				Messages.formatTime((long) rc.getCommitTime() * 1000));
	}

	public static String getAmbiguousRevisionError(Repository r,
			Collection<ObjectId> cans) throws IOException {
		StringBuilder sb = new StringBuilder("Ambiguous revision (");
		if (cans.size() > 5)
			return sb.append(">5 candidates)").toString();
		sb.append(collapseCandidates(r, cans, ", "));
		return sb.append(")").toString();
	}

	public static String getAgeString(Repository r, String revstr)
			throws IOException {
		RevWalk rw = new RevWalk(r);

		// Is there a better way to fetch the Head RevCommit?
		ObjectId oid = r.resolve("HEAD");
		if (oid == null) {
			rw.release();
			return Messages.format("GIT_HEAD_NULL");
		}
		RevCommit head = rw.parseCommit(oid);
		rw.markStart(head);

		try {
			RevCommit target = null;
			if (!revstr.toLowerCase().equals("null")) {
				oid = r.resolve(revstr);
				if (oid == null)
					return Messages.getString("GIT_RESOLVE_NULL");
				target = rw.parseCommit(oid);
				if (target == head)
					return Messages.getString("GIT_AGE_0");
			}

			List<RevCommit> revs = new LinkedList<RevCommit>();
			int age = 0;
			for (RevCommit rc = rw.next(); rc != null && rc != target; rc = rw
					.next()) {
				if (age < 5)
					revs.add(rc);
				age++;
			}

			StringBuilder ret = new StringBuilder(Integer.toString(age));
			if (age > 5 || age == 0)
				return ret.toString();
			revs.remove(0);
			revs.add(target);
			ret.append(": HEAD > ").append(collapseCandidates(r, revs, " > "));

			return ret.toString();
		} catch (AmbiguousObjectException e) {
			return getAmbiguousRevisionError(r, e.getCandidates());
		} catch (IncorrectObjectTypeException e) {
			return Messages.format("GIT_INCORRECT_OBJECT_TYPE", e.getClass(),
					e.getLocalizedMessage());
		} catch (RevisionSyntaxException e) {
			return Messages.format("GIT_REVISION_SYNTAX", e.getClass(),
					e.getLocalizedMessage());
		} finally {
			rw.release();
		}
	}

	public static String svnToHash(Repository r, int rev) {
		if (rev <= 0 || rev > 9000)
			return Messages.getString("GIT_SVN_SAFETY_OOB");
		RevWalk rw = new RevWalk(r);
		try {
			// Is there a better way to fetch the Head RevCommit?
			ObjectId h = r.resolve("HEAD");
			if (h == null) {
				rw.release();
				return Messages.format("GIT_HEAD_NULL");
			}
			rw.markStart(rw.parseCommit(h));

			// Barrel through the commits in a circular array
			RevCommit[] rcs = new RevCommit[rev];
			int ptr = 0;
			for (RevCommit rc = rw.next(); rc != null; rc = rw.next())
				rcs[ptr++ % rev] = rc;

			RevCommit rc = rcs[(ptr + 1) % rev];
			if (rc == null)
				return Messages.format("GIT_SVN_OVERFLOW", ptr);
			return rw.getObjectReader().abbreviate(rc).name();
		} catch (IOException e) {
			return e.getClass() + " -- " + e.getLocalizedMessage();
		} finally {
			rw.release();
		}
	}

	// URL/Log/Grep

	static class Filer {
		File f;
		String n;

		Filer(File f, String n) {
			this.f = f;
			this.n = n;
		}
	}

	public static String findFile(File root, String name)
			throws PatternSyntaxException {
		Pattern p = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
		Deque<Filer> next = new ArrayDeque<Filer>();
		for (File f : root.listFiles()) {
			if (p.matcher(f.getName()).matches())
				return f.getName() + (f.isDirectory() ? "/" : "");
			if (f.isDirectory())
				next.addLast(new Filer(f, f.getName() + "/"));
		}
		while (!next.isEmpty()) {
			Filer fr = next.removeFirst();
			for (File f : fr.f.listFiles()) {
				if (p.matcher(f.getName()).matches())
					return fr.n + f.getName() + (f.isDirectory() ? "/" : "");
				if (f.isDirectory())
					next.addLast(new Filer(f, fr.n + f.getName() + "/"));
			}
		}
		return null;
	}

	public static int getAgeUnsafe(Repository r, String revstr)
			throws IOException {
		RevWalk rw = new RevWalk(r);
		try {
			RevCommit head = rw.parseCommit(r.resolve("HEAD"));
			rw.markStart(head);
			RevCommit target = rw.parseCommit(r.resolve(revstr));

			int age = 0;
			for (RevCommit rc = rw.next(); rc != null && rc != target; rc = rw
					.next())
				age++;

			return age;
		} finally {
			rw.release();
		}
	}

	public static String grep(Repository r, String terms, boolean i) {
		String flags = "-nr";
		if (i)
			flags += "i";
		String cmd[] = { "grep", flags, "--", terms, "." };
		Process p;
		try {
			p = Runtime.getRuntime().exec(cmd, null, r.getWorkTree());
		} catch (NoWorkTreeException e) {
			return e.getMessage();
		} catch (IOException e) {
			return e.getMessage();
		}

		GrepReader gr = new GrepReader(p);
		gr.start();

		try {
			p.waitFor();
		} catch (InterruptedException e) {
			Messages.error(e);
		}

		try {
			gr.join();
		} catch (InterruptedException e) {
			Messages.error(e);
		}
		return grepNext();
	}

	static Queue<String> grepLines = new LinkedList<String>();
	static String grepLast = null;

	static String grepNext() {
		if (grepLines.isEmpty())
			return Messages.getString("GIT_GREP_NONE");
		return Messages.format("GIT_GREP_INFO", grepLast = grepLines.remove(),
				grepLines.size());
	}

	static class GrepReader extends Thread {
		Process p;

		GrepReader(Process p) {
			this.p = p;
		}

		public void run() {
			BufferedReader bri = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			grepLines.clear();
			try {
				bri.readLine();
				String line;
				while ((line = bri.readLine()) != null)
					grepLines.add(line);
			} catch (IOException e) {
				Messages.error(e);
			}
			try {
				bri.close();
			} catch (IOException e) {
				// Don't close, don't care. Was a stupid idea anyways.
			}
		}
	}
}
