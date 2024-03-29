package Shadowsocks_Shell;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;  
import org.jnetpcap.packet.PcapPacketHandler;
import org.jnetpcap.protocol.network.Ip4;
import org.jnetpcap.protocol.tcpip.Tcp;
import org.jnetpcap.protocol.tcpip.Udp;  

public class Main {
	private static HashMap<Integer, User> UsersInfoHashMap;
	private static HashSet<Integer> TempUserHashSet;
	private static HashSet<Integer> UserPortList = new HashSet<Integer>();
	private static HashMap<String,Long> AliveIpPortHashMap = new HashMap<String,Long>();
	private static HashMap<Integer,Long> PortBandWidthHashMap = new HashMap<Integer,Long>();
	private static HashMap<Integer,Long> PortOnlineHashMap = new HashMap<Integer,Long>();
	private static HashMap<Integer,Integer> PortUserIdHashMap = new HashMap<Integer,Integer>();
	private static HashMap<Integer,HashSet<String>> UserCurrentIP = new HashMap<Integer,HashSet<String>>();
	private static HashMap<Integer,Integer> UserLimitCount = new HashMap<Integer,Integer>();
	private static int Node_Class;
	private static String Node_ID;
	private static String Node_IP;
	private static Float Node_Rate;
	private static String DB_Address;
	private static String DB_Name;
	private static String DB_Username;
	private static String DB_Password;
	private static int Version;
	private static boolean Node_Enable;
	private static String Node_Nic;
	protected static int Node_SpeedLimit;
	private static Ip4 ipv4Header = new Ip4();
	private static Tcp tcpHeader = new Tcp();
    private static Udp udpHeader = new Udp();
	private static Integer Node_SpeedLimit_Method;
	private static Integer Speedtest;
	private static Integer CloudSafe;
	private static Long Lastreadline;
	private static Properties ConfigProperties;
	protected static String SIP;
	protected static String LIP;
	private static DBPool ConnectionPool;
	
	


