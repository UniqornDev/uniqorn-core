package local;

import java.time.ZonedDateTime;
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
import aeonics.manager.Security;
import aeonics.manager.Timeout;
import aeonics.manager.Timeout.Tracker;
import aeonics.template.*;
import aeonics.util.Http;
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
				
				// check all captchas as a side kick
				Iterator<Map.Entry<String, Tuple<String, Long>>> j = captchas.entrySet().iterator();
				while( j.hasNext() )
				{
					Map.Entry<String, Tuple<String, Long>> entry = j.next(); 
					Tuple<String, Long> e = entry.getValue();
					if( (System.currentTimeMillis() - e.b) > max )
						j.remove();
				}
				
				return Math.max(next, 1);
			}
		});
	}
	
	static final Map<String, Tuple<String, Long>> captchas = new ConcurrentHashMap<>();
	
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
		.description("This endpoint can be used to deploy a public endpoint. The operation name to use is \"deploy\".")
		.add(new Parameter("code").optional(false)
			.summary("Code")
			.description("The endpoint source code.")
			.format(Parameter.Format.TEXT)
			.max(2000)
			)
		.add(new Parameter("captcha").optional(false)
			.summary("Captcha")
			.description("A valid captcha token for this operation.")
			.format(Parameter.Format.TEXT)
			.max(64)
			)
		.create()
		.<Rest.Type>cast()
		.process((parameters) ->
		{
			Tuple<String, Long> captcha = captchas.remove(parameters.asString("captcha"));
			if( captcha == null )
				throw new HttpException(400, "Invalid captcha (Missing or expired)");
			if( System.currentTimeMillis() - captcha.b < 1000 )
				throw new HttpException(400, "Invalid captcha (Too fast)");
			if( !captcha.a.equals("deploy") )
				throw new HttpException(400, "Invalid captcha (Operation mismatch)");
			
			if( endpoints.size() >= 100 )
				throw new HttpException(500, "Too many endpoints are currently active. Please try again later.");
			
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

	private static final Endpoint.Rest.Type status = new Endpoint.Rest() { }
		.template()
		.summary("Status")
		.description("This endpoint returns the status metrics for the past 90 days.")
		.create()
		.<Rest.Type>cast()
		.process(() ->
		{
			Data status = Data.map();
			Data uptime = Data.list();
			
			for( int i = 0; i < 89; i++ )
				uptime.add(1440);
			
			// latest is proportional to current time
			ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("UTC"));
			uptime.add(now.getHour() * 60 + now.getMinute());
			
			status.put("website", uptime);
			status.put("internal_api", uptime);
			status.put("public_api", uptime);
			
			return status;
		})
		.url("/api/status")
		.method("GET")
		;
	
	private static final Endpoint.Rest.Type incidents = new Endpoint.Rest() { }
		.template()
		.summary("Incidents")
		.description("This endpoint returns the incidents for the past 10 days.")
		.create()
		.<Rest.Type>cast()
		.process(() ->
		{
			Data incidents = Data.list();
			
			/*
			 [
			{date: 1738597663200, level: 'ok', title: 'Up and running', summary: 'Checks complete.'},
			{date: 1738597663200, level: 'warn', title: 'Maintenance', summary: 'Planned maintenance, no impact foreseen.'},
			{date: 1738597573200, level: 'nok', title: 'Incident', summary: 'Server rebooted due to rollback situation. The latest snapshot has been reloaded.'},
		], 
			 */
			
			for( int i = 0; i < 10; i++ )
				incidents.add(Data.list());
			
			return incidents;
		})
		.url("/api/incidents")
		.method("GET")
		;
	
	private static final Endpoint.Rest.Type captcha = new Endpoint.Rest() { }
		.template()
		.summary("Captcha")
		.description("Fetch a captcha token to validate other actions.")
		.add(new Parameter("op").optional(false)
				.summary("Operation")
				.description("The target operation to generate a token for.")
				.format(Parameter.Format.TEXT)
				.max(20)
				)
		.create()
		.<Rest.Type>cast()
		.process((parameters) ->
		{
			if( captchas.size() > 10000 )
				throw new HttpException(503, "Too many requests pending");
			
			String token = Manager.of(Security.class).randomHash();
			captchas.put(token, Tuple.of(parameters.asString("op"), System.currentTimeMillis()));
			
			return Data.map().put("token", token);
		})
		.url("/api/captcha")
		.method("GET")
		;
	
	private static final Endpoint.Rest.Type contact = new Endpoint.Rest() { }
		.template()
		.summary("Contact form")
		.description("Send a message to the team. The operation name to use is \"contact\".")
		.add(new Parameter("name").optional(true)
				.summary("Name")
				.description("Name of the sender.")
				.format(Parameter.Format.TEXT)
				.max(50)
				)
		.add(new Parameter("topic").optional(true)
				.summary("Subject")
				.description("Subject of the message.")
				.format(Parameter.Format.TEXT)
				.max(20)
				)
		.add(new Parameter("text").optional(true)
				.summary("Content")
				.description("Content of the message.")
				.format(Parameter.Format.TEXT)
				.max(2000)
				)
		.add(new Parameter("email").optional(true)
				.summary("Email")
				.description("Reply address.")
				.format(Parameter.Format.TEXT)
				.max(200)
				)
		.add(new Parameter("rating").optional(true)
				.summary("Rating")
				.description("Rating of the service.")
				.format(Parameter.Format.TEXT)
				.max(20)
				)
		.add(new Parameter("captcha").optional(false)
				.summary("Captcha")
				.description("A valid captcha token for this operation.")
				.format(Parameter.Format.TEXT)
				.max(64)
				)
		.create()
		.<Rest.Type>cast()
		.process((parameters) ->
		{
			Tuple<String, Long> captcha = captchas.remove(parameters.asString("captcha"));
			if( captcha == null )
				throw new HttpException(400, "Invalid captcha (Missing or expired)");
			if( System.currentTimeMillis() - captcha.b < 2000 )
				throw new HttpException(400, "Invalid captcha (Too fast)");
			if( !captcha.a.equals("contact") )
				throw new HttpException(400, "Invalid captcha (Operation mismatch)");
			
			try
			{
				String message = "";
				
				String email = parameters.asString("email");
				if( email.length() >= 50 || email.length() <= 6 )
				{
					message += "\nEmail: " + email;
					email = "contact@aeonics.io";
				}
				
				String name = parameters.asString("name");
				if( name.length() >= 50 || name.length() <= 1 )
				{
					message += "\nName: " + name;
					name = "Anonymous";
				}
				
				message += "\nRating: " + parameters.asString("rating")
					+ "\nTopic: " + parameters.asString("topic")
					+ "\nText: " + parameters.asString("text");
				
				if( message.length() > 5000 )
					message = message.substring(0, 4950);
				
				Http.post("https://aeonics.io/api/contact", Data.map()
					.put("name", name) // 1-50
					.put("company", "-")
					.put("email", email) // 6-50
					.put("tel", "-")
					.put("message", message) // 5-5000
					.put("gtoken", "f33130eb1d6db8e8c4cb0eee1585aaf2e93096193191438174862cecf7dab04a")
					, null, "POST", 5000);
			}
			catch(Exception e)
			{
				Manager.of(Logger.class).warning(uniqorn.Api.class, e);
				Manager.of(Logger.class).warning(uniqorn.Api.class, parameters);
			}
			
			return Data.map().put("success", true);
		})
		.url("/api/contact")
		.method("POST")
		;
}
