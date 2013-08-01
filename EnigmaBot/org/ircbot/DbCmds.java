package org.ircbot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DbCmds
	{
	public static final String ERROR = "Unable to comply. Database error.";

	static final String host = BotProps.getString("DB.HOST");
	static final String port = BotProps.getString("DB.PORT");
	static final String db = BotProps.getString("DB.DB");
	static final String user = BotProps.getString("DB.USER");
	static final String pass = BotProps.getString("DB.PASS");
	static final String url = "jdbc:mysql://" + host + ":" + port + "/" + db;
	static Connection conn = null;

	static
		{
		try
			{
			DriverManager.registerDriver(new com.mysql.jdbc.Driver());
			//			Class.forName("com.mysql.jdbc.Driver");
			connect();
			}
		//		catch (ClassNotFoundException e)
		//			{
		//			e.printStackTrace();
		//			}
		catch (SQLException e)
			{
			e.printStackTrace();
			}
		close();
		}

	public static boolean connect()
		{
		try
			{
			if (conn != null) conn.close();
			}
		catch (SQLException e)
			{
			}
		try
			{
			conn = DriverManager.getConnection(url,user,pass);
			return true;
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			return false;
			}
		}

	public static boolean close()
		{
		if (true) return true;
		try
			{
			conn.close();
			return true;
			}
		catch (SQLException e1)
			{
			return false;
			}
		}

	public static PreparedStatement prep(String sql) throws SQLException
		{
		if (conn == null) throw new SQLException("No connection");
		if (conn.isClosed()) connect();
		return conn.prepareStatement(sql);
		}

	//Seen/Join
	public static void see(String who) throws SQLException
		{
		PreparedStatement ps = prep("INSERT INTO users VALUES (?,0,NULL,NOW()) ON DUPLICATE KEY UPDATE useen=NOW()");
		ps.setString(1,who);
		ps.execute();
		ps.close();
		close();
		}

	public static void seeOrErrorClose(String who)
		{
		try
			{
			see(who);
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			close();
			}
		}

	public static String seen(String who, boolean online)
		{
		try
			{
			PreparedStatement ps = prep("SELECT ujoin,useen FROM users WHERE uname=?");
			ps.setString(1,who);
			ResultSet rs = ps.executeQuery();
			if (!rs.next())
				{
				ps.close();
				conn.close();
				return "I've never seen " + who + (online ? " active or join, but they're here now." : ".");
				}
			String join = Messages.formatTime(rs.getTimestamp(1));
			String seen = Messages.formatTime(rs.getTimestamp(2));
			ps.close();
			close();
			return who + (online ? " (online)" : " (offline)") + " was last active " + seen
					+ " and last joined " + join;
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			close();
			return ERROR;
			}
		}

	public static String join(String who, String chan)
		{
		String whol = who.toLowerCase();
		String welcome = (whol.startsWith("josh") ? "Fuck you, " : "Welcome back, ") + who;
		try
			{
			PreparedStatement ps = prep("SELECT *, DATE_SUB(NOW(),INTERVAL 5 MINUTE) FROM users WHERE uname=?");
			ps.setString(1,whol);
			ResultSet rs = ps.executeQuery();
			String ret;
			if (!rs.next())
				ret = "Welcome to " + chan + ", " + who;
			else
				{
				Timestamp join = rs.getTimestamp(3);
				Timestamp seen = rs.getTimestamp(4);
				Timestamp now = rs.getTimestamp(5);
				if (!now.after(seen)) return null;
				ret = welcome + ". Your last visit was "
						+ Messages.formatTime(join.after(seen) ? join : seen);
				}
			ps.close();

			String sql = "INSERT INTO users VALUES (?,0,NOW(),NOW()) "
					+ "ON DUPLICATE KEY UPDATE ujoin=NOW()"; //,useen=Now()
			ps = prep(sql);
			ps.setString(1,whol);
			ps.execute();
			ps.close();
			close();

			return ret;
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			close();
			return "Welcome [back], " + who;
			}
		}

	//Tell/Msgs
	public static boolean tell(String sender, String to, String chan, String msg)
		{
		try
			{
			PreparedStatement ps = prep("INSERT INTO mail VALUES (NULL,?,?,?,?,NOW())");
			//from,to,chan,msg
			ps.setString(1,sender);
			ps.setString(2,to);
			ps.setString(3,chan);
			ps.setString(4,msg);
			ps.execute();
			ps.close();
			conn.close();
			return true;
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			close();
			return false;
			}
		}

	public static String tells(String who)
		{
		try
			{
			PreparedStatement ps = prep("SELECT msent,mfrom,mmsg FROM mail WHERE mto=?");
			ps.setString(1,who.toLowerCase());
			ResultSet rs = ps.executeQuery();

			if (!rs.next())
				{
				ps.close();
				close();
				return null;
				}

			String when = Messages.formatTime(rs.getTimestamp(1),true);
			String ret = "[" + when + "] <" + rs.getString(2) + "> " + rs.getString(3);

			if (rs.next())
				{
				ps.close();
				close();
				return "You have multiple new !msgs";
				}
			ps.close();

			ps = prep("DELETE FROM mail WHERE mto=?");
			ps.setString(1,who.toLowerCase());
			ps.execute();
			ps.close();
			close();

			return ret;
			}
		catch (SQLException e)
			{
			e.printStackTrace();
			close();
			return null;
			}
		}

	public static List<String> msgs(String who) throws SQLException
		{
		List<String> ret = new ArrayList<String>();
		PreparedStatement ps = prep("SELECT msent,mfrom,mmsg FROM mail WHERE mto=? ORDER BY msent");
		ps.setString(1,who.toLowerCase());
		ResultSet rs = ps.executeQuery();
		while (rs.next())
			{
			String when = Messages.formatTime(rs.getTimestamp(1),false);
			ret.add("[" + when + "] <" + rs.getString(2) + "> " + rs.getString(3));
			}
		ps.close();

		ps = prep("DELETE FROM mail WHERE mto=?");
		ps.setString(1,who.toLowerCase());
		ps.execute();
		ps.close();

		close();

		return ret;
		}
	}
