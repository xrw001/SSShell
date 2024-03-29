package Shadowsocks_Shell;

import java.sql.Connection; 
import java.sql.SQLException;
import java.util.Properties;
import java.beans.PropertyVetoException; 
import com.mchange.v2.c3p0.ComboPooledDataSource; 
public class DBPool{       
   private ComboPooledDataSource dataSource;     
    
   public DBPool(String Database,String Username,String Password,String Address){       
           try {       
                 dataSource = new ComboPooledDataSource();       
                 dataSource.setUser(Username);       
                 dataSource.setPassword(Password);       
                 dataSource.setJdbcUrl("jdbc:mysql://"+Address+"/"+Database+"?autoReconnect=true"); 
                 dataSource.setDriverClass("com.mysql.jdbc.Driver"); 
                 dataSource.setInitialPoolSize(2); 
                 dataSource.setMinPoolSize(3); 
                 dataSource.setMaxPoolSize(10); 
                 dataSource.setMaxStatements(50); 
                 dataSource.setMaxIdleTime(120);       
                 Properties p = new Properties(System.getProperties());
                 p.put("com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog");
                 p.put("com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "OFF"); // Off or any other level
                 System.setProperties(p);
           } catch (PropertyVetoException e) {       
               throw new RuntimeException(e);       
           }       
   }       
   
 
   public final Connection getConnection(){       
           try {       
               return dataSource.getConnection();       
           }   catch (SQLException e)   {       
               throw new RuntimeException("无法从数据源获取连接",e);       
           }       
   }     
    
 
}
