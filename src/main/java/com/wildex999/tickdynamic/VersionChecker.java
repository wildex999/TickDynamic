package com.wildex999.tickdynamic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class VersionChecker implements Runnable {

	public class VersionData {
		public boolean checkOk = false;
		public String mcVersion;
		public String modVersion;
		public String updateUrl;
	}
	
	AtomicBoolean checkDone;
	VersionData data;
	
	public VersionChecker() {
		checkDone = new AtomicBoolean();
		checkDone.set(false);
	}
	
	@Override
	public void run() {
		data = new VersionData();
		data.checkOk = false;
		
		//Create our query
		String encoding = "UTF-8";
		String url = "http://mods.stjerncraft.com:8080";
		String query = "v=error";
		try {
			query = String.format("mv=%s&v=%s", URLEncoder.encode("1.7.10-10.13.2.1230", encoding), URLEncoder.encode(TickDynamicMod.VERSION, encoding));
			URLConnection connection = new URL(url + "/?" + query).openConnection();
			connection.setRequestProperty("Accept-Charset", encoding);
			connection.setRequestProperty("Host", "mods.stjerncraft.com");
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String response = in.readLine();
			in.close();
			
			String[] args = response.split(",");
			if(args.length != 4)
			{
				data.checkOk = false;
				checkDone.set(true);
				return;
			}
			
			data.checkOk = args[0].equals("ok") ? true : false;
			data.mcVersion = args[1];
			data.modVersion = args[2];
			data.updateUrl = args[3];
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		checkDone.set(true);
	}
	
	public void runVersionCheck() {
		//Warning: Running this while already running can cause problems(Race condition)
		//Wait untill getVersionData returns non-null before calling this again!
		checkDone.set(false);
		new Thread(this).start();
	}
	
	//Returns null if version check not done
	public VersionData getVersionData() {
		if(!checkDone.get())
			return null;
		
		return data;
	}

}
