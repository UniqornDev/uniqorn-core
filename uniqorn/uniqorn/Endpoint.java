package uniqorn;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.http.HttpException;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Template;
import aeonics.util.Internal;
import aeonics.git.Bare;
import aeonics.git.GitRepo;
import aeonics.util.StringUtils;

public class Endpoint extends Item<Endpoint.Type>
{
	public static class Type extends Entity implements Closeable
	{
		/**
		 * Hardcoded category to the {@link Endpoint} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Endpoint.class); }

		public SnapshotMode snapshotMode() { return SnapshotMode.NONE; }
		public boolean internal() { return false; }

		/**
		 * Checks if the specified method and path route to this endpoint
		 * @param method the http method
		 * @param path the url
		 * @return true if this endpoint matches
		 */
		@Internal
		public boolean matches(String method, String path)
		{
			if( !valueOf("enabled").asBool() ) return false;
			if( api == null ) return false;
			aeonics.http.Endpoint.Rest.Type e = api.api();
			if( e == null ) return false;
			return e.matchesMethod(method) && e.matchesPath(path);
		}

		private AtomicLong counter = new AtomicLong(0);
		/**
		 * Returns the internal call counter
		 * @return the call counter
		 */
		@Internal
		public AtomicLong counter() { return counter; }

		private Api api = null;
		public Api api() { return api; }
		public void api(Api api) { this.api = api; }

		private static GitRepo.Type repo()
		{
			return Registry.of(GitRepo.class).get("uniqorn");
		}

		/**
		 * Delete cascade
		 */
		public void close()
		{
			if( api != null )
			{
				Registry.of(StringUtils.toLowerCase(Api.class)).remove(api.id());
				api = null;
			}
		}

		@Override
		public Data export()
		{
			return super.export().put("api", api == null ? null : api.api().export());
		}

		/**
		 * Returns the source code for this endpoint
		 * @return the source code for this endpoint
		 */
		public String code()
		{
			GitRepo.Type r = repo();
			return new String(Bare.object(r.store(), r.root(), valueOf("sha").asString()).b, StandardCharsets.ISO_8859_1);
		}

		/**
		 * Recompile the current version of the file if the SHA differs
		 */
		public synchronized void updateHead() throws Exception
		{
			GitRepo.Type r = repo();
			String sha = Bare.findFile(r.store(), r.root(), valueOf("path").asString(), null);
			if( sha == null )
			{
				// the file does not exist anymore
				// this endpoint should not exist
				internal(false);
				Registry.of(Endpoint.class).remove(this);
				Manager.of(Logger.class).config(Endpoint.class, "Cannot recompile endpoint {} for {} with missing file. Endpoint removed.", id(), valueOf("path").asString());
				return;
			}

			if( sha.equals(valueOf("sha").asString()) && api != null && api.api() != null )
			{
				Manager.of(Logger.class).config(Endpoint.class, "Skip recompile endpoint {} for {} already up to date", id(), valueOf("path").asString());
				return; // not updated
			}

			// update current sha
			parameter("sha", sha);

			Manager.of(Logger.class).config(Endpoint.class, "Recompile endpoint {} for {}", id(), valueOf("path").asString());
			updateHead(new String(Bare.object(r.store(), r.root(), sha).b, StandardCharsets.ISO_8859_1));
		}

		/**
		 * Update the code and attempt a compile.
		 * The head is only updated if compilation succeeds.
		 * @param code the code
		 */
		public synchronized void updateHead(String code) throws Exception
		{
			// ======================
			// FIRST COMPILE
			aeonics.http.Endpoint.Type jit = Registry.of(aeonics.http.Endpoint.class).get((e) -> e != null && e.url() != null && e.url().equals("/api/admin/jit/entity"));

			if( jit == null )
				throw new IllegalStateException("Publishing is not possible at this time");

			// we need to strip the package declaration and prefix the imports
			String sanitizedCode = IMPORTS + PACKAGE.matcher(code).replaceFirst("");
			Data result = null;
			try
			{
				result = jit.<aeonics.http.Endpoint.Rest.Type>cast().process(Data.map().put("code", sanitizedCode));
			}
			catch(HttpException e)
			{
				Manager.of(Logger.class).config(Api.class, "Deploy failure for {} because of {}",
					valueOf("path").asString(),
					e.data.get("error").asString("message"));
				throw e;
			}
			String id = result.asString("entity_id");

			// ======================
			// THEN SUBSTITUTE API
			if( api != null )
				Registry.of(StringUtils.toLowerCase(Api.class)).remove(api.id());
			api = Registry.of(StringUtils.toLowerCase(Api.class)).get(id);

			// ======================
			// THEN CLEANUP THE DYNAMIC
			Registry.of("aeonics.jit.dynamic").remove(result.asString("id"));

			Manager.of(Logger.class).config(Api.class, "Deploy success for {} deployed as {}", valueOf("path").asString(), fullPath());
		}

