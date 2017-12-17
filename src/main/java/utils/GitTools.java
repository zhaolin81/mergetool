package utils;


import com.jcraft.jsch.Session;
import junit.framework.Assert;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class GitTools extends AbstractScmAccessor {

	public static final String DEFAULT_LABEL = "master";
	private static final String FILE_URI_PREFIX = "file:";
	
	private GitTools.JGitFactory gitFactory = new GitTools.JGitFactory();

	private boolean initialized;
	
	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable). Default
	 * 5 seconds.
	 */
	private int timeout = 10*60;
	/**
	 * Flag to indicate that the repository should force pull. If true discard any local
	 * changes and take from remote repository.
	 */
	private boolean forcePull;

	private Git git;
	
	public GitTools.JGitFactory getGitFactory() {
		return gitFactory;
	}

	public void setGitFactory(GitTools.JGitFactory gitFactory) {
		this.gitFactory = gitFactory;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public boolean isForcePull() {
		return forcePull;
	}

	public void setForcePull(boolean forcePull) {
		this.forcePull = forcePull;
	}

	public void init(File basedir,String uri,String username,String password,String label){
		
		try {
			setUri(uri);
			setUsername(username);
			setPassword(password);
			setBasedir(basedir);
			setForcePull(true);
			git = createGitClient();
			 git.getRepository().getConfig().setString("branch", label, "merge", label);
			   Ref ref = checkout(git, label);//取分支代码
			   if (shouldPull(git, ref)) {//是否最新
			    pull(git, label, ref);//pull本地分支到最新
			    
			    if (!isClean(git)) {//本地分支不干净则reset
			     logger.warn("The local repository is dirty. Reseting it to origin/"
			       + label + ".");
			     fetch(git, label, "origin");
			     resetHard(git, label, "refs/remotes/origin/" + label);
			    }
			   }
			   
			   
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public ObjectId createTrunkBranch(File trunckfir,String moduleName) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException, IOException{
		if(git ==null){
			git = createGitClient();
		}
			Iterator<RevCommit> commitLog = git.log().all().call().iterator();
			RevCommit revCommit = null;
			while(commitLog.hasNext()){
				revCommit = commitLog.next();
			}
			String branchName = ""+System.currentTimeMillis();
			//git.branchCreate().setName(branchName).setStartPoint(revCommit).call();
			git.checkout().setStartPoint(revCommit).setName(branchName).setCreateBranch(true).call();
			//复制trunk下的文件到新建的分支
			CopyFileUtil.copyDirectory(trunckfir.getAbsolutePath()+(moduleName == null || moduleName.trim().equals("")?"":File.separator+moduleName), getBasedir().getAbsolutePath()+(moduleName == null || moduleName.trim().equals("")?"":File.separator+moduleName), true,false);
			git.add().addFilepattern(".").call();
			return git.commit().setMessage("trunk file").call().getId();

	}
	
	public void mergeBranch(String branch,ObjectId objectId) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException{
		git.checkout().setName(branch).call();
		MergeResult mr = git.merge().include(objectId).call();
	}
	
	/**
	 * Clones the remote repository and then opens a connection to it.
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private void initClonedRepository() throws GitAPIException, IOException {
		if (!getUri().startsWith(FILE_URI_PREFIX)) {
			deleteBaseDirIfExists();
			Git git = cloneToBasedir();
			if (git != null) {
				git.close();
			}
			git = openGitRepository();
			if (git != null) {
				git.close();
			}
		}

	}

	private Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		}
		else {
			// works for tags and local branches
			checkout.setName(label);
		}
		return checkout.call();
	}

	/* for testing */ boolean shouldPull(Git git, Ref ref) throws GitAPIException {
		boolean shouldPull;
		Status gitStatus = git.status().call();
		boolean isWorkingTreeClean = gitStatus.isClean();
		String originUrl = git.getRepository().getConfig().getString("remote", "origin",
				"url");

		if (this.forcePull && !isWorkingTreeClean) {
			shouldPull = true;
			logDirty(gitStatus);
		}
		else {
			shouldPull = isWorkingTreeClean && ref != null && originUrl != null;
		}
		if (!isWorkingTreeClean && !this.forcePull) {
			this.logger.info("Cannot pull from remote " + originUrl
					+ ", the working tree is not clean.");
		}
		return shouldPull;
	}

	@SuppressWarnings("unchecked")
	private void logDirty(Status status) {
		Set<String> dirties = dirties(status.getAdded(), status.getChanged(),
				status.getRemoved(), status.getMissing(), status.getModified(),
				status.getConflicting(), status.getUntracked());
		this.logger.warn(String.format("Dirty files found: %s", dirties));
	}

	@SuppressWarnings("unchecked")
	private Set<String> dirties(Set<String>... changes) {
		Set<String> dirties = new HashSet<String>();
		for (Set<String> files : changes) {
			dirties.addAll(files);
		}
		return dirties;
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	private void fetch(Git git, String label, String remote) {
		FetchCommand fetch = git.fetch().setRemote(remote);
		setTimeout(fetch);
		try {
			if (hasText(getUsername())) {
				setCredentialsProvider(fetch);
			}

			fetch.call();
		}
		catch (Exception ex) {
			this.logger.warn("Could not fetch remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url"));
		}
	}

	private boolean hasText(String username) {
		// TODO Auto-generated method stub
		return username!=null && username.trim().length() > 0;
	}

	private void resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetType.HARD);
		try {
			reset.call();
		}
		catch (Exception ex) {
			this.logger.warn("Could not reset to remote for " + label + " (current ref="
					+ ref + "), remote: " + git.getRepository().getConfig()
							.getString("remote", "origin", "url"));
		}
	}

	/**
	 * Assumes we are on a tracking branch (should be safe)
	 */
	private void pull(Git git, String label, Ref ref) {
		PullCommand pull = git.pull();
		setTimeout(pull);
		try {
			if (hasText(getUsername())) {
				setCredentialsProvider(pull);
			}
			pull.call();
		}
		catch (Exception e) {
			this.logger
					.warn("Could not pull remote for " + label + " (current ref=" + ref
							+ "), remote: "
							+ git.getRepository().getConfig().getString("remote",
									"origin", "url")
							+ ", cause: (" + e.getClass().getSimpleName() + ") "
							+ e.getMessage());
		}
	}

	private Git createGitClient() throws IOException, GitAPIException {
		if (new File(getBasedir(), ".git").exists()) {
			return openGitRepository();
		}
		else {
			return copyRepository();
		}
	}

	// Synchronize here so that multiple requests don't all try and delete the base dir
	// together (this is a once only operation, so it only holds things up on the first
	// request).
	private synchronized Git copyRepository() throws IOException, GitAPIException {
		deleteBaseDirIfExists();
		getBasedir().mkdirs();
		Assert.assertTrue("Could not create basedir: " + getBasedir(),getBasedir().exists());
		if (getUri().startsWith(FILE_URI_PREFIX)) {
			return copyFromLocalRepository();
		}
		else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		Git git = this.gitFactory.getGitByOpen(getWorkingDirectory());
		return git;
	}

	private Git copyFromLocalRepository() throws IOException {
		Git git;
		//File remote = new UrlResource(StringUtils.cleanPath(getUri())).getFile();
		File remote = new File(getUri());
		Assert.assertTrue("No directory at " + getUri(),remote.isDirectory());
		File gitDir = new File(remote, ".git");
		Assert.assertTrue("No .git at " + getUri(),gitDir.exists());
		Assert.assertTrue("No .git directory at " + getUri(),gitDir.isDirectory());
		git = this.gitFactory.getGitByOpen(remote);
		return git;
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = this.gitFactory.getCloneCommandByCloneRepository()
				.setURI(getUri()).setDirectory(getBasedir());
		setTimeout(clone);
		if (hasText(getUsername())) {
			setCredentialsProvider(clone);
		}
		return clone.call();
	}

	private void deleteBaseDirIfExists() {
		if (getBasedir().exists()) {
			try {
				FileUtils.delete(getBasedir(), FileUtils.RECURSIVE);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to initialize base directory", e);
			}
		}
	}

	private void initialize() {
		if (getUri().startsWith("file:") && !this.initialized) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking", "no");
				}
			});
			this.initialized = true;
		}
	}

	private void setCredentialsProvider(TransportCommand<?, ?> cmd) {
		cmd.setCredentialsProvider(
				new UsernamePasswordCredentialsProvider(getUsername(), getPassword()));
	}

	private void setTimeout(TransportCommand<?, ?> pull) {
		pull.setTimeout(this.timeout);
	}

	private boolean isClean(Git git) {
		StatusCommand status = git.status();
		try {
			return status.call().isClean();
		}
		catch (Exception e) {
			this.logger
					.warn("Could not execute status command on local repository. Cause: ("
							+ e.getClass().getSimpleName() + ") " + e.getMessage());

			return false;
		}
	}

	private void trackBranch(Git git, CheckoutCommand checkout, String label) {
		checkout.setCreateBranch(true).setName(label)
				.setUpstreamMode(SetupUpstreamMode.TRACK)
				.setStartPoint("origin/" + label);
	}

	private boolean isBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, ListMode.ALL);
	}

	private boolean isLocalBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, null);
	}

	private boolean containsBranch(Git git, String label, ListMode listMode)
			throws GitAPIException {
		ListBranchCommand command = git.branchList();
		if (listMode != null) {
			command.setListMode(listMode);
		}
		List<Ref> branches = command.call();
		for (Ref ref : branches) {
			if (ref.getName().endsWith("/" + label)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Wraps the static method calls to {@link org.eclipse.jgit.api.Git} and
	 * {@link org.eclipse.jgit.api.CloneCommand} allowing for easier unit testing.
	 */
	static class JGitFactory {

		public Git getGitByOpen(File file) throws IOException {
			Git git = Git.open(file);
			return git;
		}

		public CloneCommand getCloneCommandByCloneRepository() {
			CloneCommand command = Git.cloneRepository();
			return command;
		}
	}

	public void resetFile(Set<String> fileSet,String label) throws CheckoutConflictException, GitAPIException {
		ResetCommand rcommand = git.reset();
		for(String key:fileSet){
			rcommand.addPath(key);
		}
		rcommand.call();
		
		CheckoutCommand ccommand = git.checkout().setName(label);
		for(String key:fileSet){
			ccommand.addPath(key);
		}
		ccommand.call();
		
	}

	public void addFile(Set<String> fileSet) throws NoFilepatternException, GitAPIException {
		AddCommand acommand = git.add();
		for(String key:fileSet){
			acommand.addFilepattern(key);
		}
		acommand.call();
	}

	public void rmFile(Set<String> fileSet) throws NoFilepatternException, GitAPIException {
		RmCommand rcommand = git.rm();
		for(String key:fileSet){
			rcommand.addFilepattern(key);
		}
		rcommand.call();
	}
}
