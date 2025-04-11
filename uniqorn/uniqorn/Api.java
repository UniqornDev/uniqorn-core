package uniqorn;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import aeonics.data.*;
import aeonics.entity.security.User;
import aeonics.http.*;
import aeonics.http.Endpoint;
import aeonics.manager.Config;
import aeonics.manager.Executor;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Monitor;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.entity.*;
import aeonics.util.Functions.BiFunction;
import aeonics.util.Functions.Function;
import aeonics.util.Functions.Predicate;
import aeonics.util.Functions.Runnable;
import aeonics.util.Functions.Supplier;
import aeonics.util.Tuples.Tuple;
import aeonics.util.StringUtils;

/**
 * This class is the main API endpoint builder.
 */
public class Api extends Entity
{
	private final Endpoint.Rest.Type api;
	private final Endpoint.Template template;
	
	private final Set<String> allowedRoles = new HashSet<>();
	private final Set<String> allowedGroups = new HashSet<>();
	private final Set<String> allowedUsers = new HashSet<>();
	private final Set<String> deniedRoles = new HashSet<>();
	private final Set<String> deniedGroups = new HashSet<>();
	private final Set<String> deniedUsers = new HashSet<>();
	private final AtomicInteger concurrency = new AtomicInteger(-1);
	private final AtomicInteger active = new AtomicInteger(0);
	
	private void securityCheck(Data data, User.Type user)
	{
		boolean allowed = false;
		
		for( String u : deniedUsers )
			if( u != null && !u.isBlank() && (user.id().equalsIgnoreCase(u) || user.name().equalsIgnoreCase(u) || user.login().equalsIgnoreCase(u)) )
				throw new HttpException(403, "Access denied");
		
		for( String u : allowedUsers )
			if( u != null && !u.isBlank() && (user.id().equalsIgnoreCase(u) || user.name().equalsIgnoreCase(u) || user.login().equalsIgnoreCase(u)) )
				allowed = true;
		
		for( String role : deniedRoles )
			if( role != null && !role.isBlank() && user.hasRole(role) )
				throw new HttpException(403, "Access denied");
		
		for( String role : allowedRoles )
			if( role != null && !role.isBlank() && user.hasRole(role) )
				allowed = true;
		
		for( String group : deniedGroups )
			if( group != null && !group.isBlank() && user.isMemberOf(group) )
				throw new HttpException(403, "Access denied");
		
		for( String group : allowedGroups )
			if( group != null && !group.isBlank() && user.isMemberOf(group) )
				allowed = true;
		
		// allow by default if there is no other allow
		if( allowedUsers.isEmpty() && allowedRoles.isEmpty() && allowedGroups.isEmpty() )
			allowed = true;
		
		if( !allowed ) throw new HttpException(403, "Access denied");
	}
	
	/**
	 * @hidden
	 */
	public final SnapshotMode snapshotMode() { return SnapshotMode.NONE; }
	/**
	 * @hidden
	 */
	public final Endpoint.Rest.Type api() { return api; }
	/**
	 * @hidden
	 */
	public final Endpoint.Template apitemplate() { return template; }
	
	/**
	 * Creates a new API endpoint with the provided path and method
	 * @param path the path to the endpoint. The path will be prefixed with the global prefix and workspace prefix.
	 * @param method the HTTP method
	 */
	public Api(String path, String method)
	{
		if( path == null )
			throw new HttpException(413, "The api URI is invalid");
		
		// normalize the path
		path = "/" + Storage.normalize(path).replace('\\', '/');
		if( path.isBlank() || path.length() <= 1 )
			throw new HttpException(413, "The api URI is invalid");
		
		if( method == null || method.isBlank() )
			throw new HttpException(413, "The api method is invalid");
		
		// set the entity category
		initialize(StringUtils.toLowerCase(Api.class), StringUtils.toLowerCase(Api.class), null, true);
		template = new Endpoint.Rest() { }
			.template();
		Factory.of(Endpoint.class).remove(template.type());
		
		api = template.create()
			.<Endpoint.Rest.Type>cast()
			.before(this::securityCheck)
			.url(path)
			.method(method);
		api.internal(false);
		Registry.of(Endpoint.class).remove(api);
		Registry.add(this);
	}
	
