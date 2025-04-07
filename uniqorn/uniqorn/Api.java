package uniqorn;

import java.nio.file.Path;
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
	
	public final SnapshotMode snapshotMode() { return SnapshotMode.NONE; }
	public final Endpoint.Rest.Type api() { return api; }
	public final Endpoint.Template apitemplate() { return template; }
	
	public Api(String path, String method)
	{
		if( path == null )
			throw new HttpException(413, "The api URI is invalid");
		
		// normalize the path
		path = Path.of("/" + path).normalize().toString().replace('\\', '/');
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
	
	public static void error(int code, String message)
	{
		throw new HttpException(code, message);
	}
	
	public static void error(int code, Data data)
	{
		throw new HttpException(code, data);
	}

	public static Data chain(String url) throws Exception { return chain(url, "GET", Data.map(), User.SYSTEM); }
	
	public static Data chain(String url, String method) throws Exception { return chain(url, method, Data.map(), User.SYSTEM); }
	
	public static Data chain(String url, String method, Data data) throws Exception { return chain(url, method, data, User.SYSTEM); }
	
	public static Data chain(String url, String method, Data data, User.Type user) throws Exception
	{
		if( !url.startsWith(Manager.of(Config.class).get(Api.class, "prefix").asString()) )
			throw new HttpException(404);
		
		Endpoint.Rest.Type endpoint = Registry.of(Endpoint.class).get(e -> e.matchesMethod(method) && e.matchesPath(url));
		if( endpoint == null ) throw new HttpException(404);
		return endpoint.process(data, user);
	}
	
	public Api process(Function<Data, Object> handler)
	{
		if( handler == null ) throw new HttpException(413, "The endpoint process function is not valid");
		
		final Function<Data, Object> wrapper = (data) ->
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
			
	            return handler.apply(data);
	        }
			catch(HttpException he)
			{
				throw he;
			}
			catch(Exception x)
			{
				Manager.of(Logger.class).log(Logger.INFO, Api.class, x);
				throw new HttpException(500, x.getMessage());
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
	
	public Api summary(String value)
	{
		apitemplate().summary(value);
		return this;
	}
	
	public Api description(String value)
	{
		apitemplate().description(value);
		return this;
	}
	
	public Api returns(String value)
	{
		apitemplate().returns(value); 
		return this;
	}
	
	public Api allowRole(String ...role)
	{
		if( role != null && role.length > 0 )
			Collections.addAll(allowedRoles, role);
		return this;
	}
	
	public Api allowGroup(String ...group)
	{
		if( group != null && group.length > 0 )
			Collections.addAll(allowedGroups, group);
		return this;
	}
	
	public Api allowUser(String ...user)
	{
		if( user != null && user.length > 0 )
			Collections.addAll(allowedUsers, user);
		return this;
	}
	
	public Api denyRole(String ...role)
	{
		if( role != null && role.length > 0 )
			Collections.addAll(deniedRoles, role);
		return this;
	}
	
	public Api denyGroup(String ...group)
	{
		if( group != null && group.length > 0 )
			Collections.addAll(deniedGroups, group);
		return this;
	}
	
	public Api denyUser(String ...user)
	{
		if( user != null && user.length > 0 )
			Collections.addAll(deniedUsers, user);
		return this;
	}
	
	public Api parameter(String name)
	{
		return parameter(name, null);
	}
	
	public Api parameter(String name, Predicate<Data> validator)
	{
		if( name == null || name.isBlank() ) throw new HttpException(413, "The parameter name is empty");
		
		Parameter p = new Parameter(name).optional(true);
		if( validator != null )
			p.validator(validator);
		apitemplate().add(p);
		
		// caution: since the instance is created before the parameter is added to the template
		// then we need to manually add the parameter to the instance too
		api().parameters().put(p.name(), Tuple.of(null, p));
		
		return this;
	}
	
	public Api concurrency(int level)
	{
		concurrency.set(level);
		return this;
	}
	
	private static final ReentrantLock atomicLock = new ReentrantLock();
	public static void atomic(Runnable operation) throws Exception
	{
		atomicLock.lock();
		try { operation.run(); }
		finally { atomicLock.unlock(); }
	}
	
	public static <T> T atomic(Supplier<T> operation) throws Exception
	{
		atomicLock.lock();
		try { return operation.get(); }
		finally { atomicLock.unlock(); }
	}
	
	public static void defer(Runnable operation)
	{
		Manager.of(Executor.class).normal(operation).or(e -> 
		{
			Manager.of(Logger.class).warning(Api.class, e);
		});
	}
	
	public static void log(int level, String message, Object...data)
	{
		Manager.of(Logger.class).log(level, Api.class, message, data);
	}
	
	public static void log(int level, Exception error)
	{
		Manager.of(Logger.class).log(level, Api.class, error);
	}
	
	public static void debug(String tag, Object...data)
	{
		if( data == null || data.length == 0 )
			Debug.stacktrace(tag);
		else
			Debug.debug(tag, data);
	}
	
	public static void metrics(String name)
	{
		Manager.of(Monitor.class).add("Uniqorn", "Api", Monitor.UNSPECIFIED, name, 0);
	}
	
	public static void metrics(String name, long value)
	{
		Manager.of(Monitor.class).add("Uniqorn", "Api", Monitor.UNSPECIFIED, name, value);
	}
	
	public static Data env(String name)
	{
		return Manager.of(Config.class).get(Api.class, "env."+name);
	}
	
	public static Storage.Type storage(String name)
	{
		return Registry.of(Storage.class).get(s ->
		{
			if( !s.type().startsWith("uniqorn.storage.") ) return false;
			return s.name().equals(name);
		});
	}
	
	public static Database.Type database(String name)
	{
		return Registry.of(Database.class).get(d ->
		{
			if( !d.type().startsWith("uniqorn.database.") ) return false;
			return d.name().equals(name);
		});
	}
}