		/**
		 * Performs a sanity check on the code and throws an exception if it does not pass.
		 * @param code the code
		 * @throws HttpException if the code does not pass validation
		 */
		public static void checkCode(String code)
		{
			// sanity code check
			for( String word : FORBIDDEN )
				if( code.contains(word) )
					throw new HttpException(400, "Use of restricted language features: " + word);
		}

		/**
		 * Attempts to fetch the containing workspace for this endpoint
		 * @return the workspace or null if not found
		 */
		public Workspace.Type workspace()
		{
			for( Workspace.Type w : Registry.of(Workspace.class) )
				if( w.hasRelation("endpoints", this) )
					return w;
			return null;
		}

		/**
		 * Attempts to return the full path of this endpoint
		 * @return the full path of this endpoint or an empty string if the endpoint is not compiled
		 */
		public String fullPath()
		{
			Api api = api();
			if( api == null ) return "";
			aeonics.http.Endpoint.Type endpoint = api.api();
			if( endpoint == null ) return "";
			Workspace.Type workspace = workspace();

			return endpoint.method() + " " +
				Manager.of(Config.class).get(Api.class, "prefix").asString() +
				(workspace == null ? "" : workspace.valueOf("prefix").asString()) +
				endpoint.url();
		}

		public static final String IMPORTS = "import uniqorn.*; import aeonics.data.*; import aeonics.util.*; import java.util.*; import aeonics.entity.*; "
			+ "import aeonics.template.*; import aeonics.util.Functions.*; import java.util.concurrent.atomic.*; "
			+ "import aeonics.entity.security.*; ";

		public static final List<String> FORBIDDEN = Arrays.asList(
			"reflect", "Method", "Field", "Constructor", "Modifier", "AccessibleObject", "InvocationHandler", "Proxy", "Class", "ClassLoader", "Unsafe", "ServiceLoader", "Module", "com.sun", "Native", "JNI", "MXBean", "SecurityManager", "Permission", "InitialContext", "JNDI", "RMI", "AccessController", "Instrumentation", "MethodHandle", "jdk",
			"Runtime", "Shutdown", "ShutdownHook", "ProcessBuilder", "ProcessHandle", "System",
			"File", "Files", "Path", "Paths", "FileSystem", "RandomAccessFile", "Console", "InputStream", "OutputStream",
			"Thread", "ThreadLocal", "Executor", "Executors", "Callable", "Future", "ForkJoinPool", "Semaphore", "Mutex", "ReentrantLock", "Lock", "Condition", "CyclicBarrier", "CountDownLatch",
			"Socket", "Datagram", "Multicast", "Channel", "URL", "URI",
			"ScriptEngine", "Interpreter", "GroovyShell", "JavaScript", "JavaCompiler", "ToolProvider", "FileManager",
			"Serializable", "Externalizable", "readObject", "writeObject", "resolveClass",
			"goto", "invoke", "eval",
			".jit", "Registry", "Factory", "Manager", ".manager", "Item", "Entity", "Template");

		public static final Pattern PACKAGE = Pattern.compile("\\bpackage[\\w\\.\\s]*;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	}

	protected Class<? extends Endpoint.Type> defaultTarget() { return Endpoint.Type.class; }
	protected Supplier<? extends Endpoint.Type> defaultCreator() { return Endpoint.Type::new; }
	protected Class<? extends Endpoint> category() { return Endpoint.class; }

	@Override
	public Template<? extends Endpoint.Type> template()
	{
		return super.template()
			.summary("Endpoint")
			.description("An endpoint is backed by the internal GIT repository.")
			.add(new Parameter("enabled")
				.summary("Enabled")
				.description("Whether or not this endpoint is actively processing requests.")
				.format(Parameter.Format.BOOLEAN)
				.rule(Parameter.Rule.BOOLEAN)
				.optional(true)
				.defaultValue(true))
			.add(new Parameter("path")
				.summary("Path")
				.description("The path to the source file.")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("sha")
				.summary("SHA")
				.description("The SHA identifier of the source file version.")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ALPHANUM)
				.optional(false))
			;
	}
}
