package com.zhaolin81.mergetool;

import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.ObjectId;
import utils.ChecksumCRC32;
import utils.CopyFileUtil;
import utils.GitTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 跨仓库分支合并工具
 * 可以实现从svn到git之间的分支合并
 * 记录合并历史功能，支持冲突解决记录
 *
 */
public class App 
{
    public static void main( String[] args ) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException, IOException
    {

    	//模块名称，每次只merge一个模块
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
    			System.err.println("trunkDir not exist "+trunkDir);
    			return;
    	}
    	
    	GitTools gt = new GitTools();
    	//准备好干净的本地分支
    	gt.init(basedir, "git@192.168.1.231:zhaolin/gbp2p-boot.git", "", "", GitTools.DEFAULT_LABEL);
    	//创建临时分支并
    	ObjectId commit = gt.createTrunkBranch(trunkDir,module);
    	gt.mergeBranch(GitTools.DEFAULT_LABEL, commit);
    	Set<String> resetSet =new HashSet<String>();
    	Set<String> addSet =new HashSet<String>();
    	Set<String> rmSet =new HashSet<String>();
    	//处理merge files
    	Properties mergeFiles = new Properties();
    	mergeFiles.load(new FileInputStream(basedir.getAbsolutePath()+File.separator+module+File.separator+"merge_files.properties"));
    	
    	Properties crc32Files = new Properties();
    	crc32Files.load(new FileInputStream(basedir.getAbsolutePath()+File.separator+module+File.separator+"crc32.properties"));
    	
    	
    	@SuppressWarnings("unchecked")
		Enumeration<String> enumMergeFiles = (Enumeration<String>) mergeFiles.propertyNames();
    	while(enumMergeFiles.hasMoreElements()){
    		String key = enumMergeFiles.nextElement();
    		if(crc32Files.containsKey(key)){
    			String file = trunkDir.getAbsolutePath()+File.separator+key.replace("/", File.separator);
        		long crc32 = ChecksumCRC32.doChecksum(new File(file));
        		if(!Long.toHexString(crc32).equals(crc32Files.get(key))){
        			//文件变化了，不做自动处理
        			System.err.println("file changed "+file);
        			continue;
        		}
    		}else{
    			//没有做crc32校验的文件，不做处理
    			System.err.println("no crc32 file "+key);
    			continue;
    		}
    		String value = mergeFiles.getProperty(key);
    		switch(value){
    			case "mine":
    				//gt.resetFile(key);break;
    				resetSet.add(key);break;
    			case "their":
    				//从trunk抓取文件并覆盖
    				CopyFileUtil.copyFile(trunkDir.getAbsolutePath()+File.separator+key.replace("/", File.separator), basedir.getAbsolutePath()+File.separator+key.replace("/", File.separator), true);
    				//gt.addFile(key);break;
    				addSet.add(key);break;
    			case "rm":
    				//gt.rmFile(key);break;
    				rmSet.add(key);break;
    		}
    	}
    	
    	if(!resetSet.isEmpty()){
    		gt.resetFile(resetSet,GitTools.DEFAULT_LABEL);
    	}
    	if(!addSet.isEmpty()){
    		gt.addFile(addSet);
    	}
    	if(!rmSet.isEmpty()){
    		gt.rmFile(rmSet);
    	}
//    	try {
//			Git git = Git.open(new File("D:\\project\\git\\mergecode\\.git"));
//			
//			git.pull().call();
//			
//			Iterator<RevCommit> commitLog = git.log().all().call().iterator();
//			RevCommit revCommit = null;
//			while(commitLog.hasNext()){
//				revCommit = commitLog.next();
//			}
//			String branchName = ""+System.currentTimeMillis();
//			//git.branchCreate().setName(branchName).setStartPoint(revCommit).call();
//			git.checkout().setName(branchName).setCreateBranch(true).setStartPoint(revCommit).call();
//			StatusCommand sc = git.status();
//			Status status = sc.call();
//			//System.out.println(status.getConflicting());
//			Map<String,StageState> statusMap = status.getConflictingStageState();
//			for(String key:statusMap.keySet()){
//				System.out.println(statusMap.get(key) +" " +key);
//			}
//			
//			Set<String> addedSet = status.getAdded();
//			for(String key:addedSet){
//				System.out.println("ADDED " +key);
//			}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (NoWorkTreeException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (GitAPIException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
    }
}
