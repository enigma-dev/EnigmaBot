package org.ircbot;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@SuppressWarnings("restriction")
public class IrcBot extends PircBot
	{
	public static final int AUTH_IGNORE = -1, AUTH_NONE = 0, AUTH_VOICE = 2, AUTH_OP = 3,
			AUTH_ADMIN = 4, AUTH_FOUNDER = 5;
	public static String SERVER, NAME, PASS;
	public static final String MAINCHAN = BotProps.getString("IRC.CHAN");
	public static final String[] CHANNELS = { "#ismchan",MAINCHAN };

	public static final String FOUNDER = "ismavatar";

	static int ping = 0;

	public static void toLog(String msg)
		{
		System.out.println(msg);
		}

	public IrcBot()
		{
		SERVER = BotProps.getString("IRC.SERV");
		NAME = BotProps.getString("IRC.USER");
		PASS = BotProps.getString("IRC.PASS");
		setName(NAME);
		setLogin(NAME);
		setAutoNickChange(true);
		//setVerbose(System.out); //Log messages to stdout
		connect(5000);

		GitCmds.init();

		//Read stdin
		new Thread()
			{
				public void run()
					{
					Scanner sc = new Scanner(System.in);
					while (sc.hasNext())
						{
						String message = sc.nextLine();
						if (message.startsWith("say "))
							message = "PRIVMSG " + MAINCHAN + " :" + message.substring(4);
						sendRawLineViaQueue(message);
						}
					}
			}.start();

		//Ping server at interval
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask()
			{
				@Override
				public void run()
					{
					if (ping == 5)
						{
						onDisconnect();
						ping = -1;
						}
					if (ping != -1)
						{
						sendRawLineViaQueue("PING " + SERVER);
						ping++;
						}
					}
			},30 * 1000,30 * 1000);
		}

	public void connect(int delayBetweenAttempts)
		{
		while (!isConnected())
			{
			try
				{
				connect(SERVER);
				identify(PASS);
				for (String chan : CHANNELS)
					joinChannel(chan);
				ping = 0;
				}
			catch (Exception e) //IRC/IO Exceptions
				{
				try
					{
					Thread.sleep(delayBetweenAttempts);
					}
				catch (InterruptedException e1)
					{
					}
				}
			}
		}

	public int getAuth(String channel, String nick)
		{
		User u = findUser(channel,nick);
		if (u == null) return AUTH_NONE; //user not in channel??
		if (nick.equalsIgnoreCase(FOUNDER) && u.isOp()) return AUTH_FOUNDER;
		if (nick.equalsIgnoreCase("joshdreamland") && u.isOp()) return AUTH_ADMIN;
		return u.isOp() ? AUTH_OP : u.hasVoice() ? AUTH_VOICE : AUTH_NONE;
		}

	public Map<String,String> topics = new HashMap<String,String>();

	public Map<String,List<String>> tells = new HashMap<String,List<String>>();

	public User findUser(String channel, String nick)
		{
		User[] users = getUsers(channel);
		for (User un : users)
			if (un.getNick().equalsIgnoreCase(nick)) return un;
		return null;
		}

	protected void onTopic(String channel, String topic, String setBy, long date, boolean changed)
		{
		topics.put(channel,topic);
		}

	protected void onMessage(String channel, String sender, String login, String hostname,
			String message)
		{
		//guaranteed to not be my message
		DbCmds.seeOrErrorClose(sender);

		if (go)
			{
			int p;
			if ((p = message.toLowerCase().indexOf("whitespace")) != -1)
				sendMessage(channel,"s/" + message.substring(p,p + 10) + "/Definitions");
			}

		boolean command = false;
		if (message.startsWith(getNick() + ":") || message.startsWith(getNick() + ","))
			{
			message = message.substring(getNick().length() + 1);
			command = true;
			}
		message = message.trim();
		if (message.startsWith("!"))
			{
			message = message.substring(1);
			command = true;
			}
		if (!command) return;
		if (message.isEmpty()) return;
		String msg[] = message.split("\\s",2);
		if (msg[0].isEmpty()) return;
		command(channel,sender,msg[0].toLowerCase(),msg.length == 1 ? "" : msg[1]);
		}

	protected void onPrivateMessage(String sender, String login, String hostname, String message)
		{
		if (sender.equals("geordi") || sender.equals("clang"))
			{
			sendMessage(MAINCHAN,message);
			return;
			}
		if (message.startsWith("?") && sender.equals("IsmAvatar"))
			this.sendRawLineViaQueue(message.substring(1));
		if (message.startsWith("!")) message = message.substring(1);
		if (message.isEmpty()) return;
		String msg[] = message.split("\\s",2);
		if (msg[0].isEmpty()) return;
		command(sender,sender,msg[0].toLowerCase(),msg.length == 1 ? "" : msg[1]);
		}

	protected boolean go = true;

	public void command(String channel, String sender, String cmd, String arg)
		{
		int auth = getAuth(channel,sender);
		int auth2 = getAuth(MAINCHAN,sender);
		if (auth2 > auth) auth = auth2;
		if (auth == AUTH_IGNORE) return;
		if (cmd.startsWith("!") || cmd.startsWith(".") || cmd.startsWith("?")) return;
		if (cmd.equals("on"))
			{
			if (auth >= AUTH_ADMIN) go = true;
			sendMessage(channel,"Fuck on" + (auth >= AUTH_ADMIN ? "." : ""));
			return;
			}
		if (!go) return;
		if (cmd.equals("off"))
			{
			if (auth >= AUTH_ADMIN) go = false;
			sendMessage(channel,"Fuck off" + (auth >= AUTH_ADMIN ? "." : ""));
			return;
			}
		if (cmd.equals("stop") || cmd.equals("halt") || cmd.equals("pause"))
			{
			go = false;
			sendMessage(channel,"Pausing for 10 seconds");
			Timer timer = new Timer();
			timer.schedule(new TimerTask()
				{
					@Override
					public void run()
						{
						go = true;
						}
				},10 * 1000);
			return;
			}
		if (cmd.equals("reconnect") || cmd.equals("connect"))
			{
			sendMessage(channel,DbCmds.connect() ? "Success" : "Failure");
			return;
			}
		if (cmd.equals("rename"))
			{
			if (!getNick().equals(NAME))
				{
				changeNick(NAME);
				identify(PASS);
				}
			return;
			}
		if (cmd.equals("time") || cmd.equals("date") || cmd.equals("now"))
			{
			sendMessage(channel,Messages.DATETIME.format(new Date()));
			return;
			}
		if (cmd.equals("help"))
			{
			sendMessage(channel,Messages.getString("HELP"));
			return;
			}
		if (cmd.equals("ping"))
			{
			if (sender.equals("JoshDreamland")) sendMessage(channel,"js: pong");
			else sendMessage(channel,"pong");
			return;
			}
		if (cmd.equals("g++") || cmd.equals("gcc") || cmd.equals("geordi") || cmd.equals("<<")
				|| cmd.equals("clang"))
			{
			if (!channel.equals(MAINCHAN))
				{
				sendMessage(channel,Messages.format("GEORDI_NO_PRIVMSG",MAINCHAN));
				return;
				}
			sendMessage(cmd.equals("clang") ? "clang" : "geordi",cmd.equals("<<") ? "<< " + arg : arg);
			return;
			}
		if (cmd.equals("say") && auth >= AUTH_ADMIN)
			{
			sendMessage(MAINCHAN,arg);
			return;
			}
		if (cmd.equals("tell"))
			{
			if (!channel.equals(MAINCHAN) && findUser(MAINCHAN,channel) == null)
				{
				sendMessage(channel,Messages.format("TELL_NEED_CHANNEL",MAINCHAN));
				return;
				}
			String[] args = arg.split(" ",2);
			if (args.length < 2)
				{
				sendMessage(channel,Messages.getString("HELP_TELL"));
				return;
				}
			String to = args[0].toLowerCase();
			if (to.equals("josh")) to = "joshdreamland";
			if (to.equals("ism")) to = "ismavatar";
			String msg;
			User u = findUser(channel,to);
			if (u != null)
				{
				String when = Messages.formatTime(new Date(),true);
				msg = u.getNick() + ": [" + when + "] <" + sender + "> " + args[1];
				}
			else if (!DbCmds.tell(sender,to,channel,args[1]))
				msg = DbCmds.ERROR;
			else
				msg = Messages.format("TELL_DONE",to);
			sendMessage(channel,msg);
			return;
			}
		if (cmd.equals("msgs"))
			{
			List<String> msgs = tells.get(sender.toLowerCase());
			try
				{
				msgs = DbCmds.msgs(sender);
				if (msgs == null || msgs.isEmpty())
					{
					sendMessage(channel,sender + ": You have no messages.");
					return;
					}
				if (!channel.equals(sender))
					sendMessage(channel,sender + ": Check your private messages.");
				for (String msg : msgs)
					sendMessage(sender,msg);
				}
			catch (SQLException e)
				{
				e.printStackTrace();
				DbCmds.close();
				sendMessage(channel,DbCmds.ERROR);
				}
			return;
			}
		if (cmd.equals("bug") || cmd.equals("bugs") || cmd.equals("feature") || cmd.equals("features"))
			{
			String bug_lgm = Messages.getString("BUG_LGM");
			String bug_all = Messages.getString("BUG_ALL");
			String mess = Messages.format("BUG_INFO",bug_lgm,bug_all);
			sendMessage(channel,mess);
			return;
			}
		if (cmd.equals("seen"))
			{
			if (arg.isEmpty())
				{
				sendMessage(channel,"Seen whom?");
				return;
				}
			String to = arg.split(" ")[0].toLowerCase();
			if (to.equals("josh")) to = "joshdreamland";
			if (to.equals("ism")) to = "ismavatar";
			sendMessage(channel,DbCmds.seen(to,findUser(channel,to) != null));
			return;
			}
		if (cmd.equals("roulette"))
			{
			sendMessage(channel,roulette(channel,sender,auth));
			return;
			}
		String mess = GitCmds.command(cmd,arg);
		if (mess == null)
			{
			if (cmd.equals("report"))
				{
				sendMessage(channel,Messages.format("REPORT",getNick()));
				return;
				}
			mess = NetCmds.command(auth,sender,cmd,arg);
			if (mess == null) mess = Messages.format("UNKNOWN",cmd);
			}
		sendMessage(channel,mess);
		}

	double roulette = Math.random() * 6;

	private String roulette(String channel, String sender, int auth)
		{
		roulette--;
		if (!channel.startsWith("#"))
			{
			if (roulette < 0)
				{
				roulette = Math.random() * 6;
				return "Bang";
				}
			return "Click";
			}
		if (roulette < (auth >= AUTH_OP ? -0.1 : auth >= AUTH_VOICE ? -0.3 : -0.9))
			{
			kick(channel,"cheeseboy","Bang");
			kick(channel,"fundies","Bang");
			roulette = Math.random() * 6;
			return "Bang";
			}
		if (roulette < 0)
			{
			kick(channel,sender,"Bang");
			roulette = Math.random() * 6;
			return "Bang";
			}
		return "Click";
		}

	protected void onKick(String channel, String kickerNick, String kickerLogin,
			String kickerHostname, String recipientNick, String reason)
		{
		if (!recipientNick.equals(NAME))
			{
			DbCmds.seeOrErrorClose(recipientNick);
			return;
			}

		//I got kicked, rejoin
		if (recipientNick.equalsIgnoreCase(getNick())) joinChannel(channel);
		}

	protected void onJoin(String channel, String sender, String login, String hostname)
		{
		if (sender.equalsIgnoreCase(getNick())) return;
		String welcome = DbCmds.join(sender,channel);
		if (go && welcome != null) sendMessage(channel,welcome);
		String tells = DbCmds.tells(sender);
		if (tells != null) sendMessage(channel,sender + ": " + tells);
		}

	protected void onPart(String channel, String sender, String login, String hostname)
		{
		//		if (!sender.equals(NAME))
		//			{
		//			DbCmds.seeOrErrorClose(sender);
		//			return;
		//			}

		//Ghost left, try renaming
		if (!getNick().equals(NAME))
			{
			changeNick(NAME);
			identify(PASS);
			return;
			}

		//I left, rejoin
		if (channel.equals(MAINCHAN)) joinChannel(channel);
		}

	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason)
		{
		if (!sourceNick.equals(NAME))
			{
			DbCmds.seeOrErrorClose(sourceNick);
			return;
			}

		//Ghost died, try renaming
		if (!getNick().equals(NAME))
			{
			changeNick(NAME);
			identify(PASS);
			return;
			}
		}

	protected void onPong(String pongValue)
		{
		ping = 0;
		}

	protected void onDisconnect()
		{
		if (isConnected()) disconnect();
		toLog("Reconnecting...");
		connect(10000); //10s between attempts
		}

	public static void main(String[] args)
		{
		SignalHandler sh = new SignalHandler()
			{
				// Signal handler method
				public void handle(Signal signal)
					{
					if (signal.getName().equals("INT")) System.exit(0);
					System.out.println();
					System.out.println();
					System.out.println(signal);
					System.out.println();
					System.out.println();
					if (signal.getName().equals("HUP") || signal.getName().equals("PIPE")) return;
					System.exit(0);
					}
			};

		//SEGV, ILL, FPE, USR1, QUIT, 
		String sigs[] = { "BUS","SYS","ABRT","INT","TERM","HUP","TRAP","PIPE" };
		for (String s : sigs)
			{
			try
				{
				Signal.handle(new Signal(s),sh);
				}
			catch (IllegalArgumentException e)
				{
				System.err.println(e.getMessage());
				}
			}

		new IrcBot();
		}
	}