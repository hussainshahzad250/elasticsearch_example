package com.process;

import java.sql.Connection;
import java.sql.DriverManager;

public class ImageDBConnections {
	
	public Connection sqlserver(String username, String password, String ip, String dbName) {
		Connection con = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			String url = "jdbc:mysql://" + ip + ":3306/" + dbName;
			con = DriverManager.getConnection(url, username, password);
			System.out.println("Database Connection Successfull....");
		} catch (Exception e) {
		}
		return con;
	}
}