	/**
	 * Raises an error with the specified HTTP status code and message
	 * @param code the HTTP error code
	 * @param message the message
	 */
	public static void error(int code, String message)
	{
		throw new HttpException(code, message);
	}
	
	/**
	 * Raises an error with the specified HTTP status code and detailed info
	 * @param code the HTTP error code
	 * @param data more information about the error
	 */
	public static void error(int code, Data data)
	{
		throw new HttpException(code, data);
	}
	
	/**
	 * Raises an error with the specified HTTP status code and detailed info from the original error
	 * @param code the HTTP error code
	 * @param error the root cause error
	 */
	public static void error(int code, Exception error)
	{
		throw new HttpException(code, error);
	}

	/**
	 * Call another endpoint using the GET method
	 * @param url the other endpoint path
	 * @return the other endpoint response
	 * @throws Exception in case of error
	 */
	public static Data chain(String url) throws Exception { return chain(url, "GET", Data.map(), User.SYSTEM); }
	
	/**
	 * Call another endpoint
	 * @param url the other endpoint path
	 * @param method the HTTP method
	 * @return the other endpoint response
	 * @throws Exception in case of error
	 */
	public static Data chain(String url, String method) throws Exception { return chain(url, method, Data.map(), User.SYSTEM); }
	
	/**
	 * Call another endpoint
	 * @param url the other endpoint path
	 * @param method the HTTP method
	 * @param data the parameters
	 * @return the other endpoint response
	 * @throws Exception in case of error
	 */
	public static Data chain(String url, String method, Data data) throws Exception { return chain(url, method, data, User.SYSTEM); }
	
	/**
	 * Call another endpoint
	 * @param url the other endpoint path
	 * @param method the HTTP method
	 * @param data the parameters
	 * @param user the authenticated user
	 * @return the other endpoint response
	 * @throws Exception in case of error
	 */
	public static Data chain(String url, String method, Data data, User.Type user) throws Exception
	{
		if( !url.startsWith(Manager.of(Config.class).get(Api.class, "prefix").asString()) )
			throw new HttpException(404);
		
		Endpoint.Rest.Type endpoint = Registry.of(Endpoint.class).get(e -> e.matchesMethod(method) && e.matchesPath(url));
		if( endpoint == null ) throw new HttpException(404);
		
		return endpoint.process(new Message(url)
			.content(Data.map().put("method", method).put("path", url).put("get", data))
			.user(user == null ? User.ANONYMOUS.id() : user.id())
		);
	}
	
	/**
	 * Set the API processing function.
	 * <pre>api.process(() -&gt; "OK");</pre>
	 * @param handler the process function that accepts a JSON object with populated values based on declared {@link #parameter(String)}.
	 * 		The function should return the endpoint response.
	 * @return this
	 */
	public Api process(Supplier<Object> handler)
	{
		if( handler == null ) throw new HttpException(413, "The endpoint process function is not valid");
		process((data, user) -> handler.get());
		return this;
	}
	
	/**
	 * Set the API processing function.
	 * <pre>api.process(data -&gt; "OK");</pre>
	 * @param handler the process function that accepts a JSON object with populated values based on declared {@link #parameter(String)}.
	 * 		The function should return the endpoint response.
	 * @return this
	 */
	public Api process(Function<Data, Object> handler)
	{
		if( handler == null ) throw new HttpException(413, "The endpoint process function is not valid");
		process((data, user) -> handler.apply(data));
		return this;
	}
	
