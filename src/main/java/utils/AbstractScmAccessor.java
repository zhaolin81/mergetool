package utils;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.util.FileUtils;


/**
 * Base class for components that want to access a source control management system.
 *
 * @author Dave Syer
 *
 */
public class AbstractScmAccessor {

	private static final String[] DEFAULT_LOCATIONS = new String[] { "/" };

	protected Log logger = LogFactory.getLog(getClass());
	/**
	 * Base directory for local working copy of repository.
	 */
	private File basedir;
	/**
	 * URI of remote repository.
	 */
	private String uri;
	/**
	 * Username for authentication with remote repository.
	 */
	private String username;
	/**
	 * Password for authentication with remote repository.
	 */
	private String password;
	/**
	 * Search paths to use within local working copy. By default searches only the root.
	 */
	private String[] searchPaths = DEFAULT_LOCATIONS.clone();


	public AbstractScmAccessor() {
		this.basedir = createBaseDir();
	}


	protected File createBaseDir() {
		try {
			final File basedir = Files.createTempDirectory("config-repo-").toFile();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						FileUtils.delete(basedir, FileUtils.RECURSIVE);
					}
					catch (IOException e) {
						AbstractScmAccessor.this.logger.warn(
								"Failed to delete temporary directory on exit: " + e);
					}
				}
			});
			return basedir;
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create temp dir", e);
		}
	}


	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		int index = uri.indexOf("://");
		if (index > 0 && !uri.substring(index + "://".length()).contains("/")) {
			// If there's no context path add one
			uri = uri + "/";
		}
		this.uri = uri;
	}

	public String getUri() {
		return this.uri;
	}

	public void setBasedir(File basedir) {
		this.basedir = basedir.getAbsoluteFile();
	}

	public File getBasedir() {
		return this.basedir;
	}

	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
	}

	public String[] getSearchPaths() {
		return this.searchPaths;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	protected File getWorkingDirectory() {
		if (this.uri.startsWith("file:")) {
			try {
				return new File(getUri());
			}
			catch (Exception e) {
				throw new IllegalStateException(
						"Cannot convert uri to file: " + this.uri);
			}
		}
		return this.basedir;
	}


}
