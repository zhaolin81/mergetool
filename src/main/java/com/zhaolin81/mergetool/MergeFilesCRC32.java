package com.zhaolin81.mergetool;

import utils.ChecksumCRC32;

import java.io.*;
import java.util.Enumeration;
import java.util.Properties;

/**
 * 为记录在merge_files配置文件中的文件生成crc32
 */
public class MergeFilesCRC32 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		
    	String module = "testmodule";
		
    	File basedir = new File("d:\\test\\gitrepo");
    	if(!basedir.exists()){
    		if(!basedir.mkdirs()){
    			System.err.println("mkdir failed "+basedir);
    			return;
    		}
    	}
    	
    	File trunkDir = new File("D:\\develop\\svnrepo");
    	if(!trunkDir.exists()){
    			System.err.println("trunkDir not exist "+basedir);
    			return;
    	}
    	
    	Properties mergeFiles = new Properties();
    	Properties crc32Files = new Properties();
    	mergeFiles.load(new FileInputStream(basedir.getAbsolutePath()+File.separator+module+File.separator+"merge_files.properties"));
    	@SuppressWarnings("unchecked")
		Enumeration<String> enumMergeFiles = (Enumeration<String>) mergeFiles.propertyNames();
    	while(enumMergeFiles.hasMoreElements()){
    		String originalKey = enumMergeFiles.nextElement();
    		String key = originalKey.replace("/", File.separator);
    		//String value = mergeFiles.getProperty(key);
    		String file = trunkDir.getAbsolutePath()+File.separator+key.replace("/", File.separator);
    		System.out.println(file);
    		long crc32 = ChecksumCRC32.doChecksum(new File(file));
    		crc32Files.put(originalKey, Long.toHexString(crc32));
    	}
    	
    	crc32Files.store(new FileWriter(new File(basedir.getAbsolutePath()+File.separator+module+File.separator+"crc32.properties")),"merge files crc32");
	}
}