	/**
	 * Set the API processing function.
	 * <pre>api.process((data, user) -&gt; "OK");</pre>
	 * @param handler the process function that accepts a JSON object with populated values based on declared {@link #parameter(String)}, and the authenticated user.
	 * 		The function should return the endpoint response.
	 * @return this
	 */
	public Api process(BiFunction<Data, User.Type, Object> handler)
	{
		if( handler == null ) throw new HttpException(413, "The endpoint process function is not valid");
		
		final BiFunction<Data, User.Type, Object> wrapper = (data, user) ->
		{
			try
			{
				State.api.set(api().id());
				synchronized (this)
				{
		            while (concurrency.get() > 0 && active.get() >= concurrency.get())
		                wait();
		            active.incrementAndGet();
		        }
			
	            return handler.apply(data, user);
	        }
			catch(HttpException he)
			{
				throw he;
			}
			catch(Exception x)
			{
				Manager.of(Logger.class).log(Logger.INFO, Api.class, x);
				throw new HttpException(500, x);
			}
			finally
			{
				State.api.set(null);
	            synchronized (this)
	            {
	            	active.decrementAndGet();
	                notifyAll();
	            }
	        }
		};
		
		api().process(wrapper);
		return this;
	}
	
	/**
	 * Set the endpoint display name
	 * @param value the endpoint name
	 * @return this
	 */
	public Api summary(String value)
	{
		apitemplate().summary(value);
		return this;
	}
	
	/**
	 * Set the endpoint description
	 * @param value the endpoint description
	 * @return this
	 */
	public Api description(String value)
	{
		apitemplate().description(value);
		return this;
	}
	
	/**
	 * Sets the description of returned values
	 * @param value the description of returned values
	 * @return this
	 */
	public Api returns(String value)
	{
		apitemplate().returns(value); 
		return this;
	}
	
	/**
	 * Allows the specified roles to access this endpoint
	 * @param role the list of roles
	 * @return this
	 */
	public Api allowRole(String ...role)
	{
		if( role != null && role.length > 0 )
			Collections.addAll(allowedRoles, role);
		return this;
	}
	
	/**
	 * Allows the specified groups to access this endpoint
	 * @param group the list of groups
	 * @return this
	 */
	public Api allowGroup(String ...group)
	{
		if( group != null && group.length > 0 )
			Collections.addAll(allowedGroups, group);
		return this;
	}
	
	/**
	 * Allows the specified consumer users to access this endpoint
	 * @param user the list of consumer users
	 * @return this
	 */
	public Api allowUser(String ...user)
	{
		if( user != null && user.length > 0 )
			Collections.addAll(allowedUsers, user);
		return this;
	}
	
	/**
	 * Denies access to this endpoint to the specified roles
	 * @param role the list of roles
	 * @return this
	 */
	public Api denyRole(String ...role)
	{
		if( role != null && role.length > 0 )
			Collections.addAll(deniedRoles, role);
		return this;
	}
	
	/**
	 * Denies access to this endpoint to the specified groups
	 * @param group the list of groups
	 * @return this
	 */
	public Api denyGroup(String ...group)
	{
		if( group != null && group.length > 0 )
			Collections.addAll(deniedGroups, group);
		return this;
	}
	
	/**
	 * Denies access to this endpoint to the specified consumer users
	 * @param user the list of consumer users
	 * @return this
	 */
	public Api denyUser(String ...user)
	{
		if( user != null && user.length > 0 )
			Collections.addAll(deniedUsers, user);
		return this;
	}
	
	/**
	 * Declares a parameter for this endpoint
	 * @param name the parameter name
	 * @return this
	 */
	public Api parameter(String name)
	{
		return parameter(name, null, null);
	}
	
	/**
	 * Declares a parameter for this endpoint
	 * @param name the parameter name
	 * @param description the parameter description
	 * @return this
	 */
	public Api parameter(String name, String description)
	{
		return parameter(name, description, null);
	}
	
	/**
	 * Declares a parameter for this endpoint
	 * @param name the parameter name
	 * @param validator the parameter validation function
	 * @return this
	 */
	public Api parameter(String name, Predicate<Data> validator)
	{
		return parameter(name, null, validator);
	}
	
	/**
	 * Declares a parameter for this endpoint
	 * @param name the parameter name
	 * @param description the parameter description
	 * @param validator the parameter validation function
	 * @return this
	 */
	public Api parameter(String name, String description, Predicate<Data> validator)
	{
		if( name == null || name.isBlank() ) throw new HttpException(413, "The parameter name is empty");
		
		Parameter p = new Parameter(name).optional(true).description(description);
		if( validator != null )
			p.validator(validator);
		apitemplate().add(p);
		
		// caution: since the instance is created before the parameter is added to the template
		// then we need to manually add the parameter to the instance too
		api().parameters().put(p.name(), Tuple.of(null, p));
		
		return this;
	}
	
