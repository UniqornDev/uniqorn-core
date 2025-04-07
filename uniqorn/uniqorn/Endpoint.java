package uniqorn;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.http.HttpException;
import aeonics.template.Factory;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Relationship;
import aeonics.template.Template;
import aeonics.util.Internal;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Tuple;

public class Endpoint extends Item<Endpoint.Type>
{
	public static class Type extends Entity implements Closeable
	{
		/**
		 * Hardcoded category to the {@link Endpoint} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Endpoint.class); }
		
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
		
		/**
		 * Delete cascade
		 */
		public void close()
		{
			for( Tuple<Entity, Data> v : relations("versions") )
				Registry.of(Version.class).remove(v.a);
			
			Registry.of(Version.class).remove(firstRelation("head").id());
			
			if( api != null )
				Registry.of(StringUtils.toLowerCase(Api.class)).remove(api.id());
			
			clearRelation("head");
			clearRelation("versions");
			api = null;
		}
		
		@Override
		public Data export()
		{
			return super.export().put("api", api == null ? null : api.api().export());
		}
		
		/**
		 * Create a new version of the current head
		 * @param name the version name
		 * @return the new version
		 */
		public Version.Type tag(String name)
		{
			Version.Type head = firstRelation("head");
			if( head == null )
				throw new IllegalStateException("Endpoint head version is undefined");
			
			Version.Type v = Factory.of(Version.class).get(Version.class).create(
				Data.map()
					.put("name", name)
					.put("parameters", Data.map().put("code", head.valueOf("code")))
				);
			
			addRelation("versions", v);
			return v;
		}
		
		/**
		 * Restore a version as the current head
		 * @param id the version id
		 */
		public void restore(String id) throws Exception
		{
			for( Tuple<Entity, Data> t : relations("versions") )
			{
				if( t.a != null && t.a.id().equals(id) )
				{
					updateHead(t.a.valueOf("code").asString());
					return;
				}
			}
			throw new IllegalArgumentException("Target version not found");
		}
		
		/**
		 * Update, the head version and attempt a compile.
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
			
			Data result = jit.<aeonics.http.Endpoint.Rest.Type>cast().process(Data.map().put("code", IMPORTS + code));
			String id = result.asString("entity_id");
			
			// ======================
			// THEN UPDATE HEAD
			Version.Type head = firstRelation("head");
			if( head == null )
			{
				head = Factory.of(Version.class).get(Version.class).create(Data.map()
					.put("name", "HEAD")
					.put("parameters", Data.map().put("code", code))
					);
				addRelation("head", head);
			}
			else
				head.parameter("code", code);
			
			// ======================
			// THEN SUBSTITUTE API
			if( api != null )
				Registry.of(StringUtils.toLowerCase(Api.class)).remove(api.id());
			api = Registry.of(StringUtils.toLowerCase(Api.class)).get(id);
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
		
		private Api api = null;
		public Api api() { return api; }
		
		private static final String IMPORTS = "import uniqorn.*; import aeonics.data.*; import aeonics.util.*; import java.util.*; import aeonics.entity.*; "
			+ "import aeonics.template.*; import aeonics.util.Functions.*; import java.util.concurrent.atomic.*; "
			+ "import aeonics.entity.security.*; ";
		
		private static final List<String> FORBIDDEN = Arrays.asList(
			"reflect", "Method", "Field", "Constructor", "Modifier", "AccessibleObject", "InvocationHandler", "Proxy", "Class", "ClassLoader", "Unsafe", "ServiceLoader", "Module", "com.sun", "Native", "JNI", "MXBean", "SecurityManager", "Permission", "InitialContext", "JNDI", "RMI", "AccessController", "Instrumentation", "MethodHandle", "jdk",
			"Runtime", "Shutdown", "ShutdownHook", "ProcessBuilder", "ProcessHandle", "System",
			"File", "Files", "Path", "Paths", "FileSystem", "RandomAccessFile", "Console", "InputStream", "OutputStream",
			"Thread", "ThreadLocal", "Executor", "Executors", "Callable", "Future", "ForkJoinPool", "Semaphore", "Mutex", "ReentrantLock", "Lock", "Condition", "CyclicBarrier", "CountDownLatch",
			"Socket", "Datagram", "Multicast", "Channel", "URL", "URI",
			"ScriptEngine", "Interpreter", "GroovyShell", "JavaScript", "JavaCompiler", "ToolProvider", "FileManager",
			"Serializable", "Externalizable", "readObject", "writeObject", "resolveClass",
			"goto", "invoke", "eval",
			".jit", "Registry", "Factory", "Manager", ".manager", "Item", "Entity", "Template");
	}
	
	protected Class<? extends Endpoint.Type> defaultTarget() { return Endpoint.Type.class; }
	protected Supplier<? extends Endpoint.Type> defaultCreator() { return Endpoint.Type::new; }
	protected Class<? extends Endpoint> category() { return Endpoint.class; }

	@Override
	public Template<? extends Endpoint.Type> template()
	{
		return super.template()
			.summary("Endpoint")
			.description("An endpoint is contains one head version and multiple archived versions.")
			.add(new Parameter("enabled")
				.summary("Enabled")
				.description("Whether or not this endpoint is actively processing requests.")
				.format(Parameter.Format.BOOLEAN)
				.rule(Parameter.Rule.BOOLEAN)
				.optional(true)
				.defaultValue(true))
			.add(new Relationship("versions")
				.category(Version.class)
				.summary("Versions")
				.description("The different archived endpoint code versions."))
			.add(new Relationship("head")
				.category(Version.class)
				.summary("Current version")
				.description("The currently applied (head) version of the endpoint.")
				.max(1))
			;
	}
}
