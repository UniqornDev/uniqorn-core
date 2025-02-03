package local;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import uniqorn.Api;

import aeonics.data.Data;
import aeonics.entity.*;
import aeonics.entity.security.User;
import aeonics.http.*;
import aeonics.http.Endpoint.Rest;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Timeout;
import aeonics.manager.Timeout.Tracker;
import aeonics.template.*;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Triple;
import aeonics.util.Tuples.Tuple;

@SuppressWarnings("unused")
public class Endpoints
{
	private Endpoints() { /* no instances */ }
	public static void register()
	{
		// calling this method will force initialization of all private static members
		// all endpoints will be added to the registry automatically
		
		Manager.of(Timeout.class).watch(new Tracker<Void>(null)
		{
			private final int max = 720_000; // 12min
			public long delay()
			{
				long now = System.currentTimeMillis();
				long next = max;
				Iterator<Map.Entry<String, Tuple<AtomicInteger, Long>>> i = endpoints.entrySet().iterator();
				while( i.hasNext() )
				{
					Map.Entry<String, Tuple<AtomicInteger, Long>> entry = i.next(); 
					Tuple<AtomicInteger, Long> e = entry.getValue();
					if( e.b <= (now - max) )
					{
						Registry.of(StringUtils.toLowerCase(Api.class)).remove(entry.getKey());
						Manager.of(Logger.class).info(Api.class, "Instance {} was removed. It has been called {} times.", entry.getKey(), e.a);
						i.remove();
					}
					else
						next = Math.min(next, e.b + max - now + 1);
				}
				return next;
			}
		});
	}
	
	static final Map<String, Tuple<AtomicInteger, Long>> endpoints = new ConcurrentHashMap<>();

	private static final Endpoint.Rest.Type use = new Endpoint.Rest() { }
		.template()
		.summary("Call a public endpoint")
		.description("This endpoint is a proxy to other public endpoints.")
		.add(new Parameter("__euid__").optional(false)
			.summary("Id")
			.description("The endpoint id.")
			.format(Parameter.Format.TEXT)
			)
		.create()
		.<Rest.Type>cast()
		.process((parameters, user, message) ->
		{
			Tuple<AtomicInteger, Long> e = endpoints.get(parameters.asString("__euid__"));
			if( e == null )
			{
				throw new HttpException(404, "This endpoint does not exist anymore");
			}
			else if( e.a.incrementAndGet() > 10 )
			{
				throw new HttpException(429, "The endpoint call limit has been reached");
			}
			else if( e.b <= (System.currentTimeMillis() - 600000) )
			{
				throw new HttpException(429, "The endpoint has expired");
			}
			
			Api api = Registry.of(StringUtils.toLowerCase(Api.class)).get(parameters.asString("__euid__"));
			if( api == null )
			{
				endpoints.remove(parameters.asString("__euid__"));
				throw new HttpException(404,  "The endpoint has been removed");
			}
			
			Endpoint.Rest.Type endpoint = api.api();
			if( endpoint == null )
			{
				endpoints.remove(parameters.asString("__euid__"));
				throw new HttpException(404, "The endpoint target is missing");
			}
			
			message.content().get("get").remove("__euid__");
			return endpoint.process(message);
		})
		.url("/api/public/{__euid__}")
		.method("GET")
		;

	private static final List<String> FORBIDDEN = Arrays.asList(
		// java sensitive things
		"reflect", "Method", "Field", "ClassLoader", "Class", "Unsafe", "Proxy", "Constructor", "Modifier", 
		"AccessibleObject", "InvocationHandler", "ProcessHandle", "ByteBuffer",
		"System", "Runtime", "Path", "Paths", "File", "Files", "Thread", "Socket", "Channel", "ProcessBuilder", "SecurityManager", "Permission", "Executor", 
		"Executors", "Callable", "Future", "PrintStream", "Console", "RandomAccessFile", "ShutdownHook", "Shutdown",
		"Native", "JNI", "InitialContext", "JNDI", "Datagram", "Multicast", "RMI", "ServiceLoader",
		"ThreadLocal", "Semaphore", "Mutex", "ReentrantLock", "Volatile", "ForkJoinPool", "Lock", "Condition", "CyclicBarrier", "CountDownLatch",
		"ScriptEngine", "GroovyShell", "JavaScript", "ToolProvider", "JavaCompiler", "FileManager", "MXBean", "Interpreter",
		"InputStream", "OutputStream", "Serializable", "Externalizable", "readObject", "writeObject", "resolveClass", "static ",
		// keywords
		"goto", "while", "for", "void", "package", "readObject", "writeObject",
		// framework specific
		"Registry", "Factory", "Manager", "Item", "Entity", "Template");
	
	private static final String BEFORE_CODE = "import uniqorn.*; import aeonics.data.*; import aeonics.util.*; import java.util.*; import aeonics.entity.*; "
		+ "import aeonics.manager.*; import aeonics.template.*; import aeonics.util.Functions.*; import java.util.concurrent.atomic.*;\n"
		+ "\n"
		+ "public class Custom implements Supplier<Api> {\n"
		+ "	public Api get() {\n";
	
	private static final String AFTER_CODE = "}}";
	
	private static final Endpoint.Rest.Type deploy = new Endpoint.Rest() { }
		.template()
		.summary("Deploy a public endpoint")
		.description("This endpoint can be used to deploy a public endpoint.")
		.add(new Parameter("code").optional(false)
			.summary("Code")
			.description("The endpoint source code.")
			.format(Parameter.Format.TEXT)
			.max(1024)
			)
		.create()
		.<Rest.Type>cast()
		.process((parameters) ->
		{
			if( endpoints.size() >= 10000 )
				throw new HttpException(500, "Too many endpoints are currently active (about 10,000). Please try again later.");
			
			// sanitize code
			String code = parameters.asString("code");
			for( String word : FORBIDDEN )
				if( code.contains(word) )
					throw new HttpException(400, "Some classes or language structures are not allowed in this simple example for security reasons. Subscribe to the Free Tier to get more possibilities.");
			
			Endpoint.Type jit = Registry.of(Endpoint.class).get((e) -> e != null && e.url() != null && e.url().equals("/api/jit/entity"));
			if( jit == null )
				throw new HttpException(500, "Publishing is not possible at this time.");
			
			Data result = jit.<Endpoint.Rest.Type>cast().process(Data.map().put("code", BEFORE_CODE + code + AFTER_CODE));
			String id = result.asString("entity_id");
			
			endpoints.put(id, Tuple.of(new AtomicInteger(0), System.currentTimeMillis()));
			return Data.map().put("url", "/api/public/" + id);
		})
		.url("/api/public/deploy")
		.method("POST")
		;
}