	/**
	 * Sets the maximum concurrency level for this endpoint
	 * @param level the concurrency level
	 * @return this
	 */
	public Api concurrency(int level)
	{
		concurrency.set(level);
		return this;
	}
	
	private static final ReentrantLock atomicLock = new ReentrantLock();
	
	/**
	 * Executes the specified function atomically, blocking other concurrent requests in the mean time
	 * @param operation the function to run
	 * @throws Exception if anything happens
	 */
	public static void atomic(Runnable operation) throws Exception
	{
		atomicLock.lock();
		try { operation.run(); }
		finally { atomicLock.unlock(); }
	}
	
	/**
	 * Executes the specified function atomically, blocking other concurrent requests in the mean time
	 * @param <T> the function return type
	 * @param operation the function to run and get the response
	 * @return this
	 * @throws Exception if anything happens
	 */
	public static <T> T atomic(Supplier<T> operation) throws Exception
	{
		atomicLock.lock();
		try { return operation.get(); }
		finally { atomicLock.unlock(); }
	}
	
	/**
	 * Executes the specified function in the background
	 * @param operation the function to run
	 */
	public static void defer(Runnable operation)
	{
		Manager.of(Executor.class).normal(operation).or(e -> 
		{
			Manager.of(Logger.class).warning(Api.class, e);
		});
	}
	
	/**
	 * Sends the specified message to the log stream.
	 * If the message contains <code>{}</code> placeholders, they will be replaced by the additional data.
	 * @param level the log level (0 - finest to 1000 - severe)
	 * @param message the message to log
	 * @param data additional data to substitute in the message
	 */
	public static void log(int level, String message, Object...data)
	{
		Manager.of(Logger.class).log(level, Api.class, message, data);
	}
	
	/**
	 * Sends the specified error to the log stream
	 * @param level the log level (0 - finest to 1000 - severe)
	 * @param error the error to log
	 */
	public static void log(int level, Exception error)
	{
		Manager.of(Logger.class).log(level, Api.class, error);
	}
	
	/**
	 * Sends the specified data to the debug stream
	 * @param tag the debug tag
	 * @param data values to debug
	 */
	public static void debug(String tag, Object...data)
	{
		if( data == null || data.length == 0 )
			Debug.stacktrace(tag);
		else
			Debug.debug(tag, data);
	}
	
	/**
	 * Increments the hit counter for the specified metric
	 * @param name the metric name
	 */
	public static void metrics(String name)
	{
		Manager.of(Monitor.class).add("Uniqorn", "Api", Monitor.UNSPECIFIED, name, 0);
	}
	
	/**
	 * Increases the value of the specified metric by the given value
	 * @param name the metric name
	 * @param value the value
	 */
	public static void metrics(String name, long value)
	{
		Manager.of(Monitor.class).add("Uniqorn", "Api", Monitor.UNSPECIFIED, name, value);
	}
	
	/**
	 * Fetches a custom environment variable
	 * @param name the variable name
	 * @return the value or an empty data object if not found
	 */
	public static Data env(String name)
	{
		return Manager.of(Config.class).get(Api.class, "env." + name);
	}
	
	/**
	 * Fetches a storage implementation
	 * @param name the storage name
	 * @return the storage or null if not found
	 */
	public static Storage.Type storage(String name)
	{
		return Registry.of(Storage.class).get(s ->
		{
			if( !s.type().startsWith("uniqorn.storage.") ) return false;
			return s.name().equals(name);
		});
	}
	
	/**
	 * Fetches a database implementation
	 * @param name the database name
	 * @return the database or null if not found
	 */
	public static Database.Type database(String name)
	{
		return Registry.of(Database.class).get(d ->
		{
			if( !d.type().startsWith("uniqorn.database.") ) return false;
			return d.name().equals(name);
		});
	}
}