	public static void main(final String[] args){
		System.setProperty("user.timezone","GMT +08");
		
		ConfigProperties = new Properties();
		try {
			FileInputStream ConfigInput = new FileInputStream("ssshell.conf");
			try {
				ConfigProperties.load(ConfigInput);
				Node_ID = ConfigProperties.getProperty("nodeid");
				Node_IP = ConfigProperties.getProperty("ip");
				Node_Nic = ConfigProperties.getProperty("nic");
				DB_Address = ConfigProperties.getProperty("db_address");
				DB_Name = ConfigProperties.getProperty("db_name");
				DB_Username = ConfigProperties.getProperty("db_username");
				DB_Password = ConfigProperties.getProperty("db_password");
				Version = Integer.valueOf(ConfigProperties.getProperty("version"));
				Node_SpeedLimit_Method = Integer.valueOf(ConfigProperties.getProperty("speedlimit"));
				Speedtest = Integer.valueOf(ConfigProperties.getProperty("speedtest"));
				CloudSafe = Integer.valueOf(ConfigProperties.getProperty("cloudsafe"));
				Lastreadline = Long.valueOf(ConfigProperties.getProperty("lastreadline"));
				ConfigInput.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		ConnectionPool = new DBPool(DB_Name, DB_Username, DB_Password, DB_Address);
	
		Exec("killall ss-server",false);
		Exec("rm -rf /tmp/ssshell/*.pid",false);
		
		
		ResetSpeedLimit();
		if(Node_SpeedLimit_Method == 1)
		{
			PrepareSpeedLimit();
		}
		
		
		File FolderFile=new File("/tmp/ssshell");
		if(!FolderFile.exists()&&!FolderFile.isDirectory())
		{
			FolderFile.mkdirs();
		}
		FolderFile=null;
		
		UsersInfoHashMap = new HashMap<Integer,User>();
		
		
		List<PcapIf> alldevs = new ArrayList<PcapIf>(); // Will be filled with NICs  
        StringBuilder errbuf = new StringBuilder(); // For any error msgs  
		
        int r = Pcap.findAllDevs(alldevs, errbuf);  
        if (r == Pcap.NOT_OK || alldevs.isEmpty()) {  
            System.err.printf("Can't read list of devices, error is %s", errbuf  
                .toString());  
            return;  
        }  
  
        int i = 0;
        int any = -1;
        int nic = -1;
        
        for (PcapIf device : alldevs) { 
            if(device.getName().equals(Node_Nic))
            {
            	nic = i;
            }
            
            if(device.getName().equals("any"))
            {
            	any = i;
            }
            i++;
        }  
  
        if(nic == -1)
        {
        	nic = any;
        	if(nic == -1)
        	{
        		nic = i-1;
        	}
        }
        
        PcapIf device = alldevs.get(nic); // We know we have atleast 1 device  
  
        int snaplen = 64 * 1024;           // Capture all packets, no trucation  
        int flags = Pcap.MODE_PROMISCUOUS; // capture all packets  
        int timeout = 60 * 1000;           // 60 seconds in millis  
        Pcap pcap =  
            Pcap.openLive(device.getName(), snaplen, flags, timeout, errbuf);  
  
        if (pcap == null) {  
            System.err.printf("Error while opening device for capture: "  
                + errbuf.toString());  
            return;  
        }  
        
      
        
        PcapPacketHandler<String> jpacketHandler = new PcapPacketHandler<String>() {  
  
            public void nextPacket(PcapPacket packet, String user) { 
            	
            	Boolean Out = false;
            	String sIP = "";
            	String dIP = "";
            	
            	if(packet.hasHeader(ipv4Header))
                {
                    Ip4 ip = packet.getHeader(ipv4Header);                    
                    sIP = getIpAddress(ip.source());
                    if(sIP.equals(Node_IP))
                    {
                    	Out = true;
                    }
                    else
                    {
	                    dIP = getIpAddress(ip.destination());
	                    if(!dIP.equals(Node_IP))
	                    {
	                    	return;
	                    }
                    }
                    
                    
                
            	
	            	/*if(packet.hasHeader(ipv6Header))
	                {
	                    Ip6 ip = packet.getHeader(ipv6Header);                    
	                    IP = getIpAddress(ip.source());
	                    String[] IParray = IP.split(":");
	                    IP = IParray[3];
	                    if(IP.equals(Node_IP))
	                    {
	                    	Out = true;
	                    }
	                    else
	                    {
		                    IP = getIpAddress(ip.destination());
		                    String[] IParray2 = IP.split(":");
		                    IP = IParray2[3];
		                    if(!IP.equals(Node_IP))
		                    {
		                    	return;
		                    }
	                    }
	                }*/
	
	                if(packet.hasHeader(tcpHeader))
	                {
	                    Tcp tcp = packet.getHeader(tcpHeader);
	                    if(Out == true)
	                    {
	                    	if(UserPortList.contains(tcp.source()))
	                    	{
	                    		if(packet.size()>100)
	                    		{
		                    		PortBandWidthHashMap.put(tcp.source(),PortBandWidthHashMap.get(tcp.source())+packet.getPacketWirelen());
		                    		PortOnlineHashMap.put(tcp.source(), Long.valueOf(System.currentTimeMillis()/1000));
		                    		//if(packet.hasHeader(ipv4Header))
		                            {	                    			
		                    			if(UserLimitCount.get(PortUserIdHashMap.get(tcp.source()))!=0)
		                    			{
		                    				if(UserLimitCount.get(PortUserIdHashMap.get(tcp.source()))<UserCurrentIP.get(PortUserIdHashMap.get(tcp.source())).size())
		                    				{
		                    					if(!UserCurrentIP.get(PortUserIdHashMap.get(tcp.source())).contains(getIpAddress(ip.destination())))
		            							{	
		                    						AddTempBlock(getIpAddress(ip.destination()),tcp.source());
		                    						return;
		            							}
		                    				}
		                    			}
		                    			
		                    			AliveIpPortHashMap.put(getIpAddress(ip.destination())+"-"+tcp.source(), Long.valueOf(System.currentTimeMillis()/1000));
		                            }
	                    		}
	                    		/*else
	                    		{
	                    			if(packet.hasHeader(ipv6Header))
	                                {
	                    				Ip6 ip = packet.getHeader(ipv6Header);
	                    				IP = getIpAddress(ip.destination());
	            	                    String[] IParray = IP.split(":");
	            	                    IP = IParray[3];
	                        			AliveIpPortHashMap.put(IP+"-"+tcp.source(), Long.valueOf(System.currentTimeMillis()/1000));
	                                }
	                    		}*/
	                    	}
	                    }
	                    else
	                    {
	                    	if(UserPortList.contains(tcp.destination()))
	                    	{
	                    		if(packet.size()>100 && AliveIpPortHashMap.containsKey(getIpAddress(ip.source())+"-"+tcp.destination()))
	                    		{
		                    		PortBandWidthHashMap.put(tcp.destination(),PortBandWidthHashMap.get(tcp.destination())+packet.getPacketWirelen());
		                    		PortOnlineHashMap.put(tcp.destination(), Long.valueOf(System.currentTimeMillis()/1000));
	                    		}
	                    		return;
	                    	}
	                    }
	                    return;
                	}
	                
	                if(packet.hasHeader(udpHeader))
	                {
	                    Udp udp = packet.getHeader(udpHeader);
	                    if(Out == true)
	                    {
	                    	if(UserPortList.contains(udp.source()))
	                    	{
	                    		if(packet.size()>100 && AliveIpPortHashMap.containsKey(getIpAddress(ip.destination())+"-"+udp.source()))
	                    		{
		                    		PortBandWidthHashMap.put(udp.source(),PortBandWidthHashMap.get(udp.source())+packet.getPacketWirelen());
		                    		PortOnlineHashMap.put(udp.source(), Long.valueOf(System.currentTimeMillis()/1000));
		                    		{	                    			
		                    			if(UserLimitCount.get(PortUserIdHashMap.get(udp.source()))!=0)
		                    			{
		                    				if(UserLimitCount.get(PortUserIdHashMap.get(udp.source()))<UserCurrentIP.get(PortUserIdHashMap.get(udp.source())).size())
		                    				{
		                    					if(!UserCurrentIP.get(PortUserIdHashMap.get(udp.source())).contains(getIpAddress(ip.destination())))
		            							{	
		                    						AddTempBlock(getIpAddress(ip.destination()),udp.source());
		                    						return;
		            							}
		                    				}
		                    			}
		                    			
		                    			AliveIpPortHashMap.put(getIpAddress(ip.destination())+"-"+udp.source(), Long.valueOf(System.currentTimeMillis()/1000));
		                            }
	                    		}
	                    	}
	                    }
	                    else
	                    {
	                    	if(UserPortList.contains(udp.destination()))
	                    	{
	                    		if(packet.size()>100 && AliveIpPortHashMap.containsKey(getIpAddress(ip.destination())+"-"+udp.source()))
	                    		{
		                    		PortBandWidthHashMap.put(udp.destination(),PortBandWidthHashMap.get(udp.destination())+packet.getPacketWirelen());
		                    		PortOnlineHashMap.put(udp.destination(), Long.valueOf(System.currentTimeMillis()/1000));
	                    		}
	                    	}
	                    }
	                    return;
	                }
                    
                }
            }  
        };  
  
        
        
        new Thread(){
        	@SuppressWarnings("deprecation")
			@Override
        	public void run()
        	{
        		//while(true)
                {
                	pcap.loop(Pcap.LOOP_INFINATE, jpacketHandler, "jNetPcap rocks!");  
                }
        	}
        }.start();
        
        new Thread(){
        	@Override
        	public void run()
        	{
        		while(CloudSafe == 1 && Version == 3)
        		{
        			SIP = getServerIP().getHostAddress();
        			LIP = getMyIP().getHostAddress();
        			Connection MysqlConnection = null;
        			try{
	    				MysqlConnection = ConnectionPool.getConnection();
	                    
	    				
	    				
	    				HashSet<String>LocalDeny = ReadLocalDeny();
	    				Iterator<String> LocalDenyIterator = LocalDeny.iterator();
	    				while(LocalDenyIterator.hasNext())
	    				{
	    					String IP = LocalDenyIterator.next();
	    					Statement AddBlockStatement = MysqlConnection.createStatement();
	    					AddBlockStatement.execute("INSERT INTO `blockip` (`id`, `nodeid`, `ip`, `datetime`) VALUES (NULL, '"+Node_ID+"', '"+IP+"', unix_timestamp())");
	    					AddBlockStatement.close();
	    					AddBlockStatement = null;
	    				}
	    				
	    				Statement SelectBlockStatement = MysqlConnection.createStatement();
                        ResultSet SelectBlockResultSet = SelectBlockStatement.executeQuery("SELECT * FROM `blockip` where `datetime`>unix_timestamp()-60 AND `nodeid`<>"+Node_ID);
                        while (SelectBlockResultSet.next()) {
                        	AddBlock(SelectBlockResultSet.getString("ip"));
                        }
                        SelectBlockResultSet.close();
                        SelectBlockStatement.close();
	    				
	    				Statement SelectUnBlockStatement = MysqlConnection.createStatement();
                        ResultSet SelectUnBlockResultSet = SelectUnBlockStatement.executeQuery("SELECT * FROM `unblockip` where `datetime`>unix_timestamp()-60");
                        while (SelectUnBlockResultSet.next()) {
                        	DeleteBlock(SelectUnBlockResultSet.getString("ip"));
                        }
	    				
                        SelectUnBlockResultSet.close();
                        SelectUnBlockStatement.close();
	    				
	    				
	    				MysqlConnection.close();
	    				MysqlConnection = null;
        			}catch(Exception e){
        				
        			}
        			
        			try {
						sleep(60000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        		}
        	}
        }.start();
		
        new Thread(){
        	@Override
        	public void run()
        	{
        		while(Speedtest != 0)
        		{
        			
        			String tempString = Exec("speedtest-cli --list|grep China|grep Telecom|awk -F')' '{print $1}'",true);
        			String[] tempArray = tempString.split("\n");
        			String[] ChinaNetResult = new String[3];
        			for(String Id:tempArray)
        			{
        				ChinaNetResult = ExecSpeedtest("speedtest-cli --server "+Id+" --simple");
        				if(ChinaNetResult[0] != null)
        				{
        					if(!ChinaNetResult[0].contains("1800000.0"))
        					{
        						break;
        					}
        				}
        			}
        			
        			
        			tempString = Exec("speedtest-cli --list|grep China|grep Unicom|awk -F')' '{print $1}'",true);
        			tempArray = tempString.split("\n");
        			String[] ChinaUnicomResult = new String[3];
        			for(String Id:tempArray)
        			{
        				ChinaUnicomResult = ExecSpeedtest("speedtest-cli --server "+Id+" --simple");
        				if(ChinaUnicomResult[0] != null)
        				{
        					if(!ChinaUnicomResult[0].contains("1800000.0"))
        					{
        						break;
        					}
        				}
        			}
        			
        			tempString = Exec("speedtest-cli --list|grep China|grep Mobile|awk -F')' '{print $1}'",true);
        			tempArray = tempString.split("\n");
        			String[] CmccResult = new String[3];
        			for(String Id:tempArray)
        			{
        				CmccResult = ExecSpeedtest("speedtest-cli --server "+Id+" --simple");
        				if(CmccResult[0] != null)
        				{
        					if(!CmccResult[0].contains("1800000.0"))
        					{
        						break;
        					}
        				}
        			}
        			
        			Connection MysqlConnection = null;
        			try{
	    				MysqlConnection = ConnectionPool.getConnection();
	                    
	    				Statement AddSpeedtestStatement = MysqlConnection.createStatement();
	    				AddSpeedtestStatement.execute("INSERT INTO `speedtest` (`id`, `nodeid`, `datetime`, `telecomping`, `telecomeupload`, `telecomedownload`, `unicomping`, `unicomupload`, `unicomdownload`, `cmccping`, `cmccupload`, `cmccdownload`) VALUES (NULL, '"+Node_ID+"', unix_timestamp(), '"+ChinaNetResult[0]+"', '"+ChinaNetResult[1]+"', '"+ChinaNetResult[2]+"', '"+ChinaUnicomResult[0]+"', '"+ChinaUnicomResult[1]+"', '"+ChinaUnicomResult[2]+"', '"+CmccResult[0]+"', '"+CmccResult[1]+"', '"+CmccResult[2]+"')");
	    				AddSpeedtestStatement.close();
	    				AddSpeedtestStatement = null;
	    				
	    				MysqlConnection.close();
	    				MysqlConnection = null;
        			}catch(Exception e){
        				
        			}
        			
        			try {
						sleep(Speedtest*3600000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        		}
        	}
        }.start();
        
        
		
		
		new Thread(){
			@Override
        	public void run()
			{
        		while(true)
        		{
        			try {
        				Log("info","Connecting to mysql....");
        				Connection MysqlConnection = null;
        				MysqlConnection = ConnectionPool.getConnection();
                        
                        Statement SelectNodeinfoStatement = MysqlConnection.createStatement();
                        
                        ResultSet SelectNodeResultSet = SelectNodeinfoStatement.executeQuery("SELECT * FROM ss_node where `id`='"+Node_ID+"'");
                        SelectNodeResultSet.first();
                        
                        if(Version == 2||Version == 3)
                        {
                        	Node_Rate = SelectNodeResultSet.getFloat("traffic_rate");
                        }
                        else
                        {
                        	Node_Rate = (float) 1.0;
                        }
                        
                        if(Version==3)
                        { 
                        	Node_Class=SelectNodeResultSet.getInt("node_class");
                        	Node_SpeedLimit=SelectNodeResultSet.getInt("node_speedlimit");
	                        Long node_bandwidth = Long.valueOf(String.valueOf(SelectNodeResultSet.getBigDecimal("node_bandwidth")));
	                        Long node_bandwidth_limit = Long.valueOf(String.valueOf(SelectNodeResultSet.getBigDecimal("node_bandwidth_limit")));
	                        if( node_bandwidth_limit == 0||( node_bandwidth_limit != 0 && node_bandwidth_limit > node_bandwidth ))
	                        {
	                        	Node_Enable = true;
	                        }
	                        else
	                        {
	                        	Node_Enable = false;
	                        }
                        }
                        else
                        {
                        	Node_SpeedLimit = 0;
                        	Node_Enable = true;
                        }
                        
                        SelectNodeResultSet.close();
                        SelectNodeinfoStatement.close();
                        SelectNodeinfoStatement = null;
                        SelectNodeResultSet = null;
                        
                        Statement SelectUserInfoStatement = MysqlConnection.createStatement();
                        
                        ResultSet SelectUserInfoResultSet = null;
                        if(Version==3)
                        {
                        	SelectUserInfoResultSet = SelectUserInfoStatement.executeQuery("SELECT * FROM user WHERE `class`>="+Node_Class+" AND `enable`=1 AND `expire_in`>now() AND `transfer_enable`>`u`+`d`");
                        }
                        else
                        {
                        	SelectUserInfoResultSet = SelectUserInfoStatement.executeQuery("SELECT * FROM user WHERE `enable`=1 AND `transfer_enable`>`u`+`d`");
                        }
                        
                        TempUserHashSet = new HashSet<Integer>();
                        
                        if(Node_Enable == true)
                        {
                        	while (SelectUserInfoResultSet.next()) {
                            	TempUserHashSet.add(SelectUserInfoResultSet.getInt("id"));
                            	if(UsersInfoHashMap.containsKey(SelectUserInfoResultSet.getInt("id")))
                                {
                                	//存在时
                            		//监控端口变更和密码变更
                            		User SingleUser=UsersInfoHashMap.get(SelectUserInfoResultSet.getInt("id"));
                            		
                            		int SingleUserSpeedLimit = 0;
                            		int SingleUserLimit = 0;
                            		
                            		if(Version == 3)
                            		{
                            		
	                            		if(SelectUserInfoResultSet.getInt("node_speedlimit")>Node_SpeedLimit)
	                            		{
	                            			SingleUserSpeedLimit = SelectUserInfoResultSet.getInt("node_speedlimit");
	                            		}
	                            		else
	                            		{
	                            			SingleUserSpeedLimit = Node_SpeedLimit;
	                            		}
	                            		
	                            		SingleUserLimit = SelectUserInfoResultSet.getInt("node_connector");
	                            		
                            		}
                            		
                            		if(!SingleUser.getPasswd().equals(SelectUserInfoResultSet.getString("passwd"))||SingleUser.getPort()!=SelectUserInfoResultSet.getInt("port")||!SingleUser.getMethod().equals(SelectUserInfoResultSet.getString("method"))||SingleUser.getSpeedLimit()!=SingleUserSpeedLimit||SingleUser.getLimitCount()!=SingleUserLimit)
                            		{
                            			DeleteUser(SelectUserInfoResultSet.getInt("id"));
                            			AddUser(SelectUserInfoResultSet.getString("user_name"),SelectUserInfoResultSet.getInt("port"),SelectUserInfoResultSet.getString("passwd"),SelectUserInfoResultSet.getInt("id"),SelectUserInfoResultSet.getString("method"),SingleUserSpeedLimit,SingleUserLimit);
                            		}
                                }
                                else
                                {
                                	//不存在时
                                	int SingleUserSpeedLimit = 0;
                                	int SingleUserLimit = 0;
                            		
                            		if(Version == 3)
                            		{
                            		
	                            		if(SelectUserInfoResultSet.getInt("node_speedlimit")>Node_SpeedLimit)
	                            		{
	                            			SingleUserSpeedLimit = SelectUserInfoResultSet.getInt("node_speedlimit");
	                            		}
	                            		else
	                            		{
	                            			SingleUserSpeedLimit = Node_SpeedLimit;
	                            		}
	                            		
	                            		SingleUserLimit = SelectUserInfoResultSet.getInt("node_connector");
	                            		
                            		}
                            		
                                	AddUser(SelectUserInfoResultSet.getString("user_name"),SelectUserInfoResultSet.getInt("port"),SelectUserInfoResultSet.getString("passwd"),SelectUserInfoResultSet.getInt("id"),SelectUserInfoResultSet.getString("method"),SingleUserSpeedLimit,SingleUserLimit); 	
                                }
                            }
                        }
                        else
                        {
                        	Set<Integer> UsersInfoMapKeySet = UsersInfoHashMap.keySet();
                        	Iterator<Integer> UserInfoKeySetIterator = UsersInfoMapKeySet.iterator();
                        	while(UserInfoKeySetIterator.hasNext())
                            {
                        		DeleteUser(UserInfoKeySetIterator.next());
                            }
                        }
                        
                        SelectUserInfoResultSet.close();
                        SelectUserInfoStatement.close();
                        SelectUserInfoStatement = null;
                        SelectUserInfoResultSet = null;
                        
                        if(Version == 3)
                        {
	                        HashSet<Integer> firstTimeMeetUser = new HashSet<Integer>();
	                        Statement GetAliveIpStatement = MysqlConnection.createStatement();
	                        ResultSet GetAliveIpSet = GetAliveIpStatement.executeQuery("SELECT * FROM `alive_ip` where `datetime`>unix_timestamp()-90");
	                        while (GetAliveIpSet.next()) {
	                        	if(UserLimitCount.containsKey(GetAliveIpSet.getInt("userid")))
	                        	{
	                        		if(UserLimitCount.get(GetAliveIpSet.getInt("userid"))!=0)
	                        		{
		                        		if(!firstTimeMeetUser.contains(GetAliveIpSet.getInt("userid")))
		                        		{
		                        			HashSet<String> TempIpHashSet = new HashSet<String>();
		                        			
		                        			TempIpHashSet.add(GetAliveIpSet.getString("ip"));
		                        			UserCurrentIP.put(GetAliveIpSet.getInt("userid"), TempIpHashSet);
		                        			
		                        			firstTimeMeetUser.add(GetAliveIpSet.getInt("userid"));
		                        		}
		                        		else
		                        		{
		                        			HashSet<String> TempIpHashSet = UserCurrentIP.get(GetAliveIpSet.getInt("userid"));
		                        		
		                        			TempIpHashSet.add(GetAliveIpSet.getString("ip"));
		                        			UserCurrentIP.put(GetAliveIpSet.getInt("userid"), TempIpHashSet);
		                        		}
	                        		}
	                        	}
	                        }
	                        
	                        GetAliveIpSet.close();
	                        GetAliveIpStatement.close();
	                        GetAliveIpStatement = null;
	                        GetAliveIpSet = null;
	                        firstTimeMeetUser = null;
                        }
                        
                        long ThisTimeSumBandwidth = 0;
                        
                        Log("info","Getting the OnlineUser.....");
                		
                		int OnlineUserCount = 0;
                		
                		Set<Integer> PortSet = PortOnlineHashMap.keySet();
                		Iterator<Integer> PortSetItertor = PortSet.iterator();
                		
                		HashSet<Integer> DeletePortSet = new HashSet<Integer>();
                		
                		while(PortSetItertor.hasNext())
                		{
                			int Port = PortSetItertor.next();
                			if(PortOnlineHashMap.get(Port) > Long.valueOf(System.currentTimeMillis()/1000) - 300)
                			{
                				OnlineUserCount++;
                			}
                			else
                			{
                				DeletePortSet.add(Port);
                			}
                		}
                		
                		PortSetItertor = null;
                		PortSet = null;
                		
                		Iterator<Integer> DeletePortIterator = DeletePortSet.iterator();
                		
                		while(DeletePortIterator.hasNext())
                		{
                			PortOnlineHashMap.remove(DeletePortIterator.next());
                		}
                		
                		DeletePortIterator = null;
                		DeletePortSet = null;
                		
                		Set<Integer> UsersInfoSet = UsersInfoHashMap.keySet();
                        Iterator<Integer> UsersInfoIterator = UsersInfoSet.iterator();
                        HashSet<Integer> DeletedUserHashSet = new HashSet<Integer>();
                        while(UsersInfoIterator.hasNext())
                        {
                        	int CurrentUserId = UsersInfoIterator.next();
                        	if(PortBandWidthHashMap.containsKey(UsersInfoHashMap.get(CurrentUserId).getPort()))
                    		{
                    			if(PortBandWidthHashMap.get(UsersInfoHashMap.get(CurrentUserId).getPort())!=0)
                    			{
                    				//if(UserBandwidthHashMap.containsKey(CurrentUserId))
                    				{
                    					long ThisTimeBandWidth = PortBandWidthHashMap.get(UsersInfoHashMap.get(CurrentUserId).getPort());
                    					PortBandWidthHashMap.put(UsersInfoHashMap.get(CurrentUserId).getPort(),(long) 0);
                    					ThisTimeSumBandwidth = ThisTimeSumBandwidth + ThisTimeBandWidth;
                    					if(ThisTimeBandWidth > 0)
                    					{
                    						Log("info","Syncing the user traffic...."+CurrentUserId+" "+((ThisTimeBandWidth)*Node_Rate));
                    						
                    						Statement UpdateUserStatement = MysqlConnection.createStatement();
                    						UpdateUserStatement.executeUpdate("UPDATE `user` SET `d`=`d`+"+((ThisTimeBandWidth)*Node_Rate)+",`t`=unix_timestamp() WHERE `id`='"+UsersInfoHashMap.get(CurrentUserId).getId()+"'");
                    						UpdateUserStatement.close();
                    						UpdateUserStatement = null;
                    						
                    						if(Version == 2||Version == 3)
                    						{
	                    						Statement AddTrafficLogStatement = MysqlConnection.createStatement();
	                    						AddTrafficLogStatement.execute("INSERT INTO `user_traffic_log` (`id`, `user_id`, `u`, `d`, `Node_ID`, `rate`, `traffic`, `log_time`) VALUES (NULL, '"+CurrentUserId+"', '0', '"+(ThisTimeBandWidth)+"', '"+Node_ID+"', '"+Node_Rate+"', '"+TrafficShow((long)(ThisTimeBandWidth*Node_Rate))+"', unix_timestamp()); ");
	                    						AddTrafficLogStatement.close();
	                    						AddTrafficLogStatement = null;
                    						}
                    						
                    						//UserBandwidthHashMap.put(CurrentUserId, ThisTimeBandWidth);
                    					}
                    				}
                    			}
                    		}
                        	
                        	if(!TempUserHashSet.contains(CurrentUserId))
                        	{
                        		DeletedUserHashSet.add(CurrentUserId);
                        	}
                        }
                        
                        if(Version == 3)
                        {
	                        Set<String> AliveIPSet = AliveIpPortHashMap.keySet();
	                        Iterator<String> AliveIpPortIterator = AliveIPSet.iterator();
	                        HashSet<String> DeletedIpUserHashSet = new HashSet<String>();
	                        while(AliveIpPortIterator.hasNext())
	                        {
	                        	String IpPort = AliveIpPortIterator.next();
	                        	if(AliveIpPortHashMap.get(IpPort)>Long.valueOf(System.currentTimeMillis()/1000)-60)
	                        	{
		                        	String[] IpPortArray = IpPort.split("-");
		                        	String IP = IpPortArray[0];
		                        	String Port = IpPortArray[1];
		                        	Statement AliveIpStatement = MysqlConnection.createStatement();
		                        	AliveIpStatement.execute("INSERT INTO `alive_ip` (`id`, `nodeid`,`userid`, `ip`, `datetime`) VALUES (NULL, '"+Node_ID+"','"+PortUserIdHashMap.get(Integer.valueOf(Port))+"', '"+IP+"', '"+AliveIpPortHashMap.get(IpPort)+"')");
		                        	AliveIpStatement.close();
		                        	AliveIpStatement = null;
	                        	}
	                        	else
	                        	{
	                        		DeletedIpUserHashSet.add(IpPort);
	                        	}
	                        }
	                        AliveIpPortIterator = null;
	                        AliveIPSet = null;
                        
                        
                        
	                        Iterator<String> DeletedAliveIpPortIterator = DeletedIpUserHashSet.iterator();
	                        while(DeletedAliveIpPortIterator.hasNext())
	                        {
	                        	AliveIpPortHashMap.remove(DeletedAliveIpPortIterator.next());
	                        }
                        }
                        
                        if(Version == 3)
                        {
	                        Statement UpdateNodeStatement = MysqlConnection.createStatement();
	                        UpdateNodeStatement.executeUpdate("UPDATE `ss_node` SET `node_heartbeat`=unix_timestamp(),`node_bandwidth`=`node_bandwidth`+'"+(ThisTimeSumBandwidth)+"' WHERE `id` = "+Node_ID+"; ");
	                        UpdateNodeStatement.close();
	                        UpdateNodeStatement = null;
                        }
                        
						if(Version == 2 || Version == 3)
						{
	                        Statement AddNodeOnlineLogStatement = MysqlConnection.createStatement();
	                        AddNodeOnlineLogStatement.execute("INSERT INTO `ss_node_online_log` (`id`, `Node_ID`, `online_user`, `log_time`) VALUES (NULL, '"+Node_ID+"', '"+OnlineUserCount+"', unix_timestamp()); ");
	                        AddNodeOnlineLogStatement.close();
	                        AddNodeOnlineLogStatement = null;
						}
						
						if(Version == 2 || Version == 3)
						{
	                        Statement AddNodeOnlineLogStatement = MysqlConnection.createStatement();
	                        AddNodeOnlineLogStatement.execute("INSERT INTO `ss_node_info` (`id`, `node_id`, `uptime`, `load`, `log_time`) VALUES (NULL, '"+Node_ID+"', '"+GetUptime()+"', '"+GetLoad()+"', unix_timestamp()); ");
	                        AddNodeOnlineLogStatement.close();
	                        AddNodeOnlineLogStatement = null;
	                    }
                        
                    	Iterator<Integer> DeletedUserIterator = DeletedUserHashSet.iterator();
                    	while(DeletedUserIterator.hasNext())
                    	{
                    		DeleteUser(DeletedUserIterator.next());
                    	}
                    	
                    	DeletedUserIterator = null;
                    	DeletedUserHashSet = null;
                        
                        
                        try {
                        	MysqlConnection.close();
						} catch (SQLException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
                        MysqlConnection = null;
        			} catch(Exception e) {
        				System.err.println("Exception: " + e.getMessage()+e.getStackTrace().toString()+e.getLocalizedMessage()+e.getCause());
        			}
        			
        			Log("info","Sleeping...");
        			System.gc();
        			try {
						sleep(60000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        		}
        	}
        }.start();
        
	}
	
	
	public static HashSet<String> ReadLocalDeny()
	{
		HashSet<String> Temp = new HashSet<String>();
		
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(
			        new FileInputStream("/etc/hosts.deny")));
			String line = reader.readLine();
	        Long num = (long) 0;
	        while (line != null) {
	            if (Lastreadline < ++num) {
	                if(!line.startsWith("#"))
	                {
	                	String TIP = getIPAddress(line);
	                	if(TIP != null)
	                	{
	                		if(!TIP.equals(SIP) && !TIP.equals(LIP))
	                		{
	                			Temp.add(TIP);
	                		}
	                		else
	                		{
	                			DeleteBlock(TIP);
	                		}
	                	}
	                }
	            }
	            line = reader.readLine();
	        }
	        reader.close();
	        Lastreadline = num;
	        SaveLine(Lastreadline);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Temp;
	}
	
	public static InetAddress getMyIP() {
        InetAddress myIPaddress;
		try {
            myIPaddress = InetAddress.getLocalHost();
            return (myIPaddress);
        } catch (UnknownHostException e) {
        	return null;
        }    
    }
 
    public static InetAddress getServerIP() {
        InetAddress myServer;
		try {
            myServer = InetAddress.getByName(DB_Address);
            return (myServer);
        } catch (UnknownHostException e) {
        	return null;
        }
        
    }
	
	public static void SaveLine(Long Lastreadline)
	{
		FileOutputStream ConfigOutput = null;
		try {
			ConfigOutput = new FileOutputStream("ssshell.conf");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ConfigProperties.setProperty("lastreadline", String.valueOf(Lastreadline));
		try {
			Properties TempP = ConfigProperties;
			ConfigProperties.store(ConfigOutput, "glzjin");
			ConfigProperties = TempP;
			ConfigOutput.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void AddBlock(String ip)
	{
		String IP = getIPAddress(ip);
		if(IP != null)
		{
			if(!IP.equals(SIP) && !IP.equals(LIP))
    		{
				Exec("route add -host "+IP+" gw 127.0.0.1",true);
				Exec("iptables -I INPUT -s "+IP+" -j DROP",true);
				Exec("echo -e \"ALL: "+IP+"\" >> /etc/hosts.deny",true);
				Lastreadline ++;
				SaveLine(Lastreadline);
    		}
		}
	}
	
	
	public static void DeleteBlock(String ip)
	{
		String IP = getIPAddress(ip);
		if(IP != null)
		{
			Exec("route del "+IP,true);
			Exec("iptables -D INPUT -s "+IP+" -j DROP",true);
			Exec("sed -i \"s/ALL: "+IP+"/##Removed/g\" `grep "+IP+" -rl /etc/hosts.deny`",true);
		}
	}
	
	
	public static String getIPAddress(String text) {
		String IPADDRESS_PATTERN = 
		        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
		
		Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
		Matcher matcher = pattern.matcher(text);
		        if (matcher.find()) {
		            return matcher.group();
		        }
		        else{
		            return null;
		        }
	}
	
	public static int getTotalLines(String fileName) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(fileName)));
        LineNumberReader reader = new LineNumberReader(in);
        String s = reader.readLine();
        int lines = 0;
        while (s != null) {
            lines++;
            s = reader.readLine();
        }
        reader.close();
        in.close();
        return lines;
    }

	
	public static String Exec(String cmd,Boolean NeedReturn) {
        try {
            String[] cmdA = { "/bin/sh", "-c", cmd };
            if(NeedReturn==false)
            {
            	Runtime.getRuntime().exec(cmdA);
            	return null;
            }
            Process process = Runtime.getRuntime().exec(cmdA);
            LineNumberReader br = new LineNumberReader(new InputStreamReader(
                    process.getInputStream()));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
	
	public static String[] ExecSpeedtest(String cmd) {
		String[] returnvalue = new String[3];
        try {
            String[] cmdA = { "/bin/sh", "-c", cmd };
            Process process = Runtime.getRuntime().exec(cmdA);
            LineNumberReader br = new LineNumberReader(new InputStreamReader(
                    process.getInputStream()));
            //StringBuffer sb = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
            	if(line.contains("Ping: "))
            	{
            		String[] TempArray = line.split("Ping: ");
            		returnvalue[0] = TempArray[1];
            	}
            	
            	if(line.contains("Download: "))
            	{
            		String[] TempArray = line.split("Download: ");
            		returnvalue[1] = TempArray[1];
            	}
            	
            	if(line.contains("Upload: "))
            	{
            		String[] TempArray = line.split("Upload: ");
            		returnvalue[2] = TempArray[1];
            	}
            }
            return returnvalue;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
	
	
	public static void PrepareSpeedLimit()
	{
		Exec("tc qdisc add dev "+Node_Nic+" handle 8964: root htb",true);
	}
	
	public static void AddSpeedLimit(String Port,String Speed,String Id)
	{
		Exec("tc class add dev "+Node_Nic+" parent 8964:0 classid 8964:"+Id+" htb rate "+Speed+"mbps",true);
		Exec("tc class add dev eth0 parent 8964:"+Id+" classid 8964:"+(Integer.valueOf(Id)+5000)+" htb rate "+Speed+"Mbit ceil "+Speed+"Mbit",true);
		Exec("tc filter add dev "+Node_Nic+" protocol ip prio 1 u32 match ip dport "+Port+" 0xffff flowid 8964:"+(Integer.valueOf(Id)+5000),true);
		Exec("tc filter add dev "+Node_Nic+" protocol ip prio 1 u32 match ip sport "+Port+" 0xffff flowid 8964:"+(Integer.valueOf(Id)+5000),true);
	}
	
	public static void DeleteSpeedLimit(String Id)
	{
		String ReturnString = Exec("tc filter list dev "+Node_Nic+" |grep \"flowid 8964:"+Id+"\"|awk '{print $10}'",true);
		String[] ReturnArray = ReturnString.split("\n");
		for(String FId:ReturnArray)
		{
			if(Node_SpeedLimit_Method == 1)
			{
				Exec("tc filter delete dev "+Node_Nic+" parent 8964:0 protocol ip prio 1 handle "+FId+" u32",true);
			}
		}
	}

	public static void ResetSpeedLimit()
	{
	
		Exec("tc qdisc del dev "+Node_Nic+" root",true);
	
		Exec("killall trickle",true);
	
	}
	
	public static void AddTempBlock(String Ip,int Port)
	{
		Exec("iptables -A INPUT -p tcp --dport "+Port+" -s "+Ip+" -j DROP",true);
		new Thread(){
			@Override
			public void run(){
				try {
					sleep(180000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Exec("iptables -D INPUT -p tcp --dport "+Port+" -s "+Ip+" -j DROP",true);
			}
		}.start();
	}
	
	public static String GetUptime()
	{
		String ReturnString = Exec("cat /proc/uptime | awk '{ print $1 }'",true);
		String[] ReturnArray = ReturnString.split("\n");
		return ReturnArray[0];
	}
	
	public static String GetLoad()
	{
		String ReturnString = Exec("cat /proc/loadavg | awk '{ print $1\" \"$2\" \"$3 }'",true);
		String[] ReturnArray = ReturnString.split("\n");
		return ReturnArray[0];
	}
	
	public static void Log(String LogLevel,String LogContent)
	{
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		System.out.println("["+LogLevel+"]["+df.format(new Date())+"]"+LogContent);
	}
	
	public static String TrafficShow(Long Traffic)
	{
		if(Traffic<1024)
		{
			return String.valueOf((double)((Traffic*100)/100))+"B";
		}
		
		if(Traffic<1024*1024)
		{
			return String.valueOf((double)((Traffic/1024*100)/100))+"KB";
		}
		
		if(Traffic<1024*1024*1024)
		{
			return String.valueOf((double)((Traffic/1024/1024*100)/100))+"MB";
		}
		
		return String.valueOf((double)((Traffic/1024/1024/1024*100)/100))+"GB";
		
	}
	
	public static boolean DeleteFile(String fileName){     
	    File file = new File(fileName);     
	    if(file.isFile() && file.exists()){     
	        file.delete();     
	        return true;     
	    }else{     
	    	return false;     
	    }     
	}


	public static void DeleteUser(int UserId)
	{
		try{
			Log("info","Deleting User..."+UserId);
			File PidFile=new File("/tmp/ssshell/"+UsersInfoHashMap.get(UserId).getId()+".pid");
			if(PidFile.exists())
			{
				FileInputStream PidFileInputStream = new FileInputStream("/tmp/ssshell/"+UsersInfoHashMap.get(UserId).getId()+".pid");
				InputStreamReader PidFileInputStreamReader = new InputStreamReader(PidFileInputStream,"UTF-8");
				BufferedReader PidFileInputBufferedReader = new BufferedReader(PidFileInputStreamReader);
				String line = null;
				while((line=PidFileInputBufferedReader.readLine())!=null){
					Exec("kill "+line,false);
				}
				PidFileInputBufferedReader.close();
				PidFile.delete();
			}
			
			DeleteFile("/tmp/ssshell/"+UserId+".conf");
		
    		UserPortList.remove(UsersInfoHashMap.get(UserId).getPort());
    		PortBandWidthHashMap.remove(UsersInfoHashMap.get(UserId).getPort());
    		PortUserIdHashMap.remove(UsersInfoHashMap.get(UserId).getPort());
    		
    		if(UserLimitCount.get(UserId)!=0)
    		{
    			UserCurrentIP.remove(UserId);
    		}
    		
    		UserLimitCount.remove(UserId);
    		
    		
    		if(UsersInfoHashMap.get(UserId).getSpeedLimit() != 0 && Node_SpeedLimit_Method == 1)
    		{
    			DeleteSpeedLimit(String.valueOf(UserId));
    		}
    		
			UsersInfoHashMap.remove(UserId);
			//UserBandwidthHashMap.remove(UserId);
			
			
		}catch(Exception e) {
			System.err.println("Exception: " + e.getMessage());
		}
	}
	
	public static void AddUser(String UserName,int Port,String Passwd,int Id,String Method,int SpeedLimit,int LimitCount)
	{
		Log("info","Adding User..."+UserName);
		
		DeleteFile("/tmp/ssshell/"+Id+".conf");
		
		User newUser = new User(Port,Passwd,Id,Method,SpeedLimit,LimitCount);
		
		UsersInfoHashMap.put(Id, newUser);
		//UserBandwidthHashMap.put(Id, (long) 0);
		PortBandWidthHashMap.put(Port, (long) 0);
		
		try {
	         BufferedWriter FileOutPutWriter = new BufferedWriter(new FileWriter("/tmp/ssshell/"+Id+".conf"));
	         FileOutPutWriter.write("{\"server\":\"0.0.0.0\",\"server_port\":"+Port+",\"local_port\":1080,\"password\":\""+Passwd+"\",\"timeout\":60,\"method\":\""+Method+"\"}");
	         FileOutPutWriter.close();
		} catch (IOException e) {
			System.err.println("Exception: " + e.getMessage()+e.getStackTrace().toString()+e.getLocalizedMessage()+e.getCause());
		}
		
		Exec("chmod 600 /tmp/ssshell/"+Id+".conf",false);
		
		if(Node_SpeedLimit_Method == 1)
		{
			Exec("ss-server -c /tmp/ssshell/"+Id+".conf -f /tmp/ssshell/"+Id+".pid -u -d 208.67.222.222",true);
		}
		else
		{
			if(Node_SpeedLimit_Method == 2 && SpeedLimit != 0)
			{
				Exec("trickle -d "+(SpeedLimit*1024/8)+" -u "+(SpeedLimit*1024/8)+" ss-server -c /tmp/ssshell/"+Id+".conf -f /tmp/ssshell/"+Id+".pid -u -d 208.67.222.222",true);
			}
			else
			{
				Exec("ss-server -c /tmp/ssshell/"+Id+".conf -f /tmp/ssshell/"+Id+".pid -u -d 208.67.222.222",true);
			}
		}
		
		UserPortList.add(Port);
		
		UserLimitCount.put(Id, LimitCount);
		if(LimitCount != 0)
		{
			UserCurrentIP.put(Id, new HashSet<String>());
		}
		
		
		PortUserIdHashMap.put(Port, Id);
		
		if(Node_SpeedLimit_Method == 1&&SpeedLimit != 0)
		{
			AddSpeedLimit(String.valueOf(Port),String.valueOf(SpeedLimit),String.valueOf(Id));
		}
		
	}
	
	public static String getIpAddress(byte[] rawBytes) {
        int i = 4;
        String ipAddress = "";
        for (byte raw : rawBytes)
        {
            ipAddress += (raw & 0xFF);
            if (--i > 0)
            {
                ipAddress += ".";
            }
        }

        return ipAddress;
    }
}
